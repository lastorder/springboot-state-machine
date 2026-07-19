package com.example.statemachine.persistence

import com.example.statemachine.api.State

interface StateMachineRepository<S : State> {
    fun findById(machineId: String): S?

    fun save(
        machineId: String,
        state: S,
    )

    fun delete(machineId: String)

    fun existsById(machineId: String): Boolean = findById(machineId) != null
}
