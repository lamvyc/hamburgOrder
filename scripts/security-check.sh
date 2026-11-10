#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
set -a
# shellcheck disable=SC1091
source .env
set +a

FRONTEND_PORT="${APP_FRONTEND_PORT:-18081}"
BACKEND_PORT="${APP_BACKEND_PORT:-18080}"

HEADERS="$(mktemp)"
trap 'rm -f "$HEADERS"' EXIT
curl -fsS -D "$HEADERS" -o /dev/null "http://127.0.0.1:${FRONTEND_PORT}/"

grep -qi '^X-Content-Type-Options: nosniff' "$HEADERS"
grep -qi '^X-Frame-Options: DENY' "$HEADERS"
grep -qi '^Referrer-Policy: strict-origin-when-cross-origin' "$HEADERS"

UNAUTH="$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:${BACKEND_PORT}/api/orders")"
[[ "$UNAUTH" == "401" ]]

CONTAINER_UID="$(docker compose --env-file .env -f deploy/compose.yaml exec -T backend id -u | tr -d '[:space:]')"
[[ "$CONTAINER_UID" != "0" ]]

git check-ignore -q .env
git check-ignore -q backups/

echo "security check passed"
echo "frontend headers: nosniff/DENY/referrer"
echo "unauthenticated /api/orders=$UNAUTH"
echo "backend container uid=$CONTAINER_UID"
echo ".env and backups/ are ignored"
