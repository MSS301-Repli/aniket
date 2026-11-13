# notification-service

Dịch vụ gửi push cho Animal Locket (cổng `8107`, không dùng database). Nó nghe event nghiệp vụ trên Kafka, đổi mỗi event thành thông báo **ẩn danh** rồi gửi tới thiết bị của người nhận qua FCM. Kiến trúc chi tiết và sơ đồ nằm ở [architecture/07-notification-service.md](../architecture/07-notification-service.md).

## Trách nhiệm

- Nghe 6 topic: `user.events`, `friend.events`, `post.events`, `chat.events`, `media.events`, `billing.events` (group `notification-service`).
- Biến event thành thông báo (`NotificationPlanner`); tiêu đề và nội dung lấy từ template i18n, cố ý chung chung: không nêu alias, tên thú hay nội dung tin nhắn.
- Gửi push trên thread pool riêng (`pushExecutor`), retry lỗi tạm thời, gỡ token chết (`NotificationDispatcher`).
- Lấy token thiết bị và danh sách bạn từ user-service (`UserServiceDeviceDirectory`).

Service không có endpoint nghiệp vụ. Chỉ có các endpoint Actuator `health`, `info`, `prometheus`.

## Event nghe và thông báo sinh ra

| Event | Người nhận | Loại thông báo | `data` kèm theo |
|---|---|---|---|
| `FRIEND_REQUEST_RECEIVED` | người được mời | `FRIEND_REQUEST` | |
| `FRIEND_ACCEPTED` | cả hai bên | `FRIEND_ACCEPTED` | |
| `POST_PUBLISHED` | mọi bạn của tác giả (không gồm tác giả) | `NEW_POST` | `postId` |
| `POST_LIKED` | tác giả bài | `POST_LIKED` | `postId` |
| `POST_COMMENTED` | tác giả bài | `POST_COMMENTED` | `postId` |
| `MESSAGE_SENT` | người nhận tin | `MESSAGE` | `conversationId` |
| `PHOTO_REJECTED` | chủ ảnh | `PHOTO_REJECTED` | `mediaId` |
| `SUBSCRIPTION_ACTIVATED` | người đăng ký | `SUBSCRIPTION` | |

Event khác (ví dụ `USER_BLOCKED`) không sinh thông báo. Service không phát event nào.

## Gọi sang user-service

`UserServiceDeviceDirectory` gọi qua Eureka (`http://user-service`) kèm header `X-Internal-Token`:

- `GET /internal/devices/{publicId}`: danh sách token FCM của người dùng.
- `GET /internal/friends/{publicId}`: danh sách `publicId` của bạn bè (dùng cho fan-out `POST_PUBLISHED`).
- `DELETE /internal/devices/{token}`: gỡ token FCM đã chết.

## Cấu hình

| Khoá | Mặc định | Ý nghĩa |
|---|---|---|
| `animal.push.fcm.credentials-file` (`FCM_CREDENTIALS_FILE`) | rỗng | Đường dẫn file khoá dịch vụ Firebase. Có giá trị thì dùng `FcmPushSender`, rỗng thì dùng `LoggingPushSender` (chỉ ghi log). File khoá không đưa vào Git. |
| `animal.push.max-attempts` | `3` | Số lần thử mỗi token khi gặp lỗi tạm thời. |
| `animal.push.backoff-ms` | `500` | Chờ `backoff-ms * lần_thử` giữa các lần thử. |
| `animal.notification.default-locale` | `vi` | Ngôn ngữ template (`vi` hoặc `en`). Thiếu khoá ở ngôn ngữ này thì rơi về `vi`. |
| `animal.notification.fanout-batch-size` | `200` | Số thông báo trong mỗi tác vụ gửi lên `pushExecutor`. |
| `animal.user-service-url` | `http://user-service` | Địa chỉ user-service. |
| `animal.internal-token` | `dev-internal-token` | Token gọi các endpoint `/internal`. |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka bootstrap server. |

Template nằm ở `src/main/resources/i18n/notifications_vi.properties` và `notifications_en.properties` (UTF-8), khoá dạng `notification.<LOẠI>.title|body`.

## Xử lý lỗi

- **Lỗi ở tầng listener** (message không đọc được, lập kế hoạch lỗi, `pushExecutor` từ chối vì hàng đợi đầy): được ném ra khỏi `@KafkaListener`. Error handler chung của `libs/common` thử lại 3 lần cách nhau 1 giây rồi chuyển message sang `<topic>.dlq`; message không đọc được vào DLQ ngay.
- **Khử trùng lặp**: `eventId` được lưu trong Caffeine (tối đa 200.000, giữ 1 giờ) và chỉ được đánh dấu sau khi xử lý xong, nên lần retry sau lỗi không bị bỏ qua. Cache nằm trong bộ nhớ nên là best-effort; trùng một push là chấp nhận được.
- **Lỗi gửi từng thiết bị** nằm trong dispatcher và không ném ra listener:
  - `TRANSIENT_ERROR` (hoặc sender ném lỗi): retry tối đa `max-attempts`, hết lượt thì bỏ và tăng `push.dropped`.
  - `INVALID_TOKEN` (`UNREGISTERED`, `INVALID_ARGUMENT`, `SENDER_ID_MISMATCH` của FCM): gỡ token qua user-service, không retry, tăng `push.invalid_token`.
  - Không lấy được thiết bị từ user-service: bỏ thông báo đó và tăng `push.skipped{reason=directory_error}`.
- Fan-out `POST_PUBLISHED` chia batch nên một batch lỗi không kéo theo toàn bộ danh sách bạn; nếu retry sau khi đã có batch được nhận, một số bạn có thể nhận trùng (at-least-once).

## Metric

`push.sent{type}`, `push.dropped{type}`, `push.invalid_token`, `push.skipped{reason}`, xuất qua `/actuator/prometheus`.

## Chạy test

```bash
mvn -B verify -pl services/notification-service
```

Test không cần Kafka, FCM hay user-service thật: `DeviceDirectory` và `PushSender` được thay bằng bản giả, `UserServiceDeviceDirectory` được kiểm tra với `MockRestServiceServer`, và test context chạy với `LoggingPushSender`.
