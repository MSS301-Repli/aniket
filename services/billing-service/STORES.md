# Tích hợp App Store và Google Play (billing-service)

> **CẢNH BÁO: CHƯA ĐƯỢC KIỂM CHỨNG VỚI APPLE/GOOGLE THẬT.**
> Toàn bộ mã dưới đây được kiểm thử bằng chuỗi chứng chỉ giả (BouncyCastle), HTTP giả (`MockRestServiceServer`) và
> khoá RSA cục bộ. Chưa có lần chạy nào với tài khoản App Store Connect / Play Console thật. Trước khi bán hàng thật
> **bắt buộc** chạy hết danh sách kiểm tra sandbox ở cuối tài liệu. Định dạng JSON thực tế của hai store có thể khác
> chi tiết so với giả định trong test (tên trường, giá trị enum mới).

> Thanh toán **web** (trang `/pay/**`) nằm ở tài liệu riêng: PayPal ở [PAYPAL.md](PAYPAL.md), VietQR/payOS ở [PAYOS.md](PAYOS.md).

## 1. Nguyên tắc (fail closed)

- Quyền lợi (Pro, vật phẩm, gói con vật) **chỉ** được cấp từ dữ liệu do store trả về và đã xác thực chữ ký. Client chỉ
  gửi `transactionId` (Apple) hoặc `purchaseToken` (Google); server tự hỏi lại store.
- Thiếu cấu hình, store lỗi, ngắt mạch, chữ ký/chuỗi chứng chỉ sai, `bundleId`/`package`/`productId` không khớp → **không cấp gì**.
  `STORE_UNAVAILABLE` (503, client được thử lại) khác `INVALID_RECEIPT` (400, không thử lại).
- Chưa cấu hình Apple/Google thì bean tương ứng **không tồn tại**; `StoreRouter` từ chối và webhook trả 503
  (`WEBHOOK_NOT_CONFIGURED`). Không có giá trị mặc định "tạm chấp nhận".
- `DEV` store (tin biên lai client khai) chỉ bật bằng `BILLING_DEV_STORE=true`, **không bao giờ bật ở production**.

## 2. Luồng dữ liệu

```
Mua trong app ──► client gửi POST /api/v1/billing/purchases/verify {store, sku, token}
                    │  PurchaseFlow (KHÔNG giữ transaction DB khi chờ mạng)
                    ├─► StoreRouter ─► AppleStoreGateway ─► App Store Server API ──► JWS (x5c) ─► AppleJwsVerifier
                    │               └► GoogleStoreGateway ─► Play Developer API (subscriptionsv2 / products)
                    └─► BillingService.grant (1 transaction ngắn: purchase + subscription/ví + outbox)
Client chỉ "finish/acknowledge/consume" giao dịch với store SAU KHI nhận 200 từ bước trên.

Gia hạn / hết hạn / hoàn tiền ──► store gọi webhook (không có JWT người dùng, tự xác thực):
   POST /webhooks/apple   (App Store Server Notifications V2, JWS)
   POST /webhooks/google  (Pub/Sub push + OIDC token)
   └─► handler xác thực ─► (gọi mạng lấy trạng thái thật nếu cần) ─► WebhookProcessor:
       1 transaction = ghi dấu khử trùng lặp + áp dụng thay đổi. Lỗi → rollback cả hai → 5xx → store gửi lại.
```

"Hàng đợi thử lại" chính là cơ chế retry của store: mọi lỗi tạm thời trả 5xx và dấu "đã nhận" bị rollback, nên lần gửi
lại được xử lý lại. Gọi mạng luôn làm **trước** khi mở transaction.

## 3. Apple

### Lấy thông tin
1. App Store Connect → **Users and Access → Integrations → In-App Purchase**: tạo khoá, tải file `.p8` (chỉ tải được một lần),
   ghi **Key ID** và **Issuer ID**.
