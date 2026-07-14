package com.example.statemachine.task.scheduler

import com.example.statemachine.task.spec.TaskContext
import com.example.statemachine.task.spec.TaskResult
import com.example.statemachine.task.spec.TaskSpec
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.TaskInstance
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.Serializable
import java.time.Instant

class TaskSchedulerTest {
    private lateinit var schedulerClient: SchedulerClient
    private lateinit var taskScheduler: TaskScheduler
    private lateinit var testTaskSpec: TestTaskSpec

    @BeforeEach
    fun setUp() {
        schedulerClient = mockk(relaxed = true)
        taskScheduler = TaskScheduler(schedulerClient)
        testTaskSpec = TestTaskSpec()
    }

    @Test
    @DisplayName("Should schedule task with correct parameters")
    fun testSubmit() {
        val payload = TestPayload("test-data")

        taskScheduler.submit(testTaskSpec, "test-instance-1", payload)

        verify {
            schedulerClient.scheduleIfNotExists(
                match<TaskInstance<TestPayload>> {
                    it.taskName == "test-task" &&
                        it.id == "test-instance-1" &&
                        it.data == payload
                },
                any<Instant>(),
            )
        }
    }

    @Test
    @DisplayName("Should schedule task at specific time")
    fun testSubmitWithScheduledTime() {
        val payload = TestPayload("test-data")
        val scheduledTime = Instant.now().plusSeconds(60)

        taskScheduler.submit(testTaskSpec, "test-instance-1", payload, scheduledTime)

        verify {
            schedulerClient.scheduleIfNotExists(
                any<TaskInstance<TestPayload>>(),
                scheduledTime,
            )
        }
    }

    @Test
    @DisplayName("Should cancel task")
    fun testCancel() {
        taskScheduler.cancel("test-task", "test-instance-1")

        verify {
            schedulerClient.cancel(any<TaskInstance<Void>>())
        }
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

        override fun execute(context: TaskContext<TestPayload>): TaskResult = TaskResult.success()
    }
}
