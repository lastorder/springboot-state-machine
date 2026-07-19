package com.example.statemachine.presentation.controller

import com.example.statemachine.application.service.OrderCommandService
import com.example.statemachine.application.service.OrderService
import com.example.statemachine.application.service.StateMachineHistoryService
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.presentation.dto.CreateOrderRequest
import com.example.statemachine.presentation.dto.OrderResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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
    private val orderCommandService: OrderCommandService,
    private val stateMachineHistoryService: StateMachineHistoryService,
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

    @GetMapping("/order-no/{orderNo}")
    fun getOrderByOrderNo(
        @PathVariable orderNo: String,
    ): ResponseEntity<OrderResponse> {
        val order = orderService.getOrderByOrderNo(orderNo)
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

    @PostMapping("/events")
    fun sendEvent(
        @RequestParam orderNo: String,
        @RequestParam event: OrderEvent,
    ): ResponseEntity<Map<String, Any>> {
        orderCommandService.submitOrderEvent(orderNo, event)
        return ResponseEntity.accepted().body(
            mapOf(
                "orderNo" to orderNo,
                "event" to event.name,
                "status" to "submitted",
            ),
        )
    }

    @GetMapping("/{orderNo}/history")
    fun getOrderHistory(
        @PathVariable orderNo: String,
    ): ResponseEntity<List<com.example.statemachine.presentation.dto.StateMachineHistoryResponse>> {
        val history = stateMachineHistoryService.getHistoryByOrderNo(orderNo)
        return ResponseEntity.ok(history)
    }
}
