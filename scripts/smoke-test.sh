#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

health=$(curl --fail --silent --show-error --header 'Accept: application/json' "$BASE_URL/actuator/health")
case "$health" in
  *'"status":"UP"'*) ;;
  *) echo "Unexpected health response: $health" >&2; exit 1 ;;
esac

health_headers=$(curl --fail --silent --show-error --dump-header - --output /dev/null "$BASE_URL/actuator/health")
printf '%s\n' "$health_headers" | grep -iq '^x-request-id:'

printf 'smoke_ok base_url=%s health=%s\n' "$BASE_URL" "$health"
