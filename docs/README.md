# Tài liệu Animal Locket

Animal Locket: chia sẻ ảnh nhanh giữa bạn bè với danh tính ẩn danh (`publicId` + alias + avatar con vật)
và kết bạn kiểu quẹt thẻ. Backend Spring Boot microservices, app Flutter.

## Bắt đầu

| Tài liệu | Nội dung |
|---|---|
| [README](../README.md) | Clone repo, quy trình đóng góp tóm tắt |
| [CONTRIBUTING](../CONTRIBUTING.md) | Nhánh, Conventional Commits, phạm vi sở hữu, review |
| [.env.example](../.env.example) | Biến môi trường cho docker compose (giá trị mẫu, không phải secret) |

## Service

Mỗi service có một file trong [`services/`](services/): trách nhiệm, endpoint, event phát/nhận, cấu hình, vận hành.

| Service | Cổng | Mảng | Tài liệu |
|---|---|---|---|
| user-service | 8101 | IDN | [services/user.md](services/user.md) |
| media-service | 8103 | IDN | [services/media.md](services/media.md) |
| post-service | 8102 | SOC | [services/post.md](services/post.md) |
| notification-service | 8107 | SOC | [services/notification.md](services/notification.md) |
| discovery-service | 8104 | SOC | [services/discovery.md](services/discovery.md) |
| chat-service | 8105 | COM | [services/chat.md](services/chat.md) |
| billing-service | 8106 | COM | [services/billing.md](services/billing.md) |

Hạ tầng: [api-gateway](services/gateway.md) `8080`, config-server `8888`, eureka-server `8761`.

## Nền tảng (PLT)

| Thành phần | Thư mục | Ghi chú |
|---|---|---|
| Thư viện dùng chung | `libs/common`, `libs/outbox-jpa` | [libs-common.md](libs-common.md), [outbox.md](outbox.md) |
| Hạ tầng Spring Cloud | `infra/eureka-server`, `infra/config-server`, `infra/api-gateway`, `infra/config-repo` | Registry, cấu hình tập trung, gateway |
| Triển khai | `docker-compose.yml`, `deploy/` | Compose, Dockerfile, Keycloak, Traefik, observability |
| Demo | `deploy/demo/` | Runbook: [deploy-demo.md](deploy-demo.md) (deploy, rollback, backup/restore) |
| CI | `.github/workflows/` | Build/test backend, mobile, bảo mật |
| Kiểm thử API | `postman/` | Smoke collection chạy bằng Newman |

Tài liệu thiết kế đầy đủ (kiến trúc, data model, event catalog...) nằm ở repo tham khảo `animal_locket`, thư mục `docs/`.
