# ADR-001: Command Inbox Pattern for State Machine Concurrency Control

## Status

Accepted (Updated 2026-07-13)

## Context

### Problem Statement

Spring State Machine is not thread-safe. In our Order Management system with Fork/Join parallel validation, we face concurrency issues when:

1. Multiple HTTP requests trigger state transitions for the same order simultaneously
2. Kafka consumers process multiple events for an order concurrently
3. Parallel validation (inventory check + pricing check) completes and sends events at the same time
4. Timeout events fire while other events are being processed

This leads to:
- Race conditions in state transitions
- Inconsistent state machine state
- Lost events or duplicate processing
- Database optimistic locking failures

### Current Architecture

```
HTTP Request → OrderController → StateMachineService → Spring State Machine
                                        ↓
                              (Concurrent Access)
                                        ↓
Kafka Message → OrderEventConsumer ────────────────────┘
```

## Decision

Implement **Command Inbox Pattern** with **DB-Scheduler** using **one Task per groupId** model.

### Solution Architecture

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    Command Sources                       │
                    │  HTTP API | Kafka Consumer | Scheduled Tasks            │
                    └──────────────────────────┬──────────────────────────────┘
                                               │
                                               ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │                     CommandBus                           │
                    │  commandBus.submit(spec, groupId, payload)               │
                    │                                                          │
                    │  1. Deduplication check (idempotency key)                │
                    │  2. Use spec's default config (priority, retries, etc.)   │
                    │  3. Persist to command table                              │
                    │  4. Schedule DB-Scheduler task (if needed)               │
                    └──────────────────────────┬──────────────────────────────┘
                                               │
                                               ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │                      command table                        │
                    │  - id, group_id, command_type, payload, status           │
                    │  - idempotency_key (deduplication)                       │
                    │  - priority (URGENT > HIGH > NORMAL)                     │
                    │  - max_retries, backoff_strategy, backoff_config         │
                    │  - response (successful result)                          │
                    │  - metadata (traceId, spanId, source, etc.)              │
                    └──────────────────────────┬──────────────────────────────┘
                                               │
                                               ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │           DB-Scheduler (One Task Per Group)              │
                    │                                                          │
                    │  task_id = groupId (unique per group)                    │
                    │                                                          │
                    │  Loop:                                                   │
                    │    1. SELECT next PENDING command                        │
                    │    2. Get CommandSpec from CommandSpecRegistry            │
                    │    3. Execute spec.handle(context)                        │
                    │       → Success: markCompleted()                          │
                    │       → Failure: scheduleRetry() or markFailed()          │
                    │       → Skipped: markSkipped()                            │
                    │    4. Sleep 100ms                                        │
                    │    5. Check for more PENDING commands                     │
                    │       → Yes: Reschedule self                              │
                    │       → No: Task ends                                     │
                    └──────────────────────────┬──────────────────────────────┘
                                               │
                                               ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │               Spring State Machine                        │
                    │  SINGLE-THREADED per order (guaranteed by DB-Scheduler) │
                    │  Fork/Join works correctly with sequential events        │
                    └─────────────────────────────────────────────────────────┘
```

### Key Design: One Task Per Group

**Core Principle**: Each group (order) has exactly one Task instance (`task_id = groupId`)

**Why This Works**:
1. DB-Scheduler uses `SELECT FOR UPDATE SKIP LOCKED` to pick tasks
2. Same `task_id` cannot be picked by multiple threads simultaneously
3. Task loops through pending commands sequentially
4. After processing each command, Task checks for more and reschedules itself

**Task Execution Flow**:
```
Task(order-100) starts:
  ├─ Get C1 (PENDING) → Execute → Success → markCompleted(C1)
  │  └─ Sleep 100ms → Check: C2 exists (PENDING)
  │     └─ Reschedule Task(order-100)
  │
  └─ Task(order-100) picks up again:
     ├─ Get C2 (PENDING) → Execute → Failed → scheduleRetry(C2)
     │  └─ Sleep 100ms → Check: no more PENDING
     │     └─ Task ends naturally
```

### Key Components

#### 1. CommandSpec Interface

```kotlin
interface CommandSpec<P : Any, R : Any> {
    val commandType: String
    val payloadType: KClass<P>
    val responseType: KClass<R>
    
    // Default configuration
    val defaultMaxRetries: Int get() = 3
    val defaultBackoffStrategy: BackoffStrategy get() = BackoffStrategy.FIXED
    val defaultBackoffConfig: BackoffConfig get() = BackoffConfig.DEFAULT
    val defaultPriority: CommandPriority get() = CommandPriority.NORMAL
    
    fun handle(context: CommandContext<P>): CommandResult<R>
}
```

#### 2. CommandBus

- `submit(spec, groupId, payload)`: Submit command using spec's default config
- `submit(groupId, commandType, payload, ...)`: Submit with custom parameters
- `markCompleted()`: Mark command as successfully processed
- `markSkipped()`: Mark command as skipped
- `markFailed()`: Mark command as permanently failed
- `scheduleRetry()`: Schedule command for retry with backoff

#### 3. CommandSpecRegistry

- Auto-registers all `CommandSpec` beans by `commandType`
- Provides `getSpec(commandType)` for task execution

#### 4. Command Table

```sql
CREATE TABLE command (
    id BIGSERIAL PRIMARY KEY,
    group_id VARCHAR(255) NOT NULL,
    command_type VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    payload JSONB,
    response JSONB,
    metadata JSONB,
    priority INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    retry_count INT NOT NULL DEFAULT 0,
    backoff_strategy VARCHAR(20) NOT NULL DEFAULT 'FIXED',
    backoff_config JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_command_idempotency UNIQUE (group_id, command_type, idempotency_key)
);
```

### Command Status Flow

```
PENDING → (executing) → COMPLETED (success)
                       → SKIPPED (rejected by handler)
                       → RETRYING (transient failure)
                              ↓
                       FAILED (max retries exceeded)
