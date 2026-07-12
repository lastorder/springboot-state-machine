# ADR-001: Command Inbox Pattern for State Machine Concurrency Control

## Status

Accepted

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

Implement **Command Inbox Pattern** with **DB-Scheduler** using **one Task per Order** model.

### Solution Architecture

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    Command Sources                       │
                    │  HTTP API | Kafka Consumer | Scheduled Tasks            │
                    └──────────────────────────┬──────────────────────────────┘
                                               │
                                               ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │               CommandInboxService.submitCommand()        │
                    │  1. Deduplication check (idempotency key)                │
                    │  2. Expiration check                                      │
                    │  3. Priority assignment                                   │
                    │  4. Persist to command_inbox table                        │
                    │  5. Schedule DB-Scheduler task (if needed)               │
                    └──────────────────────────┬──────────────────────────────┘
                                               │
                                               ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │                    command_inbox table                    │
                    │  - id, order_id, event_type, payload, status             │
                    │  - idempotency_key (deduplication)                       │
                    │  - priority (URGENT > HIGH > NORMAL)                     │
                    │  - expires_at (command timeout)                          │
                    └──────────────────────────┬──────────────────────────────┘
                                               │
                                               ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │           DB-Scheduler (One Task Per Order)             │
                    │                                                          │
                    │  task_id = orderId (unique per order)                   │
                    │                                                          │
                    │  Loop:                                                   │
                    │    1. SELECT next PENDING command                       │
                    │    2. Execute StateMachineService.sendEvent()            │
                    │       → Success: markCompleted()                         │
                    │       → Failure: markSkipped() + WARNING log             │
                    │    3. Sleep 100ms                                        │
                    │    4. Check for more PENDING commands                    │
                    │       → Yes: Reschedule self                             │
                    │       → No: Task ends                                    │
                    └──────────────────────────┬──────────────────────────────┘
                                               │
                                               ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │               Spring State Machine                        │
                    │  SINGLE-THREADED per order (guaranteed by DB-Scheduler) │
                    │  Fork/Join works correctly with sequential events        │
                    └─────────────────────────────────────────────────────────┘
```

### Key Design: One Task Per Order

**Core Principle**: Each order has exactly one Task instance (`task_id = orderId`)

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
     ├─ Get C2 (PENDING) → Execute → Failed → markSkipped(C2)
     │  └─ Sleep 100ms → Check: no more PENDING
     │     └─ Task ends naturally
```

### Key Components

#### 1. Command Inbox Table

```sql
CREATE TABLE command_inbox (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    source VARCHAR(20) NOT NULL,           -- HTTP/KAFKA/SCHEDULED/INTERNAL
    source_reference VARCHAR(255),          -- Kafka offset, trace ID, etc.
    correlation_id VARCHAR(100),
    payload JSONB,
    headers JSONB,
    idempotency_key VARCHAR(255),           -- Deduplication
    priority SMALLINT NOT NULL DEFAULT 0,   -- 0=NORMAL, 100=HIGH, 200=URGENT
    expires_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_command_idempotency UNIQUE (order_id, event_type, idempotency_key)
);
```

#### 2. CommandInboxService

- `submitCommand()`: Submit command to inbox with deduplication, priority, expiration
- `markCompleted()`: Mark command as successfully processed
- `markSkipped()`: Mark command as skipped (state machine rejected or exception)
- `markExpired()`: Mark command as expired

#### 3. OrderStateMachineTask (DB-Scheduler One-Time Task)

```kotlin
@Bean
fun orderStateMachineTask(): OneTimeTask<OrderTaskData> {
    return object : OneTimeTask<OrderTaskData>(
        "order-state-machine",
        OrderTaskData::class.java,
    ) {
        override fun executeOnce(taskInstance, executionContext) {
            val orderId = taskInstance.data.orderId
            
            // 1. Get next pending command
            val command = commandInboxRepository.findNextPendingCommand(orderId)
            if (command == null) return  // Task ends
            
            // 2. Execute state machine
            val success = stateMachineService.sendEvent(orderId, command.eventType, headers)
            
            if (success) {
                commandInboxService.markCompleted(command.id)
            } else {
                commandInboxService.markSkipped(command.id, "State machine rejected")
            }
            
            // 3. Wait and check for more
            Thread.sleep(100)
            if (commandInboxRepository.countPendingCommands(orderId) > 0) {
                // Reschedule self
                executionContext.schedulerClient.schedule(...)
            }
        }
    }
}
```

### Command Status Flow

```
PENDING → (executed) → COMPLETED (success)
                     → SKIPPED (state machine rejected or exception)
                     → EXPIRED (past expires_at)
```

### Configuration

```yaml
command-inbox:
  enabled: true
  cleanup:
    enabled: true
    retention-days: 30

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
5. **Observability**: Full audit trail in command_inbox table; Task execution visible in scheduled_tasks
6. **Priority Handling**: Urgent operations processed first within an order
7. **Idempotency**: Duplicate submissions rejected automatically

### Negative

1. **Latency**: Commands processed asynchronously; slight delay due to 100ms wait between commands
2. **No Automatic Retry**: Failed commands marked SKIPPED instead of retrying (by design)
3. **Complexity**: Additional components (CommandInbox, DB-Scheduler tasks)

### Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Command backlog | Priority ordering ensures urgent commands first |
| Programmed SKIPPED commands | WARNING logs for investigation; consistent state |
| Database overflow | Cleanup task removes old commands after 30 days |

## Implementation

### Files Added/Modified

| File | Purpose |
|------|---------|
| `domain/CommandInbox.kt` | JPA entity |
| `domain/CommandStatus.kt` | Status enum (PENDING, COMPLETED, SKIPPED, EXPIRED) |
| `domain/CommandSource.kt` | Source enum |
| `domain/CommandPriority.kt` | Priority levels |
| `repository/CommandInboxRepository.kt` | Repository with findNextPendingCommand, countPendingCommands |
| `service/CommandInboxService.kt` | Core service |
| `scheduler/TaskData.kt` | OrderTaskData for Task |
| `scheduler/SchedulerTaskConfig.kt` | OrderStateMachineTask definition |
| `scheduler/ValidationTimeoutTaskConfig.kt` | Timeout and cleanup tasks |

### API Changes

- HTTP endpoints return `202 Accepted` with `CommandSubmitResult`
- Use `GET /api/orders/{orderId}/commands/{commandId}` to check command status

## References

- [DB-Scheduler Documentation](https://github.com/kagkarlsson/db-scheduler)
- [Single Socket Channel Pattern](https://martinfowler.com/articles/patterns-of-distributed-systems/single-socket-channel.html)

## History

- 2026-07-12: Initial proposal with per-command Tasks
- 2026-07-12: Revised to one Task per Order model (accepted)
