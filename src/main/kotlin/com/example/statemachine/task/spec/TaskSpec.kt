package com.example.statemachine.task.spec

import org.slf4j.LoggerFactory
import java.io.Serializable
import java.time.Duration

interface TaskSpec<P : Serializable> {
    val taskName: String
    val maxRetries: Int
        get() = 3
    val retryStrategy: RetryStrategy
        get() = RetryStrategy.FixedDelay(Duration.ofMinutes(5))
    val payloadClass: Class<P>

    fun execute(context: TaskContext<P>): TaskResult

    fun onFinalFailure(context: TaskContext<P>) {
        log.warn("Task {} final failure: instanceId={}", taskName, context.instanceId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(TaskSpec::class.java)
    }
}
