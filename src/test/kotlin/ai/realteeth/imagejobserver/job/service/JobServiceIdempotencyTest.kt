package ai.realteeth.imagejobserver.job.service

import ai.realteeth.imagejobserver.global.util.HashUtils
import ai.realteeth.imagejobserver.job.domain.JobEntity
import ai.realteeth.imagejobserver.job.domain.JobStatus
import ai.realteeth.imagejobserver.job.repository.InsertOrGetJobResult
import ai.realteeth.imagejobserver.job.repository.JobInsertRepository
import ai.realteeth.imagejobserver.job.repository.JobJpaRepository
import ai.realteeth.imagejobserver.job.repository.JobResultJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class JobServiceIdempotencyTest {

    private val jobRepository: JobJpaRepository = mock()
    private val jobResultRepository: JobResultJpaRepository = mock()
    private val jobInsertRepository: JobInsertRepository = mock()

    private val jobService = JobService(jobRepository, jobResultRepository, jobInsertRepository)

    @Test
    fun `idempotency key가 있으면 insertOrGet에 key와 fingerprint가 전달된다`() {
        val imageUrl = "https://example.com/a.png"
        val existingId = UUID.randomUUID()
        whenever(
            jobInsertRepository.insertOrGet(
                jobId = any(),
                imageUrl = eq(imageUrl),
                idempotencyKey = eq("idem-1"),
                fingerprint = eq(HashUtils.sha256(imageUrl)),
            ),
        ).thenReturn(
            InsertOrGetJobResult(
                jobId = existingId,
                status = JobStatus.RUNNING,
                created = false,
            ),
        )

        val response = jobService.createJob(imageUrl, "idem-1")

        assertTrue(response.deduped)
        assertEquals(existingId, response.jobId)
        verify(jobInsertRepository).insertOrGet(
            jobId = any(),
            imageUrl = eq(imageUrl),
            idempotencyKey = eq("idem-1"),
            fingerprint = eq(HashUtils.sha256(imageUrl)),
        )
        verifyNoInteractions(jobRepository)
    }

    @Test
    fun `idempotency key가 없으면 fingerprint 기반으로 insertOrGet이 호출된다`() {
        val imageUrl = "https://example.com/b.png"
        val existingId = UUID.randomUUID()
        whenever(
            jobInsertRepository.insertOrGet(
                jobId = any(),
                imageUrl = eq(imageUrl),
                idempotencyKey = anyOrNull(),
                fingerprint = eq(HashUtils.sha256(imageUrl)),
            ),
        ).thenReturn(
            InsertOrGetJobResult(
                jobId = existingId,
                status = JobStatus.QUEUED,
                created = false,
            ),
        )

        val response = jobService.createJob(imageUrl, null)

        assertTrue(response.deduped)
        assertEquals(existingId, response.jobId)
        verify(jobInsertRepository).insertOrGet(
            jobId = any(),
            imageUrl = eq(imageUrl),
            idempotencyKey = anyOrNull(),
            fingerprint = eq(HashUtils.sha256(imageUrl)),
        )
        verify(jobRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `신규 insert인 경우 RECEIVED 응답 후 내부 상태를 QUEUED로 전환한다`() {
        val imageUrl = "https://example.com/c.png"
        val createdId = UUID.randomUUID()
        whenever(
            jobInsertRepository.insertOrGet(
                jobId = any(),
                imageUrl = eq(imageUrl),
                idempotencyKey = anyOrNull(),
                fingerprint = eq(HashUtils.sha256(imageUrl)),
            ),
        ).thenReturn(
            InsertOrGetJobResult(
                jobId = createdId,
                status = JobStatus.RECEIVED,
                created = true,
            ),
        )

        val createdEntity = JobEntity(
            id = createdId,
            status = JobStatus.RECEIVED,
            imageUrl = imageUrl,
        )
        whenever(jobRepository.findById(createdId)).thenReturn(Optional.of(createdEntity))

        val response = jobService.createJob(imageUrl, null)

        assertEquals(false, response.deduped)
        assertEquals(JobStatus.RECEIVED, response.status)
        assertEquals(createdId, response.jobId)
        assertEquals(JobStatus.QUEUED, createdEntity.status)
        verify(jobRepository).save(createdEntity)
    }
}
