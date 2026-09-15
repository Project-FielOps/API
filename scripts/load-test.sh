#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ENDPOINT="${ENDPOINT:-/actuator/health}"
REQUESTS="${REQUESTS:-100}"
CONCURRENCY="${CONCURRENCY:-10}"
P95_LIMIT_MS="${P95_LIMIT_MS:-500}"

case "$REQUESTS" in
  ''|*[!0-9]*) echo "REQUESTS must be a positive integer" >&2; exit 2 ;;
esac
case "$CONCURRENCY" in
  ''|*[!0-9]*) echo "CONCURRENCY must be a positive integer" >&2; exit 2 ;;
esac
if [ "$REQUESTS" -lt 1 ] || [ "$CONCURRENCY" -lt 1 ]; then
  echo "REQUESTS and CONCURRENCY must be positive" >&2
  exit 2
fi

RESULTS_DIR="$(mktemp -d)"
trap 'rm -rf "$RESULTS_DIR"' EXIT

export BASE_URL ENDPOINT RESULTS_DIR
seq "$REQUESTS" | xargs -n1 -P "$CONCURRENCY" sh -c '
  result=$(curl --fail --silent --show-error --output /dev/null \
    --write-out "%{http_code} %{time_total}" "$BASE_URL$ENDPOINT") || exit 1
  printf "%s\n" "$result" > "$RESULTS_DIR/$0"
' 2>/dev/null

RESULTS=$(find "$RESULTS_DIR" -type f -print | sort | xargs -r cat)
COMPLETED=$(printf '%s\n' "$RESULTS" | awk 'NF { count++ } END { print count + 0 }')
FAILED=$((REQUESTS - COMPLETED))

if [ "$COMPLETED" -eq 0 ]; then
  echo "No successful requests against $BASE_URL$ENDPOINT" >&2
  exit 1
fi

P95_MS=$(printf '%s\n' "$RESULTS" | awk '{ printf "%.3f\n", $2 * 1000 }' | sort -n | awk -v count="$COMPLETED" 'NR == int((count * 95 + 99) / 100) { print; found=1 } END { if (!found) print 0 }')
HTTP_ERRORS=$(printf '%s\n' "$RESULTS" | awk '$1 !~ /^2[0-9][0-9]$/ { count++ } END { print count + 0 }')

printf 'url=%s%s requests=%s completed=%s failed=%s http_errors=%s p95_ms=%s limit_ms=%s\n' \
  "$BASE_URL" "$ENDPOINT" "$REQUESTS" "$COMPLETED" "$FAILED" "$HTTP_ERRORS" "$P95_MS" "$P95_LIMIT_MS"

if [ "$FAILED" -ne 0 ] || [ "$HTTP_ERRORS" -ne 0 ]; then
  exit 1
fi
awk -v p95="$P95_MS" -v limit="$P95_LIMIT_MS" 'BEGIN { exit !(p95 <= limit) }'
