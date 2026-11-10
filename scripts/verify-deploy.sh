#!/usr/bin/env bash
set -euo pipefail

BACKEND_PORT="${APP_BACKEND_PORT:-18080}"
FRONTEND_PORT="${APP_FRONTEND_PORT:-18081}"

curl -fsS "http://127.0.0.1:${BACKEND_PORT}/api/system/liveness" >/dev/null
curl -fsS "http://127.0.0.1:${BACKEND_PORT}/api/system/status" >/dev/null
curl -fsS "http://127.0.0.1:${FRONTEND_PORT}/" | grep -q 'Burger Order Lab'
curl -fsS "http://127.0.0.1:${FRONTEND_PORT}/api/stores" >/dev/null
echo "deployment verification passed: frontend=${FRONTEND_PORT} backend=${BACKEND_PORT}"
