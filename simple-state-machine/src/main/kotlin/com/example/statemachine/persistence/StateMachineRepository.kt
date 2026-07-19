package com.example.statemachine.persistence

import com.example.statemachine.core.StateMachine

interface StateMachineRepository<S : Enum<S>> {
    fun findById(id: String): StateMachine<S>?

    fun save(stateMachine: StateMachine<S>)

    fun deleteById(id: String)

    fun existsById(id: String): Boolean = findById(id) != null
}
