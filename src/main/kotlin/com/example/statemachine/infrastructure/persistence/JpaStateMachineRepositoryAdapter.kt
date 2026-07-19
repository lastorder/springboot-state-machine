package com.example.statemachine.infrastructure.persistence

import com.example.statemachine.core.StateMachine
import com.example.statemachine.core.TransitionTable
import com.example.statemachine.persistence.StateMachineRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class JpaStateMachineRepositoryAdapter<S : Enum<S>>(
    private val jpaRepository: StateMachineJpaRepository,
    private val initialState: S,
    private val transitionTable: TransitionTable<S>,
) : StateMachineRepository<S> {
    private val cache = ConcurrentHashMap<String, StateMachine<S>>()

    override fun findById(id: String): StateMachine<S>? {
        cache[id]?.let { return it }

        val entity = jpaRepository.findById(id).orElse(null) ?: return null

        val state = enumValueOf<S>(entity.state)
        val sm =
            StateMachine.restore(
                id = id,
                currentState = state,
                initialState = initialState,
                transitionTable = transitionTable,
                repository = this,
            )
        cache[id] = sm
        return sm
    }

    override fun save(stateMachine: StateMachine<S>) {
        val entity =
            StateMachineEntity(
                machineId = stateMachine.id,
                state = stateMachine.state.name,
            )
        jpaRepository.save(entity)
        cache[stateMachine.id] = stateMachine
        log.debug("Saved state machine: id={}, state={}", stateMachine.id, stateMachine.state)
    }

    override fun deleteById(id: String) {
        jpaRepository.deleteById(id)
        cache.remove(id)
        log.debug("Deleted state machine: id={}", id)
    }

    companion object {
        private val log = LoggerFactory.getLogger(JpaStateMachineRepositoryAdapter::class.java)
    }
}
