# 高并发抢票系统

基于 Spring Boot 3.2 + Java 21 虚拟线程 + Redis + Kafka + Nacos 的微服务架构高并发抢票系统。

## 项目架构

```
ticket-booking-backend/
├── ticket-booking-common/          # 公共模块（缓存、工具、注解）
├── ticket-user-service/             # 用户服务 (端口: 8081)
├── ticket-service/                 # 演唱会服务 (端口: 8080)
├── ticket-order-service/            # 订单服务 (端口: 8082)
├── ticket-stock-service/            # 库存服务 (端口: 8083)
├── ticket-gateway-service/          # 网关服务 (端口: 9000)
├── init-db/                        # 数据库初始化脚本
└── sh/                            # 启动/停止脚本
```

## 微服务职责

| 服务 | 端口 | 职责 |
|------|------|------|
| ticket-gateway-service | 9000 | API 网关、JWT 鉴权、Sticky Session 路由 |
| ticket-user-service | 8081 | 用户管理、登录认证 |
| ticket-service | 8080 | 演唱会管理、票价档位、缓存预热 |
| ticket-order-service | 8082 | 订单创建、抢票入口 |
| ticket-stock-service | 8083 | 库存管理、Kafka 消费、DB 库存扣减 |

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 3.2.0 |
| JDK | Java (虚拟线程) | 21 |
| 服务注册与发现 | Nacos | 2.x |
| API 网关 | Spring Cloud Gateway | 4.x |
| 服务调用 | OpenFeign | 4.x |
| ORM | MyBatis-Plus | 3.5.5 |
| 本地缓存 | Caffeine | 3.x |
| 分布式缓存 | Redis | 7 |
| 消息队列 | Kafka | 7.5.0 |
| 数据库 | MySQL | 8.0 |
| 定时任务 | XXL-Job | 2.4.0 |

## 核心流程：Lua + Kafka + DB 最终一致性

```plaintext
抢票流程架构
├─ 客户端请求
├─ Redis Lua 原子操作
│  ├─ 检查用户限购（Hash 结构）
│  ├─ 检查库存是否充足
│  ├─ 原子扣减库存（HINCRBY）
│  └─ 返回预抢票结果
├─ Kafka 消息队列
│  ├─ 异步创建订单
│  └─ 异步扣减DB库存
└─ MySQL 数据库
   ├─ 乐观锁扣减
   └─ 消费失败回滚 Redis
```

## 快速开始

### 前置要求

- **JDK 21**
- **Maven 3.9+**
- **Mac 环境需安装 Docker Desktop**

### 启动开发环境

```bash
# 1. 启动基础设施（MySQL、Redis、Kafka、Nacos、Sentinel、XXL-Job）
docker-compose -f docker-compose.dev.yaml up -d

# 2. 等待服务就绪后，启动微服务
bash sh/start-dev.sh

# 3. 停止所有服务
bash sh/stop-all.sh

# 4. 停止基础设施
docker-compose -f docker-compose.dev.yaml down
```

### 服务地址

| 服务 | 地址                                 | 说明 |
|------|------------------------------------|------|
| API 网关 | http://localhost:9000              | 所有请求入口 |
| Nacos 控制台 | http://localhost:8828         | 服务注册中心 |
| Sentinel 控制台 | http://localhost:8858              | 限流配置 (admin/admin123) |
| XXL-Job 控制台 | http://localhost:8880/xxl-job-admin | 定时任务 (admin/123456) |

## API 接口文档

| 文档类型 | 地址 | 说明 |
|------|------|------|
| 聚合接口文档 | http://localhost:9000/doc.html | Knife4j 聚合文档（推荐） |

### 单服务 OpenAPI 文档

| 服务 | 地址 |
|------|------|
| 用户服务 | http://localhost:8081/users/v3/api-docs |
| 演唱会服务 | http://localhost:8080/ticket/v3/api-docs |
| 订单服务 | http://localhost:8082/order/v3/api-docs |
| 库存服务 | http://localhost:8083/stock/v3/api-docs |

---

## 实现要点

### 1. Redis Lua 抢票脚本

```lua
-- 演唱会级别限购 (Hash 结构版本)
-- KEYS[1]: stockHashKey, KEYS[2]: userPurchaseKey, KEYS[3]: limitKey
-- ARGV: userId, quantity, gradeId, expireSeconds

-- 返回: 1=成功, -2=票务不存在, -3=库存不足, -4=限购配置不存在, -5=超出限购
local stock = tonumber(redis.call('HGET', stockHashKey, gradeId))
if stock == nil then return -2 end
if stock < quantity then return -3 end

redis.call('HINCRBY', stockHashKey, gradeId, -quantity)
redis.call('INCRBY', userPurchaseKey, quantity)
return 1
```

**文件**: `ticket-order-service/.../config/BookingLuaScript.java`

### 2. Kafka 消费失败回滚

消费失败分两种情况：
- **库存真正不足**：不回滚 Redis，标记订单失败
- **限购校验失败**：回滚 Redis（HINCRBY 恢复库存、减少用户购买计数）

**文件**: `ticket-stock-service/.../mq/OrderMessageConsumer.java`

### 3. 多级缓存 (Caffeine + Redis)

```
L1 (Caffeine) → L2 (Redis) → DB
       ↓              ↓
   本地缓存      分布式缓存
   (Sticky)     (共享)
```

- **缓存类型**: 用户信息、演唱会详情、票价档位
- **库存不加 Caffeine**（高频写、需强一致）
- **缓存失效**: Redis Pub/Sub 广播通知所有实例

**文件**: `ticket-booking-common/.../cache/MultiLevelCacheService.java`

### 4. 网关 Sticky Session (一致性哈希)

```
已登录用户 → 一致性哈希 + 虚拟节点 → 固定实例（本地缓存命中）
未登录用户 → RoundRobin 轮询
```

**文件**: `ticket-gateway-service/.../config/UserIdStickyLoadBalancer.java`

### 5. Java 21 虚拟线程

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

### 6. Sentinel 限流

- `@UserRateLimit` 注解：单用户请求频率限制
- 资源名：`ClassName:methodName`

---

## TODO

### 🟡 中优先级

- [ ] 熔断降级策略（Redis/Kafka 异常时的降级方案）
- [ ] 监控面板：Prometheus + Grafana

### 🟢 低优先级

- [ ] 性能压测与参数调优
- [ ] 库存分段/分片设计

---
