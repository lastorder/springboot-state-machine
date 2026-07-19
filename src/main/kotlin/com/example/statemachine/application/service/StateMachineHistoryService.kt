package com.example.statemachine.application.service

import com.example.statemachine.infrastructure.persistence.repository.StateMachineHistoryJpaRepository
import com.example.statemachine.presentation.dto.StateMachineHistoryResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StateMachineHistoryService(
    private val stateMachineHistoryJpaRepository: StateMachineHistoryJpaRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun getHistoryByOrderNo(orderNo: String): List<StateMachineHistoryResponse> =
        stateMachineHistoryJpaRepository
            .findByMachineIdOrderByCreatedAtAsc(orderNo)
            .map { entity ->
                StateMachineHistoryResponse(
                    id = entity.id!!,
                    machineId = entity.machineId,
                    fromState = entity.fromState,
                    toState = entity.toState,
                    event = entity.event,
                    headers = entity.headers?.let { parseHeaders(it) },
                    createdAt = entity.createdAt,
                )
            }

    @Suppress("UNCHECKED_CAST")
    private fun parseHeaders(json: String): Map<String, Any?>? =
        try {
            objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            null
        }
}
