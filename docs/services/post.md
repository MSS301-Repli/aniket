# post-service

Dịch vụ quản lý bài viết ảnh (post), lượt thích, bình luận và bảng tin (feed). Chạy cổng 8102, đăng ký Eureka với tên `post-service`, dữ liệu nằm trong MySQL `post_db`.

## Trách nhiệm

- Tạo bài từ một ảnh đã tải lên media-service, kiểm tra quota ảnh theo ngày (free 10, pro 50).
- Giữ trạng thái bài: `PENDING_MEDIA` đến `PUBLISHED` hoặc `REJECTED` theo sự kiện của media-service.
- Trả bài của mình, bài của một người dùng, và feed theo cursor thời gian (chỉ bài của bạn bè).
- Thích, bỏ thích, bình luận, xoá bình luận.
- Dọn dữ liệu khi người dùng bị xoá; làm mới cache bạn bè khi chặn, kết bạn hoặc huỷ kết bạn.

## Endpoint

Tất cả nằm dưới `/api/v1`, yêu cầu JWT (Keycloak); danh tính lấy từ `sub` của token.

| Phương thức | Đường dẫn | Mô tả |
|-------------|-----------|-------|
| POST | `/posts` | Tạo bài, trả 201 |
| GET | `/posts/mine` | Bài của mình |
| GET | `/posts/{id}` | Chi tiết một bài |
| DELETE | `/posts/{id}` | Xoá bài của mình, trả 204 |
| GET | `/posts/by/{publicId}` | Bài của một người dùng, có `before` và `limit` |
| GET | `/feed` | Feed bạn bè, có `before` và `limit` |
| PUT | `/posts/{id}/like` | Thích, idempotent, trả 204 |
| DELETE | `/posts/{id}/like` | Bỏ thích, trả 204 |
| POST | `/posts/{id}/comments` | Bình luận, trả 201 |
| GET | `/posts/{id}/comments` | Danh sách bình luận, có `after` và `limit` |
| DELETE | `/posts/{id}/comments/{commentId}` | Xoá bình luận (chủ bình luận hoặc chủ bài) |

Mọi danh sách bị giới hạn kích thước trang tối đa. Bài không tồn tại, đã bị ẩn hoặc không xem được đều trả 404.

## Quy tắc đáng chú ý

- Thích: ràng buộc duy nhất `(post_id, user_id)`, câu lệnh `insert ignore` nên hai request đồng thời chỉ tạo một dòng. Lần thích trùng được coi là đã thích, không tăng bộ đếm và không phát thêm `POST_LIKED`. Tự thích bài của mình không phát sự kiện.
- Bình luận: chuẩn hoá trước khi kiểm tra. Bỏ ký tự điều khiển Unicode (Cc, Cf) trừ xuống dòng, tab thành khoảng trắng, gộp hơn 2 dòng trống liên tiếp, cắt khoảng trắng hai đầu. Rỗng sau chuẩn hoá trả 400 `COMMENT_EMPTY`; quá 300 ký tự (tính theo code point) trả 400 `COMMENT_TOO_LONG`.
- Bài tạo xong ở `PENDING_MEDIA`. Khi nhận `PHOTO_READY` bài chuyển sang `PUBLISHED` và phát `POST_PUBLISHED`; `PHOTO_REJECTED` chuyển sang `REJECTED`.

## Sự kiện

Phát qua outbox (cùng giao dịch với dữ liệu, relay đẩy lên Kafka):

| Topic | Loại | Khi nào |
|-------|------|---------|
| post | `POST_PUBLISHED` | Ảnh sẵn sàng, bài được đăng |
| post | `POST_LIKED` | Một người thích bài của người khác |
| post | `POST_COMMENTED` | Có bình luận mới |
| post | `POST_DELETED` | Tác giả xoá bài (payload: `postId`, `authorPublicId`) |

Tiêu thụ (nhóm `post-service`, ack theo từng record, khử trùng bằng bảng `processed_events`):

| Topic | Loại | Xử lý |
|-------|------|-------|
| media | `PHOTO_READY`, `PHOTO_REJECTED` | Cập nhật trạng thái media và bài |
| user | `USER_DELETED` | Xoá bài, thích, bình luận của người đó |
| user | `USER_BLOCKED` | Xoá cache danh sách bạn của hai bên |
| friend | `FRIEND_ACCEPTED`, `FRIEND_REMOVED` | Xoá cache danh sách bạn của hai bên để feed tính lại |
| billing | `SUBSCRIPTION_ACTIVATED`, `SUBSCRIPTION_EXPIRED` | Ghi hoặc xoá cờ pro trong Redis |

## Xử lý lỗi

- `PendingPostReconciler` quét định kỳ các bài kẹt ở `PENDING_MEDIA` (mặc định 3 giây) và chốt trạng thái dựa vào media đã nhận, tránh mất sự kiện.
- Gọi user-service qua `UserDirectory` với circuit breaker và time limiter (2 giây). Không xác minh được quan hệ thì ẩn bài (404).
- Sự kiện trùng bị bỏ qua nhờ `processed_events`; lỗi ném ra thì Kafka gửi lại theo ack-mode `record`.

## Cấu hình

| Khoá | Mặc định | Ý nghĩa |
|------|----------|---------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | MySQL cục bộ `post_db` | Kết nối cơ sở dữ liệu |
| `REDIS_HOST`, `REDIS_PORT` | localhost, 6379 | Quota và cờ pro |
| `KAFKA_BOOTSTRAP` | localhost:9092 | Kafka |
| `KEYCLOAK_JWK_URI` | realm `animal-locket` | Kiểm tra JWT |
| `EUREKA_URL`, `CONFIG_SERVER_URL` | localhost | Đăng ký dịch vụ, cấu hình tập trung |
| `animal.limits.photos-per-day.free` / `.pro` | 10 / 50 | Quota ảnh mỗi ngày |
| `animal.post.reconcile-interval-ms` | 3000 | Chu kỳ quét bài kẹt |
| `JPA_DDL_AUTO` | none | Schema do Flyway (`V1__init.sql`) quản lý |

## Chạy kiểm thử

```
mvn -pl services/post-service verify
```

Test dùng H2 chế độ MySQL (`ddl-auto: create-drop`), Flyway tắt; gồm test quota, thích đồng thời, chuẩn hoá bình luận và API bình luận, listener sự kiện người dùng, sự kiện `POST_DELETED` và luồng tích hợp tạo bài đến feed.
