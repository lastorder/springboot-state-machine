package com.example.statemachine.barrieraggregate

import org.slf4j.LoggerFactory
import java.time.Instant

abstract class BarrierAggregate(
    private val repository: BarrierAggregateRepository,
) {
    protected val log = LoggerFactory.getLogger(javaClass)

    abstract val requiredBarriers: Set<String>

    val aggregateType: String = this::class.java.name

    fun initialize(aggregateKey: String) {
        initializeWithBarriers(aggregateKey, requiredBarriers)
    }

    fun initializeWithBarriers(
        aggregateKey: String,
        barriers: Set<String>,
    ) {
        val existing = repository.findByAggregateTypeAndAggregateKey(aggregateType, aggregateKey)

        if (existing != null) {
            val reset =
                existing.copy(
                    requiredBarriers = barriers,
                    passedBarriers = emptySet(),
                    initializedAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            repository.save(reset)
            log.info("Reinitialized barrier aggregate: key=$aggregateKey, barriers=$barriers, waiting=${existing.waitingDuration}")
        } else {
            val record =
                BarrierAggregateRecord(
                    aggregateType = aggregateType,
                    aggregateKey = aggregateKey,
                    requiredBarriers = barriers,
                    passedBarriers = emptySet(),
                    initializedAt = Instant.now(),
                )
            repository.save(record)
            log.info("Created barrier aggregate: key=$aggregateKey, required=$barriers")
        }
    }

    fun handleBarrierEvent(
        aggregateKey: String,
        barrierType: String,
    ): BarrierAggregateRecord? {
        val record =
            repository.findByAggregateTypeAndAggregateKey(aggregateType, aggregateKey)
                ?: run {
                    log.warn("Barrier aggregate not found: key=$aggregateKey, type=$barrierType")
                    return null
                }

        if (record.passedBarriers.contains(barrierType)) {
            log.debug("Barrier already passed: type=$barrierType, key=$aggregateKey")
            return record
        }

        val updated =
            record.copy(
                passedBarriers = record.passedBarriers + barrierType,
                updatedAt = Instant.now(),
            )

        val saved =
            try {
                repository.save(updated)
            } catch (e: org.springframework.orm.ObjectOptimisticLockingFailureException) {
                log.warn("Optimistic lock conflict, retrying: key=$aggregateKey, type=$barrierType")
                val reloaded =
                    repository.findByAggregateTypeAndAggregateKey(aggregateType, aggregateKey)
                        ?: return null
                if (reloaded.passedBarriers.contains(barrierType)) {
                    return reloaded
                }
                val retried =
                    reloaded.copy(
                        passedBarriers = reloaded.passedBarriers + barrierType,
                        updatedAt = Instant.now(),
                    )
                repository.save(retried)
            }

        log.info(
            "Barrier passed: type=$barrierType, key=$aggregateKey, passed=${saved.passedBarriers}, pending=${saved.pendingBarriers}",
        )

        if (saved.isAllBarriersPassed) {
            log.info("All barriers passed for key=$aggregateKey after ${saved.waitingDuration}")
            onAllBarriersPassed(aggregateKey)
        }

        return saved
    }

    protected abstract fun onAllBarriersPassed(aggregateKey: String)

    fun getSummary(aggregateKey: String): BarrierAggregateRecord? =
        repository.findByAggregateTypeAndAggregateKey(aggregateType, aggregateKey)

    fun delete(aggregateKey: String) {
        repository.deleteByAggregateTypeAndAggregateKey(aggregateType, aggregateKey)
        log.info("Deleted barrier aggregate: key=$aggregateKey")
    }
}
