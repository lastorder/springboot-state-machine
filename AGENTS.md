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
- `doc/state-machine-design.html` - State machine design documentation

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

This is a Spring Boot State Machine demo for order management with Fork/Join parallel validation:

- **Domain Layer**: Order entity, OrderStatus enum, OrderEvent enum, ValidationStatus enum
- **Repository Layer**: JPA repositories
- **StateMachine Layer**: Fork/Join state machine configuration with persistence
- **Command Inbox Layer**: Command queuing and serialization for thread-safe state machine access
- **Service Layer**: Business logic with retry support
- **Controller Layer**: REST APIs
- **Kafka Layer**: Event-driven communication
- **Action Layer**: State machine actions (ValidationSubmitAction, InventoryCheckAction, PricingCheckAction)
- **Scheduler Layer**: DB-Scheduler for task execution and timeout handling

## State Machine Flow (Fork/Join Parallel Validation)

### States
```
CREATED
  ↓ (Fork: SUBMIT_VALIDATION)
PENDING_VALIDATION
  ├→ INVENTORY_CHECK (parallel)
  └→ PRICING_CHECK (parallel)
  ↓ (Join: both succeed)
PENDING_CONFIRMATION
  ↓ (USER_CONFIRM)
PENDING_PAYMENT
  ↓ (PAY)
PAID
  ↓ (CONFIRM_PAYMENT)
PENDING_SHIPMENT
  ↓ (SHIP)
SHIPPED
  ↓ (DELIVER)
DELIVERED
```

### Parallel Validation
- **Fork State**: `PENDING_VALIDATION` - Entry point for parallel validation
- **Parallel States**: `INVENTORY_CHECK` and `PRICING_CHECK` run simultaneously
- **Join State**: `PENDING_CONFIRMATION` - Reached when both validations succeed
- **Failure**: Either validation failure leads to `CANCELLED`
- **Retry**: Max 3 retries via `POST /api/orders/{id}/retry-validation`
- **Timeout**: 10 minutes (configurable)

### Events Triggered By
- **AUTO**: `SUBMIT_VALIDATION` - Automatically triggered after order creation
- **Kafka**: `INVENTORY_SUCCESS`, `INVENTORY_FAILED`, `PRICING_SUCCESS`, `PRICING_FAILED`, `CONFIRM_PAYMENT`, `SHIP`, `DELIVER`, `REFUND`, `INVENTORY_MODIFIED`
- **HTTP**: `USER_CONFIRM`, `USER_REJECT`, `PAY`, `CANCEL`, `MODIFY_ORDER`, `RETRY_VALIDATION`

### Inventory Service Modification
- Only allowed in `PENDING_CONFIRMATION` and `PENDING_PAYMENT` states
- Triggered by Kafka topic `inventory.order.modified`
- Results in order update and notification

## Key Configuration

### Application Properties
```yaml
order:
  validation:
    max-retries: 3        # Max retry attempts
    timeout: 10m          # Validation timeout

# Command Inbox Configuration
command-inbox:
  enabled: true
  default-retries: 5
  cleanup:
    enabled: true
    retention-days: 30

# DB-Scheduler Configuration
db-scheduler:
  enabled: true
  threads: 5
  polling-interval: 5s
  heartbeat-interval: 1m
  immediate-execution-enabled: true
  polling-strategy: lock-and-fetch
```

### Kafka Topics
- `inventory.check.request/response` - Inventory validation
- `pricing.request/response` - Pricing validation  
- `inventory.order.modified` - Inventory service modifications
- `payment.confirmed` - Payment confirmation
- `order.shipped` - Shipping notification
- `order.delivered` - Delivery notification
- `order.refunded` - Refund notification
- `order.events` - Status change event broadcast

## Key Implementation Files

- `StateMachineConfig.kt` - Fork/Join state machine configuration
- `ValidationSubmitAction.kt` - Sends parallel validation requests
- `InventoryCheckAction.kt` - Handles inventory check state
- `PricingCheckAction.kt` - Handles pricing check state
- `OrderEventConsumer.kt` - Kafka event consumer
- `OrderService.kt` - Business logic with validation retry
- `Order.kt` - Domain entity with validation tracking fields

## Command Inbox Pattern (Thread Safety)

Spring State Machine is not thread-safe. To ensure sequential processing of state transitions for the same order, all state machine events must go through the Command Inbox:

### Architecture
```
HTTP/Kafka → CommandInboxService → DB → DB-Scheduler → StateMachineService
                                        (One Task per Order)
```

### Key Design
- **One Task per Order**: DB-Scheduler ensures only one task instance per `task_id` (orderId) can execute at a time
- **Task Execution**: Task loops through pending commands for an order sequentially
- **Self-Rescheduling**: After processing a command, Task waits 100ms then checks for more commands

### Key Components
- `CommandInbox.kt` - Command entity with deduplication, priority, expiration
- `CommandInboxService.kt` - Submit and manage commands
- `SchedulerTaskConfig.kt` - OrderStateMachineTask definition
- `ValidationTimeoutService.kt` - Timeout scheduling

### Command Priority Levels
- `URGENT (200)`: CANCEL, REFUND, PAYMENT operations
- `HIGH (100)`: Validation, confirmation operations  
- `NORMAL (0)`: Standard operations

### Command Status Flow
```
PENDING → PROCESSING → COMPLETED
                    → SKIPPED (state machine rejected)
                    → EXPIRED
```

### Execution Guarantee
1. Each order has exactly one active Task (`task_id = orderId`)
2. DB-Scheduler uses `SELECT FOR UPDATE` to ensure single-threaded execution per task
3. Task processes commands in order: `priority DESC, id ASC`
4. Failed commands are marked SKIPPED, Task continues to next command
5. Task ends when no more PENDING commands exist

See `doc/adr/001-command-inbox-pattern.md` for full ADR.
