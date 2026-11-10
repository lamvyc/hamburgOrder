#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
set -a
# shellcheck disable=SC1091
source .env
set +a

echo "== container health =="
docker compose --env-file .env -f deploy/compose.yaml --profile app ps --format '{{.Name}} {{.Status}}'

echo "== deployment endpoints =="
./scripts/verify-deploy.sh

echo "== security =="
./scripts/security-check.sh

echo "== latest backup =="
LATEST="$(ls -1t backups/*.sql.gz 2>/dev/null | head -1 || true)"
if [[ -z "$LATEST" ]]; then
  echo "no backup found" >&2
  exit 1
fi
ls -l "$LATEST"
cat "$LATEST.meta"
echo

echo "== backend tests =="
grep -H "Tests run" backend/target/surefire-reports/*.txt \
  | awk -F'Tests run: |,' '{sum+=$2} END {print "backend tests:", sum}'
echo "== frontend tests =="
(cd frontend && pnpm test --reporter=dot 2>/dev/null | grep -E 'Tests +[0-9]+ passed' | tail -1)

echo "== images =="
docker images --format '{{.Repository}}:{{.Tag}} {{.ID}} {{.Size}}' \
  | grep 'burger-order-lab-\(backend\|frontend\)' || true

echo "release check completed"
