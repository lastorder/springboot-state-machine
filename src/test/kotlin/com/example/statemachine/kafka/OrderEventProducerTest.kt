package com.example.statemachine.kafka

import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.kafka.dto.OrderStatusChangeEvent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class OrderEventProducerTest {
    @Test
    @DisplayName("Should create order status change event")
    fun testCreateOrderStatusChangeEvent() {
        val event =
            OrderStatusChangeEvent(
                orderId = 1L,
                fromStatus = OrderStatus.CREATED,
                toStatus = OrderStatus.PENDING_PAYMENT,
                event = OrderEvent.USER_CONFIRM,
                timestamp = Instant.now(),
            )

        Assertions.assertEquals(1L, event.orderId)
        Assertions.assertEquals(OrderStatus.CREATED, event.fromStatus)
        Assertions.assertEquals(OrderStatus.PENDING_PAYMENT, event.toStatus)
        Assertions.assertEquals(OrderEvent.USER_CONFIRM, event.event)
        Assertions.assertNotNull(event.timestamp)
    }

    @Test
    @DisplayName("Should create event with null values")
    fun testCreateEventWithNulls() {
        val event =
            OrderStatusChangeEvent(
                orderId = 2L,
                fromStatus = null,
                toStatus = null,
                event = null,
                timestamp = Instant.now(),
            )

        Assertions.assertEquals(2L, event.orderId)
        Assertions.assertNull(event.fromStatus)
        Assertions.assertNull(event.toStatus)
        Assertions.assertNull(event.event)
    }
}
