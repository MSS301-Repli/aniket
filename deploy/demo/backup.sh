#!/usr/bin/env bash
# Sao lưu dữ liệu demo (MySQL, PostgreSQL, MongoDB) ra một thư mục có dấu thời gian. Chạy trên máy chủ demo.
# Dùng: backup.sh [thư-mục-đích]   (mặc định $DEPLOY_ROOT/backups/<UTC timestamp>)
# Khôi phục: xem docs/deploy-demo.md, mục "Sao lưu và khôi phục".
set -euo pipefail
umask 077
root=${DEPLOY_ROOT:-/opt/animal-locket}
dest=${1:-$root/backups/$(date -u +%Y%m%dT%H%M%SZ)}
mkdir -p "$dest"
project=animal-locket

# Lệnh chạy BÊN TRONG container nên mật khẩu lấy từ biến môi trường của container, không qua dòng lệnh máy chủ.
docker exec -i "$project-mysql-1" sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --databases user_db media_db post_db' \
  | gzip >"$dest/mysql.sql.gz"
for db in billing_db keycloak_db; do
  docker exec -i "$project-postgres-1" pg_dump -U animal -Fc "$db" >"$dest/$db.dump"
done
docker exec -i "$project-mongo-1" mongodump --quiet --archive --gzip --db chat_db >"$dest/mongo-chat_db.archive.gz"

for f in "$dest"/*; do
  [[ -s "$f" ]] || { echo "Empty backup file: $f" >&2; exit 1; }
done
(cd "$dest" && sha256sum -- * >SHA256SUMS)
echo "Backup written to $dest"
