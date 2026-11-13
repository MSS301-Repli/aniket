# Runbook: triển khai demo (một VM)

Stack demo chạy trên **một máy chủ Linux** bằng Docker Compose: file gốc `docker-compose.yml` + overlay
`deploy/demo/docker-compose.demo.yml` (MySQL/PostgreSQL/MongoDB tự chạy, HTTPS Let's Encrypt, giới hạn RAM, Spring profile `prod`)
+ `deploy/demo/docker-compose.ci.yml` (image dựng từ JAR đã build sẵn bằng `Dockerfile.runtime`). Ảnh vẫn nằm trên Cloudflare R2.

> Không ghi IP, domain, email hay mật khẩu thật vào repo. Ví dụ dưới đây dùng `<domain>`, `<user>@<server>`.

## 1. Yêu cầu

- Ubuntu 22.04/24.04, **≥ 4 GB RAM + 4 GB swap** (10 JVM + Keycloak + Kafka + 3 DB), Docker Engine + Compose plugin ≥ 2.24 (cần `include`).
- Một tên miền có 3 bản ghi A trỏ về IP máy chủ: `api.<domain>`, `auth.<domain>` (và `grafana.<domain>` nếu bật `obs`).
  Không có tên miền thì dùng dịch vụ wildcard DNS kiểu `<ip-có-gạch-ngang>.nip.io`.
- Firewall chỉ mở 22, 80, 443. Cổng 80 bắt buộc cho HTTP-01 của Let's Encrypt.
- Bucket R2 riêng tư + token *Object Read & Write* chỉ cho bucket đó.

## 2. Bootstrap máy chủ (một lần)

```bash
sudo mkdir -p /opt/animal-locket/{releases,backups,deploy/secrets} && sudo chown -R "$USER" /opt/animal-locket
# Sinh .env với mật khẩu ngẫu nhiên (từ chối ghi đè nếu đã có). Chạy từ bản checkout bất kỳ của repo:
sh deploy/demo/generate-env.sh <domain>            # -> /opt/animal-locket/.env, quyền 600
nano /opt/animal-locket/.env                       # điền R2_*, (tuỳ chọn) PAYPAL_*, FCM_CREDENTIALS_FILE
```

`generate-env.sh` sinh `INTERNAL_TOKEN` 48 ký tự: profile `prod` từ chối khởi động nếu token trống, < 32 ký tự hoặc là giá trị dev.
Khoá FCM/App Store/Google Play đặt vào `/opt/animal-locket/deploy/secrets/` (mount `/secrets`).
**Giữ bản sao `.env` ở nơi an toàn**: mất file này là mất mật khẩu DB đang chạy.

## 3. Đóng gói và đưa release lên

Trên máy build (hoặc CI) tại commit cần deploy, sau khi `mvn -B verify` xanh:

```bash
mvn -B -ntp -DskipTests package
id="$(git rev-parse HEAD)-$(date -u +%Y%m%d%H%M%S)"
mkdir -p release && git archive HEAD docker-compose.yml deploy infra/config-repo | tar -x -C release
for m in infra/* services/*; do
  [ -d "$m/target" ] && mkdir -p "release/$m/target" && cp "$m"/target/*-SNAPSHOT.jar "release/$m/target/"
done
tar -czf backend-release.tar.gz -C release . && sha256sum backend-release.tar.gz > backend-release.tar.gz.sha256
ssh <user>@<server> "mkdir -p /opt/animal-locket/releases/$id"
scp backend-release.tar.gz backend-release.tar.gz.sha256 <user>@<server>:/opt/animal-locket/releases/$id/
ssh <user>@<server> "cd /opt/animal-locket/releases/$id && sha256sum -c backend-release.tar.gz.sha256 \
  && tar -xzf backend-release.tar.gz && rm backend-release.tar.gz*"
```

## 4. Deploy

```bash
bash /opt/animal-locket/releases/<id>/deploy/demo/deploy-release.sh /opt/animal-locket/releases/<id>
```

Script: khoá `flock` (không chạy hai deploy song song) → `compose config` → build image `animal-locket/<service>:<id>` từ JAR →
`up -d --wait` (chờ healthcheck tối đa 10 phút) → `smoke-check.sh` → trỏ `current-release` sang release mới và giữ `previous-release`.
Lỗi ở bất kỳ bước nào thì `current-release` không đổi. Lần đầu Keycloak cần ~2 phút và Let's Encrypt cấp chứng chỉ trong vài chục giây.

Kiểm tra thêm từ máy cá nhân bằng Postman/Newman (xem `postman/README.md`, ghi đè `api_base_url`, `auth_base_url`, `auth_http_url`).

## 5. Rollback

```bash
bash /opt/animal-locket/releases/<id-cũ>/deploy/demo/deploy-release.sh /opt/animal-locket/releases/<id-cũ> --rollback
```

Dùng lại image đã build của release cũ (không build lại). **Migration Flyway không tự đảo ngược**: nếu release mới đã đổi schema
không tương thích ngược, khôi phục DB từ bản sao lưu (mục 7) trước khi rollback.

## 6. Vận hành

```bash
cd /opt/animal-locket/current-release
dc() { docker compose -p animal-locket --project-directory . --env-file /opt/animal-locket/.env -f docker-compose.yml \
  -f deploy/demo/docker-compose.demo.yml -f deploy/demo/docker-compose.ci.yml "$@"; }
export DEPLOY_TAG=$(basename "$(readlink -f .)") COMPOSE_PROFILES=core,app
dc ps                       # trạng thái + health
dc logs -f --tail=200 user-service
dc --profile obs up -d      # bật Prometheus/Grafana (tốn thêm ~1 GB RAM)
```

Không chạy `down -v` trên máy demo: lệnh đó xoá volume DB, Kafka, chứng chỉ.

## 7. Sao lưu và khôi phục

Sao lưu (MySQL 3 schema, PostgreSQL `billing_db` + `keycloak_db`, MongoDB `chat_db`, kèm `SHA256SUMS`):

```bash
bash /opt/animal-locket/current-release/deploy/demo/backup.sh      # -> /opt/animal-locket/backups/<UTC>/
# Hằng ngày lúc 03:00 (crontab -e):
# 0 3 * * * bash /opt/animal-locket/current-release/deploy/demo/backup.sh >> /opt/animal-locket/backups/backup.log 2>&1
```

Chép thư mục backup và `.env` ra ngoài máy chủ (ổ khác/kho đối tượng). Ảnh nằm trên R2, không nằm trong backup này.
Redis (hạn mức, vị trí, cache) và Kafka không sao lưu: dữ liệu tạm, tự dựng lại.

Khôi phục (dừng service ghi dữ liệu trước, `dc` như mục 6, `B=/opt/animal-locket/backups/<UTC>`):

```bash
(cd "$B" && sha256sum -c SHA256SUMS)
dc stop api-gateway user-service media-service post-service chat-service billing-service keycloak
gunzip -c "$B/mysql.sql.gz" | dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
for db in billing_db keycloak_db; do
  dc exec -T postgres pg_restore -U animal -d "$db" --clean --if-exists < "$B/$db.dump"
done
dc exec -T mongo mongorestore --drop --archive --gzip < "$B/mongo-chat_db.archive.gz"
dc up -d --no-build --wait
```

Thử khôi phục định kỳ trên một máy khác: backup chưa từng khôi phục thì chưa phải là backup.

## 8. Kiểm tra tĩnh (không cần Docker daemon)

```bash
bash deploy/demo/validate-ci.sh   # bash -n, shellcheck (nếu có), generate-env, compose config của cả 2 overlay
```

## 9. Sự cố thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Service Java thoát ngay, log nói về internal token | Profile `prod` + `INTERNAL_TOKEN` < 32 ký tự | Sinh lại giá trị ≥ 32 ký tự trong `.env` |
| Trình duyệt báo chứng chỉ tự ký | Let's Encrypt chưa cấp được (DNS sai, cổng 80 đóng, quá rate limit) | `dc logs traefik`, kiểm tra DNS/firewall |
| `media-service` lỗi khi upload | `R2_*` còn `CHANGE_ME` | Điền R2 trong `.env` rồi deploy lại |
| Container bị `OOMKilled` | Thiếu RAM | Thêm swap, giảm `DEMO_JAVA_OPTS`, tắt profile `obs` |
