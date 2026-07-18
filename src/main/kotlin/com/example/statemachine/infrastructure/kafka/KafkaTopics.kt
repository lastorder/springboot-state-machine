package com.example.statemachine.infrastructure.kafka

object KafkaTopics {
    const val PR_APPROVED = "pr.approved"
    const val FACTORY_VOM = "factory.vom"
    const val FACTORY_DOM = "factory.dom"
    const val FACTORY_VOM_FAILED = "factory.vom.failed"
    const val COE_ORDER_CREATED = "coe.order.created"
    const val CHANGE_TRIGGER = "change-trigger"
    const val BARRIER_PASS = "barrier.pass"
    const val ORDER_EVENTS = "order.events"
}
