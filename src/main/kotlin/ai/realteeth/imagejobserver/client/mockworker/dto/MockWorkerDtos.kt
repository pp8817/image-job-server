package ai.realteeth.imagejobserver.client.mockworker.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class IssueKeyRequest(
    val candidateName: String,
    val email: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IssueKeyResponse(
    val apiKey: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProcessRequest(
    val imageUrl: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProcessStartResponse(
    val jobId: String,
    val status: MockWorkerJobStatus,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProcessStatusResponse(
    val jobId: String,
    val status: MockWorkerJobStatus,
    val result: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MockWorkerErrorResponse(
    val detail: String = "",
)

enum class MockWorkerJobStatus {
    PROCESSING,
    COMPLETED,
    FAILED,
}
