# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Multi-module Maven project (Java 21, Spring Boot 3.2). Parent POM manages Spring Boot / Spring Cloud / Spring Cloud Alibaba BOMs.

```bash
# Build everything (skip tests for speed)
mvn clean package -DskipTests

# Compile a single module + its dependencies (the common loop while iterating)
mvn clean compile -pl ticket-{module}-service -am

# Run tests for one module
mvn test -pl ticket-order-service

# Run a single test class / method
mvn test -pl ticket-order-service -Dtest=BookingLuaScriptTest
mvn test -pl ticket-order-service -Dtest=BookingLuaScriptTest#methodName

# Local dev: bring up infra then services
docker compose -f deploy/dev/docker-compose.dev.yaml up -d
bash sh/start-dev.sh        # starts all microservices
bash sh/stop-all.sh
```

Build a service image: `docker build -t ticket-booking/ticket-{module}-service:1.0.0 ./ticket-{module}-service`.

## Architecture

### Services & their boundaries

Six microservices, each owns its own MySQL database (DB-per-service). Gateway is the only public entry point (`:9000`, path `/api/{module}/**`, `StripPrefix=1`); each backend service also exposes `/{module}/internal/**` endpoints for inter-service calls.

| Service | Port | DB | Role |
|---------|------|----|------|
| ticket-gateway-service | 9000 | — | JWT auth, Sentinel gateway rate-limit, routing |
| ticket-user-service | 8081 | ticket_user | Auth, users |
| ticket-service | 8080 | ticket_concert | Concerts, price grades, cache preheat |
| ticket-order-service | 8082 | ticket_order | Order creation, booking entry (Lua) |
| ticket-stock-service | 8083 | ticket_stock | Kafka consumer, DB stock deduction |
| ticket-payment-service | 8085 | ticket_payment | Payment (strategy + capability interfaces) |

Service port + context-path live in each module's `application.yaml` (e.g. `server.port: 8082`, `servlet.context-path: /order`). Profile-specific infra addresses (Nacos/MySQL/Redis/Kafka) are in `application-dev.yaml` / `application-prod.yaml`.

### The booking flow (the system's reason for existing)

Concurrent ticket sales use a Redis-first, DB-eventually-consistent design — see README "核心流程" for the diagram. The non-obvious pieces:

- **Atomic stock deduction in Redis Lua** (`ticket-order-service/.../config/BookingLuaScript.java`): checks purchase limits + stock + deducts in one script. Returns codes (`1` success, `-2/-3/-4/-5` failure reasons) consumed by `OrderServiceImpl`.
- **Kafka decouples order creation from DB writes**. Producer: `ticket-order-service/.../mq/KafkaProducerService.java` (Sentinel-wrapped, async send). Consumer: `ticket-stock-service/.../mq/OrderMessageConsumer.java` does the real DB deduction.
- **Failure rollback is conditional** (`OrderMessageConsumer`): real stock shortage → mark order FAILED, do NOT refund Redis. Limit/purchase check failure → refund Redis (restore stock + decrement purchase count). Do not treat these two symmetrically.
- **Kafka send has a two-layer fallback**: Redis Stream (persistent) → bounded in-memory queue (10000, last-resort OOM guard), retried by an XXL-Job. See `KafkaFallbackService` / `KafkaFallbackRetryJob`.

### Multi-level caching

`MultiLevelCacheService` (common) chains Caffeine (L1, per-instance) → Redis (L2, shared) → DB. Cache invalidation fans out via Redis Pub/Sub to all instances. **Stock is never cached in Caffeine** (high write frequency, strong consistency required) — only Redis.

## Cross-cutting conventions

These patterns repeat across every service — match them, don't reinvent.

### Package layout (`com.ticketbooking.{module}`)

`controller` · `service` + `service/impl` · `mapper` · `entity` · `model/{qo,vo,dto}` · `config` · `client/{fallback}` · `converter` · `mq` · `strategy` (payment only). Application class lives at `com.ticketbooking.{module}.{Module}ServiceApplication` and is annotated `@SpringBootApplication @MapperScan @ComponentScan({"com.ticketbooking.common", "com.ticketbooking.{module}"}) @EnableFeignClients` (+ `@EnableConfigurationProperties` for any `@ConfigurationProperties` beans).

### Request/Response model split

- **`Result<T>`** (`common/result/Result.java`) wraps every controller response: `code`/`message`/`data`. Build with `Result.success(data)` / `Result.error(ErrorCode)`.
- **QO** = inbound request (validation annotations), **VO** = outbound response, **DTO** = cross-service / Feign payloads. Cross-service DTOs and QOs live in `common/model/{dto,qo}` so multiple services share them.
- **Do not add a conversion layer when fields are identical.** A request type that doubles as a service/strategy input should be passed straight through — no QO→Request manual copy. Only convert when the shapes genuinely differ.

