# 高并发抢票系统

基于 Spring Boot 3.2 + Java 21 虚拟线程 + Redis + Kafka + Nacos 的微服务架构高并发抢票系统。

## 项目架构

```
ticket-booking-backend/
├── ticket-booking-common/          # 公共模块（缓存、工具、注解）
├── ticket-user-service/            # 用户服务 (端口: 8081)
├── ticket-service/                 # 演唱会服务 (端口: 8080)
├── ticket-order-service/           # 订单服务 (端口: 8082)
├── ticket-stock-service/           # 库存服务 (端口: 8083)
├── ticket-gateway-service/         # 网关服务 (端口: 9000)
├── init-db/                        # 数据库初始化脚本
├── k6-scripts/                     # 性能压测脚本
├── deploy/                         # 部署配置
│   ├── dev/                        # 开发环境
│   │   ├── docker-compose.dev.yaml # 基础设施 + 监控
│   │   ├── prometheus/             # Prometheus 配置
│   │   └── grafana/                # Grafana 配置
│   └── k8s/                        # Kubernetes 生产环境
│       ├── apps/                   # 微服务部署
│       ├── middleware/             # 中间件部署
│       │   └── monitoring/         # Prometheus ServiceMonitor
│       ├── config/                 # ConfigMap 配置
│       ├── hpa/                    # 自动扩缩容
│       └── ...
└── sh/                             # 启动/停止脚本
    ├── start-dev.sh                # 启动开发环境
    ├── stop-all.sh                 # 停止所有服务
    └── init-sentinel-rules.sh      # 初始化 Sentinel 限流规则
```

## 微服务职责

| 服务 | 端口 | 数据库 | 职责 |
|------|------|--------|------|
| ticket-gateway-service | 9000 | - | API 网关、JWT 鉴权、Sticky Session 路由 |
| ticket-user-service | 8081 | ticket_user | 用户管理、登录认证 |
| ticket-service | 8080 | ticket_concert | 演唱会管理、票价档位、缓存预热 |
| ticket-order-service | 8082 | ticket_order | 订单创建、抢票入口 |
| ticket-stock-service | 8083 | ticket_stock | 库存管理、Kafka 消费、DB 库存扣减 |

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

---

## 快速开始

### 前置要求

- **JDK 21**
- **Maven 3.9+**
- **Docker Desktop** (用于运行基础设施)

### 启动开发环境

```bash
# 1. 启动基础设施（MySQL、Redis、Kafka、Nacos、Sentinel、XXL-Job、Prometheus、Grafana）
docker compose -f deploy/dev/docker-compose.dev.yaml up -d

# 2. 等待服务就绪后，启动微服务
bash sh/start-dev.sh

# 3. 停止所有服务
bash sh/stop-all.sh

# 4. 可选：初始化 Sentinel 限流规则（需要先启动 Sentinel Dashboard）
bash sh/init-sentinel-rules.sh

# 5. 停止基础设施
docker compose -f  deploy/dev/docker-compose.dev.yaml down
```

### 服务地址

| 服务 | 地址 | 说明 |
|------|------|------|
| API 网关 | http://localhost:9000 | 所有请求入口 |
| Nacos 控制台 | http://localhost:8828 | 服务注册中心 |
| Sentinel 控制台 | http://localhost:8858 | 限流配置 (sentinel/sentinel) |
| XXL-Job 控制台 | http://localhost:8880/xxl-job-admin | 定时任务 (admin/123456) |
| Prometheus | http://localhost:9090 | 监控指标采集 |
| Grafana | http://localhost:3030 | 监控面板 (admin/admin123) |

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
    本地缓存        分布式缓存
    (Sticky)        (共享)
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

### 6. 熔断降级与限流

项目实现了多层次的熔断降级和限流保护：

#### (1) API 接口限流 (Sentinel)

```java
@UserRateLimit  // 基于用户维度的限流
public Result<T> apiMethod() { ... }
```

- `@UserRateLimit` 注解：单用户请求频率限制
- 资源名：`ClassName:methodName`
- 限流响应：HTTP 429 + 错误码

#### (2) 服务间调用熔断 (OpenFeign + Sentinel)

```java
// FallbackFactory 模式，支持获取异常原因
@FeignClient(name = "ticket-service", fallbackFactory = TicketServiceClientFallback.class)
public interface TicketServiceClient { ... }
```

