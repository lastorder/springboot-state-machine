package com.example.statemachine.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "state_machine_history",
    indexes = [
        Index(name = "idx_smh_machine_id", columnList = "machine_id"),
        Index(name = "idx_smh_created_at", columnList = "created_at"),
    ],
)
class StateMachineHistoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "machine_id", nullable = false)
    var machineId: String,
    @Column(name = "from_state")
    var fromState: String?,
    @Column(name = "to_state", nullable = false)
    var toState: String,
    @Column(name = "event")
    var event: String?,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    var headers: String?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
