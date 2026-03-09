package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.job.domain.JobEntity
import ai.realteeth.imagejobserver.job.domain.JobStatus
import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import ai.realteeth.imagejobserver.worker.repository.WorkerClaimRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class WorkerLeaseManager(
    private val workerClaimRepository: WorkerClaimRepository,
    private val workerProperties: WorkerProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun isOwnedRunningJob(job: JobEntity): Boolean {
        return job.status == JobStatus.RUNNING && job.lockedBy == workerProperties.id
    }

    fun safeExtendLease(jobId: UUID): Boolean {
        return try {
            workerClaimRepository.extendLease(jobId, workerProperties.id, workerProperties.leaseSeconds)
        } catch (ex: Exception) {
            log.warn("Lease extension exception for job {}", jobId, ex)
            false
        }
    }

    fun extendLeaseOrThrow(jobId: UUID) {
        if (!safeExtendLease(jobId)) {
            throw LeaseLostException()
        }
    }

    fun nextPollAt(): Instant = Instant.now().plusMillis(workerProperties.statusPollIntervalMs)

    fun workerId(): String = workerProperties.id
}
