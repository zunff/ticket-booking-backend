# Kubernetes 部署指南

本文档描述如何在 Kubernetes 集群中部署票务预订系统。

## 目录结构

```
k8s/
├── README.md                          # 本文档
├── namespace.yaml                     # 命名空间定义
├── ingress.yaml                       # Ingress 配置
├── storage/
│   └── local-path-storageclass.yaml   # 本地存储类
├── config/
│   └── app-config.yaml                # 应用配置 ConfigMap
├── middleware/                        # 中间件部署
│   ├── mysql/
│   │   ├── mysql-statefulset.yaml     # MySQL StatefulSet
│   │   └── mysql-service.yaml         # MySQL Service
│   ├── redis/
│   │   ├── redis-statefulset.yaml
│   │   └── redis-service.yaml
│   ├── nacos/
│   │   ├── nacos-configmap.yaml
│   │   ├── nacos-statefulset.yaml
│   │   └── nacos-service.yaml
│   ├── kafka/
│   │   ├── zookeeper-statefulset.yaml
│   │   ├── zookeeper-service.yaml
│   │   ├── kafka-statefulset.yaml
│   │   └── kafka-service.yaml
│   └── dashboards/
│       ├── sentinel-dashboard-deployment.yaml
│       ├── sentinel-dashboard-service.yaml
│       ├── xxl-job-admin-deployment.yaml
│       └── xxl-job-admin-service.yaml
├── apps/                              # 微服务部署
│   ├── ticket-user-service/
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   ├── ticket-service/
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   ├── ticket-order-service/
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   ├── ticket-stock-service/
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   └── ticket-gateway-service/
│       ├── deployment.yaml
│       └── service.yaml
└── hpa/                               # 自动扩缩容配置
    ├── ticket-user-service-hpa.yaml
    ├── ticket-service-hpa.yaml
    ├── ticket-order-service-hpa.yaml
    ├── ticket-stock-service-hpa.yaml
    └── ticket-gateway-service-hpa.yaml
```

## 架构概览

### 服务列表

| 服务 | 端口 | 数据库 | 依赖 |
|------|------|--------|------|
| ticket-gateway-service | 9000 | - | Nacos |
| ticket-user-service | 8081 | ticket_user | Nacos, Redis, MySQL |
| ticket-service | 8080 | ticket_ticket | Nacos, Redis, MySQL, XXL-Job |
| ticket-order-service | 8082 | ticket_order | Nacos, Redis, MySQL, Kafka |
| ticket-stock-service | 8083 | ticket_stock | Nacos, Redis, MySQL, Kafka |

### 中间件

| 组件 | 端口 | NodePort | 说明 |
|------|------|----------|------|
| MySQL | 3306 | - | 主数据库 |
| Redis | 6379 | - | 缓存/库存扣减 |
| Nacos | 8848 | 30848 | 服务注册/配置中心 |
| Kafka | 9092 | - | 消息队列 |
| Zookeeper | 2181 | - | Kafka 依赖 |
| Sentinel | 8858 | 30858 | 流量控制面板 |
| XXL-Job | 8880 | 30880 | 定时任务管理 |

## 前置要求

- Kubernetes 集群 (v1.24+)
- kubectl 已配置并连接到集群
- Ingress Controller (nginx-ingress)
- 存储类支持 (默认使用 local-path)

## 部署步骤

### 1. 构建镜像

```bash
# 构建所有服务
mvn clean package -DskipTests

# 构建 Docker 镜像
docker build -t ticket-booking/ticket-user-service:1.0.0 ./ticket-user-service
docker build -t ticket-booking/ticket-service:1.0.0 ./ticket-service
docker build -t ticket-booking/ticket-order-service:1.0.0 ./ticket-order-service
docker build -t ticket-booking/ticket-stock-service:1.0.0 ./ticket-stock-service
docker build -t ticket-booking/ticket-gateway-service:1.0.0 ./ticket-gateway-service
```

### 2. 部署基础资源

```bash
# 创建命名空间
kubectl apply -f namespace.yaml

# 创建存储类
kubectl apply -f storage/local-path-storageclass.yaml

# 创建配置
kubectl apply -f config/app-config.yaml
```

### 3. 部署中间件

```bash
# 创建 MySQL 初始化脚本 ConfigMap（从 init-db 目录）
kubectl -n ticket-booking create configmap mysql-init-scripts \
  --from-file=../init-db \
  --dry-run=client -o yaml | kubectl apply -f -

# MySQL
kubectl apply -f middleware/mysql/mysql-statefulset.yaml
kubectl apply -f middleware/mysql/mysql-service.yaml

# 等待 MySQL 就绪
kubectl wait --for=condition=ready pod -l app=mysql -n ticket-booking --timeout=300s

# Redis
kubectl apply -f middleware/redis/
kubectl wait --for=condition=ready pod -l app=redis -n ticket-booking --timeout=300s

# Nacos
kubectl apply -f middleware/nacos/
kubectl wait --for=condition=ready pod -l app=nacos -n ticket-booking --timeout=300s

# Kafka (包含 Zookeeper)
kubectl apply -f middleware/kafka/
kubectl wait --for=condition=ready pod -l app=kafka -n ticket-booking --timeout=300s

# 监控面板
kubectl apply -f middleware/dashboards/
```

