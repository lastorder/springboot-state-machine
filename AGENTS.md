# Agent Instructions

## Important Rules

**NEVER access directories outside of the project root.** If temporary files or directories are needed, create them inside the project (e.g., `.tmp/` or `logs/`).

## Build & Test Commands

This project uses Gradle with Kotlin DSL. Always run the following commands after making changes:

### Build
```bash
./gradlew build
```

### Run Unit Tests (fast, ~5s)
```bash
./gradlew test
```

### Run Integration Tests (slower, ~20s)
```bash
./gradlew integrationTest
```

### Run All Tests
```bash
./gradlew check
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
- `src/test/kotlin/com/example/statemachine/` - Unit tests (pure MockK, no Spring Context)
- `src/integrationTest/kotlin/com/example/statemachine/` - Integration tests (SpringBootTest, Testcontainers)
- `build.gradle.kts` - Build configuration
- `docker-compose.yml` - Local development infrastructure
- `doc/state-machine-design.html` - State machine design documentation

## Test Structure

### Unit Tests (`src/test/`)
- Pure MockK, no Spring Context loading
- No Testcontainers, no database
- Fast execution (~5 seconds)
- Test individual classes in isolation

### Integration Tests (`src/integrationTest/`)
- Full Spring Boot Context
- Testcontainers: PostgreSQL + Kafka
- End-to-end API testing
- Slower execution (~20 seconds)

## Testing Requirements

1. **Every code change must be followed by running tests**
2. Minimum test coverage: 80%
3. All tests must pass before committing
4. Use MockK for unit tests
5. Use Testcontainers for integration tests

## Code Style

- Kotlin official style guide
- Use ktlint for formatting
- No wildcard imports
- Maximum line length: 120 characters

## Architecture

This is a Spring Boot State Machine demo for order initialization with Fork/Join pattern:

- **Domain Layer**: Order entity, OrderStatus enum, OrderEvent enum
- **Repository Layer**: JPA repositories
- **StateMachine Layer**: Fork/Join state machine configuration with persistence
- **TaskSpec Layer**: Task specification with distributed lock support
- **Service Layer**: Business logic
- **Controller Layer**: REST APIs
- **Kafka Layer**: Event-driven communication
- **Action Layer**: State machine actions (PrApprovedAction, SendCoeAction, SyncDealAction)
- **Scheduler Layer**: DB-Scheduler for task execution
- **Lock Layer**: ShedLock for distributed locking

## State Machine Flow (Fork/Join Pattern)

### States
```
INIT (初始状态)
  ↓ (PR_APPROVED: Kafka消息，保存Order到DB)
LOCAL_INITIALIZED
  ↓ (发送COE到Kafka)
FACTORY_ORDER_SUBMITTED (同步状态到deal服务REST API)
  ↓ (Fork: 等待工厂事件)
  ├→ FIRST_VOM_RECEIVED (收到VOM，等待DOM)
  └→ FIRST_DOM_RECEIVED (收到DOM，等待VOM)
  ↓ (Join: 两者都收到)
ORDER_INITIALIZE_SUCCEED

异常分支:
VOM_FAILED → ORDER_INITIALIZE_FAILED
```

### Fork/Join Pattern
- **Fork State**: `FACTORY_ORDER_SUBMITTED` - Entry point for parallel waiting
- **Parallel States**: `FIRST_VOM_RECEIVED` and `FIRST_DOM_RECEIVED` wait for each other
- **Join State**: `ORDER_INITIALIZE_SUCCEED` - Reached when both VOM and DOM received
- **Failure**: `VOM_FAILED` event leads to `ORDER_INITIALIZE_FAILED`

### Events
| Event | 来源 | 触发 |
|-------|------|------|
| PR_APPROVED | Kafka (pr.approved) | INIT → LOCAL_INITIALIZED |
| VOM | Kafka (factory.vom) | FACTORY_ORDER_SUBMITTED → FIRST_VOM_RECEIVED |
| DOM | Kafka (factory.dom) | FACTORY_ORDER_SUBMITTED → FIRST_DOM_RECEIVED |
| VOM_FAILED | Kafka (factory.vom.failed) | * → ORDER_INITIALIZE_FAILED |

### Kafka Topics
- `pr.approved` - PR审批通过事件
- `coe.order.created` - COE订单创建事件
- `factory.vom` - 工厂VOM事件
- `factory.dom` - 工厂DOM事件
- `factory.vom.failed` - 工厂VOM失败事件
- `order.events` - Status change event broadcast

## Key Configuration

### Application Properties
```yaml
order:
  validation:
    max-retries: 3        # Max retry attempts

