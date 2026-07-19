package com.example.statemachine.api

sealed class ActionResult {
    data class Success(val nextEvent: Enum<*>? = null) : ActionResult()

    sealed class Failure : ActionResult() {
        abstract val reason: String

        data class BusinessError(override val reason: String) : Failure()

        data class TechnicalError(
            override val reason: String,
            val cause: Throwable? = null,
        ) : Failure()
    }

    companion object {
        fun success(): ActionResult = Success()

        fun success(nextEvent: Enum<*>): ActionResult = Success(nextEvent)

        fun businessError(reason: String): ActionResult = Failure.BusinessError(reason)

        fun technicalError(reason: String, cause: Throwable? = null): ActionResult =
            Failure.TechnicalError(reason, cause)
    }
}
