#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p docs/evidence

BASE="${LOAD_BASE_URL:-http://127.0.0.1:18080}"

LOAD_BASE_URL="$BASE" LOAD_PATH=/api/stores/1/menu LOAD_REQUESTS=200 LOAD_CONCURRENCY=20 \
  node scripts/load-smoke.mjs > docs/evidence/stage-d3-load-menu.json
LOAD_BASE_URL="$BASE" LOAD_PATH=/api/system/status LOAD_REQUESTS=100 LOAD_CONCURRENCY=10 \
  node scripts/load-smoke.mjs > docs/evidence/stage-d3-load-status.json

echo "load baseline written:"
echo "  docs/evidence/stage-d3-load-menu.json"
echo "  docs/evidence/stage-d3-load-status.json"
