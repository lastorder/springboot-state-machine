package com.example.statemachine.infrastructure.persistence.entity

import com.example.statemachine.domain.enums.InventoryStatus
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.enums.ValidationStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "orders")
class OrderJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false)
    var product: String,
    @Column(nullable = false)
    var quantity: Int = 1,
    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.CREATED,
    @Enumerated(EnumType.STRING)
    @Column
    var inventoryStatus: InventoryStatus? = null,
    @Column
    var inventoryReference: String? = null,
    @Enumerated(EnumType.STRING)
    @Column
    var inventoryCheckStatus: ValidationStatus? = null,
    @Enumerated(EnumType.STRING)
    @Column
    var pricingCheckStatus: ValidationStatus? = null,
    @Column
    var inventoryCheckedAt: Instant? = null,
    @Column
    var pricingCheckedAt: Instant? = null,
    @Column
    var validationStartedAt: Instant? = null,
    @Column
    var validationRetryCount: Int = 0,
    @Column
    var pricingReference: String? = null,
    @Column(precision = 19, scale = 2)
    var unitPrice: BigDecimal? = null,
    @Column(precision = 19, scale = 2)
    var confirmedPrice: BigDecimal? = null,
    @Column(length = 500)
    var modificationReason: String? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
)
