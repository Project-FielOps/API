#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_PROJECT="${FIELDOPS_E2E_COMPOSE_PROJECT:-fieldops-e2e}"

export FIELDOPS_E2E_DB_PASSWORD="${FIELDOPS_E2E_DB_PASSWORD:-fieldops_e2e_local_only}"

docker compose \
  --project-name "$COMPOSE_PROJECT" \
  --file "$API_DIR/docker-compose.e2e.yml" \
  down --volumes --remove-orphans

echo "Ambiente E2E resetado: projeto Docker $COMPOSE_PROJECT e volume dedicado removidos."
