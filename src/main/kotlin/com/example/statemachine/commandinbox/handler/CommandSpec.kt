package com.example.statemachine.commandinbox.handler

import com.example.statemachine.commandinbox.domain.BackoffStrategy
import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.dto.BackoffConfig
import kotlin.reflect.KClass

interface CommandSpec<P : Any, R : Any> {
    val commandType: String
    val payloadType: KClass<P>
    val responseType: KClass<R>

    val defaultMaxRetries: Int get() = 3
    val defaultBackoffStrategy: BackoffStrategy get() = BackoffStrategy.FIXED
    val defaultBackoffConfig: BackoffConfig get() = BackoffConfig.DEFAULT
    val defaultPriority: CommandPriority get() = CommandPriority.NORMAL

    fun handle(context: CommandContext<P>): CommandResult<R>
}
