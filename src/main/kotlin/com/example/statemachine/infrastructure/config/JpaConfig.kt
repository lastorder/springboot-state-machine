package com.example.statemachine.infrastructure.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["com.example.statemachine.infrastructure.persistence.repository"])
@EntityScan(
    basePackages = [
        "com.example.statemachine.infrastructure.persistence.entity",
        "org.springframework.statemachine.data.jpa",
    ],
)
class JpaConfig
