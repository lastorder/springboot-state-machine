package com.example.statemachine.domain

enum class OrderEvent {
    // 并行验证相关
    SUBMIT_VALIDATION, // 发起并行验证（触发 Fork）
    INVENTORY_SUCCESS, // 库存检查成功
    INVENTORY_FAILED, // 库存检查失败
    PRICING_SUCCESS, // 报价成功
    PRICING_FAILED, // 报价失败
    VALIDATION_TIMEOUT, // 验证超时
    RETRY_VALIDATION, // 重试验证

    // 库存服务主动修改（独立事件）
    INVENTORY_MODIFIED,

    // 用户操作
    USER_CONFIRM,
    USER_REJECT,
    MODIFY_ORDER,

    // 支付与配送
    PAY,
    CONFIRM_PAYMENT,
    SHIP,
    DELIVER,

    // 取消与退款
    CANCEL,
    REFUND,
}
