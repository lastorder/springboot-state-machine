package com.example.statemachine.domain.model

import com.example.statemachine.domain.enums.InventoryStatus
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.enums.ValidationStatus
import java.math.BigDecimal
import java.time.Instant

class Order(
    var id: Long? = null,
    var product: String,
    var quantity: Int = 1,
    var amount: BigDecimal,
    var status: OrderStatus = OrderStatus.CREATED,
    var inventoryStatus: InventoryStatus? = null,
    var inventoryReference: String? = null,
    var inventoryCheckStatus: ValidationStatus? = null,
    var pricingCheckStatus: ValidationStatus? = null,
    var inventoryCheckedAt: Instant? = null,
    var pricingCheckedAt: Instant? = null,
    var validationStartedAt: Instant? = null,
    var validationRetryCount: Int = 0,
    var pricingReference: String? = null,
    var unitPrice: BigDecimal? = null,
    var confirmedPrice: BigDecimal? = null,
    var modificationReason: String? = null,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
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

    fun isValidationComplete(): Boolean =
        inventoryCheckStatus == ValidationStatus.SUCCESS &&
            pricingCheckStatus == ValidationStatus.SUCCESS

    fun isValidationFailed(): Boolean =
        inventoryCheckStatus == ValidationStatus.FAILED ||
            pricingCheckStatus == ValidationStatus.FAILED

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
