# Triển khai Animal Locket bằng Docker Compose

> **Trạng thái kiểm chứng:** đã chạy `docker compose config` (không cần Docker daemon). **Chưa chạy `docker compose up` thật.**

## 1. Cấu trúc

`docker-compose.yml` ở gốc repo chỉ ghép các fragment bằng [`include`](https://docs.docker.com/compose/how-tos/multiple-compose-files/include/)
với `project_directory: .`, nên mọi đường dẫn tương đối và file `.env` trong fragment đều tính từ gốc repo.

| Đường dẫn | Nội dung |
|---|---|
| `docker-compose.yml` | Điểm vào, chỉ chứa `include` |
| `.env.example` | Biến môi trường mẫu (sao chép thành `.env`, **đổi mật khẩu**) |
| `deploy/compose/core.yml` | Redis, Kafka (KRaft) + `kafka-init` |
| `deploy/compose/edge.yml` | Keycloak (import realm), Traefik |
| `deploy/compose/apps.yml` | 3 hạ tầng Spring Cloud + 7 service nghiệp vụ |
| `deploy/docker/Dockerfile.java` | Build Maven nhiều giai đoạn, dùng chung cho mọi module (`MODULE_PATH`, `APP_PORT`) |
| `deploy/docker/Dockerfile.runtime` | Chỉ đóng gói JAR đã build sẵn (CI/demo), không chạy Maven |
| `deploy/secrets/` | Nơi đặt khoá FCM/App Store/Google Play, mount vào `/secrets`. **Không commit** (đã nằm trong `.gitignore`) |
| `deploy/keycloak/animal-locket-realm.json` | Realm `animal-locket` (import khi Keycloak khởi động lần đầu, không chứa user/mật khẩu) |
| `deploy/traefik/` | Cấu hình tĩnh (`traefik.yml`) + middleware/header bảo mật + TLS (`dynamic/`) |
| `deploy/supabase/init.sql` | Tạo role/schema `billing`, `keycloak` trên Supabase (chạy tay một lần) |

Mọi fragment khai báo cùng mạng `animal-net` (định nghĩa giống hệt nhau thì `include` không báo xung đột).

## 2. Profile

| Profile | Gồm | Ghi chú |
|---|---|---|
| `core` | Redis, Kafka (+`kafka-init`), Keycloak, Traefik | Luôn cần. MySQL/PostgreSQL/MongoDB là dịch vụ ngoài, nối qua `.env` |
| `app` | eureka-server, config-server, api-gateway, user/media/post/notification/discovery/chat/billing | Cần `core` (thiếu `core` thì compose báo `depends on undefined service`) |

`.env.example` đặt sẵn `COMPOSE_PROFILES=core,app` nên `docker compose up -d --build` là đủ cho dev thông thường.

## 3. Chạy

```bash
cp .env.example .env                       # rồi MỞ .env VÀ ĐỔI MẬT KHẨU
docker compose config --quiet              # kiểm tra cú pháp + biến bắt buộc
docker compose --profile core up -d               # 1) hạ tầng; chờ keycloak healthy (~1–2 phút lần đầu)
docker compose --profile core --profile app up -d --build   # 2) service (build Maven lần đầu mất vài phút)
docker compose logs kafka-init             # danh sách topic đã tạo
```

### Địa chỉ (qua Traefik)

| URL (mặc định `PUBLIC_DOMAIN=localhost`, `HTTP_PORT=8080`) | Dịch vụ |
|---|---|
| `http://api.localhost:8080` | API gateway (profile `app`) |
| `http://auth.localhost:8080` | Keycloak. `/admin`, `/realms/master`, `/metrics`, `/health` **chỉ** mạng nội bộ (`admin-only`) |
| `http://127.0.0.1:8088` | Dashboard Traefik, chỉ loopback |

`*.localhost` tự trỏ về 127.0.0.1 trên trình duyệt; nếu `curl` không phân giải được, thêm vào hosts: `127.0.0.1 api.localhost auth.localhost grafana.localhost`.

## 4. Keycloak và Traefik

- Realm `animal-locket`: client `mobile` (public, Authorization Code + PKCE S256), `postman` (public, PKCE, callback Postman),
  `user-service-admin` (service account, quyền `manage-users`/`view-users` để xoá user). Secret của client này lấy từ
  `KEYCLOAK_ADMIN_CLIENT_SECRET` lúc import (`${...}` trong JSON); **không** có user/mật khẩu nào trong file realm.
- Keycloak dùng Postgres ngoài (`KEYCLOAK_DB_*`), schema `keycloak`; chạy `deploy/supabase/init.sql` trước.
- Traefik: chỉ container có label `traefik.enable=true` mới được public. Middleware trong `deploy/traefik/dynamic/middlewares.yml`:
  `security-headers` (API, CSP `default-src 'none'`), `security-headers-pay` (trang `/pay/**`), `security-headers-ui` (Keycloak/Grafana),
  `ratelimit` (theo IP), `admin-only` (chỉ dải IP riêng tư). TLS: `dynamic/tls.yml` (TLS ≥ 1.2, cipher hiện đại); dev dùng chứng chỉ
  tự ký của Traefik, production bật Let's Encrypt (comment cuối `traefik.yml`).

## 5. Image Java

- `Dockerfile.java`: giai đoạn build `maven:3.9-eclipse-temurin-17` (`mvn -pl <module> -am package`, cache `~/.m2` bằng BuildKit),
  giai đoạn chạy `eclipse-temurin:17-jre`, user không phải root (uid 10001), `HEALTHCHECK` gọi `/actuator/health` qua `curl`,
  `exec java` để nhận SIGTERM. `.dockerignore` riêng (`Dockerfile.java.dockerignore`) loại `.env`, `target`, `mobile`, `docs`.
- `Dockerfile.runtime`: cùng runtime nhưng chỉ `COPY <module>/target/*-SNAPSHOT.jar`; `.dockerignore` chỉ cho phép đúng các JAR.
- Build tay một module: `docker build -f deploy/docker/Dockerfile.java --build-arg MODULE_PATH=services/user-service --build-arg APP_PORT=8101 .`

## 6. Kafka topic

`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`: chỉ topic do `kafka-init` tạo mới tồn tại (3 partition, replication 1).

| Topic | Dead-letter (giữ 30 ngày) |
|---|---|
| `user.events`, `friend.events`, `media.events`, `media.results`, `post.events`, `chat.events`, `billing.events` | `<topic>.dlq` cho từng topic |

Thêm topic mới: sửa vòng `for` trong `deploy/compose/core.yml` rồi `docker compose up kafka-init` (idempotent nhờ `--if-not-exists`).

## 7. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `Thiếu REDIS_PASSWORD` khi chạy compose | Chưa có `.env` | `cp .env.example .env` |
| Service chờ mãi ở `waiting` | `kafka-init` chưa xong | `docker compose logs kafka-init` |
| Keycloak không khởi động, log nói về `${KEYCLOAK_ADMIN_CLIENT_SECRET}` | Chưa đặt biến trong `.env` | Đặt `KEYCLOAK_ADMIN_CLIENT_SECRET` rồi xoá schema `keycloak` và import lại |
| Traefik log: `client version ... is too old` | Docker Engine mới yêu cầu API cao hơn | Dùng `TRAEFIK_IMAGE=traefik:v3` bản mới nhất |
