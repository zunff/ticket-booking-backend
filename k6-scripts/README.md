# k6 压测脚本使用说明

## 环境准备

### 安装 k6

**Windows:**
```bash
choco install k6
# 或下载: https://github.com/grafana/k6/releases
```

**Linux:**
```bash
# Debian/Ubuntu
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C4914C66B1C1
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# CentOS/RHEL
curl -L https://rpm.k6.io/rpm.key | sudo rpm --import
echo "[k6]
name=k6
baseurl=https://rpm.k6.io
enabled=1
gpgcheck=1" | sudo tee /etc/yum.repos.d/k6.repo
sudo yum install k6
```

**macOS:**
```bash
brew install k6
```

### 验证安装
```bash
k6 version
```

## 目录结构

```
k6-scripts/
├── config.js              # 配置文件
├── init-test-users.sql    # 测试用户初始化SQL
├── lib/
│   ├── auth.js            # 认证工具函数
│   └── helpers.js         # 通用工具函数
├── scenarios/
│   ├── login.js           # 登录压测
│   ├── concert-list.js    # 演唱会列表查询
│   ├── concert-detail.js  # 演唱会详情查询
│   ├── booking.js         # 抢票压测（核心）
│   └── mixed-flow.js      # 混合场景
└── run-all.js             # 综合压测脚本
```

## 使用方法

### 1. 初始化测试用户

在压测前，先执行SQL插入测试用户：

```bash
mysql -u root -p ticket_user < k6-scripts/init-test-users.sql
```

这将插入500个测试用户：
- 用户名：`k6_test_001` 到 `k6_test_500`
- 密码：`testpass123`

### 2. 修改配置

编辑 `config.js` 文件，设置目标服务器地址：

```javascript
baseUrl: 'http://your-server-ip:9000',
```

或通过环境变量指定：

```bash
export BASE_URL=http://your-server-ip:9000
```

### 3. 准备测试数据

在开始压测前，确保：

1. **创建演唱会数据** - 通过管理后台创建演唱会和票档
2. **设置库存** - 在Redis中设置库存数据
3. **配置限购** - 设置演唱会限购数量

**Redis 数据准备示例：**
```bash
# 设置演唱会1的票档1库存为1000张
redis-cli SET ticket:stock:1:1 1000

# 设置用户购买记录key的前缀
# ticket:stock:{concertId}:{gradeId} - 库存key
# user:ticket:{concertId}:{gradeId}:{userId} - 用户购买记录
```

### 4. 运行单个场景

```bash
# 登录压测
k6 run scenarios/login.js

# 演唱会列表查询
k6 run scenarios/concert-list.js

# 演唱会详情查询
k6 run scenarios/concert-detail.js

# 抢票压测（核心）
k6 run scenarios/booking.js

# 混合场景
k6 run scenarios/mixed-flow.js
```

### 5. 运行综合压测

```bash
# 运行所有场景（约15分钟）
k6 run run-all.js
```

### 6. 指定参数运行

```bash
# 指定目标服务器
k6 run --env BASE_URL=http://192.168.1.100:9000 scenarios/booking.js

# 指定演唱会ID
k6 run --env CONCERT_ID=1 --env GRADE_ID=1 scenarios/booking.js

# 指定用户数量
k6 run --env USER_COUNT=200 scenarios/booking.js

# 输出JSON结果
k6 run --out json=results.json scenarios/booking.js
```

## 压测场景说明

### 场景1: 登录压测 (login.js)
- **目标**: 测试用户登录接口性能
- **并发**: 200 → 500 → 1000 → 0
- **持续时间**: 约4.5分钟
- **关注指标**: 响应时间、JWT生成效率

### 场景2: 演唱会列表查询 (concert-list.js)
- **目标**: 测试分页查询性能
- **并发**: 200 → 500 → 1000 → 0
- **持续时间**: 约4.5分钟
- **关注指标**: 数据库查询效率、缓存命中率

### 场景3: 演唱会详情查询 (concert-detail.js)
- **目标**: 测试认证接口性能
- **并发**: 200 → 500 → 1000 → 0
- **持续时间**: 约4.5分钟
- **关注指标**: JWT验证效率、限流效果

### 场景4: 抢票压测 (booking.js) - 核心
- **目标**: 测试高并发抢票场景
- **并发**: 100 → 500 → 1000 → 2000 → 3000 → 0
- **持续时间**: 约8分钟
- **关注指标**:
  - Redis Lua脚本执行效率
  - 库存扣减原子性
  - 限购逻辑正确性
  - Kafka消息处理延迟

### 场景5: 混合场景 (mixed-flow.js)
- **目标**: 模拟真实用户行为
- **用户行为分布**:
  - 30% 浏览列表
  - 40% 查看详情
  - 20% 尝试抢票
  - 10% 查看订单
- **并发**: 50 → 200 → 500 → 0
- **持续时间**: 约7.5分钟

## 监控指标

运行压测时，建议同时监控：

### Sentinel Dashboard
- 地址: http://localhost:8858
- 关注: QPS、拒绝数、响应时间

### Redis 监控
```bash
# 查看连接数
redis-cli info clients

# 查看内存使用
redis-cli info memory

# 实时监控命令
redis-cli monitor
```

### Kafka 监控
```bash
# 查看消费者组延迟
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group ticket-order-group
```

### MySQL 监控
```sql
-- 查看连接数
SHOW STATUS LIKE 'Threads_connected';

-- 查看慢查询
SHOW VARIABLES LIKE 'slow_query_log';
```

## 性能基准参考

| 接口 | P95响应时间 | P99响应时间 | 错误率 |
|------|------------|------------|--------|
| 登录 | < 500ms | < 1000ms | < 5% |
| 列表查询 | < 300ms | < 500ms | < 1% |
| 详情查询 | < 500ms | < 1000ms | < 10% |
| 抢票 | < 1000ms | < 2000ms | < 30% |

**注意**: 抢票场景允许较高错误率，因为包含库存不足、限流等正常业务失败。

## 常见问题

### Q: 如何增加并发用户数？
修改 `config.js` 中的 `stages` 配置，或通过环境变量：
```bash
k6 run --env USER_COUNT=1000 scenarios/booking.js
```

### Q: 登录失败怎么办？
1. 确认已执行 `init-test-users.sql` 插入测试用户
2. 检查数据库连接和用户表数据
3. 确认密码为 `testpass123`

### Q: 如何调整压测持续时间？
修改各脚本中的 `options.stages` 配置。

### Q: 如何避免限流导致的失败？
1. 在Sentinel中调整限流阈值
2. 减少压测并发数
3. 增加用户数量（分散到不同用户）

### Q: 如何测试特定演唱会？
```bash
k6 run --env CONCERT_ID=5 --env GRADE_ID=2 scenarios/booking.js
```