```

### Retry Strategies

| Strategy | Formula | Example (initial=1s, retries=3) |
|----------|---------|--------------------------------|
| FIXED | delay = initial | 1s, 1s, 1s |
| LINEAR | delay = initial × (retry + 1) | 1s, 2s, 3s |
| EXPONENTIAL | delay = initial × multiplier^retry | 1s, 2s, 4s |

### Configuration

```yaml
order:
  validation:
    max-retries: 3
    timeout-minutes: 10

db-scheduler:
  enabled: true
  threads: 5
  polling-interval: 5s
  heartbeat-interval: 1m
  immediate-execution-enabled: true
  polling-strategy: lock-and-fetch
```

## Consequences

### Positive

1. **Thread Safety**: State machine operations are serialized per order (guaranteed by DB-Scheduler)
2. **No Extra Locks**: No need for distributed locks or order_execution_lock table
3. **Resource Efficient**: Only active orders have Tasks; inactive orders' Tasks end naturally
4. **Simple Design**: One Task per order, self-scheduling loop
5. **Observability**: Full audit trail in command table; Task execution visible in scheduled_tasks
6. **Priority Handling**: Urgent operations processed first within an order
7. **Idempotency**: Duplicate submissions rejected automatically
8. **Type Safety**: CommandSpec provides compile-time payload/response types
9. **Auto Retry**: Configurable retry with backoff strategies
10. **Clean API**: `spec.submit(groupId, payload)` with minimal parameters

### Negative

1. **Latency**: Commands processed asynchronously; slight delay due to 100ms wait between commands
2. **Complexity**: Additional components (CommandSpec, CommandBus, DB-Scheduler tasks)

### Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Command backlog | Priority ordering ensures urgent commands first |
| Failed commands | Retry with backoff; max retries prevents infinite retry |
| Database overflow | Cleanup task removes old commands after 30 days |

## Implementation

### Files

| File | Purpose |
|------|---------|
| `commandinbox/domain/Command.kt` | Core domain model |
| `commandinbox/domain/BackoffStrategy.kt` | Retry strategies |
| `commandinbox/domain/CommandPriority.kt` | Priority levels |
| `commandinbox/domain/CommandStatus.kt` | Status enum |
| `commandinbox/dto/CommandDTOs.kt` | DTOs for submit result, metadata, backoff config |
| `commandinbox/handler/CommandSpec.kt` | Spec interface with default config |
| `commandinbox/handler/CommandContext.kt` | Execution context |
| `commandinbox/handler/CommandResult.kt` | Result types (Success/Failure/Skipped) |
| `commandinbox/handler/CommandSpecRegistry.kt` | Spec registration |
| `commandinbox/service/CommandBus.kt` | Submit and manage commands |
| `commandinbox/scheduler/CommandTaskConfig.kt` | DB-Scheduler task |
| `commandinbox/scheduler/ValidationTimeoutTaskConfig.kt` | Timeout and cleanup tasks |
| `order/handler/OrderStateMachineSpec.kt` | Order state transition spec |

### API Changes

- HTTP endpoints return `202 Accepted` with `CommandSubmitResult`
- Use `GET /api/orders/{orderId}/commands/{commandId}` to check command status

### Example Usage

```kotlin
// Define a spec
@Component
class OrderStateMachineSpec(
    private val commandBus: CommandBus,
    private val stateMachineService: StateMachineService,
) : CommandSpec<OrderEventPayload, Unit> {
    
    override val commandType = "ORDER_STATE_TRANSITION"
    override val payloadType = OrderEventPayload::class
    override val responseType = Unit::class
    override val defaultPriority = CommandPriority.URGENT
    
    override fun handle(context: CommandContext<OrderEventPayload>): CommandResult<Unit> {
        // Execute state transition
    }
    
    // Convenient submit method
    fun submit(orderId: Long, event: OrderEvent, ...) = 
        commandBus.submit(this, orderId.toString(), OrderEventPayload(event, ...))
}

// Use the spec
orderStateMachineSpec.submit(orderId, OrderEvent.USER_CONFIRM)
```

## References

- [DB-Scheduler Documentation](https://github.com/kagkarlsson/db-scheduler)
- [Single Socket Channel Pattern](https://martinfowler.com/articles/patterns-of-distributed-systems/single-socket-channel.html)

## History

- 2026-07-12: Initial proposal with per-command Tasks
- 2026-07-12: Revised to one Task per Order model (accepted)
- 2026-07-13: Refactored to CommandSpec + CommandBus pattern
  - Renamed `CommandHandler` to `CommandSpec`
  - Renamed `CommandInboxService` to `CommandBus`
  - Added default config to each spec
  - Removed `expiresAt` (controlled by maxRetries only)
  - Simplified submit API: `spec.submit(groupId, payload)`
