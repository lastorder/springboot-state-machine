package com.example.statemachine.barrieraggregate

interface BarrierAggregateRepository {
    fun save(record: BarrierAggregateRecord): BarrierAggregateRecord

    fun findByAggregateTypeAndAggregateKey(
        aggregateType: String,
        aggregateKey: String,
    ): BarrierAggregateRecord?

    fun deleteByAggregateTypeAndAggregateKey(
        aggregateType: String,
        aggregateKey: String,
    )
}
