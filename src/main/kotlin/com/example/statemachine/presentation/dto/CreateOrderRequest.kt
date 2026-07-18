package com.example.statemachine.presentation.dto

import com.example.statemachine.domain.enums.Market
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class CreateOrderRequest(
    @field:NotBlank(message = "Order number is required")
    val orderNo: String,
    val productId: String? = null,
    val productName: String? = null,
    @field:Min(value = 1, message = "Quantity must be at least 1")
    val quantity: Int? = 1,
    @field:DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    val amount: BigDecimal? = null,
    @field:NotNull(message = "Market is required")
    val market: Market,
)
