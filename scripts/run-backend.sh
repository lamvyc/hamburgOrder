#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  cp .env.example .env
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

export DB_USERNAME="${MYSQL_USER:-burger_app}"
export DB_PASSWORD="${MYSQL_PASSWORD:-burger_dev_password}"
export DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:${MYSQL_PORT:-13306}/${MYSQL_DATABASE:-burger_order}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true}"

if [[ -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
fi

if [[ -n "${MAVEN_REPO_LOCAL:-}" ]]; then
  exec mvn -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" -f backend/pom.xml spring-boot:run
fi

exec mvn -f backend/pom.xml spring-boot:run
