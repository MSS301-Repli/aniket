# Thanh toán VietQR qua payOS (billing-service)

> **payOS KHÔNG có sandbox: mọi giao dịch, kể cả lúc thử, là tiền thật** (https://payos.vn/docs/moi-truong-test).
> Mã được kiểm thử bằng HTTP giả; **chưa chạy với tài khoản payOS thật**. Phần chữ ký đã đối chiếu với bộ dữ liệu mẫu chính thức của payOS.

Cùng khuôn với [PAYPAL.md](PAYPAL.md): trang web `/pay/**` cho người dùng chọn PayPal (USD) hoặc VietQR (VND). Quyền lợi chỉ cấp khi payOS
xác nhận đã trả đủ tiền (fail closed).

## 1. Luồng

```
Trang /pay/<mã> ─ bấm "Chuyển khoản VietQR" ─► POST /pay/<mã>/payos ─► tạo link payOS (orderCode riêng) ─► 302 sang pay.payos.vn
Người dùng quét mã VietQR bằng app ngân hàng và chuyển khoản
payOS ─► trình duyệt về GET /pay/<mã>/payos/return    ─┐ cả hai đều: hỏi payOS trạng thái THẬT (GET /v2/payment-requests/{orderCode}),
payOS ─► POST /webhooks/payos (ký HMAC)               ─┘ nếu PAID và đủ số tiền → cấp quyền (idempotent)
Hủy trên payOS: GET /pay/<mã>/payos/cancel ─► quay lại trang chọn phương thức (thanh toán lại được)
```

- **orderCode** = `PAYOS_ORDER_CODE_BASE + id phiên * 100 + lần thứ n` (n ≤ 99). Từ orderCode luôn suy ra phiên, nên webhook của một link
  cũ (người dùng quét mã của lần bấm trước) vẫn tìm được phiên. Số lần thử được ghi **trước** khi gọi payOS nên link tạo dở không bị trùng orderCode.
- **Mô tả chuyển khoản** là `AL<id phiên>`, tối đa 9 ký tự (giới hạn của payOS với tài khoản ngân hàng không liên kết qua payOS).
- Số tiền VND chốt lần đầu bấm payOS và dùng lại cho các link sau.

## 2. An toàn (fail closed)

- Trạng thái thật lấy từ payOS bằng GET; cấp quyền chỉ khi `status=PAID`, đúng `orderCode`, `amount` bằng số đã chốt và `amountPaid ≥ amount`.
  `PENDING`/`PROCESSING` → "đang chờ" (không cấp). `CANCELLED` hoặc giá trị lạ → không cấp, cho thử lại.
- Tham số trên `returnUrl` (`code`, `id`, `status`, `orderCode`...) **không được ký**: chỉ `orderCode` được dùng làm gợi ý và chỉ khi nó thuộc đúng phiên này.
- Webhook: HMAC-SHA256 trên `data` (key sắp alphabet, `k=v` nối bằng `&`, null→rỗng) bằng checksum key; so sánh thời gian không đổi. Sai → 401.
  Chữ ký đúng cũng **không** quyết định quyền lợi: vẫn hỏi lại payOS. OrderCode lạ (kể cả dữ liệu mẫu của confirm-webhook) → `200 ignored`.
  payOS/mạng lỗi tạm thời → 503 để payOS gửi lại.
- Khoản trả không khớp (sai số tiền, thiếu tiền) → **không cấp**, ghi `ERROR` kèm orderCode để xử lý thủ công.
- Phiên đã hoàn tất mà một link khác cũng được trả tiền (trả hai lần, ví dụ PayPal rồi VietQR) → ghi `ERROR` "THANH TOÁN TRÙNG" để hoàn tiền thủ công.
- Địa chỉ thanh toán payOS phải là HTTPS thì mới chuyển hướng. CSP của Traefik cho `/pay/` cho phép form chuyển sang `https://pay.payos.vn`.

## 3. Cấu hình

| Biến | Ý nghĩa |
|---|---|
| `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` | khoá của kênh thanh toán (payos.vn). **Để trống = tắt.** Checksum key không bao giờ được gửi đi |
| `PAYOS_ORDER_CODE_BASE` | mặc định `100000000000`; đặt **khác nhau** cho dev và production nếu dùng chung một kênh (payOS cấm trùng orderCode) |
| `PAYPAL_RETURN_BASE_URL` | dùng chung: địa chỉ công khai để payOS đưa người dùng về (`http://api.<PUBLIC_DOMAIN>:<HTTP_PORT>`; production HTTPS) |

### Đăng ký webhook (một lần cho mỗi URL)
payOS gọi API `confirm-webhook` để kiểm tra URL: nó gửi một dữ liệu mẫu có ký tới URL của bạn và cần mã 2xx. URL phải tới được billing, ví dụ
Hookdeck chuyển về `http://api.<PUBLIC_DOMAIN>:<HTTP_PORT>/webhooks/payos`. Sau đó:

```
POST https://api-merchant.payos.vn/confirm-webhook
x-client-id: <PAYOS_CLIENT_ID>    x-api-key: <PAYOS_API_KEY>
{"webhookUrl": "https://<url công khai>/webhooks/payos"}
```

(hoặc dán URL trong trang quản trị kênh thanh toán). Billing phải đang chạy với đủ 3 khoá thì mới trả 2xx.

## 4. Dữ liệu
Migration `V4__payos.sql`: `products.price_vnd`; `checkout_sessions.payos_amount / payos_attempts / payos_link_id / payos_paid_order_code`;
`checkout_sessions.amount_cents` không còn bắt buộc (sản phẩm có thể chỉ bán qua payOS). **Giá VND trong migration là giá tạm**, hãy sửa trước khi bán thật.
Thuê bao qua payOS cũng là trả trước theo kỳ (`period_days`), không tự gia hạn.

## 5. Giới hạn đã biết
- **Đã chạy với payOS thật một lần (2026-11-07, 25.000 ₫, sản phẩm Boost × 1)**: tạo link, chuyển khoản, cấp quyền (ví +1 Boost), và webhook thật của payOS
  được billing nhận với `200` (chữ ký HMAC thật được xác minh). Chưa kiểm chứng: mã lỗi khi trùng orderCode; số tiền tối thiểu; danh sách đầy đủ giá trị `status`
  (đã biết PAID, PENDING, PROCESSING, CANCELLED); cấu trúc `transactions` của GET; chính sách gửi lại webhook; giới hạn tên mặt hàng; nhánh hủy; webhook mẫu của `confirm-webhook`.
- **Không có hoàn tiền tự động**: payOS là chuyển khoản ngân hàng, hoàn tiền làm thủ công và không thu hồi quyền lợi (khác PayPal, chưa có webhook hoàn tiền).
- Không có `expiredAt` cho link: link payOS còn dùng được sau khi phiên của ta hết hạn; trả tiền muộn vẫn được chốt qua webhook (không bị chặn bởi hạn).
- Không có job đối soát; nếu cả return lẫn webhook đều không tới, người dùng đã chuyển khoản sẽ không được cấp quyền cho đến khi bấm lại "Kiểm tra lại" trên trang cũ hoặc payOS gửi lại webhook.
- App Flutter chưa nối; trang web dùng chung với PayPal.

## 6. Danh sách kiểm tra (tiền thật, làm với số tiền nhỏ nhất bạn chấp nhận)
- [ ] Điền 3 khoá vào `.env`, dựng lại billing: log có cảnh báo "payOS đã bật", không có lỗi 401 khi gọi payOS.
- [ ] Đăng ký webhook (mục 3): `confirm-webhook` trả `code: 00`; billing không báo lỗi chữ ký với dữ liệu mẫu.
- [ ] Hạ giá một sản phẩm xuống mức nhỏ nhất payOS cho phép (ví dụ `UPDATE products SET price_vnd = ... WHERE sku = ...`), tạo phiên, bấm VietQR.
- [ ] Trang payOS hiện mã VietQR và đúng số tiền; chuyển khoản; trang quay về báo "Thanh toán thành công", `GET /billing/me` đúng.
- [ ] Webhook tới: sự kiện nhận `200`; tải lại trang quay về không cấp lần hai.
- [ ] Hủy trên payOS → quay lại trang chọn phương thức; bấm lại tạo được link mới.
- [ ] Đóng tab sau khi chuyển khoản: webhook vẫn cấp quyền.
- [ ] Sau khi thử xong **đặt lại giá thật** và hoàn tiền thủ công khoản đã thử.
