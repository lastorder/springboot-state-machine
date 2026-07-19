package com.example.statemachine.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StateMachineJpaRepository : JpaRepository<StateMachineEntity, String>
