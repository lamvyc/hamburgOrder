#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
set -a
# shellcheck disable=SC1091
source .env
set +a

BASE="${BASE_URL:-http://127.0.0.1:8080}"

start_redis() {
  docker compose --env-file .env -f deploy/compose.yaml start redis >/dev/null 2>&1 || true
}
trap start_redis EXIT

docker compose --env-file .env -f deploy/compose.yaml stop redis >/dev/null
sleep 2

HEADERS="$(mktemp)"
BODY="$(mktemp)"
trap 'rm -f "$HEADERS" "$BODY"; start_redis' EXIT
CODE="$(curl -sS -o "$BODY" -D "$HEADERS" -w '%{http_code}' "$BASE/api/stores/1/menu")"
CACHE="$(awk 'BEGIN{IGNORECASE=1} /^X-Cache:/ {gsub("\r",""); print $2}' "$HEADERS" | tail -1)"
if [[ "$CODE" != "200" || "$CACHE" != "BYPASS" ]]; then
  echo "redis fallback failed: status=$CODE cache=$CACHE" >&2
  rm -f "$HEADERS" "$BODY"
  exit 1
fi
echo "redis fallback passed: status=$CODE X-Cache=$CACHE"
rm -f "$HEADERS" "$BODY"
start_redis
for _ in $(seq 1 30); do
  health="$(docker compose --env-file .env -f deploy/compose.yaml ps --format '{{.Health}}' redis 2>/dev/null || true)"
  [[ "$health" = "healthy" ]] && break
  sleep 1
done
echo "redis restarted: $(docker compose --env-file .env -f deploy/compose.yaml ps --format '{{.Status}}' redis)"
