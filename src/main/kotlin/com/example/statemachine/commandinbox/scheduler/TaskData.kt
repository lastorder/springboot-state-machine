package com.example.statemachine.commandinbox.scheduler

import java.io.Serializable

data class CommandTaskData(
    val groupId: String,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class OrderTaskData(
    val orderId: Long,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class TimeoutCheckData(
    val orderId: Long,
    val validationStartedAt: Long,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
