package com.example.statemachine.task.spec

import java.time.Duration

sealed class RetryStrategy {
    data class FixedDelay(
        val delay: Duration,
    ) : RetryStrategy()

    data class ExponentialBackoff(
        val initialDelay: Duration,
        val multiplier: Double = 1.5,
    ) : RetryStrategy()

    data object NoRetry : RetryStrategy()

    companion object {
        fun fixedDelay(delay: Duration): RetryStrategy = FixedDelay(delay)

        fun exponentialBackoff(
            initialDelay: Duration,
            multiplier: Double = 1.5,
        ): RetryStrategy = ExponentialBackoff(initialDelay, multiplier)

        fun noRetry(): RetryStrategy = NoRetry
    }
}
