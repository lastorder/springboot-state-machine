# Spring Boot State Machine Demo

一个基于 Spring State Machine 的订单初始化系统演示项目，展示了 Barrier Aggregate 模式与事件驱动架构。

## 技术栈

- **Kotlin** 2.1.x
- **Spring Boot** 3.5.x
- **Spring State Machine** - 状态机框架
- **PostgreSQL** - 数据持久化
- **Apache Kafka** - 事件驱动
- **db-scheduler** - 任务调度
- **ShedLock** - 分布式锁

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Order State Machine                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  INIT (初始状态)                                                      │
│    │                                                                 │
│    ▼ PR_APPROVED (Kafka: pr.approved)                               │
│  LOCAL_INITIALIZED                                                   │
│    │                                                                 │
│    ▼ 发送 COE 到 Kafka + 初始化屏障 (VOM + DOM)                       │
│  FACTORY_ORDER_SUBMITTED                                             │
│    │                                                                 │
│    ├─────── Barrier Aggregate (等待 VOM 和 DOM) ───────┐            │
│    │                                                     │            │
│    │   VOM 收到 → 屏障传递       DOM 收到 → 屏障传递      │            │
│    │                                                     │            │
│    └─────────── 所有屏障通过 ────────────────────────────┘            │
│                     │                                                │
│                     ▼                                                │
│           ORDER_INITIALIZE_SUCCEED                                   │
│                                                                      │
│  异常分支: VOM_FAILED → ORDER_INITIALIZE_FAILED                      │
└─────────────────────────────────────────────────────────────────────┘
```

## 快速开始

### 前置条件

- JDK 17+
- Docker & Docker Compose

### 1. 启动基础设施

```bash
# 启动 PostgreSQL + Kafka + Kafka UI
docker-compose up -d

# 查看服务状态
docker-compose ps
```

服务地址:
- PostgreSQL: `localhost:5432` (user: postgres, password: postgres)
- Kafka: `localhost:9092`
- Kafka UI: `http://localhost:8081`

### 2. 构建项目

```bash
./gradlew build
```

### 3. 运行应用

```bash
./gradlew bootRun
```

应用启动后访问: `http://localhost:8080`

### 4. 运行测试

```bash
# 运行单元测试 (快速, ~5s)
./gradlew test

# 运行集成测试 (完整环境, ~20s)
./gradlew integrationTest

# 运行所有测试
./gradlew check
```

## 业务 Workflow 演示

### 完整订单初始化流程

#### 步骤 1: 创建订单

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORD-001",
    "productId": "PROD-123",
    "productName": "Test Product",
    "quantity": 10,
    "amount": 99.99
  }'
```

响应示例:
```json
{
  "id": 1,
  "orderNo": "ORD-001",
  "productId": "PROD-123",
  "productName": "Test Product",
  "quantity": 10,
  "amount": 99.99,
  "status": "INIT",
  "createdAt": "2026-07-15T10:00:00Z",
  "updatedAt": "2026-07-15T10:00:00Z"
}
```

#### 步骤 2: 查看 Kafka UI

访问 `http://localhost:8081` 查看:
- Topic `pr.approved` - PR 审批消息
- Topic `coe.order.created` - COE 订单创建消息
- Topic `order.events` - 状态变更事件

#### 步骤 3: 发送 Kafka 消息测试

**方法 1: 使用 kcat 命令行工具** (推荐)
```bash
# 发送 PR_APPROVED 事件 (替换 orderId 和 orderNo)
ORDER_ID=1
ORDER_NO="ORD-001"

echo "{\"orderId\":$ORDER_ID,\"orderNo\":\"$ORDER_NO\",\"productId\":\"PROD-123\",\"productName\":\"Test Product\",\"quantity\":10,\"amount\":99.99}" | \
  kcat -P -b localhost:9092 -t pr.approved

# 发送 VOM 事件
echo "{\"orderId\":$ORDER_ID}" | kcat -P -b localhost:9092 -t factory.vom

# 发送 DOM 事件
echo "{\"orderId\":$ORDER_ID}" | kcat -P -b localhost:9092 -t factory.dom
```

**方法 2: 使用 Kafka UI**
- 注意: Kafka UI 可能无法连接到 Kafka (由于 native 镜像的网络限制)
- 如果连接成功，访问 `http://localhost:8081`
- 选择 Topic → "Messages" → "Produce Message"

#### 步骤 4: 查看订单状态

```bash
curl http://localhost:8080/api/orders/1
```

预期状态流转:
- `INIT` → `LOCAL_INITIALIZED` (收到 PR_APPROVED)
- `LOCAL_INITIALIZED` → `FACTORY_ORDER_SUBMITTED` (发送 COE + 同步 Deal + 初始化屏障)
- `FACTORY_ORDER_SUBMITTED` → `ORDER_INITIALIZE_SUCCEED` (VOM 和 DOM 都收到，屏障全部通过)

### 查看所有订单

```bash
curl http://localhost:8080/api/orders
```

## 状态机事件说明

| 事件 | 来源 | 状态转换 |
|------|------|----------|
| PR_APPROVED | Kafka `pr.approved` | INIT → LOCAL_INITIALIZED |
| VOM | Kafka `factory.vom` | 通过屏障，全部通过后 FACTORY_ORDER_SUBMITTED → ORDER_INITIALIZE_SUCCEED |
| DOM | Kafka `factory.dom` | 通过屏障，全部通过后 FACTORY_ORDER_SUBMITTED → ORDER_INITIALIZE_SUCCEED |
| VOM_FAILED | Kafka `factory.vom.failed` | * → ORDER_INITIALIZE_FAILED |