### Errors & exceptions

`GlobalExceptionHandler` (common, `@RestControllerAdvice`) maps exceptions to `Result`. Throw **`BusinessException(ErrorCode)`** for expected business failures (→ HTTP 400) and **`SystemException(ErrorCode)`** for infrastructure failures (→ HTTP 500). All error codes are the `ErrorCode` enum (`common/enums/ErrorCode.java`); when adding one, follow the existing numbered segments (user 1xxx, ticket 2xxx, order 3xxx, general 4xxx, token 5xxx, 6xxx, payment 7xxx).

### Enums — stay ORM-pure

Domain enums in `common/enums` carry `code` + `desc` and a static `of(code)` lookup. **They are plain POJOs with no MyBatis/JPA annotations.** Entities store the raw `String`/`Integer` value; boundaries convert via `of()` / `getXxx()`. This matches the existing `OrderStatus` / `Order` pattern — do not sprinkle `@EnumValue` on enums (it leaks ORM coupling into the common module).

### Persistence

MyBatis-Plus. Mappers extend `BaseMapper<Entity>`; services extend `IService`/`ServiceImpl<Mapper, Entity>`. Entities use `@TableName` + `@TableId(type = IdType.AUTO)`, Lombok `@Data`. Column mapping is `map-underscore-to-camel-case` (configured in each `application.yaml`). DB schemas are one file per service in `init-db/{service}.sql` (each creates its own database).

### Inter-service calls — Feign + Sentinel fallback

Clients in `{module}/client/XxxServiceClient.java`, `@FeignClient(name=..., path="/{module}/internal", configuration=FeignClientConfig.class, fallbackFactory=XxxClientFallback.class)`. Each fallback extends `common/sentinel/FeignFallbackFactory<T>` (constructor passes the service name) and either throws `FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED)` or returns a safe default. `FeignResultDecoder` unwraps the `Result<T>` envelope so clients receive the payload directly. Internal endpoints live on `Internal{Module}Controller` under `/internal`.

### Sentinel — everything is protected

Three layers, all in place: (1) `@UserRateLimit` annotation on endpoints (per-user, resource name = `Class:method`); (2) every `RedisUtils` method carries `@SentinelResource` with `RedisBlockHandler` (prevents Redis outages cascading); (3) every Feign call has a fallback. `feign.sentinel.enabled: true`. Gateway has its own `SentinelGatewayConfig` normalizing URL numeric IDs to `/{id}` for resource names.

### Tracing & slow-log

`TraceIdFilter` (gateway + common) propagates a `traceId` via header + MDC; the console pattern includes `[%X{traceId:-}]`. A `slow-log` aspect (`slow-log.threshold-ms`) logs over-threshold calls. Both are configured per service in `application.yaml`.

## Payment module — strategy + capability interfaces

`ticket-payment-service` exists to be extended. The core abstraction is **a base strategy interface plus narrow capability interfaces**, not one fat interface — different payment platforms support different operation sets.

```
PayChannelStrategy              base interface: channel(), prepay(), parseNotify(), buildAckResponse()
 ├─ QueryCapable                extends base: query(outTradeNo)
 ├─ CloseCapable                extends base: close(outTradeNo)
 └─ RefundCapable               extends base: refund(RefundRequest)
AbstractPayChannelStrategy     template method (final prepay/parseNotify) — idempotency, Redisson lock, record management
 ├─ WechatPayStrategy           implements base + Query + Close + Refund
 └─ AlipayStrategy              implements base + Query + Close + Refund
PayChannelFactory               self-registers all strategies into a Map<PayChannel, …> via constructor injection
PaymentService / Impl           facade; resolves strategy then `instanceof`-checks capability, throws PAYMENT_CAPABILITY_NOT_SUPPORTED otherwise
```

- **Adding a channel** = one Strategy class + one Properties class + one `PayChannel` enum value. Factory picks it up automatically.
- **Adding a capability** (e.g. refund query, bill download, preauth) = one capability interface extending the base; channels implement it only if they support it.
- **Both strategies are local stubs today** (no SDK calls, fake trade numbers / pay URLs inline — these are throwaway, not config). Replacing them with real `wechatpay-java` / `alipay-sdk-java` touches only the two strategy classes; the interfaces, factory, facade, and controllers do not change.

Capability method bodies (`query`/`close`/`refund`) are **not** in `AbstractPayChannelStrategy` — they live directly on the concrete strategy, so a channel that doesn't support a capability isn't forced to stub it.

## Things to verify before trusting memory

When recalling a file path, class, or flag from prior sessions, confirm it still exists — services get refactored (e.g. gateway sticky-session routing was removed; consumer Sentinel rate-limiting was removed). Prefer `codegraph explore` / `codegraph node` or reading the current file over relying on a remembered snapshot.
