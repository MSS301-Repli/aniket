#!/usr/bin/env bash
# Chạy trên máy chủ demo đã bootstrap (xem docs/deploy-demo.md). Không bao giờ sinh lại .env hay xoá volume.
# Dùng: deploy-release.sh <DEPLOY_ROOT>/releases/<release-id> [--rollback]
set -euo pipefail
umask 077
root=${DEPLOY_ROOT:-/opt/animal-locket}
release=${1:?Usage: deploy-release.sh $root/releases/<release-id> [--rollback]}
mode=${2:-deploy}
[[ "$mode" == deploy || "$mode" == --rollback ]] || exit 2
tag=$(basename "$release")
[[ "$tag" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || { echo 'Invalid release id' >&2; exit 2; }
[[ "$release" == "$root/releases/$tag" && ! -L "$release" ]] || { echo "Release must be $root/releases/<id>" >&2; exit 2; }
[[ -f "$root/.env" && -f "$release/docker-compose.yml" ]] || {
  echo 'Bootstrap the server and generate its .env using the runbook first.' >&2; exit 1;
}
exec 9>"$root/.deploy.lock"
flock -n 9 || { echo 'Another deployment is running' >&2; exit 1; }
export COMPOSE_PROFILES=core,app
export DEPLOY_TAG="$tag"
export DEPLOY_ROOT="$root"
compose() {
  docker compose --project-name animal-locket --project-directory "$release" \
    --env-file "$root/.env" -f "$release/docker-compose.yml" \
    -f "$release/deploy/demo/docker-compose.demo.yml" \
    -f "$release/deploy/demo/docker-compose.ci.yml" "$@"
}
compose config --quiet
services=(eureka-server config-server api-gateway user-service media-service post-service
          notification-service discovery-service chat-service billing-service)
if [[ "$mode" == --rollback ]]; then
  # Rollback dùng lại image đã build của release cũ, không build lại.
  for service in "${services[@]}"; do
    docker image inspect "animal-locket/$service:$tag" >/dev/null
  done
else
  for service in "${services[@]}"; do
    compose build "$service"
  done
fi

# Build lỗi ở trên thì container đang chạy không bị đụng tới. Migration DB bên dưới KHÔNG tự rollback:
# lỗi thì dừng lại kiểm tra trước khi rollback.
if ! compose up -d --no-build --wait --wait-timeout 600; then
  compose ps -a
  echo 'Release failed health checks. Inspect logs; current-release was not advanced.' >&2
  exit 1
fi
bash "$release/deploy/demo/smoke-check.sh" "$root/.env"
previous=
if [[ -L "$root/current-release" ]]; then
  previous=$(readlink -f "$root/current-release")
fi
if [[ -n "$previous" && "$previous" != "$release" ]]; then
  ln -sfn "$previous" "$root/previous-release"
fi
ln -sfn "$release" "$root/current-release"
echo "Deployed release $tag"
