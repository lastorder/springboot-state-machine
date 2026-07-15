package com.example.statemachine.task.spec

import java.time.Duration

sealed class TaskResult {
    data class Success(
        val message: String? = null,
    ) : TaskResult()

    data class Fail(
        val reason: String,
        val cause: Throwable? = null,
        val retryable: Boolean = true,
        val delay: Duration? = null,
    ) : TaskResult()

    companion object {
        fun success(message: String? = null): TaskResult = Success(message)

        fun fail(
            reason: String,
            cause: Throwable? = null,
            retryable: Boolean = true,
            delay: Duration? = null,
        ): TaskResult = Fail(reason, cause, retryable, delay)

        fun failWithoutRetry(
            reason: String,
            cause: Throwable? = null,
        ): TaskResult = Fail(reason, cause, retryable = false)
    }
}
