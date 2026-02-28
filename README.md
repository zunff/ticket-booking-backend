# 高并发抢票系统

基于 Spring Boot + Redis + RabbitMQ + Kubernetes 的高并发用户抢票系统演示项目。

## 项目架构

```
ticket-booking-backend/
├── ticket-booking-common/          # 公共模块
├── user-service/                   # 用户服务 (端口: 8081)
├── ticket-service/                 # 票务服务 (端口: 8080)
├── k8s/                            # Kubernetes 部署文件
├── scripts/                        # 测试脚本
├── init-db/                        # 数据库初始化脚本
└── docker-compose.dev.yaml         # 本地开发环境
```

## 技术栈

| 组件 | 技术 |
|------|------|
| 基础框架 | Spring Boot 3.2.0 |
| ORM | MyBatis-Plus 3.5.5 |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3 |
| 数据库 | MySQL 8.0 |
| 容器编排 | Kubernetes (Docker Desktop) |

## 高并发抢票方案对比

### 方案一：数据库悲观锁 (SELECT FOR UPDATE)

```
┌─────────────────────────────────────────────────────────────────┐
│  请求 → 数据库 SELECT FOR UPDATE → 扣减库存 → 创建订单 → 返回    │
└─────────────────────────────────────────────────────────────────┘
```

**优点**：
- 实现简单，数据库原生支持
- 数据一致性有保障

**缺点**：
- 所有请求串行执行，性能极差
- 数据库连接池容易耗尽
- 不适合高并发场景

**QPS**: < 50

---

### 方案二：分布式锁 + Redis 原子操作

```
┌─────────────────────────────────────────────────────────────────┐
│  请求 → 获取分布式锁 → Redis 扣库存 → 创建订单 → 释放锁 → 返回   │
└─────────────────────────────────────────────────────────────────┘
```

**优点**：
- 保护临界区，防止超卖
- 实现相对简单

**缺点**：
- 锁竞争严重，大量请求等待
- 锁粒度大，并发度低
- 存在死锁风险

**QPS**: ~30

---

### 方案三：分段锁 (Segment Lock)

```
┌─────────────────────────────────────────────────────────────────┐
│                        库存分段                                  │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                  │
│  │Seg 0 │ │Seg 1 │ │Seg 2 │ │ ...  │ │Seg 9 │                  │
│  │Lock  │ │Lock  │ │Lock  │ │      │ │Lock  │                  │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘                  │
│     ↓         ↓         ↓                   ↓                  │
│  请求按 userId % 10 分配到不同分段，减少锁竞争                   │
└─────────────────────────────────────────────────────────────────┘
```

**优点**：
- 减少锁竞争，提高并发度
- 相比单锁有提升

**缺点**：
- 分段数量有限，仍有竞争
- 串行重试机制增加延迟
- 数据库仍是瓶颈

**QPS**: ~30

---

### 方案四：Redis Lua 脚本 + MQ 异步处理 ⭐ 最终选择

```
┌─────────────────────────────────────────────────────────────────┐
│                        最终方案架构                              │
│                                                                 │
│   ┌─────────┐     ┌─────────────────────────────────────────┐  │
│   │  请求   │ ──→ │           Redis Lua 原子操作             │  │
│   └─────────┘     │  ┌─────────────────────────────────────┐│  │
│                   │  │ 1. 检查用户是否已购买 (SETNX)        ││  │
│                   │  │ 2. 检查库存是否充足                  ││  │
│                   │  │ 3. 原子扣减库存                      ││  │
│                   │  │ 4. 返回预抢票结果                    ││  │
│                   │  └─────────────────────────────────────┘│  │
│                   └─────────────────────────────────────────┘  │
│                              │                                  │
│                              ↓                                  │
│                   ┌─────────────────────────────────────────┐  │
│                   │           RabbitMQ 消息队列             │  │
│                   │        (异步创建订单 + 扣减DB库存)       │  │
│                   └─────────────────────────────────────────┘  │
│                              │                                  │
│                              ↓                                  │
│                   ┌─────────────────────────────────────────┐  │
│                   │              MySQL 数据库               │  │
│                   │        (最终一致性，异步写入)            │  │
│                   └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**优点**：
- **无锁设计**：Lua 脚本在 Redis 中原子执行，无需分布式锁
- **极高并发**：Redis 单线程也能支持 10万+ QPS
- **削峰填谷**：MQ 异步处理保护数据库
- **用户体验好**：快速返回抢票结果，订单异步生成
- **数据安全**：Redis 持久化 + MQ 可靠投递保证数据不丢失

**缺点**：
- 实现复杂度较高
- 需要处理最终一致性
- MQ 消费失败需要重试机制

**QPS**: 500+ (预期)

---

### 方案对比总结

| 方案 | QPS | 实现复杂度 | 数据一致性 | 适用场景 |
|------|-----|-----------|-----------|---------|
| 数据库悲观锁 | <50 | 低 | 强一致 | 低并发 |
| 分布式锁 | ~30 | 中 | 强一致 | 中等并发 |
| 分段锁 | ~30 | 中 | 强一致 | 中等并发 |
| **Lua + MQ** | **500+** | 高 | 最终一致 | **超高并发** |

## 为什么选择 Lua + MQ 方案？

1. **性能优先**：抢票场景的核心诉求是高并发处理能力
2. **用户体验**：快速响应用户请求，避免长时间等待
3. **系统稳定**：MQ 削峰保护数据库，防止系统崩溃
4. **业界实践**：淘宝、京东等大厂秒杀系统均采用类似架构

## 快速开始

### 开发环境 (本地 Docker)

```bash
# 1. 启动本地基础设施 (MySQL、Redis、RabbitMQ)
docker-compose -f docker-compose.dev.yaml up -d

