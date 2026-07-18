package com.example.statemachine.barrieraggregate

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class BarrierAggregateTest {
    private lateinit var repository: BarrierAggregateRepository
    private lateinit var barrierAggregate: TestBarrierAggregate

    class TestBarrierAggregate(
        repository: BarrierAggregateRepository,
    ) : BarrierAggregate(repository) {
        var onAllBarriersPassedCalled = false
        var lastAggregateKey: String? = null

        override val requiredBarriers = setOf("BARRIER_A", "BARRIER_B")

        override fun onAllBarriersPassed(aggregateKey: String) {
            onAllBarriersPassedCalled = true
            lastAggregateKey = aggregateKey
        }
    }

    @BeforeEach
    fun setUp() {
        repository = mockk()
        barrierAggregate = TestBarrierAggregate(repository)
    }

    @Test
    @DisplayName("Should initialize new barrier aggregate")
    fun testInitializeNew() {
        every { repository.findByAggregateTypeAndAggregateKey(any(), "KEY-001") } returns null
        every { repository.save(any()) } answers {
            firstArg<BarrierAggregateRecord>()
        }

        barrierAggregate.initialize("KEY-001")

        verify { repository.save(any()) }
    }

    @Test
    @DisplayName("Should reinitialize existing barrier aggregate")
    fun testReinitialize() {
        val existing =
            BarrierAggregateRecord(
                id = 1L,
                aggregateType = barrierAggregate.aggregateType,
                aggregateKey = "KEY-001",
                requiredBarriers = setOf("BARRIER_A", "BARRIER_B"),
                passedBarriers = setOf("BARRIER_A"),
                initializedAt = Instant.now().minusSeconds(3600),
            )

        every { repository.findByAggregateTypeAndAggregateKey(any(), "KEY-001") } returns existing
        every { repository.save(any()) } answers {
            firstArg<BarrierAggregateRecord>()
        }

        barrierAggregate.initialize("KEY-001")

        verify { repository.save(any()) }
    }

    @Test
    @DisplayName("Should handle barrier event and trigger callback when all passed")
    fun testHandleBarrierEventAllPassed() {
        val record =
            BarrierAggregateRecord(
                id = 1L,
                aggregateType = barrierAggregate.aggregateType,
                aggregateKey = "KEY-001",
                requiredBarriers = setOf("BARRIER_A", "BARRIER_B"),
                passedBarriers = setOf("BARRIER_B"),
                initializedAt = Instant.now(),
            )

        every { repository.findByAggregateTypeAndAggregateKey(any(), "KEY-001") } returns record
        every { repository.save(any()) } answers {
            val saved = firstArg<BarrierAggregateRecord>()
            saved.copy(passedBarriers = saved.passedBarriers)
        }

        val result = barrierAggregate.handleBarrierEvent("KEY-001", "BARRIER_A")

        assertNotNull(result)
        assertTrue(result!!.isAllBarriersPassed)
        assertTrue(barrierAggregate.onAllBarriersPassedCalled)
        assertEquals("KEY-001", barrierAggregate.lastAggregateKey)
    }

    @Test
    @DisplayName("Should handle barrier event without callback when not all passed")
    fun testHandleBarrierEventNotAllPassed() {
        val record =
            BarrierAggregateRecord(
                id = 1L,
                aggregateType = barrierAggregate.aggregateType,
                aggregateKey = "KEY-001",
                requiredBarriers = setOf("BARRIER_A", "BARRIER_B"),
                passedBarriers = emptySet(),
                initializedAt = Instant.now(),
            )

        every { repository.findByAggregateTypeAndAggregateKey(any(), "KEY-001") } returns record
        every { repository.save(any()) } answers {
            firstArg<BarrierAggregateRecord>()
        }

        val result = barrierAggregate.handleBarrierEvent("KEY-001", "BARRIER_A")

        assertNotNull(result)
        assertFalse(result!!.isAllBarriersPassed)
        assertFalse(barrierAggregate.onAllBarriersPassedCalled)
    }

    @Test
    @DisplayName("Should return null when barrier aggregate not found")
    fun testHandleBarrierEventNotFound() {
        every { repository.findByAggregateTypeAndAggregateKey(any(), "KEY-001") } returns null

        val result = barrierAggregate.handleBarrierEvent("KEY-001", "BARRIER_A")

        assertEquals(null, result)
        assertFalse(barrierAggregate.onAllBarriersPassedCalled)
    }

    @Test
    @DisplayName("Should be idempotent when barrier already passed")
    fun testHandleBarrierEventIdempotent() {
        val record =
            BarrierAggregateRecord(
                id = 1L,
                aggregateType = barrierAggregate.aggregateType,
                aggregateKey = "KEY-001",
                requiredBarriers = setOf("BARRIER_A", "BARRIER_B"),
                passedBarriers = setOf("BARRIER_A"),
                initializedAt = Instant.now(),
            )

        every { repository.findByAggregateTypeAndAggregateKey(any(), "KEY-001") } returns record

        val result = barrierAggregate.handleBarrierEvent("KEY-001", "BARRIER_A")

        assertNotNull(result)
        assertTrue(result!!.passedBarriers.contains("BARRIER_A"))
        verify(inverse = true) { repository.save(any()) }
    }
}
