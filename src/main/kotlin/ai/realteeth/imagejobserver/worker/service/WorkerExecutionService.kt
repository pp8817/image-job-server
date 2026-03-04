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
        val job = jobService.findById(jobId) ?: return

        if (job.status != JobStatus.RUNNING) {
            return
        }

        if (job.lockedBy != workerProperties.id) {
            return
        }

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

            workerClaimRepository.extendLease(jobId, workerProperties.id, workerProperties.leaseSeconds)

            val statusResponse = executeWithRetry(jobId) {
                mockWorkerClient.getProcessStatus(externalJobId)
            }

            when (statusResponse.status) {
                MockWorkerJobStatus.PROCESSING -> {
                    workerClaimRepository.extendLease(jobId, workerProperties.id, workerProperties.leaseSeconds)
                }

                MockWorkerJobStatus.COMPLETED -> {
                    jobService.completeSucceeded(jobId, statusResponse.result ?: "")
                }

                MockWorkerJobStatus.FAILED -> {
                    jobService.completeFailed(
                        jobId = jobId,
                        errorCode = JobErrorCode.MOCK_WORKER_FAILED,
                        message = statusResponse.result ?: "Mock Worker returned FAILED",
                    )
                }
            }
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

                workerClaimRepository.extendLease(jobId, workerProperties.id, workerProperties.leaseSeconds)

                try {
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw MockWorkerException(
                        errorCode = JobErrorCode.INTERNAL,
                        retryable = false,
                        message = "Retry interrupted",
                        cause = ie,
                    )
                }

                delayMs = (delayMs * 2).coerceAtMost(8_000)
            }
        }
    }
}
