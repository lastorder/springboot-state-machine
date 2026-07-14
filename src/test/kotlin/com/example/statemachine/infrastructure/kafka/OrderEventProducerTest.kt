package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.kafka.dto.OrderStatusChangeEvent
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
                fromStatus = OrderStatus.INIT,
                toStatus = OrderStatus.LOCAL_INITIALIZED,
                event = OrderEvent.PR_APPROVED,
                timestamp = Instant.now(),
            )

        Assertions.assertEquals(1L, event.orderId)
        Assertions.assertEquals(OrderStatus.INIT, event.fromStatus)
        Assertions.assertEquals(OrderStatus.LOCAL_INITIALIZED, event.toStatus)
        Assertions.assertEquals(OrderEvent.PR_APPROVED, event.event)
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
