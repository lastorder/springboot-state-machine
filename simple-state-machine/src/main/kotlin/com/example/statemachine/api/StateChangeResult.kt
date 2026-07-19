package com.example.statemachine.api

data class StateChangeResult<S : Enum<S>>(
    val accepted: Boolean,
    val previousState: S,
    val newState: S,
    val event: Enum<*>?,
    val failureReason: FailureReason? = null,
) {
    enum class FailureReason {
        INVALID_TRANSITION,
        BUSINESS_ERROR,
        TECHNICAL_ERROR,
    }

    fun stateChanged(): Boolean = previousState != newState

    fun shouldRetry(): Boolean = failureReason == FailureReason.TECHNICAL_ERROR

    companion object {
        fun <S : Enum<S>> invalidTransition(
            currentState: S,
            event: Enum<*>,
        ): StateChangeResult<S> =
            StateChangeResult(
                accepted = false,
                previousState = currentState,
                newState = currentState,
                event = event,
                failureReason = FailureReason.INVALID_TRANSITION,
            )

        fun <S : Enum<S>> businessError(
            currentState: S,
            event: Enum<*>,
        ): StateChangeResult<S> =
            StateChangeResult(
                accepted = false,
                previousState = currentState,
                newState = currentState,
                event = event,
                failureReason = FailureReason.BUSINESS_ERROR,
            )

        fun <S : Enum<S>> technicalError(
            currentState: S,
            event: Enum<*>,
        ): StateChangeResult<S> =
            StateChangeResult(
                accepted = false,
                previousState = currentState,
                newState = currentState,
                event = event,
                failureReason = FailureReason.TECHNICAL_ERROR,
            )
    }
}
