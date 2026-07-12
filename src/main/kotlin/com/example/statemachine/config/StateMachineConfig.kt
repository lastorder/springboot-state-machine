package com.example.statemachine.config

import com.example.statemachine.action.InventoryCheckAction
import com.example.statemachine.action.NotifyAction
import com.example.statemachine.action.OrderModificationAction
import com.example.statemachine.action.PaymentAction
import com.example.statemachine.action.PricingCheckAction
import com.example.statemachine.action.ShipAction
import com.example.statemachine.action.SubmitAction
import com.example.statemachine.action.ValidationSubmitAction
import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.guard.PaymentGuard
import org.springframework.context.annotation.Configuration
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.StateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer

@Configuration
@EnableStateMachineFactory
class StateMachineConfig(
    private val submitAction: SubmitAction,
    private val paymentAction: PaymentAction,
    private val shipAction: ShipAction,
    private val notifyAction: NotifyAction,
    private val paymentGuard: PaymentGuard,
    private val validationSubmitAction: ValidationSubmitAction,
    private val inventoryCheckAction: InventoryCheckAction,
    private val pricingCheckAction: PricingCheckAction,
    private val orderModificationAction: OrderModificationAction,
) : StateMachineConfigurerAdapter<OrderStatus, OrderEvent>() {
    override fun configure(states: StateMachineStateConfigurer<OrderStatus, OrderEvent>) {
        states.withStates()
            .initial(OrderStatus.CREATED)
            .fork(OrderStatus.PENDING_VALIDATION)
            .join(OrderStatus.PENDING_CONFIRMATION)
            .state(OrderStatus.PENDING_CONFIRMATION)
            .state(OrderStatus.PENDING_PAYMENT)
            .state(OrderStatus.PAID)
            .state(OrderStatus.PENDING_SHIPMENT)
            .state(OrderStatus.SHIPPED)
            .end(OrderStatus.DELIVERED)
            .end(OrderStatus.CANCELLED)
            .end(OrderStatus.REJECTED)
            .end(OrderStatus.REFUNDED)
            .and()
            // Fork 的第一个子状态区域 - 库存检查
            .withStates()
            .parent(OrderStatus.PENDING_VALIDATION)
            .initial(OrderStatus.INVENTORY_CHECK)
            .end(OrderStatus.INVENTORY_CHECK)
            .state(OrderStatus.INVENTORY_CHECK, inventoryCheckAction, null)
            .and()
            // Fork 的第二个子状态区域 - 报价检查
            .withStates()
            .parent(OrderStatus.PENDING_VALIDATION)
            .initial(OrderStatus.PRICING_CHECK)
            .end(OrderStatus.PRICING_CHECK)
            .state(OrderStatus.PRICING_CHECK, pricingCheckAction, null)
    }

    override fun configure(transitions: StateMachineTransitionConfigurer<OrderStatus, OrderEvent>) {
        transitions
            // ========== Fork: 进入并行验证 ==========
            .withFork()
            .source(OrderStatus.CREATED)
            .target(OrderStatus.PENDING_VALIDATION)
            .and()
            // ========== Join: 并行验证完成，进入用户确认 ==========
            .withJoin()
            .source(OrderStatus.INVENTORY_CHECK)
            .source(OrderStatus.PRICING_CHECK)
            .target(OrderStatus.PENDING_CONFIRMATION)
            .and()
            // ========== 并行验证 - 成功处理 ==========
            // 库存检查成功 - 保持当前子状态，等待另一个完成
            .withExternal()
            .source(OrderStatus.INVENTORY_CHECK).target(OrderStatus.INVENTORY_CHECK)
            .event(OrderEvent.INVENTORY_SUCCESS)
            .and()
            // 报价检查成功 - 保持当前子状态，等待另一个完成
            .withExternal()
            .source(OrderStatus.PRICING_CHECK).target(OrderStatus.PRICING_CHECK)
            .event(OrderEvent.PRICING_SUCCESS)
            .and()
            // ========== 并行验证 - 失败处理 ==========
            // 库存检查失败 -> 取消
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.INVENTORY_FAILED)
            .action(notifyAction)
            .and()
            // 报价检查失败 -> 取消
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.PRICING_FAILED)
            .action(notifyAction)
            .and()
            // 验证超时 -> 取消
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.VALIDATION_TIMEOUT)
            .action(notifyAction)
            .and()
            // 并行验证中取消
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .action(notifyAction)
            .and()
            // ========== 重试验证 ==========
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CREATED)
            .event(OrderEvent.RETRY_VALIDATION)
            .action(validationSubmitAction)
            .and()
            // ========== 用户确认流程 ==========
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.PENDING_PAYMENT)
            .event(OrderEvent.USER_CONFIRM)
            .action(submitAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.REJECTED)
            .event(OrderEvent.USER_REJECT)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.CREATED)
            .event(OrderEvent.MODIFY_ORDER)
            .action(orderModificationAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .action(notifyAction)
            .and()
            // 库存服务主动修改（只在 PENDING_CONFIRMATION 和 PENDING_PAYMENT 状态）
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.PENDING_CONFIRMATION)
            .event(OrderEvent.INVENTORY_MODIFIED)
            .action(notifyAction)
            .and()
            // ========== 支付流程 ==========
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.CREATED)
            .event(OrderEvent.MODIFY_ORDER)
            .action(orderModificationAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.PAID)
            .event(OrderEvent.PAY)
            .guard(paymentGuard)
            .action(paymentAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .action(notifyAction)
            .and()
            // 库存服务主动修改（在 PENDING_PAYMENT 状态）
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.PENDING_CONFIRMATION)
            .event(OrderEvent.INVENTORY_MODIFIED)
            .action(notifyAction)
            .and()
            // ========== 支付后流程 ==========
            .withExternal()
            .source(OrderStatus.PAID).target(OrderStatus.PENDING_SHIPMENT)
            .event(OrderEvent.CONFIRM_PAYMENT)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.PAID).target(OrderStatus.REFUNDED)
            .event(OrderEvent.REFUND)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_SHIPMENT).target(OrderStatus.SHIPPED)
            .event(OrderEvent.SHIP)
            .action(shipAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_SHIPMENT).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.SHIPPED).target(OrderStatus.DELIVERED)
            .event(OrderEvent.DELIVER)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.DELIVERED).target(OrderStatus.REFUNDED)
            .event(OrderEvent.REFUND)
            .action(notifyAction)
            .and()
    }
}
