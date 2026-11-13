#!/usr/bin/env sh
# Sinh .env cho máy chủ demo với mật khẩu/khoá ngẫu nhiên. KHÔNG ghi đè file đã có (tránh mất credential của DB đang chạy).
# Dùng: sh deploy/demo/generate-env.sh <public-domain>      (ENV_FILE=... để đổi đường dẫn, mặc định /opt/animal-locket/.env)
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <public-domain>" >&2
  exit 2
fi

public_domain=$1
case "$public_domain" in
  *[!A-Za-z0-9.-]* | "" | -* ) echo "Invalid public domain: $public_domain" >&2; exit 2 ;;
esac
env_file=${ENV_FILE:-/opt/animal-locket/.env}
umask 077

if [ -e "$env_file" ]; then
  echo "Refusing to overwrite $env_file; reuse existing credentials when redeploying." >&2
  exit 1
fi

# 48 ký tự hex: đủ ≥ 32 ký tự mà profile prod yêu cầu cho INTERNAL_TOKEN, và an toàn trong chuỗi JDBC/URI.
random_hex() {
  openssl rand -hex 24
}

mysql_password=$(random_hex)
postgres_password=$(random_hex)

cat >"$env_file" <<EOF
COMPOSE_PROJECT_NAME=animal-locket
COMPOSE_PROFILES=core,app

PUBLIC_DOMAIN=$public_domain
HTTP_PORT=80
HTTPS_PORT=443
KEYCLOAK_PUBLIC_URL=https://auth.$public_domain

MYSQL_HOST=mysql
MYSQL_PORT=3306
MYSQL_USER=animal
MYSQL_PASSWORD=$mysql_password
MYSQL_ROOT_PASSWORD=$(random_hex)

BILLING_DB_URL=jdbc:postgresql://postgres:5432/billing_db
BILLING_DB_USER=animal
BILLING_DB_PASSWORD=$postgres_password
KEYCLOAK_DB_URL=jdbc:postgresql://postgres:5432/keycloak_db
KEYCLOAK_DB_USER=animal
KEYCLOAK_DB_PASSWORD=$postgres_password
POSTGRES_PASSWORD=$postgres_password

MONGO_URI=mongodb://mongo:27017/chat_db
REDIS_PASSWORD=$(random_hex)
JPA_DDL_AUTO=none

# Cloudflare R2: điền tay token chỉ có quyền Object Read & Write trên bucket demo.
R2_ENDPOINT=https://CHANGE_ME_account_id.r2.cloudflarestorage.com
R2_ACCESS_KEY=CHANGE_ME_r2_access_key_id
R2_SECRET_KEY=CHANGE_ME_r2_secret_access_key
R2_BUCKET=animal-photos

KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=$(random_hex)
KEYCLOAK_ADMIN_CLIENT_SECRET=$(random_hex)

INTERNAL_TOKEN=$(random_hex)
DISCOVERY_SECRET=$(random_hex)
CONFIG_USER=config
CONFIG_PASSWORD=$(random_hex)
EUREKA_USER=eureka
EUREKA_PASSWORD=$(random_hex)
CORS_ALLOWED_ORIGINS=

TRACE_SAMPLING=0
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=$(random_hex)
FCM_CREDENTIALS_FILE=
DEV_BUDDY_PUBLIC_ID=

BILLING_DEV_STORE=false
PAYPAL_MODE=sandbox
PAYPAL_CLIENT_ID=
PAYPAL_CLIENT_SECRET=
PAYPAL_WEBHOOK_ID=
PAYPAL_RETURN_BASE_URL=https://api.$public_domain
PAYPAL_APP_RETURN_URL=animallocket://checkout
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
PAYOS_ORDER_CODE_BASE=200000000000

GLYPH_WRITE_ENABLED=true
GLYPH_MAX_ACTIVE=100
GLYPH_MAX_CREATES_PER_DAY=20

TRAEFIK_IMAGE=traefik:v3
KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.0
EOF

chmod 600 "$env_file"
echo "Created $env_file for $public_domain (fill in R2_* before deploying)"
