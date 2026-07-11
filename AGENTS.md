# Agent Instructions

## Build & Test Commands

This project uses Gradle with Kotlin DSL. Always run the following commands after making changes:

### Build
```bash
./gradlew build
```

### Run Tests
```bash
./gradlew test
```

### Run Tests with Coverage
```bash
./gradlew test jacocoTestReport
```

### Check Code Style
```bash
./gradlew ktlintCheck
```

### Format Code
```bash
./gradlew ktlintFormat
```

### Run Application Locally
```bash
./gradlew bootRun
```

### Run with Docker Compose
```bash
docker-compose up -d
```

## Project Structure

- `src/main/kotlin/com/example/statemachine/` - Main source code
- `src/test/kotlin/com/example/statemachine/` - Test code
- `build.gradle.kts` - Build configuration
- `docker-compose.yml` - Local development infrastructure

## Testing Requirements

1. **Every code change must be followed by running tests**
2. Minimum test coverage: 80%
3. All tests must pass before committing
4. Use Testcontainers for integration tests (PostgreSQL, Kafka)
5. Use MockK for unit tests

## Code Style

- Kotlin official style guide
- Use ktlint for formatting
- No wildcard imports
- Maximum line length: 120 characters

## Architecture

This is a Spring Boot State Machine demo for order management:

- **Domain Layer**: Order entity, OrderStatus enum, OrderEvent enum
- **Repository Layer**: JPA repositories
- **StateMachine Layer**: State machine configuration with persistence
- **Service Layer**: Business logic
- **Controller Layer**: REST APIs
- **Kafka Layer**: Event-driven communication

## State Machine Flow

Order states: CREATED → PENDING_PAYMENT → PAID → PENDING_SHIPMENT → SHIPPED → DELIVERED

Events triggered by:
- HTTP: SUBMIT, PAY, CANCEL
- Kafka: CONFIRM_PAYMENT, SHIP, DELIVER, REFUND
