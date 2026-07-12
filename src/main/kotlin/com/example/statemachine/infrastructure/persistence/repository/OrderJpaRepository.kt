package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.infrastructure.persistence.entity.OrderJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long>
