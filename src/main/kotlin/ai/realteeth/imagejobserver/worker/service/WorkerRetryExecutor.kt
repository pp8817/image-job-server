package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.client.mockworker.MockWorkerException
import ai.realteeth.imagejobserver.job.service.JobService
import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WorkerRetryExecutor(
    private val jobService: JobService,
    private val workerLeaseManager: WorkerLeaseManager,
    private val workerProperties: WorkerProperties,
) {

    fun <T> execute(jobId: UUID, action: () -> T): T {
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

                workerLeaseManager.extendLeaseOrThrow(jobId)

                try {
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw LeaseLostException(ie)
                }

                delayMs = (delayMs * 2).coerceAtMost(8_000)
            }
        }
    }
}
