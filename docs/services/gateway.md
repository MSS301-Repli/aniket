# `api-gateway` (`infra/api-gateway`, cổng 8080)

Cửa vào duy nhất từ client (Traefik → gateway → service qua Eureka `lb://`). Spring Cloud Gateway (reactive).

## Luồng một request

1. **JWT**: kiểm tra Bearer token với Keycloak (`KEYCLOAK_JWK_URI`).
2. **IdentityFilter**: xoá mọi header `X-Public-Id` client gửi lên (chống giả danh), rồi tra `sub` → `publicId`
   qua `user-service` `/internal/identity/{sub}` (kèm `X-Internal-Token`) và gắn lại `X-Public-Id`.
   - Kết quả cache Caffeine 5 phút, tối đa 100 000 người dùng.
   - Chưa có hồ sơ: không gắn header, không cache; service trả `403 PROFILE_REQUIRED`.
3. **Rate limit** (Redis) theo khoá `u:<publicId>`, chưa có thì `ip:<địa chỉ>` (tin 1 proxy trong `X-Forwarded-For`).
4. **Circuit breaker** + timeout, rồi chuyển tới service.

## Route

| Route | Đường dẫn | Ghi chú |
|---|---|---|
| user-service | `/api/v1/{users,friends,blocks,reports,animals,devices}/**` | |
| post-service | `/api/v1/posts/**`, `/api/v1/feed/**` | |
| media-service | `/api/v1/media/**` | |
| discovery-service | `/api/v1/discovery/**` | Rate limit riêng 2 req/s, burst 10; fallback trả `{"cards":[],"degraded":true}` |
| chat-ws | `/ws/**` | WebSocket `lb:ws://chat-service` |
| chat-service | `/api/v1/chat/**` | |
| billing-service | `/api/v1/billing/**`, `/webhooks/**`, `/pay/**` | `/webhooks/**` và `/pay/**` không cần JWT (billing tự xác thực chữ ký/mã) |

notification-service không có endpoint công khai nên không có route.

## Truy cập

| Đường dẫn | Quyền |
|---|---|
| `/internal/**` | **Luôn 403** (không route nào trỏ tới, thêm `denyAll`), kể cả khi tắt bảo mật |
| `/actuator/health/**`, `/actuator/prometheus`, `/fallback/**` | Công khai |
| `/webhooks/**`, `/pay/**`, `OPTIONS` | Công khai |
| Còn lại | Cần JWT |

Service lỗi hoặc quá timeout: `503` với `{"status":503,"code":"SERVICE_UNAVAILABLE"}` (`/fallback/default`).

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `INTERNAL_TOKEN` | — | **Bắt buộc**: gateway không khởi động nếu trống |
| `KEYCLOAK_JWK_URI` | localhost:8081 | JWK set của realm `animal-locket` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | localhost, 6379, trống | Rate limit. Redis sập thì health DOWN |
| `GATEWAY_CONNECT_TIMEOUT_MS` | `2000` | Timeout kết nối tới service |
| `GATEWAY_RESPONSE_TIMEOUT` | `10s` | Timeout phản hồi, cũng là time limiter của circuit breaker |
| `GATEWAY_RATE_REPLENISH`, `GATEWAY_RATE_BURST` | `20`, `40` | Rate limit mặc định (req/s, burst) |
| `CORS_ALLOWED_ORIGINS` | trống | Origin web được phép |
| `CONFIG_SERVER_URL`, `CONFIG_PASSWORD`, `EUREKA_URL` | | Spring Cloud Config, Eureka (có basic auth) |

## Thêm route cho service mới

Sửa `infra/api-gateway/src/main/resources/application.yml` (route) và thêm case vào test route table
(`infra/api-gateway/src/test`). **Không** route `/internal/**`.
