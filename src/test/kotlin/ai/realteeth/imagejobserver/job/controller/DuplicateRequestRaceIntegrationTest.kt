package ai.realteeth.imagejobserver.job.controller

import ai.realteeth.imagejobserver.job.repository.JobJpaRepository
import ai.realteeth.imagejobserver.job.repository.JobResultJpaRepository
import ai.realteeth.imagejobserver.job.service.JobService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class DuplicateRequestRaceIntegrationTest {

    @Autowired
    private lateinit var jobService: JobService

    @Autowired
    private lateinit var jobRepository: JobJpaRepository

    @Autowired
    private lateinit var jobResultRepository: JobResultJpaRepository

    @BeforeEach
    fun setUp() {
        jobResultRepository.deleteAll()
        jobRepository.deleteAll()
    }

    @Test
    fun `동일 payload 동시 요청에서도 job row는 하나만 생성된다`() {
        val threadCount = 8
        val executor = Executors.newFixedThreadPool(8)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val jobIds = Collections.synchronizedList(mutableListOf<String>())

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await(5, TimeUnit.SECONDS)
                    val response = jobService.createJob("https://example.com/race.png", null)
                    jobIds.add(response.jobId.toString())
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(20, TimeUnit.SECONDS)
        executor.shutdownNow()

        assertTrue(finished, "모든 요청 스레드가 종료되어야 합니다")
        assertTrue(errors.isEmpty(), "요청 처리 중 예외가 없어야 합니다: $errors")

        val distinctJobIds = jobIds.toSet()
        assertEquals(1, distinctJobIds.size)
        assertEquals(1L, jobRepository.count())
    }
}
