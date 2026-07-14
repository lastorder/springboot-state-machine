package com.example.statemachine.task.scheduler

import com.example.statemachine.task.spec.TaskContext
import com.example.statemachine.task.spec.TaskResult
import com.example.statemachine.task.spec.TaskSpec
import com.github.kagkarlsson.scheduler.task.ExecutionContext
import com.github.kagkarlsson.scheduler.task.TaskInstance
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.Serializable
import java.time.Instant

@Component
class TaskSpecAdapterFactory(
    private val taskSpecRegistry: com.example.statemachine.task.spec.TaskSpecRegistry,
) {
    fun <P : Serializable> createOneTimeTask(spec: TaskSpec<P>): OneTimeTask<P> = AdapterOneTimeTask(spec)

    inner class AdapterOneTimeTask<P : Serializable>(
        private val spec: TaskSpec<P>,
    ) : OneTimeTask<P>(spec.taskName, spec.payloadClass) {
        override fun executeOnce(
            taskInstance: TaskInstance<P>,
            context: ExecutionContext,
        ) {
            val payload = taskInstance.data
            val retryCount = context.execution.consecutiveFailures

            val taskContext =
                TaskContext(
                    instanceId = taskInstance.id,
                    payload = payload,
                    scheduledTime = context.execution.executionTime,
                    executionTime = Instant.now(),
                    retryCount = retryCount,
                )

            try {
                val result = spec.execute(taskContext)
                handleResult(result, spec, taskContext, retryCount)
            } catch (e: Exception) {
                handleException(e, spec, taskContext, retryCount)
            }
        }

        private fun handleResult(
            result: TaskResult,
            spec: TaskSpec<P>,
            context: TaskContext<P>,
            retryCount: Int,
        ) {
            when (result) {
                is TaskResult.Success -> {
                    log.info("Task {} completed: {}", spec.taskName, context.instanceId)
                }
                is TaskResult.Retry -> {
                    log.warn("Task {} retry requested: {}, reason: {}", spec.taskName, context.instanceId, result.reason)
                    if (retryCount >= spec.maxRetries) {
                        spec.onMaxRetriesExceeded(context)
                    } else {
                        throw RetryRequestedException(result.reason)
                    }
                }
                is TaskResult.Fail -> {
                    log.error("Task {} failed: {}, reason: {}", spec.taskName, context.instanceId, result.reason)
                    if (retryCount >= spec.maxRetries) {
                        spec.onMaxRetriesExceeded(context)
                    } else {
                        throw TaskFailedException(result.reason, result.cause)
                    }
                }
            }
        }

        private fun handleException(
            e: Exception,
            spec: TaskSpec<P>,
            context: TaskContext<P>,
            retryCount: Int,
        ) {
            log.error("Task {} threw exception: {}", spec.taskName, context.instanceId, e)
            if (retryCount >= spec.maxRetries) {
                spec.onMaxRetriesExceeded(context)
            }
            throw e
        }
    }

    class RetryRequestedException(
        message: String,
    ) : RuntimeException(message)

    class TaskFailedException(
        message: String?,
        cause: Throwable?,
    ) : RuntimeException(message, cause)

    companion object {
        private val log = LoggerFactory.getLogger(TaskSpecAdapterFactory::class.java)
    }
}
