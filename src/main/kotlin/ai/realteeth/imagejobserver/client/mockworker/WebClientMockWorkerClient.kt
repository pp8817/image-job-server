package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.client.mockworker.dto.IssueKeyRequest
import ai.realteeth.imagejobserver.client.mockworker.dto.IssueKeyResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.MockWorkerErrorParser
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessRequest
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStartResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStatusResponse
import com.fasterxml.jackson.databind.ObjectMapper
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException

@Component
class WebClientMockWorkerClient(
    private val mockWorkerWebClient: WebClient,
    private val properties: MockWorkerProperties,
    private val mockApiKeyProvider: MockApiKeyProvider,
    private val objectMapper: ObjectMapper,
) : MockWorkerClient {

    override fun issueKey(candidateName: String, email: String): IssueKeyResponse {
        return execute {
            mockWorkerWebClient.post()
                .uri("/auth/issue-key")
                .bodyValue(IssueKeyRequest(candidateName, email))
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { Mono.error(toException(response.statusCode().value(), extractDetail(it))) }
                }
                .bodyToMono(IssueKeyResponse::class.java)
                .block() ?: throw MockWorkerException(
                errorCode = JobErrorCode.INTERNAL,
                retryable = false,
                message = "Empty response from issue-key",
            )
        }
    }

    override fun startProcess(imageUrl: String): ProcessStartResponse {
        val apiKey = mockApiKeyProvider.resolveApiKey()

        return execute {
            mockWorkerWebClient.post()
                .uri("/process")
                .headers {
                    if (!apiKey.isNullOrBlank()) {
                        it.set("X-API-KEY", apiKey)
                    }
                }
                .bodyValue(ProcessRequest(imageUrl))
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { Mono.error(toException(response.statusCode().value(), extractDetail(it))) }
                }
                .bodyToMono(ProcessStartResponse::class.java)
                .block() ?: throw MockWorkerException(
                errorCode = JobErrorCode.INTERNAL,
                retryable = false,
                message = "Empty response from process start",
            )
        }
    }

    override fun getProcessStatus(externalJobId: String): ProcessStatusResponse {
        return execute {
            mockWorkerWebClient.get()
                .uri("/process/{job_id}", externalJobId)
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { Mono.error(toException(response.statusCode().value(), extractDetail(it))) }
                }
                .bodyToMono(ProcessStatusResponse::class.java)
                .block() ?: throw MockWorkerException(
                errorCode = JobErrorCode.INTERNAL,
                retryable = false,
                message = "Empty response from process status",
            )
        }
    }

    private fun <T> execute(call: () -> T): T {
        try {
            return call()
        } catch (ex: MockWorkerException) {
            throw ex
        } catch (ex: WebClientResponseException) {
            throw toException(ex.statusCode.value(), ex.responseBodyAsString)
        } catch (ex: WebClientRequestException) {
            val timeout = ex.cause is TimeoutException
            throw if (timeout) {
                MockWorkerException(
                    errorCode = JobErrorCode.TIMEOUT,
                    retryable = true,
                    message = "Timeout while calling Mock Worker",
                    cause = ex,
                )
            } else {
                MockWorkerException(
                    errorCode = JobErrorCode.INTERNAL,
                    retryable = true,
                    message = "Network error while calling Mock Worker",
                    cause = ex,
                )
            }
        }
    }

    private fun extractDetail(body: String): String {
        return runCatching { objectMapper.readTree(body) }
            .map(MockWorkerErrorParser::extractDetail)
            .getOrElse { body.ifBlank { "Mock Worker error" } }
    }

    private fun toException(statusCode: Int, detail: String): MockWorkerException {
        return when (statusCode) {
            400, 422 -> MockWorkerException(JobErrorCode.BAD_REQUEST, retryable = false, message = detail)
            401 -> MockWorkerException(JobErrorCode.UNAUTHORIZED, retryable = false, message = detail)
            429 -> MockWorkerException(JobErrorCode.RATE_LIMITED, retryable = true, message = detail)
            in 500..599 -> MockWorkerException(JobErrorCode.INTERNAL, retryable = true, message = detail)
            else -> MockWorkerException(JobErrorCode.INTERNAL, retryable = false, message = detail)
        }
    }
}
