package com.example.statemachine.barrieraggregate

import java.time.Duration
import java.time.Instant

data class BarrierAggregateRecord(
    val id: Long? = null,
    val aggregateType: String,
    val aggregateKey: String,
    val requiredBarriers: Set<String>,
    val passedBarriers: Set<String> = emptySet(),
    val initializedAt: Instant,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    val pendingBarriers: Set<String> get() = requiredBarriers - passedBarriers
    val isAllBarriersPassed: Boolean get() = passedBarriers.containsAll(requiredBarriers)
    val waitingDuration: Duration get() = Duration.between(initializedAt, Instant.now())
}
