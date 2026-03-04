package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.client.mockworker.dto.IssueKeyResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStartResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStatusResponse

interface MockWorkerClient {

    fun issueKey(candidateName: String, email: String): IssueKeyResponse

    fun startProcess(imageUrl: String): ProcessStartResponse

    fun getProcessStatus(externalJobId: String): ProcessStatusResponse
}
