package com.example.statemachine.task.scheduler

import com.example.statemachine.task.spec.RetryStrategy
import com.example.statemachine.task.spec.TaskContext
import com.example.statemachine.task.spec.TaskResult
import com.example.statemachine.task.spec.TaskSpec
import com.github.kagkarlsson.scheduler.task.ExecutionComplete
import com.github.kagkarlsson.scheduler.task.ExecutionContext
import com.github.kagkarlsson.scheduler.task.FailureHandler
import com.github.kagkarlsson.scheduler.task.TaskInstance
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.math.pow
import kotlin.math.round

@Component
class TaskSpecAdapterFactory(
    private val taskSpecRegistry: com.example.statemachine.task.spec.TaskSpecRegistry,
) {
    fun <P : Serializable> createOneTimeTask(spec: TaskSpec<P>): OneTimeTask<P> =
        AdapterOneTimeTask(spec)

    inner class AdapterOneTimeTask<P : Serializable>(
        private val spec: TaskSpec<P>,
    ) : OneTimeTask<P>(
            spec.taskName,
            spec.payloadClass,
            createFailureHandler(spec),
        ) {
        override fun executeOnce(
            taskInstance: TaskInstance<P>,
            context: ExecutionContext,
        ) {
            val payload = taskInstance.data
            val taskContext =
                TaskContext(
                    instanceId = taskInstance.id,
                    payload = payload,
                    scheduledTime = context.execution.executionTime,
                    executionTime = Instant.now(),
                    retryCount = context.execution.consecutiveFailures,
                )

            val result = spec.execute(taskContext)
            handleResult(result, spec, taskContext)
        }

        private fun handleResult(
            result: TaskResult,
            spec: TaskSpec<P>,
            context: TaskContext<P>,
        ) {
            when (result) {
                is TaskResult.Success -> {
                    log.info("Task {} completed: {}", spec.taskName, context.instanceId)
                }
                is TaskResult.Fail -> {
                    if (result.retryable) {
                        log.warn(
                            "Task {} failed (retryable): {}, reason: {}",
                            spec.taskName,
                            context.instanceId,
                            result.reason,
                        )
                        throw TaskFailedException(result.reason, result.cause, result.delay)
                    } else {
                        log.error(
                            "Task {} failed (no retry): {}, reason: {}",
                            spec.taskName,
                            context.instanceId,
                            result.reason,
                        )
                        spec.onFinalFailure(context)
                    }
                }
            }
        }
    }

    class TaskFailedException(
        message: String,
        cause: Throwable? = null,
        val delay: Duration? = null,
    ) : RuntimeException(message, cause)

    companion object {
        private val log = LoggerFactory.getLogger(TaskSpecAdapterFactory::class.java)

        private fun <P : Serializable> createFailureHandler(spec: TaskSpec<P>): FailureHandler<P> =
            FailureHandler { executionComplete, executionOperations ->
                val consecutiveFailures = executionComplete.execution.consecutiveFailures
                val totalFailures = consecutiveFailures + 1

                if (totalFailures > spec.maxRetries) {
                    log.error(
                        "Task {} exceeded max retries ({}), removing: {}",
                        spec.taskName,
                        spec.maxRetries,
                        executionComplete.execution.taskInstance.id,
                    )
                    val context = createTaskContext(executionComplete, spec.payloadClass)
                    spec.onFinalFailure(context)
                    executionOperations.remove()
                } else {
                    val delay = calculateDelay(spec, consecutiveFailures)
                    val nextTry = executionComplete.timeDone.plus(delay)
                    log.info(
                        "Retrying task {} (attempt {}/{}) at {}",
                        spec.taskName,
                        totalFailures,
                        spec.maxRetries,
                        nextTry,
                    )
                    executionOperations.reschedule(executionComplete, nextTry)
                }
            }

        @Suppress("UNCHECKED_CAST")
        private fun <P : Serializable> createTaskContext(
            executionComplete: ExecutionComplete,
            payloadClass: Class<P>,
        ): TaskContext<P> {
            val execution = executionComplete.execution
            return TaskContext(
                instanceId = execution.taskInstance.id,
                payload = execution.taskInstance.data as P,
                scheduledTime = execution.executionTime,
                executionTime = executionComplete.timeDone,
                retryCount = execution.consecutiveFailures,
            )
        }

        private fun <P : Serializable> calculateDelay(
            spec: TaskSpec<P>,
            consecutiveFailures: Int,
        ): Duration =
            when (val strategy = spec.retryStrategy) {
                is RetryStrategy.FixedDelay -> strategy.delay
                is RetryStrategy.ExponentialBackoff -> {
                    val delayMs =
                        round(
                            strategy.initialDelay.toMillis() *
                                strategy.multiplier.pow(consecutiveFailures),
                        ).toLong()
                    Duration.ofMillis(delayMs)
                }
                is RetryStrategy.NoRetry -> Duration.ZERO
            }
    }
}
