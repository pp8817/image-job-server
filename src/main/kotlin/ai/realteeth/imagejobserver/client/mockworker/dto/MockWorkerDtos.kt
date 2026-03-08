package ai.realteeth.imagejobserver.client.mockworker.dto

import com.fasterxml.jackson.databind.JsonNode
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

object MockWorkerErrorParser {

    fun extractDetail(root: JsonNode?): String {
        if (root == null || root.isNull) {
            return "Mock Worker error"
        }

        val detailNode = root.get("detail") ?: return root.toString()

        if (detailNode.isTextual) {
            return detailNode.asText()
        }

        if (detailNode.isArray) {
            val messages = detailNode.mapNotNull { item ->
                val msg = item.get("msg")?.asText()?.takeIf { it.isNotBlank() }
                val loc = item.get("loc")
                    ?.takeIf { it.isArray }
                    ?.mapNotNull { locPart -> locPart.asText().takeIf { it.isNotBlank() } }
                    ?.joinToString(".")
                    ?.takeIf { it.isNotBlank() }

                when {
                    msg != null && loc != null -> "$loc: $msg"
                    msg != null -> msg
                    else -> null
                }
            }

            return messages.joinToString("; ").ifBlank { detailNode.toString() }
        }

        return detailNode.toString()
    }
}

enum class MockWorkerJobStatus {
    PROCESSING,
    COMPLETED,
    FAILED,
}
