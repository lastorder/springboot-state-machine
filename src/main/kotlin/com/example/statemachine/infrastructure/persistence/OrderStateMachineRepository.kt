package com.example.statemachine.infrastructure.persistence

import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.core.StateMachine
import com.example.statemachine.core.TransitionTable
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.persistence.StateMachineRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class OrderStateMachineRepository(
    private val jpaRepository: StateMachineJpaRepository,
    private val transitionTable: TransitionTable<OrderStatus>,
    private val listener: StateChangedListener<OrderStatus>,
) : StateMachineRepository<OrderStatus> {
    private val cache = ConcurrentHashMap<String, StateMachine<OrderStatus>>()

    override fun findById(id: String): StateMachine<OrderStatus>? {
        cache[id]?.let { return it }

        val entity = jpaRepository.findById(id).orElse(null) ?: return null

        val state = OrderStatus.valueOf(entity.state)
        val sm =
            StateMachine.restore(
                id = id,
                currentState = state,
                initialState = OrderStatus.INIT,
                transitionTable = transitionTable,
                listener = listener,
                repository = this,
            )
        cache[id] = sm
        return sm
    }

    override fun save(stateMachine: StateMachine<OrderStatus>) {
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
        private val log = LoggerFactory.getLogger(OrderStateMachineRepository::class.java)
    }
}
