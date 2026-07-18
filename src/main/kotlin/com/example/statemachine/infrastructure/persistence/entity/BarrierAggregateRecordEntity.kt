package com.example.statemachine.infrastructure.persistence.entity

import com.example.statemachine.barrieraggregate.BarrierAggregateRecord
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "barrier_aggregate",
    indexes = [
        Index(
            name = "idx_barrier_aggregate_lookup",
            columnList = "aggregate_type, aggregate_key",
            unique = true,
        ),
        Index(
            name = "idx_barrier_aggregate_initialized",
            columnList = "initialized_at",
        ),
    ],
)
class BarrierAggregateRecordEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "aggregate_type", length = 500, nullable = false)
    var aggregateType: String,
    @Column(name = "aggregate_key", nullable = false)
    var aggregateKey: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_barriers", nullable = false)
    var requiredBarriers: Set<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "passed_barriers", nullable = false)
    var passedBarriers: Set<String> = emptySet(),
    @Column(name = "initialized_at", nullable = false)
    var initializedAt: Instant,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun toDomain(): BarrierAggregateRecord =
        BarrierAggregateRecord(
            id = id,
            aggregateType = aggregateType,
            aggregateKey = aggregateKey,
            requiredBarriers = requiredBarriers,
            passedBarriers = passedBarriers,
            initializedAt = initializedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
        )

    companion object {
        fun fromDomain(record: BarrierAggregateRecord): BarrierAggregateRecordEntity =
            BarrierAggregateRecordEntity(
                id = record.id,
                aggregateType = record.aggregateType,
                aggregateKey = record.aggregateKey,
                requiredBarriers = record.requiredBarriers,
                passedBarriers = record.passedBarriers,
                initializedAt = record.initializedAt,
                createdAt = record.createdAt,
                updatedAt = record.updatedAt,
                version = record.version,
            )
    }
}
