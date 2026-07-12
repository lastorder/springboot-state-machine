package com.example.statemachine.config

import com.example.statemachine.commandinbox.service.CommandInboxService
import com.example.statemachine.infrastructure.kafka.OrderEventProducer
import com.example.statemachine.statemachine.action.InventoryCheckAction
import com.example.statemachine.statemachine.action.NotifyAction
import com.example.statemachine.statemachine.action.OrderModificationAction
import com.example.statemachine.statemachine.action.PaymentAction
import com.example.statemachine.statemachine.action.PricingCheckAction
import com.example.statemachine.statemachine.action.ShipAction
import com.example.statemachine.statemachine.action.SubmitAction
import com.example.statemachine.statemachine.action.ValidationSubmitAction
import com.example.statemachine.statemachine.guard.PaymentGuard
import com.example.statemachine.statemachine.service.StateMachineService
import com.github.kagkarlsson.scheduler.SchedulerClient
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate

@TestConfiguration
class TestConfig {
    @Bean
    @Primary
    fun kafkaTemplate(): KafkaTemplate<String, Any> = mockk(relaxed = true)

    @Bean
    @Primary
    fun orderEventProducer(kafkaTemplate: KafkaTemplate<String, Any>): OrderEventProducer = OrderEventProducer(kafkaTemplate)

    @Bean
    @Primary
    fun stateMachineService(): StateMachineService = mockk(relaxed = true)

    @Bean
    @Primary
    fun commandInboxService(): CommandInboxService = mockk(relaxed = true)

    @Bean
    @Primary
    fun schedulerClient(): SchedulerClient = mockk(relaxed = true)

    @Bean
    fun submitAction(): SubmitAction = SubmitAction()

    @Bean
    fun paymentAction(): PaymentAction = PaymentAction()

    @Bean
    fun shipAction(): ShipAction = ShipAction()

    @Bean
    fun notifyAction(orderEventProducer: OrderEventProducer): NotifyAction = NotifyAction(orderEventProducer)

    @Bean
    fun paymentGuard(): PaymentGuard = PaymentGuard(mockk(relaxed = true))

    @Bean
    fun inventoryCheckAction(): InventoryCheckAction =
        InventoryCheckAction(
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

    @Bean
    fun validationSubmitAction(): ValidationSubmitAction =
        ValidationSubmitAction(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

    @Bean
    fun pricingCheckAction(): PricingCheckAction =
        PricingCheckAction(
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

    @Bean
    fun orderModificationAction(): OrderModificationAction = OrderModificationAction()
}
