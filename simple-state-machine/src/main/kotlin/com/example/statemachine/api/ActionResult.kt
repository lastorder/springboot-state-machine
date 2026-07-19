package com.example.statemachine.api

sealed class ActionResult {
    data class Success(val nextEvent: Enum<*>? = null) : ActionResult()

    data class Failure(val reason: String) : ActionResult()

    companion object {
        fun success(): ActionResult = Success()

        fun success(nextEvent: Enum<*>): ActionResult = Success(nextEvent)

        fun failure(reason: String): ActionResult = Failure(reason)
    }
}
