package com.example.statemachine.api

sealed class ActionResult<out E : Event> {
    data class Success<E : Event>(
        val nextEvent: E? = null,
    ) : ActionResult<E>()

    data class Failure<E : Event>(
        val reason: String,
        val cause: Throwable? = null,
    ) : ActionResult<E>()

    companion object {
        fun <E : Event> success(): ActionResult<E> = Success()

        fun <E : Event> success(nextEvent: E): ActionResult<E> = Success(nextEvent)

        fun <E : Event> failure(
            reason: String,
            cause: Throwable? = null,
        ): ActionResult<E> = Failure(reason, cause)
    }
}
