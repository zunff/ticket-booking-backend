# 高并发抢票系统

基于 Spring Boot 3.2 + Java 21 虚拟线程 + Redis + Kafka + Kubernetes 的高并发用户抢票系统演示项目。

## 项目架构

```
ticket-booking-backend/
├── ticket-booking-common/          # 公共模块
├── user-service/                   # 用户服务 (端口: 8081)
├── ticket-service/                 # 票务服务 (端口: 8080)
├── k8s/                            # Kubernetes 部署文件
├── scripts/                        # 测试脚本
├── init-db/                        # 数据库初始化脚本
├── docker-compose.dev.yaml         # 本地开发环境 (MySQL/Redis/Kafka)
├── k8s_deploy.sh                   # K8s 部署脚本
├── k8s_cleanup.sh                  # K8s 清理脚本
└── stop_dependencies.sh            # 停止外部依赖脚本
```

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 3.2.0 |
| JDK | Java (虚拟线程) | 21 |
| ORM | MyBatis-Plus | 3.5.5 |
| 缓存 | Redis | 7 |
| 消息队列 | Kafka | 7.5.0 |
| 数据库 | MySQL | 8.0 |
| 容器编排 | Kubernetes | Docker Desktop |

## 高并发抢票方案对比

### 方案一：数据库悲观锁 (SELECT FOR UPDATE)

```
请求 → 数据库 SELECT FOR UPDATE → 扣减库存 → 创建订单 → 返回
```

**优点**：实现简单，数据一致性有保障

**缺点**：所有请求串行执行，性能极差，数据库连接池容易耗尽

**QPS**: < 50

---

### 方案二：分布式锁 + Redis 原子操作

```
请求 → 获取分布式锁 → Redis 扣库存 → 创建订单 → 释放锁 → 返回
```

**优点**：保护临界区，防止超卖

**缺点**：锁竞争严重，大量请求等待，锁粒度大，并发度低

**QPS**: ~30

---

### 方案三：Redis Lua 脚本 + MQ 异步处理 ⭐ 最终选择

```
┌─────────────────────────────────────────────────────────────────┐
│                        最终方案架构                              │
│                                                                 │
│   ┌─────────┐     ┌─────────────────────────────────────────┐  │
│   │  请求   │ ──→ │           Redis Lua 原子操作             │  │
│   └─────────┘     │  1. 检查用户是否已购买 (SETNX)           │  │
│                   │  2. 检查库存是否充足                      │  │
│                   │  3. 原子扣减库存                          │  │
│                   │  4. 返回预抢票结果                        │  │
│                   └─────────────────────────────────────────┘  │
│                              │                                  │
│                              ↓                                  │
│                   ┌─────────────────────────────────────────┐  │
│                   │              Kafka 消息队列              │  │
│                   │        (异步创建订单 + 扣减DB库存)        │  │
│                   └─────────────────────────────────────────┘  │
│                              │                                  │
│                              ↓                                  │
│                   ┌─────────────────────────────────────────┐  │
│                   │              MySQL 数据库                │  │
│                   │        (最终一致性，异步写入)             │  │
│                   └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**优点**：
- **无锁设计**：Lua 脚本在 Redis 中原子执行，无需分布式锁
- **极高并发**：Redis 单线程也能支持 10万+ QPS
- **削峰填谷**：MQ 异步处理保护数据库
- **虚拟线程**：Java 21 虚拟线程进一步提升并发处理能力
- **用户体验好**：快速返回抢票结果，订单异步生成

**QPS**: 500+ (预期)

---

### 方案对比总结

| 方案 | QPS | 实现复杂度 | 数据一致性 | 适用场景 |
|------|-----|-----------|-----------|---------|
| 数据库悲观锁 | <50 | 低 | 强一致 | 低并发 |
| 分布式锁 | ~30 | 中 | 强一致 | 中等并发 |
| **Lua + MQ** | **500+** | 高 | 最终一致 | **超高并发** |

## 快速开始

### 开发环境 (本地 Docker)

```bash
# 1. 启动本地基础设施 (MySQL、Redis、Kafka)
docker compose -f docker-compose.dev.yaml up -d

# 2. 等待 Kafka 就绪
sleep 45

# 3. 编译项目
mvn clean package -DskipTests

