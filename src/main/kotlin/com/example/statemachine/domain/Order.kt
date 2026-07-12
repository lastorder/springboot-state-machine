package com.example.statemachine.domain

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
class Order(
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
    // 库存相关信息
    @Enumerated(EnumType.STRING)
    @Column
    var inventoryStatus: InventoryStatus? = null,
    @Column
    var inventoryReference: String? = null,
    // 并行验证状态追踪
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
    // 报价相关信息
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
) {
    fun updateStatus(newStatus: OrderStatus) {
        this.status = newStatus
        this.updatedAt = Instant.now()
    }

    fun startValidation() {
        this.validationStartedAt = Instant.now()
        this.inventoryCheckStatus = ValidationStatus.PENDING
        this.pricingCheckStatus = ValidationStatus.PENDING
        this.updatedAt = Instant.now()
    }

    fun markInventorySuccess(inventoryReference: String?) {
        this.inventoryCheckStatus = ValidationStatus.SUCCESS
        this.inventoryReference = inventoryReference
        this.inventoryCheckedAt = Instant.now()
        this.inventoryStatus = InventoryStatus.CONFIRMED
        this.updatedAt = Instant.now()
    }

    fun markInventoryFailed() {
        this.inventoryCheckStatus = ValidationStatus.FAILED
        this.inventoryCheckedAt = Instant.now()
        this.inventoryStatus = InventoryStatus.FAILED
        this.updatedAt = Instant.now()
    }

    fun markPricingSuccess(
        pricingReference: String?,
        unitPrice: BigDecimal?,
    ) {
        this.pricingCheckStatus = ValidationStatus.SUCCESS
        this.pricingReference = pricingReference
        this.unitPrice = unitPrice
        this.pricingCheckedAt = Instant.now()
        unitPrice?.let { this.amount = it.multiply(BigDecimal(quantity)) }
        this.updatedAt = Instant.now()
    }

    fun markPricingFailed() {
        this.pricingCheckStatus = ValidationStatus.FAILED
        this.pricingCheckedAt = Instant.now()
        this.updatedAt = Instant.now()
    }

    fun isValidationComplete(): Boolean {
        return inventoryCheckStatus == ValidationStatus.SUCCESS &&
            pricingCheckStatus == ValidationStatus.SUCCESS
    }

    fun isValidationFailed(): Boolean {
        return inventoryCheckStatus == ValidationStatus.FAILED ||
            pricingCheckStatus == ValidationStatus.FAILED
    }

    fun incrementRetryCount() {
        this.validationRetryCount++
        this.updatedAt = Instant.now()
    }

    fun resetValidation() {
        this.inventoryCheckStatus = null
        this.pricingCheckStatus = null
        this.inventoryCheckedAt = null
        this.pricingCheckedAt = null
        this.validationStartedAt = null
        this.inventoryReference = null
        this.pricingReference = null
        this.unitPrice = null
        this.inventoryStatus = null
        this.updatedAt = Instant.now()
    }

    fun updateForModification(
        newProduct: String?,
        newQuantity: Int?,
        reason: String? = null,
    ) {
        newProduct?.let { this.product = it }
        newQuantity?.let { this.quantity = it }
        this.modificationReason = reason
        resetValidation()
    }

    fun confirmPrice() {
        this.confirmedPrice = this.amount
        this.updatedAt = Instant.now()
    }
}
