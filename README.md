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
