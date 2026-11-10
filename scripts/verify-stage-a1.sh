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

section "1. Store list"
curl -fsS --max-time 8 "$BASE_URL/api/stores" -o "$TMP_DIR/stores.json"
python3 - "$TMP_DIR/stores.json" <<'PY'
import json, sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
items = body["items"]
assert len(items) == 3, items
assert [s["name"] for s in items] == ["中央车站店", "滨江店", "老城店"]
assert [s["orderable"] for s in items] == [True, False, False]
print("3 stores, orderable flags = [True, False, False]")
PY

section "2. Central menu: store scoping, on-sale filter, price string"
curl -fsS --max-time 8 "$BASE_URL/api/stores/1/menu" -o "$TMP_DIR/menu.json"
python3 - "$TMP_DIR/menu.json" <<'PY'
import json, sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
assert body["store"]["id"] == 1
categories = body["categories"]
assert [c["code"] for c in categories] == ["BURGER", "SIDE", "DRINK"]
count = sum(len(c["products"]) for c in categories)
assert count == 6, count
assert categories[0]["products"][0]["price"] == "32.00"
names = [p["name"] for c in categories for p in c["products"]]
assert "海盐奶昔" not in names
print("central menu: 3 categories, 6 on-sale products, off-sale shake excluded")
PY

section "3. Closed store still browsable but not orderable"
curl -fsS --max-time 8 "$BASE_URL/api/stores/3/menu" -o "$TMP_DIR/closed.json"
python3 - "$TMP_DIR/closed.json" <<'PY'
import json, sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
assert body["store"]["businessStatus"] == "CLOSED"
assert body["store"]["orderable"] is False
assert sum(len(c["products"]) for c in body["categories"]) == 4
print("closed store menu: 4 on-sale products, orderable=false")
PY

section "4. 404 / 400 semantics"
for path in /api/stores/999 /api/stores/999/menu; do
  code="$(curl -sS --max-time 8 -o "$TMP_DIR/error.json" -w '%{http_code}' "$BASE_URL$path")"
  test "$code" = "404"
  python3 - "$TMP_DIR/error.json" <<'PY'
import json, sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
assert body["code"] == "STORE_NOT_FOUND", body
assert body["traceId"]
PY
  echo "$path -> HTTP 404 STORE_NOT_FOUND"
done

code="$(curl -sS --max-time 8 -o "$TMP_DIR/bad-id.json" -w '%{http_code}' "$BASE_URL/api/stores/abc")"
test "$code" = "400"
python3 - "$TMP_DIR/bad-id.json" <<'PY'
import json, sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
assert body["code"] == "INVALID_PARAMETER", body
assert body["traceId"]
PY
echo "/api/stores/abc -> HTTP 400 INVALID_PARAMETER"

section "5. Vite proxy"
curl -fsS --max-time 8 "$FRONTEND_URL/api/stores" -o "$TMP_DIR/proxy.json"
python3 - "$TMP_DIR/proxy.json" <<'PY'
import json, sys
assert len(json.load(open(sys.argv[1], encoding="utf-8"))["items"]) == 3
print("Vite proxy /api/stores -> 3 stores")
PY

section "6. Seed counts in MySQL"
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT s.id, s.name, s.business_status, s.accepting_orders,
             COUNT(p.id) AS total_products,
             SUM(p.on_sale) AS on_sale_products
      FROM stores s LEFT JOIN products p ON p.store_id = s.id
      GROUP BY s.id, s.name, s.business_status, s.accepting_orders
      ORDER BY s.id;"

echo
echo "Stage A1 verification completed."
