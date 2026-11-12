# Outbox & idempotency (`libs/outbox-jpa`)

## Dùng trong service

- Phát event: `outboxService.publish(topic, type, key, payload)` **trong** transaction nghiệp vụ (bắt buộc, `MANDATORY`).
- Relay (`OutboxRelay`) đẩy event lên Kafka mỗi `animal.outbox.relay.interval-ms` (mặc định 500 ms), theo thứ tự tạo.
- Test: đặt `animal.outbox.relay.enabled=false`.

## Schema bắt buộc

Mỗi service dùng outbox phải có bảng sau trong Flyway (MySQL):

```sql
CREATE TABLE outbox_events (
    id VARCHAR(36) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    type VARCHAR(60) NOT NULL,
    payload TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    failed_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_unpublished (published_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

Service đã có `outbox_events` từ trước (V1 của user/media/post) **phải thêm migration mới**, nếu không relay sẽ lỗi
"Unknown column" trên MySQL (test H2 `create-drop` không phát hiện được):

```sql
ALTER TABLE outbox_events
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN failed_at DATETIME(6) NULL,
    ADD COLUMN last_error VARCHAR(500) NULL;
```

## Poison event

| Lỗi khi đẩy | Xử lý |
|---|---|
| Tạm thời: broker sập, timeout, `RetriableException` | Dừng lô, giữ thứ tự, **không** tính attempt |
| Vĩnh viễn: record quá lớn, topic sai, lỗi serialize... | `attempts++`, lưu `last_error`; đạt `animal.outbox.relay.max-attempts` (mặc định 5) thì đặt `failed_at` và đi tiếp |

Event FAILED không bị dọn tự động. Kiểm tra và đẩy lại thủ công sau khi sửa nguyên nhân:

```sql
SELECT id, type, topic, attempts, last_error, failed_at FROM outbox_events WHERE failed_at IS NOT NULL;
UPDATE outbox_events SET failed_at = NULL, attempts = 0 WHERE id = '<id>';  -- relay sẽ đẩy lại
```

Đánh đổi: khi một event bị FAILED, event sau cùng key có thể đến consumer trước nó.

## Idempotency phía consumer

```java
@Transactional
void on(EventEnvelope env) {
    if (!idempotency.firstTime(env.eventId())) return; // đã xử lý
    // ... xử lý nghiệp vụ trong cùng transaction
}
```

- Bảng `processed_events (event_id VARCHAR(36) PK, processed_at DATETIME(6) NOT NULL)`.
- Hai consumer cùng nhận một event: INSERT thứ hai vi phạm khoá chính, Kafka thử lại, lần sau `firstTime` trả `false`.
- Dấu vết cũ hơn `animal.outbox.processed-retention` (mặc định `7d`) được dọn mỗi giờ. Retention phải dài hơn thời gian
  một event có thể bị giao lại (retry, replay DLQ).
