package com.example.statemachine.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "state_machine")
class StateMachineEntity(
    @Id
    @Column(name = "machine_id", nullable = false)
    var machineId: String,
    @Column(name = "state", nullable = false)
    var state: String,
    @Lob
    @Column(name = "state_machine_context")
    var context: ByteArray? = null,
)
