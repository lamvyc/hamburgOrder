#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
set -a
# shellcheck disable=SC1091
source .env
set +a

BACKUP="${1:-$(ls -1t "$ROOT"/backups/*.sql.gz 2>/dev/null | head -1 || true)}"
if [[ -z "${BACKUP:-}" || ! -f "$BACKUP" ]]; then
  echo "backup file not found; run scripts/backup-db.sh first" >&2
  exit 1
fi
SCRATCH="burger_order_restore_check"

cleanup() {
  docker compose --env-file .env -f deploy/compose.yaml exec -T \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    mysql -uroot -e "DROP DATABASE IF EXISTS \`$SCRATCH\`" >/dev/null 2>&1 || true
}
trap cleanup EXIT

gunzip -t "$BACKUP"

docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
  mysql -uroot -e "DROP DATABASE IF EXISTS \`$SCRATCH\`; CREATE DATABASE \`$SCRATCH\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" >/dev/null

gunzip -c "$BACKUP" | docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
  mysql -uroot "$SCRATCH"

counts() {
  local db="$1"
  docker compose --env-file .env -f deploy/compose.yaml exec -T \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    mysql -uroot "$db" -N -e "
      SELECT 'stores',COUNT(*) FROM stores UNION ALL
      SELECT 'products',COUNT(*) FROM products UNION ALL
      SELECT 'users',COUNT(*) FROM users UNION ALL
      SELECT 'orders',COUNT(*) FROM orders UNION ALL
      SELECT 'order_items',COUNT(*) FROM order_items UNION ALL
      SELECT 'stock',COUNT(*) FROM stock UNION ALL
      SELECT 'stock_logs',COUNT(*) FROM stock_logs UNION ALL
      SELECT 'idempotency_records',COUNT(*) FROM idempotency_records UNION ALL
      SELECT 'coupon_templates',COUNT(*) FROM coupon_templates UNION ALL
      SELECT 'user_coupons',COUNT(*) FROM user_coupons UNION ALL
      SELECT 'admin_audit_logs',COUNT(*) FROM admin_audit_logs UNION ALL
      SELECT 'admin_stock_batches',COUNT(*) FROM admin_stock_batches UNION ALL
      SELECT 'admin_stock_batch_lines',COUNT(*) FROM admin_stock_batch_lines
    " | sort
}

SRC="$(counts "$MYSQL_DATABASE")"
DST="$(counts "$SCRATCH")"

if [[ "$SRC" != "$DST" ]]; then
  echo "source counts:" >&2
  echo "$SRC" >&2
  echo "restored counts:" >&2
  echo "$DST" >&2
  echo "restore verification failed" >&2
  exit 1
fi

VERSION="$(docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
  mysql -uroot "$SCRATCH" -N -e \
  "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1")"

echo "restore drill passed"
echo "backup=$BACKUP"
echo "scratch=$SCRATCH"
echo "flywayVersion=$VERSION"
echo "$SRC"
