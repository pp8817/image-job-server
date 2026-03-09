package ai.realteeth.imagejobserver.job.service

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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
    fun `idempotency key가 있으면 insertOrGet에 key가 전달된다`() {
        val imageUrl = "https://example.com/a.png"
        val existingId = UUID.randomUUID()
        whenever(
            jobInsertRepository.insertOrGet(
                jobId = any(),
                imageUrl = eq(imageUrl),
                idempotencyKey = eq("idem-1"),
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
        )
        verifyNoInteractions(jobRepository)
    }

    @Test
    fun `동일 imageUrl라도 다른 idempotency key면 서로 다른 요청으로 처리된다`() {
        val imageUrl = "https://example.com/b.png"
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        whenever(
            jobInsertRepository.insertOrGet(
                jobId = any(),
                imageUrl = eq(imageUrl),
                idempotencyKey = eq("idem-1"),
            ),
        ).thenReturn(InsertOrGetJobResult(jobId = firstId, status = JobStatus.RECEIVED, created = true))
        whenever(
            jobInsertRepository.insertOrGet(
                jobId = any(),
                imageUrl = eq(imageUrl),
                idempotencyKey = eq("idem-2"),
            ),
        ).thenReturn(InsertOrGetJobResult(jobId = secondId, status = JobStatus.RECEIVED, created = true))
        whenever(jobRepository.findById(firstId)).thenReturn(Optional.of(JobEntity(id = firstId, status = JobStatus.RECEIVED, imageUrl = imageUrl, idempotencyKey = "idem-1")))
        whenever(jobRepository.findById(secondId)).thenReturn(Optional.of(JobEntity(id = secondId, status = JobStatus.RECEIVED, imageUrl = imageUrl, idempotencyKey = "idem-2")))

        val firstResponse = jobService.createJob(imageUrl, "idem-1")
        val secondResponse = jobService.createJob(imageUrl, "idem-2")

        assertEquals(false, firstResponse.deduped)
        assertEquals(false, secondResponse.deduped)
        assertTrue(firstResponse.jobId != secondResponse.jobId)
    }

    @Test
    fun `신규 insert인 경우 RECEIVED 응답 후 내부 상태를 QUEUED로 전환한다`() {
        val imageUrl = "https://example.com/c.png"
        val createdId = UUID.randomUUID()
        whenever(
            jobInsertRepository.insertOrGet(
                jobId = any(),
                imageUrl = eq(imageUrl),
                idempotencyKey = eq("idem-created"),
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
            idempotencyKey = "idem-created",
        )
        whenever(jobRepository.findById(createdId)).thenReturn(Optional.of(createdEntity))

        val response = jobService.createJob(imageUrl, "idem-created")

        assertEquals(false, response.deduped)
        assertEquals(JobStatus.RECEIVED, response.status)
        assertEquals(createdId, response.jobId)
        assertEquals(JobStatus.QUEUED, createdEntity.status)
        verify(jobRepository).save(createdEntity)
    }
}