# 4. 运行用户服务 (dev 环境)
java -jar user-service/target/user-service-1.0.0.jar --spring.profiles.active=dev &

# 5. 运行票务服务 (dev 环境)
java -jar ticket-service/target/ticket-service-1.0.0.jar --spring.profiles.active=dev &

# 6. 生成测试 JWT Token
python3 scripts/performance_test.py --generate-tokens 2000

# 7. 运行性能测试
python3 scripts/performance_test.py --test --url http://localhost:8080 --ticket-id 1 --requests 2000 --concurrency 200 --token-file test_data/tokens_*.json

# 8. 停止服务
./stop_dependencies.sh
```

### 生产环境 (Kubernetes)

```bash
# 1. 启动外部依赖 (MySQL、Redis、Kafka)
docker compose -f docker-compose.dev.yaml up -d

# 2. 等待服务就绪
sleep 45

# 3. 部署到 K8s (包含编译、构建镜像、部署)
./k8s_deploy.sh

# 4. 端口转发
kubectl port-forward svc/ticket-service -n ticket-booking 8080:8080 &
kubectl port-forward svc/user-service -n ticket-booking 8081:8081 &

# 5. 运行性能测试
python3 scripts/performance_test.py --generate-tokens 2000
python3 scripts/performance_test.py --test --url http://localhost:8080 --ticket-id 1 --requests 2000 --concurrency 200 --token-file test_data/tokens_*.json

# 6. 清理 K8s 资源
./k8s_cleanup.sh

# 7. 停止外部依赖
./stop_dependencies.sh
```

## API 接口

### 票务服务 (端口: 8080)

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/tickets` | GET | 获取所有票务 |
| `/api/tickets/{id}` | GET | 获取票务详情 |
| `/api/tickets/book` | POST | 抢票 (需 JWT 认证) |
| `/api/orders/{orderNo}` | GET | 查询订单 |
| `/api/orders/user/{userId}` | GET | 查询用户订单 |
| `/api/health` | GET | 健康检查 |

### 用户服务 (端口: 8081)

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/users` | POST | 创建用户 |
| `/api/users/{id}` | GET | 获取用户信息 |
| `/api/users/login` | POST | 用户登录 |
| `/api/health` | GET | 健康检查 |

## 性能测试结果

测试配置：2000 请求，200 并发，1000 库存，3个Pod副本

| 指标 | Kafka 方案 |
|------|-----------|
| 成功抢票 | 999 张 |
| QPS | **68.48** |
| 平均响应时间 | 2183ms |
| 最小响应时间 | 586ms |
| 最大响应时间 | 8562ms |
| 成功率 | 49.95% (票售罄后失败) |

### 测试环境

- **本地开发环境**: MacBook Pro, Docker Desktop
- **Kubernetes**: 3个 ticket-service Pod 副本, 1个 user-service Pod
- **数据库**: MySQL 8.0 (Docker)
- **缓存**: Redis 7 (Docker)
- **消息队列**: Kafka 7.5.0 (Docker)
- **JDK**: Java 21 (虚拟线程启用)

## 服务访问

- **Kafka**: localhost:9093
- **Zookeeper**: localhost:2181
- **票务服务 API**: http://localhost:8080/api
- **用户服务 API**: http://localhost:8081/api

## 核心实现

### Redis Lua 脚本

```lua
-- 原子抢票操作
local stockKey = KEYS[1]
local userTicketKey = KEYS[2]
local userId = ARGV[1]
local quantity = tonumber(ARGV[2])

-- 检查用户是否已购买
if redis.call('EXISTS', userTicketKey) == 1 then
    return -1  -- 已购买
end

-- 检查库存
local stock = tonumber(redis.call('GET', stockKey) or '0')
if stock == 0 then
    return -2  -- 票务不存在
end

if stock < quantity then
    return -3  -- 库存不足
end

-- 扣减库存
redis.call('DECRBY', stockKey, quantity)
redis.call('SET', userTicketKey, userId)

