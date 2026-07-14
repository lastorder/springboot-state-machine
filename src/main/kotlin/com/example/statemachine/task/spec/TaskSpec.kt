package com.example.statemachine.task.spec

import org.slf4j.LoggerFactory
import java.io.Serializable

interface TaskSpec<P : Serializable> {
    val taskName: String
    val maxRetries: Int
        get() = 3
    val payloadClass: Class<P>

    fun execute(context: TaskContext<P>): TaskResult

    fun onMaxRetriesExceeded(context: TaskContext<P>) {
        log.warn("Task {} exceeded max retries: instanceId={}", taskName, context.instanceId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(TaskSpec::class.java)
    }
}
