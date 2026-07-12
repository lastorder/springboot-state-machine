package com.example.statemachine

import com.example.statemachine.config.TestConfig
import com.example.statemachine.repository.OrderRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@Import(TestConfig::class)
@ActiveProfiles("test")
class OrderStateMachineApplicationTests {
    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Test
    fun contextLoads() {
        Assertions.assertNotNull(orderRepository)
    }
}
