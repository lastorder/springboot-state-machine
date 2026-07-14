package com.example.statemachine.task.spec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.Serializable
import java.time.Instant

class TaskSpecTest {
    private lateinit var taskSpec: TestTaskSpec
    private lateinit var taskContext: TaskContext<TestPayload>

    @BeforeEach
    fun setUp() {
        taskSpec = TestTaskSpec()
        taskContext =
            TaskContext(
                instanceId = "test-instance-1",
                payload = TestPayload("test-data"),
                scheduledTime = Instant.now(),
                executionTime = Instant.now(),
                retryCount = 0,
            )
    }

    @Test
    @DisplayName("Should return default maxRetries")
    fun testDefaultMaxRetries() {
        assertEquals(3, taskSpec.maxRetries)
    }

    @Test
    @DisplayName("Should return task name")
    fun testTaskName() {
        assertEquals("test-task", taskSpec.taskName)
    }

    @Test
    @DisplayName("Should return success result when under retry limit")
    fun testExecuteSuccess() {
        val result = taskSpec.execute(taskContext)
        assertTrue(result is TaskResult.Success)
    }

    @Test
    @DisplayName("Should return fail result when at retry limit")
    fun testExecuteFailAtRetryLimit() {
        val contextWithRetry = taskContext.copy(retryCount = 3)
        val result = taskSpec.execute(contextWithRetry)
        assertTrue(result is TaskResult.Fail)
    }

    data class TestPayload(
        val data: String,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    class TestTaskSpec : TaskSpec<TestPayload> {
        override val taskName: String = "test-task"
        override val payloadClass: Class<TestPayload> = TestPayload::class.java

        override fun execute(context: TaskContext<TestPayload>): TaskResult =
            if (context.retryCount >= maxRetries) {
                TaskResult.fail("Max retries exceeded")
            } else {
                TaskResult.success()
            }
    }
}
