package com.example.statemachine.domain.model

import com.example.statemachine.domain.enums.InventoryStatus
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.enums.ValidationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderTest {
    @Test
    @DisplayName("Should update order status")
    fun testUpdateStatus() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.CREATED,
            )

        order.updateStatus(OrderStatus.PENDING_VALIDATION)

        assertEquals(OrderStatus.PENDING_VALIDATION, order.status)
    }

    @Test
    @DisplayName("Should start validation")
    fun testStartValidation() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )

        order.startValidation()

        assertEquals(ValidationStatus.PENDING, order.inventoryCheckStatus)
        assertEquals(ValidationStatus.PENDING, order.pricingCheckStatus)
        assertTrue(order.validationStartedAt != null)
    }

    @Test
    @DisplayName("Should mark inventory success")
    fun testMarkInventorySuccess() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )

        order.markInventorySuccess("INV-123")

        assertEquals(ValidationStatus.SUCCESS, order.inventoryCheckStatus)
        assertEquals("INV-123", order.inventoryReference)
        assertEquals(InventoryStatus.CONFIRMED, order.inventoryStatus)
        assertTrue(order.inventoryCheckedAt != null)
    }

    @Test
    @DisplayName("Should mark inventory failed")
    fun testMarkInventoryFailed() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )

        order.markInventoryFailed()

        assertEquals(ValidationStatus.FAILED, order.inventoryCheckStatus)
        assertEquals(InventoryStatus.FAILED, order.inventoryStatus)
        assertTrue(order.inventoryCheckedAt != null)
    }

    @Test
    @DisplayName("Should mark pricing success")
    fun testMarkPricingSuccess() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )

        order.markPricingSuccess("PRICE-123", BigDecimal("50.00"))

        assertEquals(ValidationStatus.SUCCESS, order.pricingCheckStatus)
        assertEquals("PRICE-123", order.pricingReference)
        assertEquals(BigDecimal("50.00"), order.unitPrice)
        assertEquals(BigDecimal("250.00"), order.amount) // 50 * 5
        assertTrue(order.pricingCheckedAt != null)
    }

    @Test
    @DisplayName("Should mark pricing failed")
    fun testMarkPricingFailed() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )

        order.markPricingFailed()

        assertEquals(ValidationStatus.FAILED, order.pricingCheckStatus)
        assertTrue(order.pricingCheckedAt != null)
    }

    @Test
    @DisplayName("Should check if validation is complete")
    fun testIsValidationComplete() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
                inventoryCheckStatus = ValidationStatus.SUCCESS,
                pricingCheckStatus = ValidationStatus.SUCCESS,
            )

        assertTrue(order.isValidationComplete())
    }

    @Test
    @DisplayName("Should return false when validation is not complete")
    fun testIsValidationComplete_NotComplete() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
                inventoryCheckStatus = ValidationStatus.SUCCESS,
                pricingCheckStatus = ValidationStatus.PENDING,
            )

        assertFalse(order.isValidationComplete())
    }

    @Test
    @DisplayName("Should check if validation is failed")
    fun testIsValidationFailed() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
                inventoryCheckStatus = ValidationStatus.FAILED,
                pricingCheckStatus = ValidationStatus.PENDING,
            )

        assertTrue(order.isValidationFailed())
    }

    @Test
    @DisplayName("Should return false when validation is not failed")
    fun testIsValidationFailed_NotFailed() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
                inventoryCheckStatus = ValidationStatus.SUCCESS,
                pricingCheckStatus = ValidationStatus.SUCCESS,
            )

        assertFalse(order.isValidationFailed())
    }

    @Test
    @DisplayName("Should increment retry count")
    fun testIncrementRetryCount() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
                validationRetryCount = 0,
            )

        order.incrementRetryCount()

        assertEquals(1, order.validationRetryCount)
    }

    @Test
    @DisplayName("Should reset validation")
    fun testResetValidation() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
                inventoryCheckStatus = ValidationStatus.SUCCESS,
                pricingCheckStatus = ValidationStatus.SUCCESS,
                inventoryReference = "INV-123",
                pricingReference = "PRICE-123",
                unitPrice = BigDecimal("50.00"),
                inventoryStatus = InventoryStatus.CONFIRMED,
            )

        order.resetValidation()

        assertNull(order.inventoryCheckStatus)
        assertNull(order.pricingCheckStatus)
        assertNull(order.inventoryCheckedAt)
        assertNull(order.pricingCheckedAt)
        assertNull(order.validationStartedAt)
        assertNull(order.inventoryReference)
        assertNull(order.pricingReference)
        assertNull(order.unitPrice)
        assertNull(order.inventoryStatus)
    }

    @Test
    @DisplayName("Should update for modification")
    fun testUpdateForModification() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_CONFIRMATION,
                inventoryCheckStatus = ValidationStatus.SUCCESS,
                pricingCheckStatus = ValidationStatus.SUCCESS,
            )

        order.updateForModification("New Product", 10, "Customer request")

        assertEquals("New Product", order.product)
        assertEquals(10, order.quantity)
        assertEquals("Customer request", order.modificationReason)
        assertNull(order.inventoryCheckStatus)
        assertNull(order.pricingCheckStatus)
    }

    @Test
    @DisplayName("Should update for modification with null values")
    fun testUpdateForModification_NullValues() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_CONFIRMATION,
            )

        order.updateForModification(null, null, null)

        assertEquals("Test Product", order.product)
        assertEquals(5, order.quantity)
    }

    @Test
    @DisplayName("Should confirm price")
    fun testConfirmPrice() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("250.00"),
                status = OrderStatus.PENDING_PAYMENT,
            )

        order.confirmPrice()

        assertEquals(BigDecimal("250.00"), order.confirmedPrice)
    }

    @Test
    @DisplayName("Should mark pricing success with null unit price")
    fun testMarkPricingSuccess_NullUnitPrice() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )

        order.markPricingSuccess("PRICE-123", null)

        assertEquals(ValidationStatus.SUCCESS, order.pricingCheckStatus)
        assertEquals("PRICE-123", order.pricingReference)
        assertNull(order.unitPrice)
        assertEquals(BigDecimal("100.00"), order.amount) // unchanged
    }
}
