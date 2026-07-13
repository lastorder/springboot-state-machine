package com.example.statemachine.infrastructure.persistence.entity

import com.example.statemachine.commandinbox.domain.BackoffStrategy
import com.example.statemachine.commandinbox.domain.CommandStatus
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
    name = "command",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_command_idempotency",
            columnNames = ["group_id", "command_type", "idempotency_key"],
        ),
    ],
)
class CommandJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "group_id", nullable = false, length = 255)
    val groupId: String,
    @Column(name = "command_type", nullable = false, length = 100)
    val commandType: String,
    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    val payload: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response", columnDefinition = "jsonb")
    var response: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: String? = null,
    @Column(name = "priority", nullable = false)
    val priority: Int = 0,
    @Column(name = "max_retries", nullable = false)
    val maxRetries: Int = 3,
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "backoff_strategy", nullable = false, length = 20)
    val backoffStrategy: BackoffStrategy = BackoffStrategy.FIXED,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "backoff_config", columnDefinition = "jsonb")
    val backoffConfig: String? = null,
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
    @Column(name = "next_retry_at")
    var nextRetryAt: Instant? = null,
)
