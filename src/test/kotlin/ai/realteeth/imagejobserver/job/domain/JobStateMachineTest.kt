package ai.realteeth.imagejobserver.job.domain

import ai.realteeth.imagejobserver.global.exception.IllegalStateTransitionException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JobStateMachineTest {

    @Test
    fun `허용된 상태 전이는 예외가 발생하지 않는다`() {
        assertDoesNotThrow { JobStateMachine.requireTransition(JobStatus.RECEIVED, JobStatus.QUEUED) }
        assertDoesNotThrow { JobStateMachine.requireTransition(JobStatus.QUEUED, JobStatus.RUNNING) }
        assertDoesNotThrow { JobStateMachine.requireTransition(JobStatus.RUNNING, JobStatus.SUCCEEDED) }
        assertDoesNotThrow { JobStateMachine.requireTransition(JobStatus.RUNNING, JobStatus.FAILED) }
        assertDoesNotThrow { JobStateMachine.requireTransition(JobStatus.RUNNING, JobStatus.QUEUED) }
    }

    @Test
    fun `허용되지 않은 상태 전이는 예외가 발생한다`() {
        assertThrows(IllegalStateTransitionException::class.java) {
            JobStateMachine.requireTransition(JobStatus.RECEIVED, JobStatus.RUNNING)
        }
        assertThrows(IllegalStateTransitionException::class.java) {
            JobStateMachine.requireTransition(JobStatus.SUCCEEDED, JobStatus.RUNNING)
        }
    }
}
