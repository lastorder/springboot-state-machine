package com.example.statemachine.controller

import com.example.statemachine.controller.dto.CreateOrderRequest
import com.example.statemachine.controller.dto.ModifyOrderRequest
import com.example.statemachine.controller.dto.OrderResponse
import com.example.statemachine.controller.dto.PaymentRequest
import com.example.statemachine.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
) {
    @PostMapping
    fun createOrder(
        @Valid @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<OrderResponse> {
        val order = orderService.createOrder(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(order)
    }

    @GetMapping("/{id}")
    fun getOrder(
        @PathVariable id: Long,
    ): ResponseEntity<OrderResponse> {
        val order = orderService.getOrder(id)
        return if (order != null) {
            ResponseEntity.ok(order)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping
    fun getAllOrders(): ResponseEntity<List<OrderResponse>> {
        val orders = orderService.getAllOrders()
        return ResponseEntity.ok(orders)
    }

    @PostMapping("/{id}/retry-validation")
    fun retryValidation(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        val success = orderService.retryValidation(id)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "message" to "Validation retry initiated"))
        } else {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to retry validation"))
        }
    }

    @PatchMapping("/{id}")
    fun modifyOrder(
        @PathVariable id: Long,
        @Valid @RequestBody request: ModifyOrderRequest,
    ): ResponseEntity<Map<String, Any>> {
        val success = orderService.modifyOrder(id, request)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "message" to "Order modified successfully, re-entering validation process"))
        } else {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to modify order"))
        }
    }

    @PostMapping("/{id}/confirm")
    fun confirmOrder(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        val success = orderService.confirmOrder(id)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "message" to "Order confirmed successfully"))
        } else {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to confirm order"))
        }
    }

    @PostMapping("/{id}/reject")
    fun rejectOrder(
        @PathVariable id: Long,
        @RequestParam(required = false) reason: String?,
    ): ResponseEntity<Map<String, Any>> {
        val success = orderService.rejectOrder(id, reason)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "message" to "Order rejected"))
        } else {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to reject order"))
        }
    }

    @PostMapping("/{id}/submit")
    fun submitOrder(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        val success = orderService.submitOrder(id)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "message" to "Order submitted successfully"))
        } else {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to submit order"))
        }
    }

    @PostMapping("/{id}/pay")
    fun payOrder(
        @PathVariable id: Long,
        @Valid @RequestBody request: PaymentRequest,
    ): ResponseEntity<Map<String, Any>> {
        val success = orderService.payOrder(id, request.amount)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "message" to "Payment processed successfully"))
        } else {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to process payment"))
        }
    }

    @PostMapping("/{id}/cancel")
    fun cancelOrder(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        val success = orderService.cancelOrder(id)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "message" to "Order cancelled successfully"))
        } else {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to cancel order"))
        }
    }
}
