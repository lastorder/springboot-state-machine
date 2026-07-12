package com.example.statemachine.infrastructure.persistence.entity

import com.example.statemachine.commandinbox.domain.CommandSource
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.domain.enums.OrderEvent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "command_inbox",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_command_idempotency",
            columnNames = ["order_id", "event_type", "idempotency_key"],
        ),
    ],
)
class CommandInboxJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "order_id", nullable = false)
    val orderId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: OrderEvent,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    val source: CommandSource,
    @Column(name = "source_reference", length = 255)
    val sourceReference: String? = null,
    @Column(name = "correlation_id", length = 100)
    val correlationId: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    val payload: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    val headers: String? = null,
    @Column(name = "idempotency_key", length = 255)
    val idempotencyKey: String? = null,
    @Column(name = "priority", nullable = false)
    var priority: Int = 0,
    @Column(name = "expires_at")
    var expiresAt: Instant? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: CommandStatus = CommandStatus.PENDING,
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Column(name = "processed_at")
    var processedAt: Instant? = null,
)