# DB-Scheduler Configuration
db-scheduler:
  enabled: true
  threads: 5
  polling-interval: 5s
  heartbeat-interval: 1m
  immediate-execution-enabled: true
  polling-strategy: lock-and-fetch

# ShedLock Configuration (for distributed locking)
shedlock:
  enabled: true
```

## TaskSpec Pattern

Simplified task scheduling with distributed lock support:

### Architecture
```
TaskSpec<P> (interface)
    └── LockingTaskSpec<P> (abstract, ShedLock)
            └── OrderStateMachineTaskSpec (concrete)

TaskScheduler -> SchedulerClient -> scheduled_tasks table
```

### Key Components
- `TaskSpec<P>` - Interface defining task type, maxRetries, execution logic
- `LockingTaskSpec<P>` - Abstract class with ShedLock distributed lock
- `TaskScheduler` - Facade wrapping db-scheduler's SchedulerClient
- `TaskSpecAdapterFactory` - Converts TaskSpec to db-scheduler OneTimeTask
- `TaskSpecAutoConfiguration` - Auto-registers TaskSpec beans

### Usage Example
```kotlin
@Component
class OrderStateMachineTaskSpec(
    stateMachineService: StateMachineService,
    lockProvider: LockProvider
) : LockingTaskSpec<OrderEventPayload>(
    taskName = "order-state-machine",
    maxRetries = 5,
    lockProvider = lockProvider,
    lockKeyProvider = { ctx -> "order:${ctx.payload.orderId}" }
) {
    override fun executeWithLock(context: TaskContext<OrderEventPayload>): TaskResult {
        stateMachineService.sendEvent(context.payload.orderId, context.payload.event)
        return TaskResult.success()
    }
}
```

### Submitting Tasks
```kotlin
taskScheduler.submit(
    spec = orderStateMachineTaskSpec,
    instanceId = "order-123-PR_APPROVED-${UUID.randomUUID()}",
    payload = OrderEventPayload(123L, OrderEvent.PR_APPROVED)
)
```

## Key Implementation Files

### State Machine
- `StateMachineConfig.kt` - Fork/Join state machine configuration
- `PrApprovedAction.kt` - Saves Order from PR_APPROVED event
- `SendCoeAction.kt` - Sends COE to Kafka
- `SyncDealAction.kt` - Syncs order to deal service
- `OrderEventConsumer.kt` - Kafka event consumer
- `OrderService.kt` - Business logic

### TaskSpec
- `task/spec/TaskSpec.kt` - Core interface
- `task/spec/LockingTaskSpec.kt` - Distributed lock wrapper
- `task/spec/TaskContext.kt` - Execution context
- `task/spec/TaskResult.kt` - Result sealed class
- `task/scheduler/TaskScheduler.kt` - Submit tasks
- `task/scheduler/TaskSpecAdapterFactory.kt` - Adapter
- `task/scheduler/ShedLockConfig.kt` - ShedLock configuration
- `order/task/OrderStateMachineTaskSpec.kt` - Order state machine task

### Tables
- `scheduled_tasks` - db-scheduler native table
- `shedlock` - ShedLock lock table
- `orders` - Order entity
- `state_machine` - State machine persistence

## Code Quality

### Suppressing Deprecation Warnings
The following deprecation warnings are intentionally suppressed:

1. **db-scheduler `schedule()` method** - Suppressed in `TaskScheduler.kt` and `TaskSchedulerTest.kt`
   ```kotlin
   @Suppress("DEPRECATION")  // db-scheduler's schedule() is deprecated but still functional
   fun <P : Serializable> submit(...) { ... }
   ```

2. **testcontainers KafkaContainer** - Suppressed in `IntegrationTestConfig.kt`
   ```kotlin
   @file:Suppress("DEPRECATION")  // Using legacy KafkaContainer for compatibility
   ```

### Conventions
- Use `requireNotNull()` or explicit null checks instead of `!!` force unwrap
- Use short class names with imports instead of fully qualified class names
- No unused code - remove deprecated methods when replacing with newer API
