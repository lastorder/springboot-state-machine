package com.example.statemachine.task.scheduler

import com.example.statemachine.task.spec.TaskSpec
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.TaskInstance
import org.springframework.stereotype.Service
import java.io.Serializable
import java.time.Instant

@Service
class TaskScheduler(
    private val schedulerClient: SchedulerClient,
) {
    fun <P : Serializable> submit(
        spec: TaskSpec<P>,
        instanceId: String,
        payload: P,
        scheduledTime: Instant = Instant.now(),
    ): Boolean {
        val taskInstance = TaskInstance(spec.taskName, instanceId, payload)
        return schedulerClient.scheduleIfNotExists(taskInstance, scheduledTime)
    }

    fun cancel(
        taskName: String,
        instanceId: String,
    ) {
        val taskInstance: TaskInstance<Void> = TaskInstance(taskName, instanceId)
        schedulerClient.cancel(taskInstance)
    }
}
