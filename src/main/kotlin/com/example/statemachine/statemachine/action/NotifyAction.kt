package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.kafka.OrderEventProducer
import com.example.statemachine.infrastructure.kafka.dto.OrderStatusChangeEvent
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class NotifyAction(
    private val orderEventProducer: OrderEventProducer,
) : Action<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
        val orderId = context.message.headers["orderId", Long::class.java]
        val fromStatus = context.source?.id
        val toStatus = context.target?.id
        val event = context.event

        log.info("Sending notification: orderId=$orderId, event=$event, from=$fromStatus, to=$toStatus")

        val changeEvent =
            OrderStatusChangeEvent(
                orderId = orderId ?: 0,
                fromStatus = fromStatus,
                toStatus = toStatus,
                event = event,
                timestamp = Instant.now(),
            )
        orderEventProducer.sendStatusChangeEvent(changeEvent)
    }
}
