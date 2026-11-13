# discovery-service

Cổng `8104`. Gợi ý người dùng ở gần hoặc đang online, quẹt trái/phải, trạng thái online của bạn bè và Boost.
Không dùng SQL; trạng thái nằm trong Redis (có bản in-memory để test). Package `com.animallocket.discovery`.

## Trách nhiệm

- Lưu vị trí đã làm tròn về ô lưới 500 m, cộng độ lệch cố định theo người dùng (HMAC-SHA256 với secret, tối đa 250 m). Chỉ trả dải khoảng cách thô, không bao giờ trả toạ độ.
- Tách dữ liệu theo nhóm tuổi (`ADULT` và `MINOR`): hai nhóm không thấy nhau. Nhóm MINOR có bán kính nhỏ hơn và không có dải `UNDER_1KM`.
- Dựng bộ thẻ: người trong bán kính (GEO) hoặc đang online trong 60 giây; loại bạn bè, người chặn nhau, người đã quẹt trái (nhớ 30 ngày) và người đã quẹt phải.
- Quẹt phải có hạn mức mỗi ngày (giờ Asia/Ho_Chi_Minh): free và pro. Lượt được hoàn khi gọi user-service lỗi, bị chặn, đã là bạn hoặc đã gửi lời mời.
- Boost 30 phút: trừ một Boost trong ví của billing-service, người dùng được xếp đầu bộ thẻ của người khác.

## API (qua gateway, tiền tố `/api/v1/discovery`)

Mọi endpoint cần JWT; gateway gắn header `X-Public-Id`. Chi tiết trong `docs/06-api-reference.md` mục 6.5.

| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| PUT | `/location` | Body `{lat, lon}`; cần đang bật khám phá (403 nếu tắt). 204 |
| DELETE | `/location` | Xoá vị trí ngay. 204 |
| POST | `/heartbeat` | Đánh dấu online. 204 |
| GET | `/presence?ids=` | Online của bạn bè; tối đa 200 id, mỗi id tối đa 64 ký tự |
| GET | `/cards?limit=` | Mặc định 20, ép về 1..30. Giới hạn số lần tìm mỗi phút (429 `TOO_MANY_SEARCHES`) |
| POST | `/swipes` | Body `{targetPublicId, direction}`; hết lượt thì 429 `SWIPE_LIMIT` |
| POST | `/boost` | Header `Idempotency-Key` tuỳ chọn. 204; hết Boost thì 402 `NO_BOOST` |

Lỗi kiểm tra đầu vào trả 400 `VALIDATION_FAILED`: toạ độ ngoài khoảng, `targetPublicId` rỗng hoặc dài quá 64, id presence dài quá 64,
`Idempotency-Key` không khớp `[A-Za-z0-9_.:-]{1,64}`.

## Boost và khoá idempotency

billing-service chưa có endpoint hoàn tiền và hiện bỏ qua `Idempotency-Key`, nên discovery tự chống trừ hai lần:

1. Không có header thì discovery tạo một UUID. Khoá được gửi tiếp cho billing trong header `Idempotency-Key`.
2. Khoá được chiếm nguyên tử bằng `setIfAbsent` trong KeyValueStore (khoá `boost:idem:<publicId>:<key>`, TTL 24 giờ) với trạng thái `PENDING`, rồi `CONSUMED`, rồi `APPLIED`.
3. Chiếm được khoá thì mới gọi billing; billing lỗi hoặc hết Boost thì xoá khoá để client thử lại. Chiếm thua: `APPLIED` trả thành công, `CONSUMED` chỉ áp Boost (không trừ nữa), `PENDING` nghĩa là request khác đang xử lý nên trả 409 `BOOST_IN_PROGRESS`.
4. Trừ ví thành công nhưng ghi Boost vào store lỗi: thử ghi lại một lần. Vẫn lỗi thì giữ `CONSUMED` và trả 503 `BOOST_PENDING`;
   client gửi lại cùng khoá sẽ áp Boost mà không bị trừ lần hai.
5. Billing trả hết Boost thì 402 `NO_BOOST` và không lưu khoá.

## Sự kiện (Kafka, group `discovery-service`)

Nghe topic `user` và `billing`. Mọi xử lý đều idempotent nên không cần bảng khử trùng lặp.

| Sự kiện | Việc làm |
|---|---|
| `USER_DELETED` | Xoá toàn bộ dữ liệu khám phá của người đó |
| `DISCOVERY_DISABLED` | Xoá vị trí và trạng thái online |
| `SUBSCRIPTION_ACTIVATED` | Ghi cờ pro đến ngày hết hạn (hạn mức quẹt cao hơn) |
| `SUBSCRIPTION_EXPIRED` | Xoá cờ pro |

Tác vụ định kỳ `purge` (mặc định 60 giây) dọn vị trí hết hạn vì GEO của Redis không có TTL theo từng member.

## Cấu hình

| Khoá | Mặc định | Ý nghĩa |
|---|---|---|
| `animal.discovery.secret` | `dev-only-secret-change-me-in-prod` | Bí mật tạo độ lệch vị trí; bắt buộc đặt riêng ở production |
| `animal.discovery.location-ttl` | `30m` | Thời gian giữ vị trí |
| `animal.discovery.radius-adult-m` / `radius-minor-m` | `10000` / `5000` | Bán kính tìm kiếm |
| `animal.discovery.cards-per-minute` | `10` | Số lần lấy bộ thẻ mỗi phút |
| `animal.limits.swipes-per-day.free` / `pro` | `30` / `150` | Hạn mức quẹt phải |
| `animal.kv.type` | Redis | `memory` để chạy không cần Redis |
| `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP`, `KEYCLOAK_JWK_URI`, `DISCOVERY_SECRET` | xem `application.yml` | Biến môi trường |

## Chịu lỗi

- `GET /cards` chạy theo kiểu fail soft: user-service không phản hồi (503) hoặc Redis lỗi thì trả `{cards: [], degraded: true}` thay vì lỗi.
  Lỗi nghiệp vụ như 403 và 429 vẫn giữ nguyên mã.
- Các lời gọi sang user-service và billing-service đi qua circuit breaker Resilience4j; quẹt phải dùng timeout 6 giây vì user-service tạo lời mời khá chậm.
- Gọi billing lỗi thì Boost trả 503 và không cộng Boost; gửi lại cùng `Idempotency-Key` là an toàn.
- `purge` nuốt lỗi và ghi log, lần chạy sau sẽ dọn tiếp.

## Chạy test

```bash
mvn -pl services/discovery-service verify
```

Test chạy với store in-memory, không cần Redis hay Kafka. Test với Redis thật là tuỳ chọn:

```bash
REDIS_TEST_HOST=localhost mvn -pl services/discovery-service verify
```
