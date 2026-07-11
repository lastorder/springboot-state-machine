package com.example.statemachine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OrderStateMachineApplication

fun main(args: Array<String>) {
    runApplication<OrderStateMachineApplication>(*args)
}
