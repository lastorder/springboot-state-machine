package com.example.statemachine.statemachine.config

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.statemachine.service.OrderStatusSyncService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State
import org.springframework.statemachine.transition.Transition
import org.springframework.stereotype.Component

@Component
class StateMachineListener(
    private val orderStatusSyncService: OrderStatusSyncService,
) : StateMachineListenerAdapter<OrderStatus, OrderEvent>() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun stateChanged(
        from: State<OrderStatus, OrderEvent>?,
        to: State<OrderStatus, OrderEvent>?,
    ) {
        log.info("State changed: ${from?.id} -> ${to?.id}")
    }

    override fun transition(transition: Transition<OrderStatus, OrderEvent>) {
        log.info(
            "Transition: ${transition.source?.id} -> ${transition.target?.id}, trigger=${transition.trigger}",
        )
    }

    override fun stateMachineStopped(stateMachine: StateMachine<OrderStatus, OrderEvent>) {
        // 状态机停止时同步最终状态到订单表
        val orderNo = stateMachine.id
        val finalState = stateMachine.state?.id

        log.info("State machine stopped: orderNo=$orderNo, finalState=$finalState")

        if (!orderNo.isNullOrBlank() && finalState != null) {
            syncOrderStatus(orderNo, finalState)
        }
    }

    override fun eventNotAccepted(event: Message<OrderEvent>) {
        log.warn("Event not accepted: ${event.payload}")
    }

    override fun stateMachineError(
        stateMachine: StateMachine<OrderStatus, OrderEvent>,
        exception: Exception,
    ) {
        log.error("State machine error", exception)
    }

    override fun extendedStateChanged(
        key: Any?,
        value: Any?,
    ) {
        log.debug("Extended state changed: $key = $value")
    }

    override fun stateEntered(state: State<OrderStatus, OrderEvent>) {
        log.debug("State entered: ${state.id}")
    }

    override fun stateExited(state: State<OrderStatus, OrderEvent>) {
        log.debug("State exited: ${state.id}")
    }

    /**
     * 同步订单状态
     * machineId 就是 orderNo
     */
    private fun syncOrderStatus(
        orderNo: String,
        newStatus: OrderStatus,
    ) {
        if (orderNo.isBlank()) {
            log.debug("Skipping status sync for empty orderNo")
            return
        }

        // 使用独立事务的服务进行状态同步
        try {
            orderStatusSyncService.syncOrderStatusByOrderNo(orderNo, newStatus)
        } catch (e: Exception) {
            // 状态同步失败不影响状态机执行，只记录日志
            log.error("Status sync failed but state machine continues: orderNo=$orderNo, status=$newStatus", e)
        }
    }
}
