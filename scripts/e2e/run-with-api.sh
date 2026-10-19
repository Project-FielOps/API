#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$API_DIR/docker-compose.e2e.yml"
COMPOSE_PROJECT="${FIELDOPS_E2E_COMPOSE_PROJECT:-fieldops-e2e}"
API_PORT="${FIELDOPS_E2E_API_PORT:-8080}"
DB_PORT="${FIELDOPS_E2E_DB_PORT:-55433}"
HEALTH_URL="http://127.0.0.1:${API_PORT}/actuator/health/readiness"
LOG_DIR="${FIELDOPS_E2E_LOG_DIR:-${API_DIR}/target/e2e}"
API_LOG="$LOG_DIR/api.log"
API_PID=""

if [[ -z "${JAVA_HOME:-}" && -d /usr/lib/jvm/java-21-temurin-jdk ]]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-temurin-jdk
fi
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! java -version 2>&1 | grep -q 'version "21'; then
    echo "Java 21 é obrigatório para o ambiente E2E; configure JAVA_HOME para um JDK 21." >&2
    exit 1
fi

export FIELDOPS_E2E_API_PORT="$API_PORT"
export FIELDOPS_E2E_DB_PORT="$DB_PORT"
export FIELDOPS_E2E_DB_NAME="${FIELDOPS_E2E_DB_NAME:-fieldops_e2e}"
export FIELDOPS_E2E_DB_USER="${FIELDOPS_E2E_DB_USER:-fieldops_e2e}"
export FIELDOPS_E2E_DB_PASSWORD="${FIELDOPS_E2E_DB_PASSWORD:-fieldops_e2e_local_only}"
export FIELDOPS_E2E_SEED_PASSWORD="${FIELDOPS_E2E_SEED_PASSWORD:-FieldOps-E2E-Local-Only-2026!}"
export FIELDOPS_E2E_PASSWORD="${FIELDOPS_E2E_PASSWORD:-$FIELDOPS_E2E_SEED_PASSWORD}"
export FIELDOPS_E2E_JWT_SECRET="${FIELDOPS_E2E_JWT_SECRET:-fieldops-e2e-local-secret-change-me-01234567890123456789012345678901234567890123456789}"
export FIELDOPS_E2E_DB_URL="jdbc:postgresql://127.0.0.1:${DB_PORT}/${FIELDOPS_E2E_DB_NAME}"

usage() {
    echo "Uso: $0 [--reset] -- <comando> [argumentos...]" >&2
    echo "Ex.: $0 --reset -- npm --prefix ../interface-administrativa run e2e" >&2
}

RESET=false
while (($# > 0)); do
    case "$1" in
        --reset) RESET=true; shift ;;
        --) shift; break ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Argumento inesperado: $1" >&2; usage; exit 2 ;;
    esac
done

if (($# == 0)); then
    usage
    exit 2
fi

compose() {
    docker compose --project-name "$COMPOSE_PROJECT" --file "$COMPOSE_FILE" "$@"
}

cleanup() {
    local exit_code=$?
    trap - EXIT INT TERM
    if [[ -n "$API_PID" ]] && kill -0 "$API_PID" 2>/dev/null; then
        kill -- -"$API_PID" 2>/dev/null || kill "$API_PID" 2>/dev/null || true
        wait "$API_PID" 2>/dev/null || true
    fi
    compose down --remove-orphans >/dev/null 2>&1 || true
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

mkdir -p "$LOG_DIR"
cd "$API_DIR"

if [[ "$RESET" == true ]]; then
    compose down --volumes --remove-orphans
fi
compose up --detach --wait postgres

FIELDOPS_E2E_DB_URL="$FIELDOPS_E2E_DB_URL" \
FIELDOPS_E2E_DB_PASSWORD="$FIELDOPS_E2E_DB_PASSWORD" \
FIELDOPS_E2E_SEED_PASSWORD="$FIELDOPS_E2E_SEED_PASSWORD" \
FIELDOPS_E2E_PASSWORD="$FIELDOPS_E2E_PASSWORD" \
FIELDOPS_E2E_JWT_SECRET="$FIELDOPS_E2E_JWT_SECRET" \
FIELDOPS_E2E_API_PORT="$FIELDOPS_E2E_API_PORT" \
  setsid mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=e2e >"$API_LOG" 2>&1 &
API_PID=$!

for attempt in $(seq 1 120); do
    if curl --fail --silent --show-error "$HEALTH_URL" | grep -q '"status":"UP"'; then
        break
    fi
    if ! kill -0 "$API_PID" 2>/dev/null; then
        echo "A API encerrou antes de ficar pronta. Log: $API_LOG" >&2
        tail -n 80 "$API_LOG" >&2 || true
        exit 1
    fi
    sleep 1
    if [[ "$attempt" == 120 ]]; then
        echo "Timeout aguardando readiness em $HEALTH_URL. Log: $API_LOG" >&2
        tail -n 80 "$API_LOG" >&2 || true
        exit 1
    fi
done

echo "API E2E pronta em http://127.0.0.1:${API_PORT} (log: $API_LOG)"
"$@"
