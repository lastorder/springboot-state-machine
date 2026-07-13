package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.dto.CommandMetadata
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.kafka.dto.InventoryCheckResponse
import com.example.statemachine.infrastructure.kafka.dto.InventoryOrderModified
import com.example.statemachine.infrastructure.kafka.dto.OrderDeliveredEvent
import com.example.statemachine.infrastructure.kafka.dto.OrderRefundedEvent
import com.example.statemachine.infrastructure.kafka.dto.OrderShippedEvent
import com.example.statemachine.infrastructure.kafka.dto.PaymentConfirmedEvent
import com.example.statemachine.infrastructure.kafka.dto.PricingResponse
import com.example.statemachine.order.service.OrderCommandService
import com.example.statemachine.order.service.OrderService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer(
    private val orderCommandService: OrderCommandService,
    private val orderService: OrderService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["inventory.check.response"], groupId = "order-state-machine-group")
    fun onInventoryCheckResponse(record: ConsumerRecord<String, InventoryCheckResponse>) {
        val event = record.value()
        log.info("Received inventory check response: orderId=${event.orderId}, available=${event.available}")

        val metadata =
            CommandMetadata(
                source = "KAFKA",
                sourceReference = "kafka:${record.partition()}:${record.offset()}",
            )

        if (event.available) {
            orderService.markInventorySuccess(
                orderId = event.orderId,
                inventoryReference = event.inventoryReference,
            )

            orderCommandService.submitOrderEvent(
                orderId = event.orderId,
                event = OrderEvent.INVENTORY_SUCCESS,
                metadata = metadata,
                priority = CommandPriority.HIGH,
            )
        } else {
            orderService.markInventoryFailed(event.orderId)
            orderCommandService.submitOrderEvent(
                orderId = event.orderId,
                event = OrderEvent.INVENTORY_FAILED,
                metadata = metadata,
                priority = CommandPriority.HIGH,
            )
        }
    }

    @KafkaListener(topics = ["pricing.response"], groupId = "order-state-machine-group")
    fun onPricingResponse(record: ConsumerRecord<String, PricingResponse>) {
        val event = record.value()
        log.info("Received pricing response: orderId=${event.orderId}, unitPrice=${event.unitPrice}")

        val metadata =
            CommandMetadata(
                source = "KAFKA",
                sourceReference = "kafka:${record.partition()}:${record.offset()}",
            )

        orderService.markPricingSuccess(
            orderId = event.orderId,
            pricingReference = event.pricingReference,
            unitPrice = event.unitPrice,
        )

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.PRICING_SUCCESS,
            metadata = metadata,
            priority = CommandPriority.HIGH,
        )
    }

    @KafkaListener(topics = ["inventory.order.modified"], groupId = "order-state-machine-group")
    fun onInventoryOrderModified(record: ConsumerRecord<String, InventoryOrderModified>) {
        val event = record.value()
        log.info("Received inventory order modified: orderId=${event.orderId}, reason=${event.reason}")

        val metadata =
            CommandMetadata(
                source = "KAFKA",
                sourceReference = "kafka:${record.partition()}:${record.offset()}",
            )

        orderService.updateFromInventoryModification(
            orderId = event.orderId,
            modifiedProduct = event.modifiedProduct,
            modifiedQuantity = event.modifiedQuantity,
            reason = event.reason,
        )

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.INVENTORY_MODIFIED,
            metadata = metadata,
        )
    }

    @KafkaListener(topics = ["payment.confirmed"], groupId = "order-state-machine-group")
    fun onPaymentConfirmed(record: ConsumerRecord<String, PaymentConfirmedEvent>) {
        val event = record.value()
        log.info("Received payment confirmed event: orderId=${event.orderId}")

        val metadata =
            CommandMetadata(
                source = "KAFKA",
                sourceReference = "kafka:${record.partition()}:${record.offset()}",
            )

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.CONFIRM_PAYMENT,
            metadata = metadata,
            priority = CommandPriority.URGENT,
        )
    }

    @KafkaListener(topics = ["order.shipped"], groupId = "order-state-machine-group")
    fun onOrderShipped(record: ConsumerRecord<String, OrderShippedEvent>) {
        val event = record.value()
        log.info("Received order shipped event: orderId=${event.orderId}")

        val metadata =
            CommandMetadata(
                source = "KAFKA",
                sourceReference = "kafka:${record.partition()}:${record.offset()}",
            )

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.SHIP,
            metadata = metadata,
        )
    }

    @KafkaListener(topics = ["order.delivered"], groupId = "order-state-machine-group")
    fun onOrderDelivered(record: ConsumerRecord<String, OrderDeliveredEvent>) {
        val event = record.value()
        log.info("Received order delivered event: orderId=${event.orderId}")

        val metadata =
            CommandMetadata(
                source = "KAFKA",
                sourceReference = "kafka:${record.partition()}:${record.offset()}",
            )

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.DELIVER,
            metadata = metadata,
        )
    }

    @KafkaListener(topics = ["order.refunded"], groupId = "order-state-machine-group")
    fun onOrderRefunded(record: ConsumerRecord<String, OrderRefundedEvent>) {
        val event = record.value()
        log.info("Received order refunded event: orderId=${event.orderId}")

        val metadata =
            CommandMetadata(
                source = "KAFKA",
                sourceReference = "kafka:${record.partition()}:${record.offset()}",
            )

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.REFUND,
            metadata = metadata,
            priority = CommandPriority.URGENT,
        )
    }
}