- 所有 FeignClient 配置 `fallbackFactory`
- 统一继承 `FeignFallbackFactory` 基类
- 降级时抛出 `FeignFallbackException`，错误码 `SERVICE_DEGRADED`

#### (3) Redis 操作熔断保护

```java
@SentinelResource(value = "redis:get", blockHandler = "handleGet", blockHandlerClass = RedisBlockHandler.class)
public String get(String key) { ... }
```

- `RedisUtils` 所有方法通过 `@SentinelResource` 保护
- `RedisBlockHandler` 统一处理限流/熔断异常
- 防止 Redis 故障拖垮整个系统

#### (4) Kafka 消息发送熔断降级

```
Kafka 发送失败降级链路：
Kafka Producer → Sentinel 限流 → 发送失败
                      ↓                ↓
               BlockException      Exception
                      ↓                ↓
               KafkaFallbackService.saveToFallback()
                      ↓
            ┌─────────┴─────────┐
            ↓                   ↓
       Redis Stream         本地内存队列
       (持久化优先)       (最后防线，容量 10000)
```

- **第一层降级**：Redis Stream（持久化、可恢复）
- **第二层降级**：本地有界队列（防止 OOM）
- **重试机制**：XXL-Job 定时任务重试降级消息

**关键文件**：
- `ticket-booking-common/.../sentinel/SentinelConfig.java` - Sentinel 全局配置
- `ticket-booking-common/.../sentinel/RedisBlockHandler.java` - Redis 熔断处理
- `ticket-booking-common/.../sentinel/FeignFallbackFactory.java` - Feign 降级基类
- `ticket-order-service/.../mq/KafkaFallbackService.java` - Kafka 两层降级
- `ticket-order-service/.../job/KafkaFallbackRetryJob.java` - 降级消息重试任务

---

## Kubernetes 部署

### 目录结构

```
deploy/k8s/
├── namespace.yaml                     # 命名空间定义
├── ingress.yaml                       # Ingress 配置
├── storage/local-path-storageclass.yaml
├── config/app-config.yaml             # 应用配置 ConfigMap
├── middleware/                        # 中间件部署
│   ├── mysql/
│   ├── redis/
│   ├── nacos/
│   ├── kafka/
│   ├── dashboards/                    # Sentinel、XXL-Job
│   └── monitoring/                    # Prometheus ServiceMonitor
├── apps/                              # 微服务部署
│   ├── ticket-user-service/
│   ├── ticket-service/
│   ├── ticket-order-service/
│   ├── ticket-stock-service/
│   └── ticket-gateway-service/
└── hpa/                               # 自动扩缩容配置
```

### 前置要求

- Kubernetes 集群 (v1.24+)
- kubectl 已配置并连接到集群
- Ingress Controller (nginx-ingress)
- 存储类支持 (默认使用 local-path)
- Helm 3.x（用于安装 Prometheus Operator）

### 安装 Helm

```bash
# macOS
brew install helm

# Linux (Debian/Ubuntu)
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# 验证安装
helm version
```

### 安装 Prometheus Operator

```bash
# 添加 Helm 仓库
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# 安装 kube-prometheus-stack
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace

# 验证安装
kubectl get crd servicemonitors.monitoring.coreos.com
```

### 部署步骤

```bash
# 1. 构建镜像
mvn clean package -DskipTests
docker build -t ticket-booking/ticket-user-service:1.0.0 ./ticket-user-service
docker build -t ticket-booking/ticket-service:1.0.0 ./ticket-service
docker build -t ticket-booking/ticket-order-service:1.0.0 ./ticket-order-service
docker build -t ticket-booking/ticket-stock-service:1.0.0 ./ticket-stock-service
docker build -t ticket-booking/ticket-gateway-service:1.0.0 ./ticket-gateway-service

# 2. 部署基础资源
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/storage/local-path-storageclass.yaml
kubectl apply -f deploy/k8s/config/app-config.yaml

# 3. 部署中间件
kubectl -n ticket-booking create configmap mysql-init-scripts --from-file=init-db
kubectl apply -f deploy/k8s/middleware/mysql/
kubectl apply -f deploy/k8s/middleware/redis/
kubectl apply -f deploy/k8s/middleware/nacos/
kubectl apply -f deploy/k8s/middleware/kafka/
kubectl apply -f deploy/k8s/middleware/dashboards/

# 4. 部署监控（需要先安装 Prometheus Operator）
kubectl apply -f deploy/k8s/middleware/monitoring/

# 5. 部署微服务
kubectl apply -f deploy/k8s/apps/ticket-user-service/
kubectl apply -f deploy/k8s/apps/ticket-service/
kubectl apply -f deploy/k8s/apps/ticket-order-service/
kubectl apply -f deploy/k8s/apps/ticket-stock-service/
kubectl apply -f deploy/k8s/apps/ticket-gateway-service/

# 6. 部署 Ingress 和 HPA
kubectl apply -f deploy/k8s/ingress.yaml
kubectl apply -f deploy/k8s/hpa/
```

