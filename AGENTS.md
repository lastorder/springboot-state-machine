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
docker-compose up -d && ./gradlew bootRun
```

## Project Structure

```
src/main/kotlin/com/example/statemachine/
├── domain/                    # 领域模型
│   ├── enums/                 # OrderStatus, OrderEvent, Market
│   ├── model/                 # Order 实体
│   └── repository/            # Repository 接口
├── barrieraggregate/          # Barrier Aggregate 框架
│   ├── BarrierAggregate.kt
│   ├── MarketAwareBarrierAggregate.kt
│   └── BarrierAggregateRecord.kt
├── order/                     # 订单模块
│   ├── barrier/               # 屏障实现
│   └── service/               # OrderService, OrderCommandService
├── statemachine/              # 状态机
│   ├── config/                # StateMachineConfig
│   ├── action/                # Actions (使用 OrderActionUtils)
│   └── service/               # StateMachineService
├── infrastructure/            # 基础设施
│   ├── kafka/                 # Kafka Consumer/Producer (使用 KafkaTopics)
│   ├── persistence/           # JPA 实现
│   └── rest/                  # REST Client
├── task/                      # TaskSpec 框架
└── presentation/              # REST API
```

## Architecture

### State Machine Flow

```
INIT
  ↓ PR_APPROVED (Kafka)
LOCAL_INITIALIZED
  ↓ Auto
FACTORY_ORDER_SUBMITTED (屏障: VOM + DOM)
  ↓ 屏障通过
ORDER_INITIALIZE_SUCCEED
  ↓ PURCHASE_REQUEST_ACCEPT (手动)
PURCHASE_REQUEST_ACCEPTING (屏障: DE 3 / IT 6)
  ↓ 屏障通过
PURCHASE_REQUEST_ACCEPTED
  ↓ CDOA_ACCEPT (手动)
CDOA_ACCEPTING (屏障: DE 3 / IT 6)
  ↓ 屏障通过
CDOA_ACCEPTED
```

### Market Barrier Configuration

| Market | Barriers |
|--------|----------|
| DE | SVS, PRICE, FINANCE |
| IT | SVS, BODYBUILDER, CONTRACT_ROLES, PRICING, PAYMENT_SPLIT, FINANCING_BLUEPRINT |

### Key Design Patterns

1. **BarrierAggregate** - Single-table JSONB with optimistic locking
2. **MarketAwareBarrierAggregate** - Market-specific barrier configuration
3. **OrderActionUtils** - Shared utility for extracting orderNo from StateContext
4. **KafkaTopics** - Centralized Kafka topic constants

## Code Quality

### Naming Conventions
- Use `orderNo: String` instead of `orderId: Long` for all new APIs
- Use `byOrderNo` suffix for methods: `syncOrderByOrderNo`, `sendCoeEventByOrderNo`

### Kafka Topics
Always use `KafkaTopics` object:
```kotlin
@KafkaListener(topics = [KafkaTopics.PR_APPROVED])
```

### Barrier Implementation
Extend `MarketAwareBarrierAggregate` for market-based barriers:
```kotlin
class MyBarrierAggregate(
    repository: BarrierAggregateRepository,
    stateMachineService: StateMachineService,
) : MarketAwareBarrierAggregate(repository, MyBarrier) {
    override fun onAllBarriersPassed(aggregateKey: String) {
        stateMachineService.sendEvent(aggregateKey, MyEvent.SUCCESS)
    }
}
```

## Testing

- Unit tests: MockK, no Spring Context
- Integration tests: Testcontainers (PostgreSQL + Kafka)
- Minimum coverage: 80%
- Always run `./gradlew check` before committing
