package com.example.statemachine.presentation.controller

import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.domain.CommandSource
import com.example.statemachine.commandinbox.dto.CommandStatusResponse
import com.example.statemachine.commandinbox.dto.CommandSubmitResult
import com.example.statemachine.commandinbox.exception.CommandNotFoundException
import com.example.statemachine.commandinbox.service.CommandInboxService
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.order.service.OrderService
import com.example.statemachine.presentation.dto.CreateOrderRequest
import com.example.statemachine.presentation.dto.ModifyOrderRequest
import com.example.statemachine.presentation.dto.OrderResponse
import com.example.statemachine.presentation.dto.PaymentRequest
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
    private val commandInboxService: CommandInboxService,
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
    ): ResponseEntity<CommandSubmitResult> {
        val result =
            commandInboxService.submitCommand(
                orderId = id,
                event = OrderEvent.RETRY_VALIDATION,
                source = CommandSource.HTTP,
                priority = CommandPriority.HIGH,
            )
        return ResponseEntity.accepted().body(result)
    }

    @PatchMapping("/{id}")
    fun modifyOrder(
        @PathVariable id: Long,
        @Valid @RequestBody request: ModifyOrderRequest,
    ): ResponseEntity<CommandSubmitResult> {
        val payload = mutableMapOf<String, Any>()
        request.quantity?.let { payload["quantity"] = it }
        request.product?.let { payload["product"] = it }

        val result =
            commandInboxService.submitCommand(
                orderId = id,
                event = OrderEvent.MODIFY_ORDER,
                source = CommandSource.HTTP,
                payload = payload.ifEmpty { null },
            )
        return ResponseEntity.accepted().body(result)
    }

    @PostMapping("/{id}/confirm")
    fun confirmOrder(
        @PathVariable id: Long,
    ): ResponseEntity<CommandSubmitResult> {
        val result =
            commandInboxService.submitCommand(
                orderId = id,
                event = OrderEvent.USER_CONFIRM,
                source = CommandSource.HTTP,
                priority = CommandPriority.HIGH,
            )
        return ResponseEntity.accepted().body(result)
    }

    @PostMapping("/{id}/reject")
    fun rejectOrder(
        @PathVariable id: Long,
        @RequestParam(required = false) reason: String?,
    ): ResponseEntity<CommandSubmitResult> {
        val result =
            commandInboxService.submitCommand(
                orderId = id,
                event = OrderEvent.USER_REJECT,
                source = CommandSource.HTTP,
                payload = if (reason != null) mapOf("reason" to reason) else null,
            )
        return ResponseEntity.accepted().body(result)
    }

    @PostMapping("/{id}/submit")
    fun submitOrder(
        @PathVariable id: Long,
    ): ResponseEntity<CommandSubmitResult> {
        val result =
            commandInboxService.submitCommand(
                orderId = id,
                event = OrderEvent.SUBMIT_VALIDATION,
                source = CommandSource.HTTP,
            )
        return ResponseEntity.accepted().body(result)
    }

    @PostMapping("/{id}/pay")
    fun payOrder(
        @PathVariable id: Long,
        @Valid @RequestBody request: PaymentRequest,
    ): ResponseEntity<CommandSubmitResult> {
        val result =
            commandInboxService.submitCommand(
                orderId = id,
                event = OrderEvent.PAY,
                source = CommandSource.HTTP,
                payload = mapOf("amount" to request.amount),
                priority = CommandPriority.HIGH,
            )
        return ResponseEntity.accepted().body(result)
    }

    @PostMapping("/{id}/cancel")
    fun cancelOrder(
        @PathVariable id: Long,
    ): ResponseEntity<CommandSubmitResult> {
        val result =
            commandInboxService.submitCommand(
                orderId = id,
                event = OrderEvent.CANCEL,
                source = CommandSource.HTTP,
                priority = CommandPriority.URGENT,
            )
        return ResponseEntity.accepted().body(result)
    }

    @GetMapping("/{orderId}/commands/{commandId}")
    fun getCommandStatus(
        @PathVariable orderId: Long,
        @PathVariable commandId: Long,
    ): ResponseEntity<CommandStatusResponse> {
        val command =
            commandInboxService.getCommandStatus(orderId, commandId)
                ?: throw CommandNotFoundException("Command not found: orderId=$orderId, commandId=$commandId")

        return ResponseEntity.ok(
            CommandStatusResponse(
                commandId = command.id!!,
                orderId = command.orderId,
                eventType = command.eventType,
                status = command.status,
                errorMessage = command.errorMessage,
                createdAt = command.createdAt,
                processedAt = command.processedAt,
            ),
        )
    }
}
