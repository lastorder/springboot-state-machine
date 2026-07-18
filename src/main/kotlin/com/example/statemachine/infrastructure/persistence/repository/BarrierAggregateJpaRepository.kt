package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.infrastructure.persistence.entity.BarrierAggregateRecordEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BarrierAggregateJpaRepository : JpaRepository<BarrierAggregateRecordEntity, Long> {
    fun findByAggregateTypeAndAggregateKey(
        aggregateType: String,
        aggregateKey: String,
    ): BarrierAggregateRecordEntity?

    fun deleteByAggregateTypeAndAggregateKey(
        aggregateType: String,
        aggregateKey: String,
    )
}
