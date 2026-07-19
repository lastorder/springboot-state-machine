package com.example.statemachine.persistence

import com.example.statemachine.api.State
import java.util.concurrent.ConcurrentHashMap

class InMemoryStateMachineRepository<S : State> : StateMachineRepository<S> {
    private val storage: ConcurrentHashMap<String, S> = ConcurrentHashMap()

    override fun findById(machineId: String): S? = storage[machineId]

    override fun save(
        machineId: String,
        state: S,
    ) {
        storage[machineId] = state
    }

    override fun delete(machineId: String) {
        storage.remove(machineId)
    }

    override fun existsById(machineId: String): Boolean = storage.containsKey(machineId)

    fun clear() {
        storage.clear()
    }

    fun size(): Int = storage.size
}