2. **Bundle ID** của app iOS.
3. Tải **Apple Root CA - G3** (định dạng `.cer`, DER) từ <https://www.apple.com/certificateauthority/> và lưu trên server.
   `AppleJwsVerifier` đọc cả PEM lẫn DER. Chuỗi trong JWS của Apple phải kết thúc ở đúng root này.
4. App Store Connect → App → **App Store Server Notifications**: đặt URL production và sandbox là
   `https://<domain>/webhooks/apple`, phiên bản **V2**.

### Cấu hình (biến môi trường → `animal.billing.apple.*`)

| Biến | Ý nghĩa |
|---|---|
| `APPLE_BUNDLE_ID` | bundle id; payload của Apple phải khớp |
| `APPLE_ISSUER_ID`, `APPLE_KEY_ID` | thông tin khoá API |
| `APPLE_PRIVATE_KEY_FILE` | đường dẫn file `.p8` (PKCS#8) |
| `APPLE_ROOT_CERT_FILES` | các file root, ngăn cách bằng dấu phẩy |
| `APPLE_ENVIRONMENT` | `PRODUCTION` (mặc định) hoặc `SANDBOX` |
| `APPLE_ALLOW_SANDBOX_FALLBACK` | `true` chỉ trong lúc App Review/TestFlight: 404 ở production thì thử sandbox |
| `APPLE_REQUIRE_OID` | mặc định `true`: leaf phải có OID `1.2.840.113635.100.6.11.1` |

Cần đủ **cả năm** (bundle, issuer, key id, file .p8, root) thì `AppleStoreGateway` mới được tạo; chỉ cần root + bundle id
thì webhook hoạt động (xác thực chữ ký không cần khoá .p8). File `.p8` đọc lỗi → gateway từ chối mọi giao dịch (và ghi log lỗi).

### Kiểm tra được thực hiện
- JWS: `alg` đúng `ES256` (từ chối `none/HS256/...`), có `x5c`, chuỗi PKIX kết thúc ở root cấu hình, còn hạn, leaf có OID Apple,
  chữ ký ES256 (định dạng P1363) khớp khoá của leaf. Không kiểm tra OCSP/CRL.
- Giao dịch: `bundleId` đúng, `productId` = sku yêu cầu, `transactionId` = id client gửi, `revocationDate` → `revoked`,
  `expiresDate` → hạn thuê bao, giao dịch `Sandbox` bị từ chối ở production trừ khi bật fallback.
- Webhook: `SUBSCRIBED/DID_RENEW/RENEWAL_EXTENDED` → cập nhật hạn; `EXPIRED/GRACE_PERIOD_EXPIRED/REVOKE` → kết thúc Pro;
  `REFUND` → thu hồi giao dịch (không có bản ghi mua thì vẫn kết thúc thuê bao); loại khác → 200 bỏ qua. Khử trùng lặp theo `notificationUUID`.

## 4. Google Play

### Lấy thông tin
1. Google Cloud: tạo **service account**, tạo khoá JSON. Play Console → **Users and permissions** (hoặc *API access*): mời email
   service account đó với quyền xem dữ liệu tài chính/đơn hàng và **quản lý đơn hàng và đăng ký**. Bật *Google Play Android Developer API*.
2. Play Console → **Monetization setup → Real-time developer notifications**: nhập tên Pub/Sub topic.
3. Pub/Sub: tạo **push subscription** cho topic, endpoint `https://<domain>/webhooks/google`, bật *Enable authentication*,
   chọn một service account làm người ký và đặt **Audience** (ví dụ chính URL webhook).

### Cấu hình (`animal.billing.google.*`)

| Biến | Ý nghĩa |
|---|---|
| `GOOGLE_PACKAGE_NAME` | applicationId của app Android |
| `GOOGLE_SERVICE_ACCOUNT_FILE` | JSON service account gọi Play Developer API |
| `GOOGLE_PUSH_AUDIENCE` | audience đã đặt ở push subscription |
| `GOOGLE_PUSH_SERVICE_ACCOUNT` | email service account ký token push |
| `GOOGLE_SERVER_ACKNOWLEDGE` | mặc định `false` (xem mục 6) |

`GoogleStoreGateway` cần `package-name` + `service-account-file`; webhook cần thêm `push-audience` + `push-service-account`.

### Kiểm tra được thực hiện
- Webhook: `Authorization: Bearer` là OIDC token do Google ký (JWKS `https://www.googleapis.com/oauth2/v3/certs`),
  issuer `https://accounts.google.com`, chưa hết hạn, audience và email đúng cấu hình, `email_verified = true`.
- **Nội dung thông báo không bao giờ quyết định quyền lợi.** Thông báo chỉ cho biết token nào vừa đổi; server hỏi lại Google
  (`subscriptionsv2.get`) và áp dụng trạng thái thật. Thông báo của package khác bị bỏ qua. Khử trùng lặp theo `messageId`.
- Trạng thái: `ACTIVE`, `IN_GRACE_PERIOD`, `CANCELED` (còn hạn) được dùng đến `expiryTime`; `ON_HOLD`, `PAUSED`, `EXPIRED` không được
  dùng dù hạn còn xa; `PENDING` → thử lại sau (503); trạng thái lạ → từ chối. Mua lẻ: `purchaseState` phải là `0`, phải có `orderId`.

## 5. Quyết định thiết kế quan trọng

**`originalTransactionId` của Google** = SHA-256 (hex, 64 ký tự) của **purchaseToken gốc của chuỗi thuê bao**:
khi người dùng đăng ký lại hoặc đổi gói Google cấp token mới có `linkedPurchaseToken` trỏ về token cũ; ta lần theo chuỗi
(tối đa 5 bước; token cũ bị Google xoá thì coi token liên kết cuối cùng là gốc) rồi băm. Verify và webhook dùng **cùng hàm**
(`GoogleStoreGateway.rootKey`) nên cùng khoá cho cùng thuê bao. Băm vì token dài hơn cột `original_transaction_id VARCHAR(100)`.
`transactionId` = `latestOrderId` (đổi mỗi kỳ gia hạn, nên mỗi kỳ có một dòng `purchases` mới giống Apple).

Apple: `originalTransactionId` là id gốc của chuỗi thuê bao do Apple cấp; webhook tra thuê bao theo id này.

Nếu hai tài khoản gửi cùng một chuỗi thuê bao thì tài khoản thứ hai nhận `409 RECEIPT_ALREADY_USED` (đã có trong `BillingService`).

**Bỏ qua bearer của resource server ở `/webhooks/**`** (`WebhookSecurityConfig`): `BearerTokenAuthenticationFilter` của Spring
vẫn cố giải mã mọi header `Authorization: Bearer`, kể cả trên đường dẫn `permitAll`, bằng decoder của Keycloak và trả 401 nếu sai.
Token Pub/Sub không phải JWT Keycloak nên nếu không bỏ qua thì webhook Google luôn 401 ở production. `OidcGooglePushAuth` cố ý
**không** là bean `JwtDecoder` để không xung đột với decoder của resource server.

**Resilience**: `StoreCaller` bọc mọi lời gọi store bằng Retry (3 lần, backoff luỹ thừa từ 500 ms) bên trong CircuitBreaker
(cửa sổ 20, mở khi ≥ 50% lỗi sau 10 lần, mở 30 s). Chỉ lỗi `UNAVAILABLE` được retry và tính vào ngắt mạch; biên lai sai không bao giờ làm ngắt mạch.
(Các khoá `resilience4j.*` trong `application.yml` cho `store-apple`/`store-google` hiện **không** được dùng bởi `StoreCaller`; tham số nằm trong mã.)

## 6. Giới hạn đã biết

- **Chưa kiểm chứng với store thật** (xem đầu tài liệu). Không kiểm tra OCSP/CRL của chứng chỉ Apple.
- Chuỗi chứng chỉ được kiểm tra tại **thời điểm hiện tại**, không phải thời điểm Apple ký (`signedDate`). JWS cũ có thể bị từ chối nếu
  leaf đã hết hạn; chấp nhận được vì ta chỉ xử lý thông báo vừa nhận và giao dịch vừa lấy.
- **Acknowledge của Google**: giao dịch không được acknowledge trong 3 ngày sẽ bị Google tự hoàn tiền. Mặc định client phải gọi
  `acknowledge`/`consume` sau khi nhận 200. `GOOGLE_SERVER_ACKNOWLEDGE=true` cho server acknowledge ngay sau khi xác thực, nhưng việc đó xảy ra
  **trước** khi ghi quyền lợi vào DB (nếu ghi DB lỗi thì người dùng đã được "giao hàng" phía Google nhưng chưa có quyền lợi; họ gọi lại verify là xong, vì idempotent).
  Chưa nối acknowledge vào sau bước ghi DB (cần đổi chữ ký `BillingService.grant`).
- Mua lẻ Google loại *consumable* cần client `consume` thì mới mua lại được; server không làm việc này.
- Hoàn tiền gói con vật (`PACK`) **không** thu hồi con vật đã mở (chủ đích, ghi log).
- Một thuê bao Google có nhiều line item (add-on) chỉ lấy line item thuộc danh mục thuê bao đang bán và có hạn muộn nhất.
- Chưa có trang quản trị để xem/gỡ rối giao dịch; chưa có job đối soát định kỳ với store.

## 7. Danh sách kiểm tra sandbox (làm trước khi mở bán)

Apple (TestFlight/Sandbox, `APPLE_ENVIRONMENT=SANDBOX` hoặc production + `APPLE_ALLOW_SANDBOX_FALLBACK=true` tạm thời):
- [ ] Server khởi động, log không có "Không đọc được khoá API"; `GET /api/v1/billing/products` đúng danh mục.
- [ ] Mua thuê bao sandbox → `POST /purchases/verify` trả `plan=PRO` và `planExpiresAt` hợp lý (sandbox gia hạn nhanh).
- [ ] Gửi lại cùng giao dịch → vẫn 200, không nhân đôi; tài khoản khác gửi cùng giao dịch → 409.
- [ ] Mua lẻ Boost/Invite/gói → số dư/sự kiện đúng; mua lại với transactionId mới.
- [ ] Webhook: dùng *Request a Test Notification* trong App Store Connect → 200 `ignored`; để sandbox tự gia hạn → `DID_RENEW` làm hạn mới; để hết hạn → `EXPIRED`.
- [ ] Hoàn tiền (Customer Support/Sandbox refund) → `REFUND` thu hồi đúng.
- [ ] Chứng minh fail closed: đổi sai `APPLE_ISSUER_ID` → verify trả 503, không cấp gì; tắt mạng tới Apple → 503 rồi ngắt mạch.
- [ ] Xác nhận chuỗi `x5c` thật của Apple có OID `1.2.840.113635.100.6.11.1` ở leaf (nếu không, đặt `APPLE_REQUIRE_OID=false` và ghi lại lý do).

Google (license tester + closed testing track):
- [ ] Service account gọi được `subscriptionsv2.get` (không 401/403); `GOOGLE_PACKAGE_NAME` đúng.
- [ ] Mua thuê bao bằng license tester → verify `plan=PRO`; đổi gói/đăng ký lại → vẫn cùng `original_transaction_id` (kiểm tra cột DB).
- [ ] Pub/Sub test: *Send test notification* → 200 `ignored`; sai audience/email trong cấu hình → 401.
- [ ] Gia hạn/huỷ/hết hạn/tạm giữ (on hold) → trạng thái trong `GET /billing/me` đúng theo Play Console.
- [ ] Hoàn tiền (voided purchase) → `REFUNDED` và hết Pro.
- [ ] Mua lẻ: acknowledge/consume đúng cách ở client; kiểm tra không bị Google tự hoàn sau 3 ngày.
- [ ] Tắt mạng/giả lập 5xx từ Google → webhook trả 503 và Pub/Sub gửi lại thành công khi Google hồi phục.
