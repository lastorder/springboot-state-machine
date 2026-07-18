plugins {
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    kotlin("plugin.jpa") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("jacoco")
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springStateMachineVersion"] = "4.0.2"
extra["dbSchedulerVersion"] = "16.12.0"
extra["shedLockVersion"] = "6.2.0"

sourceSets {
    create("integrationTest") {
        kotlin {
            srcDir("src/integrationTest/kotlin")
            compileClasspath += main.get().output + test.get().output
            runtimeClasspath += main.get().output + test.get().output
        }
        resources {
            srcDir("src/integrationTest/resources")
        }
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

val integrationTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")

    implementation("org.springframework.statemachine:spring-statemachine-starter:${property("springStateMachineVersion")}")
    implementation("org.springframework.statemachine:spring-statemachine-data-jpa:${property("springStateMachineVersion")}")

    implementation("com.github.kagkarlsson:db-scheduler-spring-boot-starter:${property("dbSchedulerVersion")}")

    implementation("net.javacrumbs.shedlock:shedlock-spring:${property("shedLockVersion")}")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:${property("shedLockVersion")}")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    runtimeOnly("org.postgresql:postgresql")

    // Unit test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.springframework.statemachine:spring-statemachine-test:${property("springStateMachineVersion")}")

    // Integration test dependencies (Testcontainers)
    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.testcontainers:testcontainers:1.21.1")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter:1.21.1")
    "integrationTestImplementation"("org.testcontainers:postgresql:1.21.1")
    "integrationTestImplementation"("org.testcontainers:kafka:1.21.1")
    "integrationTestImplementation"("org.springframework.kafka:spring-kafka-test")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    description = "Runs unit tests."
    group = "verification"
    systemProperty("spring.profiles.active", "unit")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "integration")
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(tasks.named("integrationTest"))
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

ktlint {
    android.set(false)
    outputColorName.set("RED")
    version.set("1.5.0")
}
