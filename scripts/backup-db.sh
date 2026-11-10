#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "missing .env" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

BACKUP_DIR="${BACKUP_DIR:-$ROOT/backups}"
mkdir -p "$BACKUP_DIR"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="$BACKUP_DIR/${MYSQL_DATABASE}-${TS}.sql.gz"
TMP="$FILE.tmp"

cleanup() {
  rm -f "$TMP"
}
trap cleanup EXIT

docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
  mysqldump -uroot \
    --single-transaction \
    --routines \
    --triggers \
    --no-tablespaces \
    --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 \
    "$MYSQL_DATABASE" | gzip -9 > "$TMP"

mv "$TMP" "$FILE"
chmod 600 "$FILE"
SHA="$(shasum -a 256 "$FILE" | awk '{print $1}')"
BYTES="$(wc -c < "$FILE" | tr -d ' ')"
cat > "$FILE.meta" <<META
{"createdAt":"$TS","database":"$MYSQL_DATABASE","file":"$(basename "$FILE")","bytes":$BYTES,"sha256":"$SHA"}
META
echo "backup=$FILE"
echo "bytes=$BYTES"
echo "sha256=$SHA"
