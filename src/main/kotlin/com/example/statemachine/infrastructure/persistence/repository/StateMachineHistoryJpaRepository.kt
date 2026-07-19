package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.infrastructure.persistence.entity.StateMachineHistoryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StateMachineHistoryJpaRepository : JpaRepository<StateMachineHistoryEntity, Long> {
    fun findByMachineIdOrderByCreatedAtAsc(machineId: String): List<StateMachineHistoryEntity>

    fun findByMachineIdOrderByCreatedAtDesc(machineId: String): List<StateMachineHistoryEntity>
}