### Kubernetes 服务端口

| 组件 | 端口 | NodePort | 说明 |
|------|------|----------|------|
| MySQL | 3306 | - | 主数据库 |
| Redis | 6379 | - | 缓存/库存扣减 |
| Nacos | 8848 | 30848 | 服务注册/配置中心 |
| Kafka | 9092 | - | 消息队列 |
| Sentinel | 8858 | 30858 | 流量控制面板 |
| XXL-Job | 8880 | 30880 | 定时任务管理 |

### 运维命令

```bash
# 查看 Pod 状态
kubectl get pods -n ticket-booking

# 扩缩容
kubectl scale deployment ticket-service -n ticket-booking --replicas=5

# 查看日志
kubectl logs -f deployment/ticket-service -n ticket-booking

# 滚动重启
kubectl rollout restart deployment/ticket-service -n ticket-booking
```

---

## 性能压测 (k6)

### 目录结构

```
k6-scripts/
├── config.js              # 配置文件
├── init-test-users.sql    # 测试用户初始化SQL
├── .token-cache.json      # Token缓存文件（由 sh/pre-login.sh 生成）
├── lib/
│   ├── auth.js            # 认证工具函数
│   ├── token-cache.js     # Token缓存模块
│   └── helpers.js         # 通用工具函数
├── scenarios/
│   ├── login.js           # 登录压测
│   ├── concert-list.js    # 演唱会列表查询
│   ├── concert-detail.js  # 演唱会详情查询
│   ├── booking.js         # 抢票压测（核心）
│   ├── mixed-flow.js      # 混合场景
│   └── sentinel-rate-limit.js  # Sentinel限流测试
└── run-all.js             # 综合压测脚本
```

### 安装 k6

```bash
# macOS
brew install k6

# Linux (Debian/Ubuntu)
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C4914C66B1C1
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Windows
choco install k6
```

### 运行压测

```bash
# 1. 初始化测试用户（500个用户：k6_test_001 ~ k6_test_500，密码：123456）
mysql -u root -p ticket_user < k6-scripts/init-test-users.sql

# 2. 预登录生成 Token 缓存（重要！避免压测时重复登录）
#    参数：<用户数量> <网关地址>
./sh/pre-login.sh 500 http://localhost:9000

# 3. 运行单个场景
k6 run k6-scripts/scenarios/login.js
k6 run k6-scripts/scenarios/booking.js \
  -e BASE_URL=http://192.168.1.100:9000 \
  -e CONCERT_ID=2 \
  -e GRADE_ID=6

# 4. 运行混合场景（模拟真实用户行为）
k6 run k6-scripts/scenarios/mixed-flow.js \
  -e BASE_URL=http://192.168.1.100:9000 \
  -e CONCERT_ID=2 \
  -e GRADE_ID=6

# 5. 运行综合压测（所有场景）
k6 run k6-scripts/run-all.js
```

### 压测流程说明

**步骤 1: 初始化测试用户**

```bash
mysql -u root -p ticket_user < k6-scripts/init-test-users.sql
```

**步骤 2: 预登录生成 Token 缓存**

```bash
./sh/pre-login.sh 500 http://localhost:9000
```

生成 `k6-scripts/.token-cache.json`，包含 500 个有效 Token。

**步骤 3: 运行压测**

```bash
k6 run k6-scripts/scenarios/booking.js -e BASE_URL=http://localhost:9000
```

**为什么需要预登录？**

- 避免压测时大量登录请求影响测试结果
- 减少登录接口压力，专注测试目标接口
- 每个虚拟用户(VU)分配不同的 Token，模拟真实多用户场景

### 压测场景说明

