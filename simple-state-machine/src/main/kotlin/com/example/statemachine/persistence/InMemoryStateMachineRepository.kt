package com.example.statemachine.persistence

import com.example.statemachine.core.StateMachine
import java.util.concurrent.ConcurrentHashMap

class InMemoryStateMachineRepository<S : Enum<S>> : StateMachineRepository<S> {
    private val storage: ConcurrentHashMap<String, StateMachine<S>> = ConcurrentHashMap()

    override fun findById(id: String): StateMachine<S>? = storage[id]

    override fun save(stateMachine: StateMachine<S>) {
        storage[stateMachine.id] = stateMachine
    }

    override fun deleteById(id: String) {
        storage.remove(id)
    }

    override fun existsById(id: String): Boolean = storage.containsKey(id)

    fun clear() {
        storage.clear()
    }

    fun size(): Int = storage.size
}
