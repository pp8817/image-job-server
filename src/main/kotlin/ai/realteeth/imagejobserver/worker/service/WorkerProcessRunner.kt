package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.client.mockworker.MockWorkerClient
import ai.realteeth.imagejobserver.client.mockworker.dto.MockWorkerJobStatus
import ai.realteeth.imagejobserver.job.domain.JobEntity
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import ai.realteeth.imagejobserver.job.service.JobService
import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class WorkerProcessRunner(
    private val jobService: JobService,
    private val mockWorkerClient: MockWorkerClient,
    private val workerRetryExecutor: WorkerRetryExecutor,
    private val workerLeaseManager: WorkerLeaseManager,
    private val workerProperties: WorkerProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun run(jobId: UUID, initialJob: JobEntity) {
        val startedAtNanos = System.nanoTime()
        val externalJobId = resolveExternalJobId(jobId, initialJob)

        while (true) {
            val job = jobService.findById(jobId) ?: return
            if (!workerLeaseManager.isOwnedRunningJob(job)) {
                return
            }

            if (isProcessingTimedOut(startedAtNanos)) {
                log.warn(
                    "Processing timeout reached for job {}, abandon current execution for stale recovery",
                    jobId,
                )
                return
            }

            if (!workerLeaseManager.safeExtendLease(jobId)) {
                log.warn("Lease extension failed for job {}, abandon current execution", jobId)
                return
            }

            val statusResponse = workerRetryExecutor.execute(jobId) {
                mockWorkerClient.getProcessStatus(externalJobId)
            }

            when (statusResponse.status) {
                MockWorkerJobStatus.PROCESSING -> {
                    if (!workerLeaseManager.sleepNextPoll()) {
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
    }

    private fun resolveExternalJobId(jobId: UUID, job: JobEntity): String {
        return job.externalJobId ?: run {
            val startResponse = workerRetryExecutor.execute(jobId) {
                mockWorkerClient.startProcess(job.imageUrl)
            }
            jobService.saveExternalJobId(jobId, startResponse.jobId)
            startResponse.jobId
        }
    }

    private fun isProcessingTimedOut(startedAtNanos: Long): Boolean {
        val elapsedNanos = System.nanoTime() - startedAtNanos
        val maxNanos = Duration.ofSeconds(workerProperties.maxProcessingSeconds.toLong()).toNanos()
        return elapsedNanos >= maxNanos
    }
}
