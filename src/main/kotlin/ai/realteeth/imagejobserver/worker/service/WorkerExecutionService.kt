package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.client.mockworker.MockWorkerClient
import ai.realteeth.imagejobserver.client.mockworker.MockWorkerException
import ai.realteeth.imagejobserver.client.mockworker.dto.MockWorkerJobStatus
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import ai.realteeth.imagejobserver.job.domain.JobStatus
import ai.realteeth.imagejobserver.job.service.JobService
import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import ai.realteeth.imagejobserver.worker.repository.WorkerClaimRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class WorkerExecutionService(
    private val jobService: JobService,
    private val mockWorkerClient: MockWorkerClient,
    private val workerClaimRepository: WorkerClaimRepository,
    private val workerProperties: WorkerProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(jobId: UUID) {
        var job = jobService.findById(jobId) ?: return

        if (job.status != JobStatus.RUNNING) {
            return
        }

        if (job.lockedBy != workerProperties.id) {
            return
        }

        val startedAtNanos = System.nanoTime()

        try {
            val externalJobId = if (job.externalJobId == null) {
                val startResponse = executeWithRetry(jobId) {
                    mockWorkerClient.startProcess(job.imageUrl)
                }
                jobService.saveExternalJobId(jobId, startResponse.jobId)
                startResponse.jobId
            } else {
                job.externalJobId!!
            }

            while (true) {
                job = jobService.findById(jobId) ?: return
                if (job.status != JobStatus.RUNNING || job.lockedBy != workerProperties.id) {
                    return
                }

                if (isProcessingTimedOut(startedAtNanos)) {
                    log.warn(
                        "Processing timeout reached for job {}, abandon current execution for stale recovery",
                        jobId,
                    )
                    return
                }

                if (!safeExtendLease(jobId)) {
                    log.warn("Lease extension failed for job {}, abandon current execution", jobId)
                    return
                }

                val statusResponse = executeWithRetry(jobId) {
                    mockWorkerClient.getProcessStatus(externalJobId)
                }

                when (statusResponse.status) {
                    MockWorkerJobStatus.PROCESSING -> {
                        if (!sleepNextPoll()) {
                            return
                        }
                    }

                    MockWorkerJobStatus.COMPLETED -> {
                        jobService.completeSucceeded(jobId, statusResponse.result ?: "")
                        return
                    }

                    MockWorkerJobStatus.FAILED -> {
                        jobService.completeFailed(
                            jobId = jobId,
                            errorCode = JobErrorCode.MOCK_WORKER_FAILED,
                            message = statusResponse.result ?: "Mock Worker returned FAILED",
                        )
                        return
                    }
                }
            }
        } catch (ex: LeaseLostException) {
            log.warn("Lease lost while running job {}, abandon current execution", jobId)
        } catch (ex: MockWorkerException) {
            log.warn("Worker failed for job {}: {}", jobId, ex.message, ex)
            jobService.completeFailed(
                jobId = jobId,
                errorCode = ex.errorCode,
                message = ex.message ?: "Mock Worker call failed",
            )
        } catch (ex: Exception) {
            log.error("Unexpected worker error for job {}", jobId, ex)
            jobService.completeFailed(
                jobId = jobId,
                errorCode = JobErrorCode.INTERNAL,
                message = ex.message ?: "Unexpected error",
            )
        }
    }

    private fun isProcessingTimedOut(startedAtNanos: Long): Boolean {
        val elapsedNanos = System.nanoTime() - startedAtNanos
        val maxNanos = Duration.ofSeconds(workerProperties.maxProcessingSeconds.toLong()).toNanos()
        return elapsedNanos >= maxNanos
    }

    private fun safeExtendLease(jobId: UUID): Boolean {
        return try {
            workerClaimRepository.extendLease(jobId, workerProperties.id, workerProperties.leaseSeconds)
        } catch (ex: Exception) {
            log.warn("Lease extension exception for job {}", jobId, ex)
            false
        }
    }

    private fun sleepNextPoll(): Boolean {
        return try {
            Thread.sleep(workerProperties.statusPollIntervalMs)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun <T> executeWithRetry(jobId: UUID, action: () -> T): T {
        var consumedAttempts = jobService.findById(jobId)?.attemptCount ?: 0
        var delayMs = 500L

        while (true) {
            try {
                return action()
            } catch (ex: MockWorkerException) {
                if (!ex.retryable) {
                    throw ex
                }

                consumedAttempts += 1
                jobService.incrementAttemptCount(jobId)

                if (consumedAttempts >= workerProperties.maxAttempts) {
                    throw MockWorkerException(
                        errorCode = ex.errorCode,
                        retryable = false,
                        message = ex.message ?: "Retry exhausted",
                        cause = ex,
                    )
                }

                if (!safeExtendLease(jobId)) {
                    throw LeaseLostException()
                }

                try {
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw LeaseLostException(cause = ie)
                }

                delayMs = (delayMs * 2).coerceAtMost(8_000)
            }
        }
    }

    private class LeaseLostException(cause: Throwable? = null) : RuntimeException(cause)
}
