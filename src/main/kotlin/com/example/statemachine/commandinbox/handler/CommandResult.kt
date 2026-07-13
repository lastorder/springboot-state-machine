package com.example.statemachine.commandinbox.handler

sealed class CommandResult<out R> {
    data class Success<R>(
        val response: R? = null,
    ) : CommandResult<R>()

    data class Failure(
        val error: String,
        val retryable: Boolean = true,
    ) : CommandResult<Nothing>()

    data class Skipped(
        val reason: String,
    ) : CommandResult<Nothing>()
}
