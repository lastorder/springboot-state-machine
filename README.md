# Spring Boot State Machine Demo

一个基于 Spring State Machine 的订单完整生命周期系统，展示了 Barrier Aggregate 模式与事件驱动架构。

## 技术栈

- **Kotlin** 2.1.x
- **Spring Boot** 3.5.x
- **Spring State Machine** - 状态机框架
- **PostgreSQL** - 数据持久化
- **Apache Kafka** - 事件驱动
- **db-scheduler** - 任务调度
- **ShedLock** - 分布式锁

## 架构概览

### 完整状态流转

```
INIT (初始状态)
  │
  ▼ PR_APPROVED (Kafka)
LOCAL_INITIALIZED
  │
  ▼ 自动
FACTORY_ORDER_SUBMITTED (屏障: VOM + DOM)
  │
  ▼ 屏障全部通过
ORDER_INITIALIZE_SUCCEED
  │
  ▼ PURCHASE_REQUEST_ACCEPT (手动触发)
PURCHASE_REQUEST_ACCEPTING (屏障: DE 3个 / IT 6个)
  │
  ▼ 屏障全部通过
PURCHASE_REQUEST_ACCEPTED
  │
  ▼ CDOA_ACCEPT (手动触发)
CDOA_ACCEPTING (屏障: DE 3个 / IT 6个)
  │
  ▼ 屏障全部通过
CDOA_ACCEPTED

异常分支:
- VOM_FAILED → ORDER_INITIALIZE_FAILED
- PR_ACCEPT 失败 → PURCHASE_REQUEST_ACCEPT_FAILED
- CDOA 失败 → CDOA_ACCEPT_FAILED
```

### 市场屏障配置

| 市场 | 屏障数量 | 屏障类型 |
|------|----------|----------|
| DE | 3 | SVS, PRICE, FINANCE |
| IT | 6 | SVS, BODYBUILDER, CONTRACT_ROLES, PRICING, PAYMENT_SPLIT, FINANCING_BLUEPRINT |

## 快速开始

### 前置条件

- JDK 17+
- Docker & Docker Compose

### 1. 启动基础设施

```bash
docker-compose up -d
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

### 4. 运行测试

```bash
# 单元测试 (~5s)
./gradlew test

# 集成测试 (~20s)
./gradlew integrationTest

# 所有测试
./gradlew check
```

## 业务 Workflow 演示

### 完整流程 (DE 市场)

```bash
# 1. 发送 PR_APPROVED 事件
ORDER_NO="ORD-DE-$(shuf -i 1000000000-9999999999 -n 1)"
echo "{\"orderNo\":\"$ORDER_NO\",\"productId\":\"P001\",\"productName\":\"Test\",\"quantity\":10,\"amount\":100.00,\"market\":\"DE\"}" | \
  docker exec -i order-statemachine-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic pr.approved

# 2. 发送 VOM 事件
echo "{\"orderNo\":\"$ORDER_NO\"}" | \
  docker exec -i order-statemachine-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic factory.vom

# 3. 发送 DOM 事件
echo "{\"orderNo\":\"$ORDER_NO\"}" | \
  docker exec -i order-statemachine-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic factory.dom

# 4. 触发 PURCHASE_REQUEST_ACCEPT (需等待状态机进入 ORDER_INITIALIZE_SUCCEED)
curl -X POST "http://localhost:8080/api/orders/$ORDER_NO/purchase-request-accept"

# 5. 传递 DE 市场屏障 (SVS, PRICE, FINANCE)
for barrier in SVS PRICE FINANCE; do
  echo "{\"orderNo\":\"$ORDER_NO\",\"barrierType\":\"$barrier\",\"flowType\":\"PR_ACCEPT\",\"success\":true}" | \
    docker exec -i order-statemachine-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic barrier.pass
done

# 6. 触发 CDOA_ACCEPT
curl -X POST "http://localhost:8080/api/orders/$ORDER_NO/cdoa-accept"

