#!/usr/bin/env bash
# Kiểm tra tĩnh script + overlay demo (không cần Docker daemon). Chạy từ gốc repo: bash deploy/demo/validate-ci.sh
set -euo pipefail
for script in deploy/demo/*.sh; do bash -n "$script"; done
if command -v shellcheck >/dev/null; then shellcheck deploy/demo/*.sh; fi
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
ENV_FILE="$tmp/demo.env" sh deploy/demo/generate-env.sh ci.example.test
grep -q '^PAYPAL_RETURN_BASE_URL=https://api.ci.example.test$' "$tmp/demo.env"
token=$(sed -n 's/^INTERNAL_TOKEN=//p' "$tmp/demo.env")
[[ ${#token} -ge 32 ]] || { echo 'INTERNAL_TOKEN must be >= 32 chars (Spring profile prod)' >&2; exit 1; }
before=$(sha256sum "$tmp/demo.env")
if ENV_FILE="$tmp/demo.env" sh deploy/demo/generate-env.sh different.example.test 2>/dev/null; then
  echo 'generate-env must refuse to overwrite existing credentials' >&2; exit 1
fi
[[ "$(sha256sum "$tmp/demo.env")" == "$before" ]]
if ENV_FILE="$tmp/bad.env" sh deploy/demo/generate-env.sh 'bad domain;rm' 2>/dev/null; then
  echo 'generate-env must reject an invalid domain' >&2; exit 1
fi
for overlay in demo ci; do
  args=(-f docker-compose.yml -f deploy/demo/docker-compose.demo.yml)
  [[ "$overlay" == ci ]] && args+=(-f deploy/demo/docker-compose.ci.yml)
  DEPLOY_TAG=validation docker compose --env-file "$tmp/demo.env" "${args[@]}" config --quiet
done
echo 'Deployment scripts and Compose configuration validated'
