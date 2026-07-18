package com.example.statemachine.infrastructure.persistence.entity

import com.example.statemachine.domain.enums.Market
import com.example.statemachine.domain.enums.OrderStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "orders")
class OrderJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true)
    var orderNo: String,
    @Column
    var productId: String? = null,
    @Column
    var productName: String? = null,
    @Column(nullable = false)
    var quantity: Int = 1,
    @Column(precision = 19, scale = 2)
    var amount: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.INIT,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var market: Market,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
)
