#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  echo "Missing .env. Run ./scripts/db-up.sh first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:5173}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

section() {
  printf '\n=== %s ===\n' "$1"
}

section "1. Docker Compose status"
docker compose --env-file .env -f deploy/compose.yaml ps

section "2. Liveness (must not depend on MySQL)"
curl -sS --max-time 5 -D "$TMP_DIR/liveness.headers" \
  "$BASE_URL/api/system/liveness" -o "$TMP_DIR/liveness.json"
grep -iE '^(HTTP/|X-Trace-Id:|Cache-Control:)' "$TMP_DIR/liveness.headers" || true
python3 -m json.tool "$TMP_DIR/liveness.json"

section "3. Status (real database probe)"
status_code="$(curl -sS --max-time 8 -D "$TMP_DIR/status.headers" \
  "$BASE_URL/api/system/status" -o "$TMP_DIR/status.json" -w '%{http_code}')"
echo "HTTP ${status_code}"
grep -iE '^(X-Trace-Id:|Cache-Control:)' "$TMP_DIR/status.headers" || true
python3 -m json.tool "$TMP_DIR/status.json"

if [[ "$status_code" != "200" ]]; then
  echo "Status endpoint is not ready. Inspect traceId in the response and the backend log." >&2
  exit 2
fi

section "4. Actuator liveness and readiness"
for probe in liveness readiness; do
  code="$(curl -sS --max-time 5 -o "$TMP_DIR/$probe.json" \
    -w '%{http_code}' "$BASE_URL/actuator/health/$probe")"
  echo "$probe: HTTP $code $(cat "$TMP_DIR/$probe.json")"
done

section "5. Migrated rows in MySQL"
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT meta_key, meta_value, updated_at FROM system_metadata ORDER BY meta_key; SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

section "6. Vite page shell"
frontend_code="$(curl -sS --max-time 5 -o "$TMP_DIR/frontend.html" \
  -w '%{http_code}' "$FRONTEND_URL/")"
echo "HTTP $frontend_code"
grep -o '<title>[^<]*</title>' "$TMP_DIR/frontend.html" || true

echo
echo "Stage 0 verification completed."
