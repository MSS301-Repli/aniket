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

Mọi fragment khai báo cùng mạng `animal-net` (định nghĩa giống hệt nhau thì `include` không báo xung đột).

## 2. Profile

| Profile | Gồm | Ghi chú |
|---|---|---|
| `core` | Redis, Kafka (+`kafka-init`) | Luôn cần. MySQL/PostgreSQL/MongoDB là dịch vụ ngoài, nối qua `.env` |

## 3. Chạy

```bash
cp .env.example .env                       # rồi MỞ .env VÀ ĐỔI MẬT KHẨU
docker compose config --quiet              # kiểm tra cú pháp + biến bắt buộc
docker compose --profile core up -d
docker compose logs kafka-init             # danh sách topic đã tạo
```

## 4. Kafka topic

`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`: chỉ topic do `kafka-init` tạo mới tồn tại (3 partition, replication 1).

| Topic | Dead-letter (giữ 30 ngày) |
|---|---|
| `user.events`, `friend.events`, `media.events`, `media.results`, `post.events`, `chat.events`, `billing.events` | `<topic>.dlq` cho từng topic |

Thêm topic mới: sửa vòng `for` trong `deploy/compose/core.yml` rồi `docker compose up kafka-init` (idempotent nhờ `--if-not-exists`).

## 5. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `Thiếu REDIS_PASSWORD` khi chạy compose | Chưa có `.env` | `cp .env.example .env` |
| Service chờ mãi ở `waiting` | `kafka-init` chưa xong | `docker compose logs kafka-init` |
