# Sentinel 集成踩坑记录

> 项目环境: Spring Boot 3.2.0 + Spring Cloud Alibaba 2023.0.3.2 + Sentinel 1.8.8

## 1. 版本兼容性问题

### 问题描述
Spring Cloud Alibaba 2023.0.3.2 对应的 Sentinel 版本是 **1.8.8**。

### 解决方案
查看 Spring Cloud Alibaba BOM 中管理的版本：
```xml
<spring-cloud-alibaba.version>2023.0.3.2</spring-cloud-alibaba.version>
```

Sentinel 版本由 BOM 自动管理，无需手动指定。

---

## 2. sentinel-datasource 依赖问题

### 问题描述
原计划使用 Nacos 作为 Sentinel 规则的数据源，配置了 `spring-cloud-alibaba-sentinel-datasource` 依赖。

### 解决方案
改为**服务从 Sentinel Dashboard 拉取规则**的方式：
- 移除 `spring-cloud-alibaba-sentinel-datasource` 依赖
- 移除 YAML 中的 `datasource` 配置
- 只保留 Dashboard 连接配置：
```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8858
        port: 8719
      eager: true
```

---

## 3. Gateway Sentinel 依赖缺失

### 问题描述
`spring-cloud-starter-alibaba-sentinel-gateway` 在 Spring Cloud Alibaba 2023.0.0.0-RC1 中**不存在**。

```
Could not find artifact com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel-gateway:jar:2023.0.0.0-RC1
```

### 解决方案
Gateway 需要单独添加 `sentinel-spring-cloud-gateway-adapter` 依赖：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>

<!-- 单独添加 Gateway 适配器 -->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-spring-cloud-gateway-adapter</artifactId>
    <version>1.8.6</version>
</dependency>
```

---

## 4. SentinelGatewayBlockExceptionHandler 构造函数变化

### 问题描述
Sentinel 1.8.6 中 `SentinelGatewayBlockExceptionHandler` 的构造函数签名变化：

```
需要: List<ViewResolver>, ServerCodecConfigurer
找到: ObjectProvider<ServerCodecConfigurer>
```

### 解决方案
修改 `SentinelGatewayConfig.java`：

```java
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE)
public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler(
        List<ViewResolver> viewResolvers,
        ServerCodecConfigurer serverCodecConfigurer) {
    return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
}
```

---

## 5. ExceptionPredicate 接口不存在

### 问题描述
Sentinel 1.8.6 中**不存在** `com.alibaba.csp.sentinel.slots.block.degrade.ExceptionPredicate` 接口。

```
找不到符号: 类 ExceptionPredicate
```

### 解决方案

**方案一（推荐）：升级 Spring Cloud Alibaba 到 2023.0.3.2**

该版本对应 Sentinel 1.8.8，提供了完整的异常过滤支持：

```xml
<spring-cloud-alibaba.version>2023.0.3.2</spring-cloud-alibaba.version>
```

Sentinel 1.8.8 的 `Tracer` 类提供了 `setExceptionPredicate()` 方法：

```java
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.util.function.Predicate;

@Component
public class GlobalSentinelExceptionPredicate implements Predicate<Throwable> {

    @PostConstruct
    public void init() {
        Tracer.setExceptionPredicate(this);
    }

    @Override
    public boolean test(Throwable throwable) {
        // BusinessException 不纳入熔断统计
        if (throwable instanceof BusinessException) {
            return false;
        }
        // Feign 4xx 不纳入统计，仅 5xx 纳入
        if (throwable instanceof FeignException fe) {
            return fe.status() >= 500;
        }
        return true;
    }
}
```

**方案二（旧版本兼容）：工具类手动调用**

如果无法升级版本，可将类改为工具类，在具体熔断逻辑中手动调用：

```java
@Component
public class GlobalSentinelExceptionPredicate {
    public boolean shouldCount(Throwable throwable) {
        if (throwable instanceof BusinessException) {
            return false;
        }
        if (throwable instanceof FeignException feignException) {
            return feignException.status() >= 500;
        }
        return true;
    }
}
```

---

## 6. 父 POM 依赖管理问题

### 问题描述
在父 POM 的 `dependencyManagement` 中声明 Sentinel 依赖时缺少版本号：

```
'dependencies.dependency.version' for spring-cloud-starter-alibaba-sentinel:jar is missing
```

### 解决方案
Sentinel 相关依赖的版本已由 `spring-cloud-alibaba-dependencies` BOM 管理，**不需要在父 POM 中重复声明**。

只需在各模块中直接引用依赖，无需指定版本：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
    <!-- 无需 version -->
</dependency>
```

---

## 7. Sentinel Dashboard 账号密码

### 默认配置
使用 `bladex/sentinel-dashboard:1.8.8` 镜像时：
- 默认账号: `sentinel`
- 默认密码: `sentinel`

### 自定义配置
在 docker-compose 中添加环境变量：

```yaml
sentinel-dashboard:
  image: bladex/sentinel-dashboard:1.8.7
  environment:
    JAVA_OPTS: "-Dserver.port=8858 -Dsentinel.dashboard.auth.username=admin -Dsentinel.dashboard.auth.password=admin123"
```

---

## 8. 服务注册到 Dashboard 的条件

### 问题描述
服务启动后，Sentinel Dashboard 中看不到服务列表。

### 原因
1. 服务必须有请求流量才会注册到 Dashboard
2. 需要配置 `eager: true` 实现立即初始化

### 解决方案
```yaml
spring:
  cloud:
    sentinel:
      eager: true  # 立即初始化，不等待首次请求
```

发送一个请求后，可以通过以下方式验证：
```bash
curl http://localhost:8719/tree
```

---

## 9. 限流规则配置方式

### API 方式（测试用）
```bash
curl -X POST "http://localhost:8719/setRules" \
  -d "type=flow" \
  -d "data=[{\"resource\":\"/concerts\",\"limitApp\":\"default\",\"grade\":1,\"count\":2}]"
```

### Dashboard 方式（推荐）
1. 访问 http://localhost:8858
2. 登录后选择应用
3. 在"流控规则"页面添加规则

---

## 10. 验证限流是否生效

### 测试命令
```bash
# 连续发送请求
for i in {1..5}; do
  curl -s http://localhost:8080/concerts
  echo ""
done
```

### 预期结果
```
{"code":200,...}  # 正常响应
{"code":200,...}  # 正常响应
{"code":4003,"message":"请求过于频繁，请稍后重试"}  # 被限流
{"code":4003,"message":"请求过于频繁，请稍后重试"}  # 被限流
{"code":4003,"message":"请求过于频繁，请稍后重试"}  # 被限流
```

---

## 总结

| 问题 | 根因 | 解决方案 |
|------|------|----------|
| sentinel-gateway 依赖不存在 | 2023 版本未发布该模块 | 使用 sentinel-spring-cloud-gateway-adapter |
| ExceptionPredicate 不存在 | Sentinel 1.8.6 API 差异 | 升级 SCA 到 2023.0.3.2 (Sentinel 1.8.8) |
| 构造函数签名变化 | 版本 API 变化 | 调整参数列表 |
| 版本管理混乱 | BOM 已管理版本 | 移除父 POM 重复声明 |
| Dashboard 看不到服务 | 需要流量触发 | 配置 eager: true |

---

## 参考链接

- [Sentinel 官方文档](https://sentinelguard.io/zh-cn/)
- [Spring Cloud Alibaba 版本说明](https://github.com/alibaba/spring-cloud-alibaba/wiki)
