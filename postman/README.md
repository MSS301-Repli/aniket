# Postman smoke test

| File | Nội dung |
|---|---|
| `Animal-Locket-Smoke.postman_collection.json` | Smoke test qua Traefik → API gateway → 7 service |
| `Animal-Locket-Local.postman_environment.json` | Biến cho stack local (`docker compose`, `PUBLIC_DOMAIN=localhost`, `HTTP_PORT=8080`) |

Không có IP/domain cố định trong collection: mọi URL lấy từ biến. Môi trường khác (demo) thì ghi đè biến bằng `--env-var`
hoặc nhân bản file environment, **không commit** domain/IP thật.

| Biến | Ý nghĩa |
|---|---|
| `api_base_url` | `http(s)://api.<PUBLIC_DOMAIN>[:cổng]` |
| `auth_base_url` | URL Keycloak, phải trùng `KEYCLOAK_PUBLIC_URL` (là `iss` của token) |
| `auth_http_url` | Bản `http://` của Keycloak, dùng cho kiểm tra redirect HTTP → HTTPS |
| `realm`, `client_id` | `animal-locket`, `postman` (client public, PKCE, có sẵn trong realm) |
| `access_token` | JWT người dùng (kiểu `secret`, để trống trong file, không commit) |

## Thứ tự folder

1. `00 - Public edge`: OIDC discovery, gateway trả 401 khi thiếu token. Kiểm tra redirect HTTPS và chặn `/admin` chỉ chạy khi
   `auth_base_url` là `https://` (dev HTTP sẽ hiện *skipped*).
2. `01 - User bootstrap`: đăng ký hồ sơ (`POST /api/v1/users/register`, chỉ lần đầu với mỗi tài khoản), profile, animals, friends.
3. `02 - Business service smoke`: post, media, discovery, chat, billing.

Notification, Config Server, Eureka, Kafka, Redis không có API public: kiểm tra bằng `docker compose ps` (healthcheck).

## Lấy token

Realm chỉ cho Authorization Code + PKCE (không bật password grant). Trong Postman: tab *Authorization* của collection →
OAuth 2.0, Grant type *Authorization Code (With PKCE)*, Auth URL `{{auth_base_url}}/realms/{{realm}}/protocol/openid-connect/auth`,
Token URL `{{auth_base_url}}/realms/{{realm}}/protocol/openid-connect/token`, Client ID `{{client_id}}`, Callback
`https://oauth.pstmn.io/v1/callback` (tick *Authorize using browser*). Đăng ký/đăng nhập một tài khoản test, rồi chép access token vào `access_token`.

## Chạy bằng Newman

```bash
# Chỉ folder không cần token
npx newman run postman/Animal-Locket-Smoke.postman_collection.json \
  -e postman/Animal-Locket-Local.postman_environment.json --folder "00 - Public edge"

# Toàn bộ, token truyền qua biến môi trường shell (không ghi vào file)
npx newman run postman/Animal-Locket-Smoke.postman_collection.json \
  -e postman/Animal-Locket-Local.postman_environment.json \
  --env-var "access_token=$ACCESS_TOKEN"

# Môi trường khác: ghi đè URL
npx newman run postman/Animal-Locket-Smoke.postman_collection.json \
  -e postman/Animal-Locket-Local.postman_environment.json \
  --env-var "api_base_url=https://api.<domain>" --env-var "auth_base_url=https://auth.<domain>" \
  --env-var "auth_http_url=http://auth.<domain>" --env-var "access_token=$ACCESS_TOKEN"
```

Token hết hạn sau 10 phút (`accessTokenLifespan`). `curl`/Newman không phân giải `*.localhost`? Thêm `127.0.0.1 api.localhost auth.localhost` vào hosts.

## An toàn

- Không commit `access_token`, mật khẩu Keycloak, domain/IP thật.
- Không dùng client `mobile` cho callback Postman; không tắt PKCE hay bật direct password grant.