## Kafka Topics

| Topic | 说明 |
|-------|------|
| `pr.approved` | PR 审批通过事件 |
| `coe.order.created` | COE 订单创建事件 (生产) |
| `factory.vom` | 工厂 VOM 事件 |
| `factory.dom` | 工厂 DOM 事件 |
| `factory.vom.failed` | 工厂 VOM 失败事件 |
| `order.events` | 订单状态变更广播 |

## 项目结构

```
src/
├── main/kotlin/com/example/statemachine/
│   ├── domain/                 # 领域模型
│   │   └── enums/              # OrderStatus, OrderEvent
│   ├── order/                  # 订单模块
│   │   ├── service/            # OrderService, OrderCommandService
│   │   └── task/               # OrderStateMachineTaskSpec
│   ├── infrastructure/         # 基础设施
│   │   ├── persistence/        # JPA Entity, Repository
│   │   └── kafka/              # Kafka Consumer, Producer, DTO
│   ├── statemachine/           # 状态机配置
│   │   ├── StateMachineConfig.kt
│   │   └── action/             # PrApprovedAction, SendCoeAction, SyncDealAction
│   ├── task/                   # TaskSpec 框架
│   │   ├── spec/               # TaskSpec, LockingTaskSpec
│   │   └── scheduler/          # TaskScheduler, TaskSpecAdapterFactory
│   └── presentation/           # REST API
│       ├── controller/
│       └── dto/
├── test/kotlin/                # 单元测试 (MockK, 无 Spring Context)
└── integrationTest/kotlin/     # 集成测试 (Testcontainers)
```

## Barrier Aggregate 模式

本项目使用 **Barrier Aggregate** 模式来处理并行事件等待，采用单表 JSONB 设计：

### 数据结构

```sql
CREATE TABLE barrier_aggregate (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(500) NOT NULL,      -- 屏障聚合类型（类全名）
    aggregate_key VARCHAR(255) NOT NULL,       -- 业务键（如 orderNo）
    required_barriers JSONB NOT NULL,          -- 必需屏障 ["VOM", "DOM"]
    passed_barriers JSONB NOT NULL DEFAULT '[]', -- 已通过屏障 ["VOM"]
    initialized_at TIMESTAMP NOT NULL,         -- 初始化时间（可计算等待时长）
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,         -- 乐观锁版本号
    CONSTRAINT uk_barrier_aggregate UNIQUE(aggregate_type, aggregate_key)
);
```

### API 使用

```kotlin
// 初始化屏障（幂等，已存在则重置）
orderInitBarrierAggregate.initialize(orderNo)

// 处理屏障事件（自动追加到 passedBarriers，全过时触发回调）
orderInitBarrierAggregate.handleBarrierEvent(orderNo, "VOM")

// 查询状态
val summary = orderInitBarrierAggregate.getSummary(orderNo)
println("等待时长: ${summary?.waitingDuration}")
```

### 优势

1. **单行设计** - 每个聚合对象一行记录，直观清晰
2. **JSONB 字段** - `requiredBarriers` 和 `passedBarriers` 可扩展
3. **等待时长** - `initializedAt` 可计算等待时间
4. **乐观锁** - `@Version` 并发安全
5. **幂等性** - `initialize()` 重置，`handleBarrierEvent()` 追加去重

### 关键实现

- `barrieraggregate/BarrierAggregate.kt` - 抽象基类，包含核心逻辑
- `order/barrier/OrderInitBarrier.kt` - 屏障常量 (VOM, DOM)
- `order/barrier/OrderInitBarrierAggregate.kt` - 订单初始化屏障实现
- `SendCoeAction.kt` - 初始化屏障
- `OrderEventConsumer.kt` - 处理 VOM/DOM 事件

## TaskSpec 模式

简化版任务调度框架，基于 db-scheduler + ShedLock:

```kotlin
// 定义任务
@Component
class OrderStateMachineTaskSpec(
    stateMachineService: StateMachineService,
    lockProvider: LockProvider
) : LockingTaskSpec<OrderEventPayload>(
    taskName = "order-state-machine",
    maxRetries = 5,
    lockProvider = lockProvider,
    lockKeyProvider = { "order:${it.payload.orderId}" }
) {
    override fun executeWithLock(context: TaskContext<OrderEventPayload>): TaskResult {
        stateMachineService.sendEvent(context.payload.orderId, context.payload.event)
        return TaskResult.success()
    }
}

// 提交任务
taskScheduler.submit(
    spec = orderStateMachineTaskSpec,
    instanceId = "order-123-PR_APPROVED",
    payload = OrderEventPayload(123L, OrderEvent.PR_APPROVED)
)
```

## 开发指南

### 代码风格

```bash
# 检查代码风格
./gradlew ktlintCheck

# 格式化代码
./gradlew ktlintFormat
```

### 数据库表

- `orders` - 订单实体
- `state_machine` - 状态机持久化
- `barrier_aggregate` - 屏障聚合记录
- `scheduled_tasks` - db-scheduler 任务表
- `shedlock` - 分布式锁表

### 配置文件

主要配置在 `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/order_state_machine
    username: postgres
    password: postgres
  kafka:
    bootstrap-servers: localhost:9092

db-scheduler:
  enabled: true
  threads: 5
  polling-interval: 5s

logging:
  level:
    com.example.statemachine: DEBUG
```

## 停止服务

```bash
# 停止应用
Ctrl+C

# 停止 Docker 服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

## License

MIT
