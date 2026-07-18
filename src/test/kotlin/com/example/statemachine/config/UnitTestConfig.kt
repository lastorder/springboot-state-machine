package com.example.statemachine.config

import com.example.statemachine.barrieraggregate.BarrierAggregateRepository
import com.example.statemachine.infrastructure.kafka.CoeProducer
import com.example.statemachine.infrastructure.kafka.OrderEventProducer
import com.example.statemachine.infrastructure.rest.DealClient
import com.example.statemachine.order.barrier.OrderInitBarrierAggregate
import com.example.statemachine.statemachine.action.PrApprovedAction
import com.example.statemachine.statemachine.action.SendCoeAction
import com.example.statemachine.statemachine.action.SyncDealAction
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate

@TestConfiguration
class UnitTestConfig {
    @Bean
    @Primary
    fun kafkaTemplate(): KafkaTemplate<String, Any> = mockk(relaxed = true)

    @Bean
    @Primary
    fun orderEventProducer(kafkaTemplate: KafkaTemplate<String, Any>): OrderEventProducer = OrderEventProducer(kafkaTemplate)

    @Bean
    @Primary
    fun coeProducer(kafkaTemplate: KafkaTemplate<String, Any>): CoeProducer = CoeProducer(kafkaTemplate)

    @Bean
    @Primary
    fun dealClient(): DealClient = mockk(relaxed = true)

    @Bean
    @Primary
    fun barrierAggregateRepository(): BarrierAggregateRepository = mockk(relaxed = true)

    @Bean
    fun prApprovedAction(
        orderRepository: com.example.statemachine.domain.repository.OrderRepository,
        stateMachineService: com.example.statemachine.statemachine.service.StateMachineService,
    ): PrApprovedAction = PrApprovedAction(orderRepository, stateMachineService)

    @Bean
    fun sendCoeAction(
        coeProducer: CoeProducer,
        orderInitBarrierAggregate: OrderInitBarrierAggregate,
    ): SendCoeAction = SendCoeAction(coeProducer, orderInitBarrierAggregate)

    @Bean
    fun syncDealAction(dealClient: DealClient): SyncDealAction = SyncDealAction(dealClient)
}
