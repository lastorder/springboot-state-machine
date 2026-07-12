package com.example.statemachine.presentation.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class ModifyOrderRequest(
    @field:Size(min = 1, max = 255, message = "Product name must be between 1 and 255 characters")
    val product: String? = null,
    @field:Min(value = 1, message = "Quantity must be at least 1")
    val quantity: Int? = null,
) {
    fun hasChanges(): Boolean = product != null || quantity != null
}
