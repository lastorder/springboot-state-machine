package com.example.statemachine.kafka

import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.kafka.dto.InventoryCheckResponse
import com.example.statemachine.kafka.dto.InventoryOrderModified
import com.example.statemachine.kafka.dto.OrderDeliveredEvent
import com.example.statemachine.kafka.dto.OrderRefundedEvent
import com.example.statemachine.kafka.dto.OrderShippedEvent
import com.example.statemachine.kafka.dto.PaymentConfirmedEvent
import com.example.statemachine.kafka.dto.PricingResponse
import com.example.statemachine.service.OrderService
import com.example.statemachine.service.StateMachineService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer(
    private val stateMachineService: StateMachineService,
    private val orderService: OrderService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["inventory.check.response"], groupId = "order-state-machine-group")
    fun onInventoryCheckResponse(record: ConsumerRecord<String, InventoryCheckResponse>) {
        val event = record.value()
        log.info("Received inventory check response: orderId=${event.orderId}, available=${event.available}")

        if (event.available) {
            orderService.markInventorySuccess(
                orderId = event.orderId,
                inventoryReference = event.inventoryReference,
            )

            // 发送库存检查成功事件
            stateMachineService.sendEvent(event.orderId, OrderEvent.INVENTORY_SUCCESS)

            // 检查是否所有验证都完成
            checkValidationComplete(event.orderId)
        } else {
            orderService.markInventoryFailed(event.orderId)
            stateMachineService.sendEvent(event.orderId, OrderEvent.INVENTORY_FAILED)
        }
    }

    @KafkaListener(topics = ["pricing.response"], groupId = "order-state-machine-group")
    fun onPricingResponse(record: ConsumerRecord<String, PricingResponse>) {
        val event = record.value()
        log.info("Received pricing response: orderId=${event.orderId}, unitPrice=${event.unitPrice}")

        orderService.markPricingSuccess(
            orderId = event.orderId,
            pricingReference = event.pricingReference,
            unitPrice = event.unitPrice,
        )

        // 发送报价成功事件
        stateMachineService.sendEvent(event.orderId, OrderEvent.PRICING_SUCCESS)

        // 检查是否所有验证都完成
        checkValidationComplete(event.orderId)
    }

    @KafkaListener(topics = ["inventory.order.modified"], groupId = "order-state-machine-group")
    fun onInventoryOrderModified(record: ConsumerRecord<String, InventoryOrderModified>) {
        val event = record.value()
        log.info("Received inventory order modified: orderId=${event.orderId}, reason=${event.reason}")

        orderService.updateFromInventoryModification(
            orderId = event.orderId,
            modifiedProduct = event.modifiedProduct,
            modifiedQuantity = event.modifiedQuantity,
            reason = event.reason,
        )

        stateMachineService.sendEvent(event.orderId, OrderEvent.INVENTORY_MODIFIED)
    }

    @KafkaListener(topics = ["payment.confirmed"], groupId = "order-state-machine-group")
    fun onPaymentConfirmed(record: ConsumerRecord<String, PaymentConfirmedEvent>) {
        val event = record.value()
        log.info("Received payment confirmed event: orderId=${event.orderId}")
        stateMachineService.sendEvent(event.orderId, OrderEvent.CONFIRM_PAYMENT)
    }

    @KafkaListener(topics = ["order.shipped"], groupId = "order-state-machine-group")
    fun onOrderShipped(record: ConsumerRecord<String, OrderShippedEvent>) {
        val event = record.value()
        log.info("Received order shipped event: orderId=${event.orderId}")
        stateMachineService.sendEvent(event.orderId, OrderEvent.SHIP)
    }

    @KafkaListener(topics = ["order.delivered"], groupId = "order-state-machine-group")
    fun onOrderDelivered(record: ConsumerRecord<String, OrderDeliveredEvent>) {
        val event = record.value()
        log.info("Received order delivered event: orderId=${event.orderId}")
        stateMachineService.sendEvent(event.orderId, OrderEvent.DELIVER)
    }

    @KafkaListener(topics = ["order.refunded"], groupId = "order-state-machine-group")
    fun onOrderRefunded(record: ConsumerRecord<String, OrderRefundedEvent>) {
        val event = record.value()
        log.info("Received order refunded event: orderId=${event.orderId}")
        stateMachineService.sendEvent(event.orderId, OrderEvent.REFUND)
    }

    private fun checkValidationComplete(orderId: Long) {
        val order = orderService.getOrderEntity(orderId)
        if (order != null && order.isValidationComplete()) {
            log.info("Validation complete for order: orderId=$orderId, triggering Join")
            // Spring State Machine 的 Join 会在所有子状态结束时自动触发
            // 这里我们只需要确保两个子状态都已经结束
        }
    }
}