# 2. 等待服务就绪
sleep 30

# 3. 编译项目
mvn clean package -DskipTests

# 4. 运行票务服务 (dev 环境)
java -jar ticket-service/target/ticket-service-1.0.0.jar --spring.profiles.active=dev

# 5. 生成测试 JWT Token
python3 scripts/performance_test.py --generate-tokens 100

# 6. 运行测试
python3 scripts/performance_test.py --test --url http://localhost:8080 --ticket-id 1 --requests 100 --concurrency 20 --token-file test_data/tokens_*.json
```

### 生产环境 (Kubernetes)

```bash
# 1. 构建镜像
docker build -t ticket-service:latest -f ticket-service/Dockerfile .

# 2. 部署基础设施
kubectl apply -f k8s/mysql.yaml -f k8s/redis.yaml -f k8s/rabbitmq.yaml

# 3. 创建 JWT Secret
kubectl create secret generic app-secret --from-literal=JWT_SECRET=ticket-booking-secret-key-2024

# 4. 等待基础设施就绪
kubectl wait --for=condition=ready pod -l app=mysql --timeout=180s
kubectl wait --for=condition=ready pod -l app=redis --timeout=180s
kubectl wait --for=condition=ready pod -l app=rabbitmq --timeout=180s

# 5. 初始化数据库
kubectl exec -it deployment/mysql -- mysql -uroot -proot123 -e "
CREATE DATABASE IF NOT EXISTS ticket_booking;
USE ticket_booking;
CREATE TABLE IF NOT EXISTS tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    start_time DATETIME,
    end_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    ticket_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);"

# 6. 部署票务服务
kubectl apply -f k8s/ticket-service.yaml

# 7. 端口转发
kubectl port-forward svc/ticket-service 8080:8080 &

# 8. 运行测试
python3 scripts/performance_test.py --generate-tokens 1000
python3 scripts/performance_test.py --test --url http://localhost:8080 --ticket-id 1 --requests 1000 --concurrency 100 --token-file test_data/tokens_*.json
```

### 清理资源

```bash
# 停止本地 Docker 服务
docker-compose -f docker-compose.dev.yaml down

# 清理 Kubernetes 资源
kubectl delete -f k8s/

# 运行清理脚本
./cleanup.sh
```

## API 接口

### 票务服务 (端口: 8080)

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/tickets` | GET | 获取所有票务 |
| `/api/tickets/available` | GET | 获取可购票务 |
| `/api/tickets/{id}` | GET | 获取票务详情 |
| `/api/tickets` | POST | 创建票务 |
| `/api/tickets/book` | POST | 抢票 (需 JWT 认证) |
| `/api/orders/{orderNo}` | GET | 查询订单 |
| `/api/orders/user/{userId}` | GET | 查询用户订单 |
| `/api/health` | GET | 健康检查 |

## 性能测试结果

测试配置：1000 请求，100 并发，1000 库存，3个Pod副本

| 指标 | 分段锁方案 | Lua + MQ 方案 |
|------|-----------|---------------|
| 成功抢票 | 312 张 | **980 张** |
| QPS | ~30 | **~40** |
| 平均响应时间 | ~2300ms | **~1700ms** |
| 成功率 | 31.2% | **98%** |

### 结果分析

1. **成功率大幅提升**：从 31.2% 提升到 98%，几乎无失败请求
2. **响应时间优化**：平均响应时间从 2300ms 降至 1700ms
3. **无锁设计优势**：Redis Lua 脚本原子操作避免了锁竞争
4. **MQ 削峰效果**：异步处理保护了数据库，避免了连接池耗尽

### 测试环境

- **本地开发环境**: MacBook Pro, Docker Desktop
- **Kubernetes**: 3个 ticket-service Pod 副本
- **数据库**: MySQL 8.0 单实例
- **缓存**: Redis 7 单实例
- **消息队列**: RabbitMQ 3 单实例

## 服务访问

- **RabbitMQ 管理**: http://localhost:15672 (用户名/密码: guest/guest)
- **票务服务 API**: http://localhost:8080

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

### 消息队列处理流程

1. **生产者**：抢票成功后发送订单消息到 RabbitMQ
2. **消费者**：异步消费消息，创建订单并扣减数据库库存
3. **失败处理**：消费失败时回滚 Redis 库存，删除用户购买记录