# 7. 传递 CDOA 屏障
for barrier in SVS PRICE FINANCE; do
  echo "{\"orderNo\":\"$ORDER_NO\",\"barrierType\":\"$barrier\",\"flowType\":\"CDOA\",\"success\":true}" | \
    docker exec -i order-statemachine-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic barrier.pass
done

# 8. 查看最终状态
curl "http://localhost:8080/api/orders/order-no/$ORDER_NO"
```

## Kafka Topics

| Topic | 说明 |
|-------|------|
| `pr.approved` | PR 审批通过事件 |
| `coe.order.created` | COE 订单创建事件 |
| `factory.vom` | 工厂 VOM 事件 |
| `factory.dom` | 工厂 DOM 事件 |
| `barrier.pass` | 屏障传递事件 |
| `change-trigger` | 变更触发事件 |
| `order.events` | 订单状态变更广播 |

## 项目结构

```
src/main/kotlin/com/example/statemachine/
├── domain/                    # 领域模型
│   ├── enums/                 # OrderStatus, OrderEvent, Market
│   ├── model/                 # Order 实体
│   └── repository/            # Repository 接口
├── barrieraggregate/          # Barrier Aggregate 框架
│   ├── BarrierAggregate.kt    # 抽象基类
│   ├── MarketAwareBarrierAggregate.kt
│   └── BarrierAggregateRecord.kt
├── order/                     # 订单模块
│   ├── barrier/               # 屏障实现
│   │   ├── CommonBarrier.kt
│   │   ├── OrderInitBarrierAggregate.kt
│   │   ├── PurchaseRequestAcceptBarrierAggregate.kt
│   │   └── CdoaAcceptBarrierAggregate.kt
│   └── service/               # OrderService
├── statemachine/              # 状态机
│   ├── config/                # StateMachineConfig
│   ├── action/                # Actions
│   └── service/               # StateMachineService
├── infrastructure/            # 基础设施
│   ├── kafka/                 # Kafka Consumer/Producer
│   ├── persistence/           # JPA 实现
│   └── rest/                  # REST Client
├── task/                      # TaskSpec 框架
└── presentation/              # REST API
```

## 核心设计模式

### Barrier Aggregate 模式

单表 JSONB 设计，支持乐观锁：

```sql
CREATE TABLE barrier_aggregate (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(500) NOT NULL,
    aggregate_key VARCHAR(255) NOT NULL,
    required_barriers JSONB NOT NULL,
    passed_barriers JSONB NOT NULL DEFAULT '[]',
    initialized_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_barrier_aggregate UNIQUE(aggregate_type, aggregate_key)
);
```

**使用方式**:

```kotlin
// 初始化屏障
barrierAggregate.initialize(orderNo, market)

// 处理屏障事件
barrierAggregate.handleBarrierEvent(orderNo, barrierType)

// 查询状态
val summary = barrierAggregate.getSummary(orderNo)
println("等待时长: ${summary?.waitingDuration}")
```

### MarketAwareBarrierAggregate

市场感知的屏障聚合基类，简化多市场屏障配置：

```kotlin
@Component
class PurchaseRequestAcceptBarrierAggregate(
    repository: BarrierAggregateRepository,
    stateMachineService: StateMachineService,
) : MarketAwareBarrierAggregate(repository, PurchaseRequestAcceptBarrier) {
    
    override fun onAllBarriersPassed(aggregateKey: String) {
        stateMachineService.sendEvent(aggregateKey, OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS)
    }
}
```

### OrderActionUtils

提取状态机 Action 的公共逻辑：

```kotlin
override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
    val orderNo = OrderActionUtils.extractOrderNo(context)
    // ...
}
```

### KafkaTopics

统一的 Kafka Topic 常量：

```kotlin
@KafkaListener(topics = [KafkaTopics.PR_APPROVED])
fun onPrApproved(record: ConsumerRecord<String, PrApprovedEvent>) { ... }
```

## 配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/order_state_machine
  kafka:
    bootstrap-servers: localhost:9092

db-scheduler:
  enabled: true
  threads: 5
```

## License

MIT
