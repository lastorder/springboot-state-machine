package com.example.statemachine.config

import com.example.statemachine.action.InventoryCheckAction
import com.example.statemachine.action.NotifyAction
import com.example.statemachine.action.OrderModificationAction
import com.example.statemachine.action.PaymentAction
import com.example.statemachine.action.PricingCheckAction
import com.example.statemachine.action.ShipAction
import com.example.statemachine.action.SubmitAction
import com.example.statemachine.action.ValidationSubmitAction
import com.example.statemachine.guard.PaymentGuard
import com.example.statemachine.kafka.OrderEventProducer
import com.example.statemachine.service.StateMachineService
import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate

@TestConfiguration
class TestConfig {
    @Bean
    @Primary
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
    }

    @Bean
    @Primary
    fun orderEventProducer(kafkaTemplate: KafkaTemplate<String, Any>): OrderEventProducer {
        return OrderEventProducer(kafkaTemplate)
    }

    @Bean
    @Primary
    fun stateMachineService(): StateMachineService {
        return Mockito.mock(StateMachineService::class.java)
    }

    @Bean
    fun submitAction(): SubmitAction = SubmitAction()

    @Bean
    fun paymentAction(): PaymentAction = PaymentAction()

    @Bean
    fun shipAction(): ShipAction = ShipAction()

    @Bean
    fun notifyAction(orderEventProducer: OrderEventProducer): NotifyAction {
        return NotifyAction(orderEventProducer)
    }

    @Bean
    fun paymentGuard(): PaymentGuard {
        return PaymentGuard(Mockito.mock(com.example.statemachine.repository.OrderRepository::class.java))
    }

    @Bean
    fun inventoryCheckAction(): InventoryCheckAction {
        return InventoryCheckAction(
            Mockito.mock(com.example.statemachine.repository.OrderRepository::class.java),
            Mockito.mock(OrderEventProducer::class.java),
        )
    }

    @Bean
    fun validationSubmitAction(): ValidationSubmitAction {
        return ValidationSubmitAction(
            Mockito.mock(com.example.statemachine.repository.OrderRepository::class.java),
            Mockito.mock(OrderEventProducer::class.java),
        )
    }

    @Bean
    fun pricingCheckAction(): PricingCheckAction {
        return PricingCheckAction(
            Mockito.mock(com.example.statemachine.repository.OrderRepository::class.java),
            Mockito.mock(OrderEventProducer::class.java),
        )
    }

    @Bean
    fun orderModificationAction(): OrderModificationAction = OrderModificationAction()
}
