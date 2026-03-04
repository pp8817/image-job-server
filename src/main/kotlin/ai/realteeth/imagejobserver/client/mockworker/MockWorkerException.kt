package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.job.domain.JobErrorCode

class MockWorkerException(
    val errorCode: JobErrorCode,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
