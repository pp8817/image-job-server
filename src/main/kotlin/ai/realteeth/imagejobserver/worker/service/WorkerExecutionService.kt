package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.client.mockworker.MockWorkerException
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import ai.realteeth.imagejobserver.job.service.JobService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WorkerExecutionService(
    private val jobService: JobService,
    private val workerProcessRunner: WorkerProcessRunner,
    private val workerLeaseManager: WorkerLeaseManager,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(jobId: UUID) {
        val job = jobService.findById(jobId) ?: return
        if (!workerLeaseManager.isOwnedRunningJob(job)) {
            return
        }

        try {
            workerProcessRunner.run(jobId, job)
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
}