| 场景 | 文件 | 并发 | 持续时间 | 关注指标 |
|------|------|------|----------|----------|
| 登录压测 | login.js | 200→500→1000 | ~4.5分钟 | JWT生成效率 |
| 演唱会列表 | concert-list.js | 200→500→1000 | ~4.5分钟 | 缓存命中率 |
| 演唱会详情 | concert-detail.js | 200→500→1000 | ~4.5分钟 | 限流效果 |
| 抢票压测 | booking.js | 100→3000 | ~8分钟 | Lua执行效率、Kafka延迟 |
| 混合场景 | mixed-flow.js | 50→500 | ~7.5分钟 | 真实用户行为模拟 |
| Sentinel限流测试 | sentinel-rate-limit.js | 阶梯式到达率 | ~2.5分钟 | 限流配置验证 |

### 性能基准参考

| 接口 | P95响应时间 | P99响应时间 | 错误率 |
|------|------------|------------|--------|
| 登录 | < 500ms | < 1000ms | < 5% |
| 列表查询 | < 300ms | < 500ms | < 1% |
| 详情查询 | < 500ms | < 1000ms | < 10% |
| 抢票 | < 1000ms | < 2000ms | < 30% |

**注意**: 抢票场景允许较高错误率，包含库存不足、限流等正常业务失败。

### 压测监控

推荐使用 Grafana 仪表板查看实时监控指标：http://localhost:3030 (admin/admin123)

主要关注指标：
- **JVM**: 堆内存、GC 暂停时间、线程数
- **HTTP**: 请求 QPS、响应时间 (P95/P99)、错误率
- **Redis**: 连接数、命令延迟、内存使用
- **Kafka**: 消费延迟、消息积压

如需命令行调试：

```bash
# Redis 监控
redis-cli info clients
redis-cli info memory

# Kafka 消费延迟
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group stock-group
```

---

## TODO

- [ ] 性能压测与参数调优
- [ ] 库存分段/分片设计

---

## 性能优化记录

### 2025-04-06 Gateway JWT 缓存优化

#### 背景
压测发现 Gateway 单实例 CPU 打满（200%），QPS 只有 260/s，成为系统瓶颈。

#### 问题分析
- JWT HMAC-SHA256 签名验证是 CPU 密集操作
- 每个请求都要完整验证，无缓存
- Gateway 单实例成为瓶颈

#### 优化方案

**1. JWT Token 缓存**

在 Gateway 层缓存 JWT 验证结果，使用 Caffeine 本地缓存：

```yaml
jwt:
  cache:
    enabled: true           # 启用缓存
    expire-minutes: 5       # 缓存 TTL 5分钟（JWT 过期时间 24小时）
    max-size: 10000         # 最大缓存 10000 条
```

**关键实现**：
- 缓存签名验证结果（跳过 HMAC 计算）
- 每次请求检查 JWT 真实过期时间（`exp` claim）
- 缓存 key 使用 token.hashCode()，不存储完整 token

**文件变更**：
- `ticket-gateway-service/.../filter/JwtAuthFilter.java` - JWT 缓存逻辑
- `ticket-gateway-service/.../controller/CacheStatsController.java` - 缓存监控端点
- `ticket-gateway-service/.../resources/application-prod.yaml` - 缓存配置

**2. JVM 参数调优**

针对 Gateway CPU 密集型特点优化：

```bash
# Gateway (CPU 密集，高并发)
-Xmx1024m -Xms512m 
-XX:+UseG1GC 
-XX:+UseStringDeduplication    # 字符串去重，减少 10-20% 堆内存
-XX:MaxGCPauseMillis=100       # 目标 GC 停顿 100ms
-XX:+ParallelRefProcEnabled    # 并行引用处理
-XX:InitiatingHeapOccupancyPercent=45
-XX:G1ReservePercent=15
```

#### 优化效果（抢票接口压测数据）

**服务配置**：
- Gateway: 1 实例，1GB 堆内存
- Order Service: 3 实例，各 1GB 堆内存
- Stock Service: 3 实例，各 1GB 堆内存
- User/Concert Service: 各 1 实例，384MB 堆内存

**压测参数**：800 VU 持续 30s

**核心指标**：

| 指标 | 数值 |
|------|------|
| **QPS** | 600/s |
| 成功请求数 | 18420 |
| 失败率 | 0.11% (22/18442) |
| JWT 缓存命中率 | 99.15% |
| P95 响应时间 | 2.78s |
| P99 响应时间 | 3.02s |
| 平均响应时间 | 1.27s |
| 最大并发 VU | 800 |