return 1  -- 成功
```

### Kafka 消息处理流程

1. **生产者**：抢票成功后发送订单消息到 Kafka Topic
2. **消费者**：异步消费消息，创建订单并扣减数据库库存
3. **失败处理**：消费失败时回滚 Redis 库存，删除用户购买记录

### Java 21 虚拟线程配置

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

## 项目亮点

1. **Java 21 虚拟线程**：大幅提升并发处理能力，减少线程切换开销
2. **Redis Lua 原子操作**：无锁设计，避免分布式锁竞争
3. **Kafka 消息队列**：高吞吐量，支持分区和副本，适合大规模分布式系统
4. **Kubernetes 部署**：使用独立命名空间，支持水平扩展
5. **环境分离**：dev/prod 配置分离，外部依赖与 K8s 解耦

---

## TODO (待实现功能)

### 🔴 高优先级 - 核心高并发能力完善

#### 1. Sentinel 限流降级
- [ ] 接口级别限流规则配置（抢票接口 QPS 限制）
- [ ] 用户维度限流（单用户请求频率限制，防刷票）
- [ ] 熔断降级策略（Redis/Kafka 异常时的降级方案）
- [ ] 热点参数限流（针对热门票 ID 的限流）

**预期收益**：保护后端服务，防止恶意刷票，提升系统稳定性

#### 2. Caffeine 本地缓存
- [ ] 票务详情本地缓存（减少 Redis 查询）
- [ ] 用户信息本地缓存
- [ ] 缓存穿透/击穿/雪崩防护
- [ ] 缓存命中率监控

**预期收益**：减少 60%+ Redis 访问，查询响应时间降低至 10ms 以内

#### 3. 性能调优与压测优化
- [ ] QPS 从 68 优化至 500+ （瓶颈分析与优化）
- [ ] 数据库连接池调优（当前配置未优化）
- [ ] Redis 连接池调优
- [ ] Kafka 生产者/消费者参数调优
- [ ] 虚拟线程池参数优化

**预期收益**：达到真正的"高并发"性能标准

#### 4. 完善库存架构
- [ ] 第一层（预热）：XXL-Job 定时任务，活动开始前强制写入 Redis（主力）
- [ ] 第二层（兜底）：接口内部双检锁 + 分布式锁 + 短暂重试（防止 Redis 意外丢失 Key）
- [ ] 第三层（底线）：数据库层面加乐观锁（`update stock set count = count -1 where id = 1 and count > 0`），防止 Redis 逻辑出 Bug 导致超卖（最后一道救命线）

**预期收益**：构建多层级库存防护体系，确保高并发下数据零超卖

---

### 🟡 中优先级 - 架构完整性

#### 4. 监控与可观测性
- [ ] Prometheus + Grafana 监控面板
- [ ] 接口响应时间 P50/P95/P99 分布
- [ ] Redis 命中率统计
- [ ] Kafka 消费延迟监控
- [ ] JVM 虚拟线程监控

**预期收益**：系统可观测，性能问题可追溯

#### 5. 库存分段/分片设计
- [ ] 单库存拆分为多段（如 1000 库存 → 10 段 100）
- [ ] 减少单 Key 热点竞争
- [ ] 分段扣减后的合并策略

**预期收益**：进一步提升并发能力 2-3 倍

#### 6. 数据库优化
- [ ] MySQL 读写分离架构
- [ ] Ticket 表分表策略（按票务类型/时间）
- [ ] Order 表分表策略（按用户 ID 哈希）
- [ ] 索引优化与慢查询分析

**预期收益**：支撑更大规模数据量

---

### 🟢 低优先级 - 功能完善

#### 7. 分布式事务与补偿
- [ ] RocketMQ/RabbitMQ 事务消息方案（备选）
- [ ] 订单状态机实现
- [ ] 失败订单自动重试机制
- [ ] 库存回滚补偿日志

#### 8. 安全增强
- [ ] 接口防重放攻击（请求签名 + 时间戳）
- [ ] 抢票验证码（防止脚本抢票）
- [ ] 风控规则识别异常用户

#### 9. 业务场景完善
- [ ] 热点票优先级队列（VIP 用户优先）
- [ ] 抢票排队/预约机制
- [ ] 抢票前预售/预热功能
- [ ] 多级票价系统（VIP/普通/站票）

---

### 📊 性能目标

| 指标 | 当前 | 目标 |
|------|------|------|
| QPS | 68 | 500+ |
| 平均响应时间 | 2183ms | < 100ms |
| P99 响应时间 | 8562ms | < 500ms |
| Redis 命中率 | - | > 90% |
| 支持并发用户 | 200 | 1000+ |
