package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import ai.realteeth.imagejobserver.worker.repository.WorkerClaimRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.Executor

@Component
class WorkerScheduler(
    private val workerProperties: WorkerProperties,
    private val workerClaimRepository: WorkerClaimRepository,
    private val workerExecutionService: WorkerExecutionService,
    @Qualifier("workerTaskExecutor") private val workerTaskExecutor: Executor,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.worker.poll-interval-ms:1000}")
    fun pollAndDispatch() {
        if (!workerProperties.enabled) {
            return
        }

        val stale = workerClaimRepository.requeueStaleRunningJobs(
            batchSize = workerProperties.batchSize,
            maxAttempts = workerProperties.maxAttempts,
        )
        if (stale.isNotEmpty()) {
            log.info("Requeued {} stale jobs", stale.size)
        }

        val claimed = workerClaimRepository.claimQueuedJobs(
            workerId = workerProperties.id,
            leaseSeconds = workerProperties.leaseSeconds,
            batchSize = workerProperties.batchSize,
        )

        claimed.forEach { jobId ->
            workerTaskExecutor.execute {
                workerExecutionService.execute(jobId)
            }
        }
    }
}