### 4. 部署微服务

```bash
# 部署所有微服务
kubectl apply -f apps/ticket-user-service/
kubectl apply -f apps/ticket-service/
kubectl apply -f apps/ticket-order-service/
kubectl apply -f apps/ticket-stock-service/
kubectl apply -f apps/ticket-gateway-service/

# 等待服务就绪
kubectl wait --for=condition=ready pod -l app=ticket-user-service -n ticket-booking --timeout=300s
kubectl wait --for=condition=ready pod -l app=ticket-service -n ticket-booking --timeout=300s
kubectl wait --for=condition=ready pod -l app=ticket-order-service -n ticket-booking --timeout=300s
kubectl wait --for=condition=ready pod -l app=ticket-stock-service -n ticket-booking --timeout=300s
kubectl wait --for=condition=ready pod -l app=ticket-gateway-service -n ticket-booking --timeout=300s
```

### 5. 部署 Ingress 和 HPA

```bash
kubectl apply -f ingress.yaml
kubectl apply -f hpa/
```

### 6. 验证部署

```bash
# 查看所有 Pod 状态
kubectl get pods -n ticket-booking

# 查看所有 Service
kubectl get svc -n ticket-booking

# 查看 Ingress
kubectl get ingress -n ticket-booking

# 查看 HPA 状态
kubectl get hpa -n ticket-booking
```

## 访问服务

### 配置本地 hosts

```bash
# 获取 Ingress IP
kubectl get ingress -n ticket-booking

# 添加到 /etc/hosts (Windows: C:\Windows\System32\drivers\etc\hosts)
<INGRESS_IP> ticket-booking.local
```

### 访问地址

- **API 网关**: http://ticket-booking.local
- **Nacos 控制台**: http://<NODE_IP>:30848/nacos (默认账号: nacos/nacos)
- **Sentinel 控制台**: http://<NODE_IP>:30858 (默认账号: sentinel/sentinel)
- **XXL-Job 控制台**: http://<NODE_IP>:30880/xxl-job-admin (默认账号: admin/123456)

## 运维命令

### 扩缩容

```bash
# 手动扩容
kubectl scale deployment ticket-service -n ticket-booking --replicas=5

# 查看自动扩缩容状态
kubectl get hpa -n ticket-booking
```

### 日志查看

```bash
# 查看服务日志
kubectl logs -f deployment/ticket-service -n ticket-booking

# 查看特定 Pod 日志
kubectl logs -f <pod-name> -n ticket-booking
```

### 重启服务

```bash
# 滚动重启
kubectl rollout restart deployment/ticket-service -n ticket-booking

# 查看重启状态
kubectl rollout status deployment/ticket-service -n ticket-booking
```

### 更新镜像

```bash
# 更新服务镜像
kubectl set image deployment/ticket-service ticket-service=ticket-booking/ticket-service:1.0.1 -n ticket-booking
```

## 配置修改

### 修改应用配置

编辑 `config/app-config.yaml` 后应用：

```bash
kubectl apply -f config/app-config.yaml

# 重启相关服务使配置生效
kubectl rollout restart deployment -n ticket-booking
```

### 修改资源限制

编辑各服务的 `deployment.yaml` 中的 `resources` 部分。

## 故障排查

### Pod 无法启动

```bash
# 查看 Pod 详情
kubectl describe pod <pod-name> -n ticket-booking

# 查看事件
kubectl get events -n ticket-booking --sort-by='.lastTimestamp'
```

### 服务无法访问

```bash
# 检查 Service endpoints
kubectl get endpoints -n ticket-booking

# 检查 DNS 解析
kubectl run -it --rm debug --image=busybox --restart=Never -- nslookup ticket-service.ticket-booking.svc.cluster.local
```

### 数据库连接失败

```bash
# 检查 MySQL 服务状态
kubectl get pods -l app=mysql -n ticket-booking

# 进入 MySQL Pod 排查
kubectl exec -it <mysql-pod> -n ticket-booking -- mysql -uroot -proot123
```

## 清理资源

```bash
# 删除所有资源
kubectl delete -f .
kubectl delete namespace ticket-booking
```

## 注意事项

1. **生产环境建议**:
   - 使用持久化存储（如 Ceph、NFS）替代 local-path
   - MySQL 和 Redis 配置高可用集群
   - Kafka 配置多副本
   - 启用 TLS/SSL 加密

2. **资源调整**:
   - 根据实际负载调整 Pod 的 CPU 和内存限制
   - 调整 HPA 的阈值和副本范围

3. **安全配置**:
   - 修改默认密码
   - 配置 NetworkPolicy 限制网络访问
   - 启用 Pod Security Policy
