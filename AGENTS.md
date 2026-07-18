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

This is a Spring Boot State Machine demo for order initialization with Barrier Aggregate pattern:

- **Domain Layer**: Order entity, OrderStatus enum, OrderEvent enum
- **Repository Layer**: JPA repositories
- **StateMachine Layer**: Simple state machine configuration with persistence
- **Barrier Aggregate Layer**: Handles parallel event waiting (VOM + DOM)
- **TaskSpec Layer**: Task specification with distributed lock support
- **Service Layer**: Business logic
- **Controller Layer**: REST APIs
- **Kafka Layer**: Event-driven communication
- **Action Layer**: State machine actions (PrApprovedAction, SendCoeAction, SyncDealAction)
- **Scheduler Layer**: DB-Scheduler for task execution
- **Lock Layer**: ShedLock for distributed locking

## State Machine Flow (Barrier Aggregate Pattern)

### States
```
INIT (初始状态)
  ↓ (PR_APPROVED: Kafka消息，保存Order到DB)
LOCAL_INITIALIZED
  ↓ (发送COE到Kafka，初始化屏障)
FACTORY_ORDER_SUBMITTED (同步状态到deal服务REST API)
  ↓ (Barrier Aggregate: 等待 VOM 和 DOM)
  ├→ VOM 收到 → 屏障传递
  └→ DOM 收到 → 屏障传递
  ↓ (所有屏障通过)
ORDER_INITIALIZE_SUCCEED

异常分支:
VOM_FAILED → ORDER_INITIALIZE_FAILED
```

### Barrier Aggregate Pattern

Single-table JSONB design with optimistic locking:

```sql
CREATE TABLE barrier_aggregate (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(500) NOT NULL,      -- 屏障聚合类型（类全名）
    aggregate_key VARCHAR(255) NOT NULL,       -- 业务键（如 orderNo）
    required_barriers JSONB NOT NULL,          -- 必需屏障 ["VOM", "DOM"]
    passed_barriers JSONB NOT NULL DEFAULT '[]', -- 已通过屏障 ["VOM"]
    initialized_at TIMESTAMP NOT NULL,         -- 初始化时间
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,         -- 乐观锁版本号
    CONSTRAINT uk_barrier_aggregate UNIQUE(aggregate_type, aggregate_key)
);
```

**Key Features**:
- **Single Row**: One record per aggregate object
- **JSONB Fields**: `requiredBarriers` and `passedBarriers` stored as JSON arrays
- **Waiting Duration**: `initializedAt` enables calculating wait time
- **Optimistic Locking**: `@Version` annotation for concurrent safety
- **Idempotent**: `initialize()` resets, `handleBarrierEvent()` appends with deduplication

### Events
| Event | 来源 | 触发 |
|-------|------|------|
| PR_APPROVED | Kafka (pr.approved) | INIT → LOCAL_INITIALIZED |
| VOM | Kafka (factory.vom) | Passes VOM barrier |
| DOM | Kafka (factory.dom) | Passes DOM barrier |
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
- `StateMachineConfig.kt` - Simple state machine configuration
- `PrApprovedAction.kt` - Saves Order from PR_APPROVED event
- `SendCoeAction.kt` - Sends COE to Kafka, initializes barriers
- `SyncDealAction.kt` - Syncs order to deal service
- `OrderEventConsumer.kt` - Kafka event consumer
- `OrderService.kt` - Business logic

### Barrier Aggregate
- `barrieraggregate/BarrierAggregate.kt` - Abstract base class with core logic
- `barrieraggregate/BarrierAggregateRecord.kt` - Domain model with computed properties
- `barrieraggregate/BarrierAggregateRepository.kt` - Repository interface
- `order/barrier/OrderInitBarrier.kt` - Barrier constants (VOM, DOM)
- `order/barrier/OrderInitBarrierAggregate.kt` - Order init barrier implementation

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
- `barrier_aggregate` - Barrier aggregate records

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
