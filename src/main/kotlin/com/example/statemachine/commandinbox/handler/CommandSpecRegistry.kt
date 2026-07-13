package com.example.statemachine.commandinbox.handler

import org.springframework.stereotype.Component

@Component
class CommandSpecRegistry(
    specs: List<CommandSpec<*, *>>,
) {
    private val specs: Map<String, CommandSpec<*, *>> = specs.associateBy { it.commandType }

    fun getSpec(commandType: String): CommandSpec<*, *>? = specs[commandType]

    fun requireSpec(commandType: String): CommandSpec<*, *> =
        getSpec(commandType)
            ?: throw IllegalArgumentException("No spec registered for command type: $commandType")

    fun hasSpec(commandType: String): Boolean = specs.containsKey(commandType)

    fun getAllCommandTypes(): Set<String> = specs.keys
}
