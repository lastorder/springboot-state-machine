package com.example.statemachine.task.spec

sealed class TaskResult {
    data class Success(
        val message: String? = null,
    ) : TaskResult()

    data class Retry(
        val reason: String,
    ) : TaskResult()

    data class Fail(
        val reason: String,
        val cause: Throwable? = null,
    ) : TaskResult()

    companion object {
        fun success(message: String? = null): TaskResult = Success(message)

        fun retry(reason: String): TaskResult = Retry(reason)

        fun fail(
            reason: String,
            cause: Throwable? = null,
        ): TaskResult = Fail(reason, cause)
    }
}
