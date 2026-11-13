#!/usr/bin/env bash
# Kiểm tra nhanh ranh giới công khai sau khi deploy: HTTPS + OIDC discovery, API bắt buộc JWT.
# (Chặn /admin kiểm tra từ NGOÀI máy chủ bằng Postman: chạy trên máy chủ có thể đi qua dải IP nội bộ được phép.)
# Dùng: smoke-check.sh /path/to/.env
set -euo pipefail
env_file=${1:?Usage: smoke-check.sh /path/to/.env}
# Chỉ đọc PUBLIC_DOMAIN. Không source .env, không in credential.
domain=$(sed -n 's/^PUBLIC_DOMAIN=//p' "$env_file" | tr -d '\r')
[[ "$domain" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]+$ ]] || { echo 'Invalid PUBLIC_DOMAIN' >&2; exit 1; }
curl --fail --silent --show-error --retry 12 --retry-all-errors --retry-delay 5 \
  --connect-timeout 10 --max-time 20 \
  "https://auth.$domain/realms/animal-locket/.well-known/openid-configuration" >/dev/null
# Eureka đăng ký có thể chậm hơn healthcheck container. Route bảo vệ phải từ chối request ẩn danh.
status=000
for attempt in {1..24}; do
  status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --connect-timeout 10 --max-time 20 "https://api.$domain/api/v1/users/me" || true)
  [[ "$status" == 401 ]] && { echo 'HTTPS/OIDC/protected API smoke checks passed'; exit 0; }
  echo "Waiting for protected API ($attempt/24, HTTP $status)"
  sleep 5
done
echo "Protected API smoke failed (HTTP $status)" >&2
exit 1
