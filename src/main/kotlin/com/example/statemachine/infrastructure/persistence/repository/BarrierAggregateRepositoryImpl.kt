package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.barrieraggregate.BarrierAggregateRecord
import com.example.statemachine.barrieraggregate.BarrierAggregateRepository
import com.example.statemachine.infrastructure.persistence.entity.BarrierAggregateRecordEntity
import org.springframework.stereotype.Repository

@Repository
class BarrierAggregateRepositoryImpl(
    private val jpaRepository: BarrierAggregateJpaRepository,
) : BarrierAggregateRepository {
    override fun save(record: BarrierAggregateRecord): BarrierAggregateRecord {
        val entity = BarrierAggregateRecordEntity.fromDomain(record)
        val saved = jpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findByAggregateTypeAndAggregateKey(
        aggregateType: String,
        aggregateKey: String,
    ): BarrierAggregateRecord? =
        jpaRepository
            .findByAggregateTypeAndAggregateKey(aggregateType, aggregateKey)
            ?.toDomain()

    override fun deleteByAggregateTypeAndAggregateKey(
        aggregateType: String,
        aggregateKey: String,
    ) {
        jpaRepository.deleteByAggregateTypeAndAggregateKey(aggregateType, aggregateKey)
    }
}
