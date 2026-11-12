# Thanh toán web qua PayPal (billing-service)

> **CHƯA CHẠY VỚI PAYPAL THẬT.** Mã được kiểm thử bằng HTTP giả (`MockRestServiceServer`) và `PayPalClient` giả. Định dạng JSON
> thật của PayPal có thể khác chi tiết (tên trường, cách trả liên kết duyệt). Trước khi dùng, chạy hết danh sách ở mục 6 với
> **sandbox**, rồi mới cân nhắc `live`.

Dùng cho đồ án và bản web. App iOS/Android phát hành lên store vẫn phải dùng thanh toán trong ứng dụng cho hàng hoá số
(xem [STORES.md](STORES.md)); dẫn người dùng sang trang này có thể bị store từ chối khi duyệt.

## 1. Luồng

```
App (có JWT) ─ POST /api/v1/billing/checkout-sessions {sku} ─► billing-service
      ◄─ 201 { url: <PAYPAL_RETURN_BASE_URL>/pay/<mã>, expiresAt }      (mã dùng một lần, lưu băm SHA-256, sống 30 phút)
App mở url bằng trình duyệt ─► GET /pay/<mã>            trang tóm tắt đơn (Thymeleaf)
      ─► POST /pay/<mã>/start ─► tạo đơn PayPal ─► 302 sang paypal.com để người mua duyệt
      ─► PayPal đưa về GET /pay/<mã>/return ─► CAPTURE + kiểm tra ─► billing.grant(...) ─► trang kết quả
Trang kết quả có nút mở lại app (deep link PAYPAL_APP_RETURN_URL?status=success). App vẫn nên gọi GET /billing/me.
Người mua đóng PayPal: GET /pay/<mã>/cancel ─► quay lại trang tóm tắt, thanh toán lại được đến khi hết hạn.
```

`/pay/**` công khai về JWT (gateway `permitAll`, `animal.security.public-paths`). Mã trên URL là thứ duy nhất xác định phiên;
`publicId` luôn lấy từ phiên (do app đã đăng nhập tạo), không lấy từ URL hay từ PayPal.

## 1b. Webhook `POST /webhooks/paypal`

Thanh toán thành công **đã chốt bằng redirect**; webhook chỉ bù những gì trình duyệt không biết. Chữ ký được PayPal xác nhận
(`verify-webhook-signature` với `PAYPAL_WEBHOOK_ID`) trước khi làm bất cứ điều gì: sai/thiếu → 401, PayPal không trả lời → 503 (PayPal gửi lại).

| Sự kiện | Việc làm |
|---|---|
| `PAYMENT.CAPTURE.COMPLETED` | Chốt nốt phiên (capture idempotent + cấp quyền) dù link đã hết hạn: người dùng đóng tab, server sập giữa capture và cấp quyền, hoặc khoản `PENDING` vừa hoàn tất. Trạng thái thật luôn hỏi lại PayPal. |
| `PAYMENT.CAPTURE.REFUNDED` | Hoàn **toàn phần** (số hoàn ≥ số đã thu) → thu hồi quyền lợi qua `BillingService.onRefund`. Hoàn một phần → giữ quyền lợi. |
| `PAYMENT.CAPTURE.REVERSED` | Chargeback → thu hồi quyền lợi. |
| khác | `200 ignored`. |

Khử trùng lặp theo id sự kiện (`webhook_events`) cùng transaction với việc thu hồi. Muốn nhận webhook ở máy dev cần URL công khai tới
`/webhooks/paypal` (ví dụ Hookdeck chuyển về `localhost:8080`); trong app PayPal đặt URL đó, rồi chép **Webhook ID** vào `PAYPAL_WEBHOOK_ID`.

## 2. Nguyên tắc an toàn (fail closed)

- Quyền lợi chỉ cấp khi PayPal báo đơn **và** khoản thu `COMPLETED`, đúng `order id` đã gắn với phiên, đúng **số tiền và tiền tệ**
  chốt lúc tạo phiên, và `custom_id` (nếu PayPal trả) khớp. Không tin tham số nào trên URL quay về (kể cả `token`/`PayerID`).
- Khoản thu `PENDING` → `PAYMENT_HELD`, **không cấp**; bấm "Kiểm tra lại" để thử lại khi PayPal hoàn tất.
- Capture và cấp quyền **idempotent**: `PayPal-Request-Id` cố định theo phiên; nếu PayPal báo `ORDER_ALREADY_CAPTURED` thì đọc lại đơn;
  `(store, transactionId)` duy nhất ở bảng `purchases` nên F5 hoặc hai tab cùng lúc không cấp hai lần.
- Capture lỗi tạm thời → trang "thử lại"; không thu hai lần. Capture khớp sai (số tiền, đơn...) → **không cấp**, ghi `ERROR` kèm
  order/capture id để hoàn tiền thủ công.
