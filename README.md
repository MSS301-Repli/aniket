# Animal Locket (aniket)

Chia sẻ ảnh nhanh giữa bạn bè với danh tính ẩn danh (`publicId` + alias + avatar con vật) và kết bạn kiểu quẹt thẻ.
Backend: Spring Boot 3.3 microservices (Java 17, Spring Cloud 2023.0). App: Flutter (`mobile/`).

## Cấu trúc

| Thư mục | Nội dung |
|---|---|
| `libs/common` | Hằng số, event contract (`Events`), lỗi RFC 7807, bảo mật JWT + `/internal/**`, KV store, Kafka DLQ, Resilience4j |
| `libs/outbox-jpa` | Transactional outbox + idempotency consumer ([docs/outbox.md](docs/outbox.md)) |
| `infra/` | `eureka-server`, `config-server`, `api-gateway`, `config-repo` (cấu hình tập trung) |
| `services/` | user, media, post, notification, discovery, chat, billing |
| `mobile/` | App Flutter |
| `deploy/` | Compose fragment, Dockerfile, Keycloak, Traefik, observability, demo |
| `postman/` | Smoke collection chạy bằng Newman |
| `docs/` | Tài liệu ([mục lục](docs/README.md)) |

## Bắt đầu

Yêu cầu: JDK 17+, Maven 3.9; Docker chỉ cần khi chạy cả hệ thống; Flutter khi làm `mobile/`.

```bash
git clone https://github.com/MSS301-Repli/aniket.git && cd aniket
mvn -B verify                       # build + test toàn bộ backend (giống Backend CI)
mvn -B -pl libs/common -am verify   # chỉ một module
```

Chạy cả hệ thống bằng Docker Compose:

```bash
cp .env.example .env                # sửa mọi CHANGE_ME_* trước
docker compose --profile core --profile app up -d --build
docker compose --profile core --profile obs up -d   # thêm Prometheus/Grafana/Loki/Tempo
```

Có `make`: `make help` liệt kê các lối tắt tương ứng.

## CI

| Workflow | Kiểm tra |
|---|---|
| Backend CI | `mvn verify` (Redis service cho test opt-in), dependency review |
| Mobile CI | `flutter analyze`, `flutter test` |
| CodeQL | Phân tích bảo mật Java |
| Secret scan | gitleaks |

## Đóng góp

1. Tạo branch `feature/<ID>-<slug>` (hoặc `fix/<ID>-<slug>`) từ `dev`.
2. Commit và tiêu đề PR theo [Conventional Commits](https://www.conventionalcommits.org/) (`feat(scope): ...`).
3. Mở Pull Request vào `dev` (squash merge). `main` chỉ nhận PR từ `dev` khi release.

Chi tiết: [CONTRIBUTING.md](CONTRIBUTING.md). Code owner theo mảng: [.github/CODEOWNERS](.github/CODEOWNERS).

## Trạng thái

- Nền tảng (PLT-01…40): xong — libs, infra, compose, CI, observability, Postman, deploy demo.
- Service: user, media, post, notification, discovery đã có; chat, billing đang làm (mảng COM).
