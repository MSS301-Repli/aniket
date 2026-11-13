# `libs/common`: dùng trong service

Thêm dependency (phiên bản do root pom quản lý):

```xml
<dependency>
    <groupId>com.animallocket</groupId>
    <artifactId>common</artifactId>
</dependency>
```

Mọi thứ dưới đây tự bật qua auto-configuration, không cần `@Import`.

## Có sẵn

| Thành phần | Dùng thế nào |
|---|---|
| `Headers` | `@RequestHeader(Headers.PUBLIC_ID) String me` — người gọi do gateway gắn, không nhận từ body/query |
| `Topics`, `EventTypes`, `Events` | Topic Kafka, type event, record payload (nguồn contract duy nhất) |
| `EventEnvelope`, `EventJson` | Đọc/ghi event; JSON hỏng ném `IllegalArgumentException` → vào DLQ ngay |
| `PublicIds` | `generate()`, `isValid(id)` — kiểm tra id từ path/body trước khi query |
| `ApiException` | `notFound`, `forbidden`, `conflict`, `badRequest`, `tooMany` với `code` UPPER_SNAKE |
| `ApiExceptionHandler` | Trả `application/problem+json`: `VALIDATION_FAILED` (400), `PROFILE_REQUIRED` (403 khi thiếu `X-Public-Id`), `CONFLICT` (409 vi phạm ràng buộc DB), `INTERNAL_ERROR` (500) |
| `SecurityFilterChain` | JWT resource server cho mọi request trừ actuator, `/internal/**`, `OPTIONS`, public-paths. Tự khai báo chain riêng thì chain này lùi lại |
| `InternalTokenFilter` | Bảo vệ `/internal/**` bằng `X-Internal-Token` |
| `KeyValueStore` | Hạn mức, cache, khoá: Redis hoặc bộ nhớ |
| Kafka error handler | Thử lại 3 lần cách 1 giây rồi gửi `<topic>.dlq` |
| Circuit breaker | Mặc định cho `CircuitBreakerFactory` (timeout 2s, mở mạch ≥ 50% lỗi) |
| Actuator + Prometheus + OTLP | `/actuator/health`, `/actuator/prometheus`, trace tới Tempo |

Redis, Kafka và Resilience4j là dependency **optional** của common: service cần thì tự thêm
`spring-boot-starter-data-redis`, `spring-kafka`, `spring-cloud-starter-circuitbreaker-resilience4j`.

## Property

| Khoá | Mặc định | Ý nghĩa |
|---|---|---|
| `animal.internal-token` | — | Khoá nội bộ. **prod: bắt buộc, ≥ 32 ký tự**; profile khác thiếu thì dùng `dev-internal-token` |
| `animal.security.enabled` | `true` | `false` mở mọi endpoint trừ `/internal/**` (chỉ test/dev) |
| `animal.security.public-paths` | trống | Đường dẫn không cần JWT, phân tách bằng dấu phẩy |
| `animal.security.cors.allowed-origins` | trống (tắt) | Origin được gọi trực tiếp |
| `animal.kv.type` | `redis` | `redis` hoặc `memory` |
| `animal.resilience.timeout` | `2s` | Timeout circuit breaker |
| `animal.resilience.open-wait` | `10s` | Thời gian mạch mở |

Outbox: xem [outbox.md](outbox.md).

## Cấu hình test của service

`src/test/resources/application.yml`:

```yaml
spring:
  cloud.config.enabled: false
  kafka.listener.auto-startup: false
eureka.client.enabled: false
animal:
  kv.type: memory
  outbox.relay.enabled: false
```

và `@MockBean JwtDecoder jwtDecoder;` trong test `@SpringBootTest`. Gọi endpoint nội bộ trong test:
`.header(Headers.INTERNAL_TOKEN, "dev-internal-token")`.
