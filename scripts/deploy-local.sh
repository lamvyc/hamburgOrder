#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "missing .env; copy .env.example to .env first" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

BACKEND_PORT="${APP_BACKEND_PORT:-18080}"
FRONTEND_PORT="${APP_FRONTEND_PORT:-18081}"

docker compose --env-file .env -f deploy/compose.yaml --profile app build backend frontend
docker compose --env-file .env -f deploy/compose.yaml --profile app up -d

for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${BACKEND_PORT}/api/system/liveness" >/dev/null 2>&1 \
    && curl -fsS "http://127.0.0.1:${FRONTEND_PORT}/" >/dev/null 2>&1; then
    echo "deployed: frontend=http://127.0.0.1:${FRONTEND_PORT} backend=http://127.0.0.1:${BACKEND_PORT}"
    exit 0
  fi
  sleep 2
done

echo "deployment did not become healthy" >&2
docker compose --env-file .env -f deploy/compose.yaml --profile app ps >&2
exit 1