- Liên kết duyệt PayPal phải là HTTPS thì mới chuyển hướng. Sản phẩm chưa có giá (`price_usd_cents` null) không bán được qua web.
- Trang `/pay/**` gửi `Cache-Control: no-store` và `Referrer-Policy: no-referrer` vì mã nằm trên URL.
- `PAYPAL_MODE` mặc định `sandbox`; giá trị khác `sandbox`/`live` làm service **không khởi động**. Chế độ `live` ghi cảnh báo khi khởi động.

## 3. Cấu hình

| Biến | Ý nghĩa |
|---|---|
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | khoá app REST (developer.paypal.com → Apps & Credentials). **Để trống = tắt** |
| `PAYPAL_WEBHOOK_ID` | id webhook trong app PayPal; **để trống = `/webhooks/paypal` trả 503** |
| `PAYPAL_MODE` | `sandbox` (mặc định) hoặc `live` |
| `PAYPAL_RETURN_BASE_URL` | địa chỉ công khai để trình duyệt mở `/pay/**` và quay lại từ PayPal. Với Docker Compose, Traefik định tuyến theo Host nên phải là `http://api.<PUBLIC_DOMAIN>:<HTTP_PORT>` (compose tự đặt mặc định như vậy; `localhost` **không** khớp). Production phải HTTPS |
| `PAYPAL_APP_RETURN_URL` | deep link mở lại app (mặc định `animallocket://checkout`; không dùng `com.animallocket.app:` vì scheme đó thuộc flutter_appauth) |

Khoá đặt trong `.env` (không commit). Khoá live chỉ nằm trên server triển khai thật.

## 4. Dữ liệu

Migration `V2__web_checkout.sql`: thêm `products.price_usd_cents`, `products.period_days`; bảng `checkout_sessions`.
**Giá trong migration là giá tạm (USD)**, hãy sửa trước khi bán thật. Thuê bao web là **trả trước theo kỳ** (`period_days`), không tự
gia hạn; mua lại sẽ tạo thuê bao mới tính từ lúc mua (chưa cộng dồn ngày còn lại).

## 5. Giới hạn đã biết

- **Chưa chạy với sandbox/live thật.** Chưa kiểm chứng: tên trường và `rel` của liên kết duyệt (`payer-action`/`approve`), vị trí `custom_id`
  trong phản hồi capture.
- **Webhook chưa chạy với PayPal thật.** Chưa kiểm chứng: `verify-webhook-signature` chấp nhận nội dung sự kiện sau khi server đọc và ghi lại JSON;
  vị trí `supplementary_data.related_ids.order_id` trong `PAYMENT.CAPTURE.COMPLETED`; capture id của sự kiện hoàn tiền nằm ở liên kết `rel=up`
  (nếu sai, log `ERROR` "không xác định được capture id" và quyền lợi **không** bị thu hồi). Không có sự kiện `DENIED`/tranh chấp mở: chỉ xử lý 3 loại ở trên.
- Hoàn tiền một phần giữ nguyên quyền lợi (chủ đích); muốn thu hồi thì hoàn toàn phần.
- Không có job đối soát; đơn đã duyệt nhưng người dùng không quay về sẽ **không bị thu tiền** (capture chỉ xảy ra ở bước quay về).
- Chỉ USD. PayPal không dùng cho VND ở đây.
- App Flutter chưa gọi `checkout-sessions` và chưa bắt deep link; mới có phía server.
- VietQR chưa làm.

## 6. Danh sách kiểm tra sandbox

- [ ] Khởi động với khoá sandbox; log không có "Không lấy được access token PayPal".
- [ ] `POST /api/v1/billing/checkout-sessions` trả URL; mở được trang tóm tắt, hiện đúng sản phẩm và số tiền.
- [ ] Bấm thanh toán → sang sandbox PayPal → duyệt bằng tài khoản người mua sandbox → quay về trang "Thanh toán thành công".
- [ ] `GET /api/v1/billing/me` trả `plan=PRO` (hoặc số dư Boost); bảng `purchases` có dòng `store=PAYPAL`.
- [ ] Tải lại trang `/return` nhiều lần: không thêm dòng `purchases`, không thu thêm tiền.
- [ ] Đóng PayPal giữa chừng (cancel) → quay lại trang tóm tắt, thanh toán lại được; không có tiền bị trừ.
- [ ] Để quá 30 phút → trang báo hết hạn (410), không capture.
- [ ] Sai khoá hoặc tắt mạng tới PayPal → trang "thử lại", không cấp gì.
- [ ] Xác nhận cấu trúc phản hồi thật (liên kết duyệt, `custom_id`) khớp với giả định ở mục 5.
- [ ] Webhook: tạo webhook trong app sandbox, đặt `PAYPAL_WEBHOOK_ID`; dùng *Webhooks simulator* gửi `PAYMENT.CAPTURE.COMPLETED` → `200 ignored` (đơn lạ), không phải 401.
- [ ] Hoàn tiền toàn phần một giao dịch sandbox từ dashboard → Pro/số dư bị thu hồi; hoàn một phần → giữ nguyên.
- [ ] Gửi lại cùng sự kiện → `duplicate`; sai `PAYPAL_WEBHOOK_ID` → 401.
