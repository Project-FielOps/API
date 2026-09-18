#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Erro: arquivo .env não encontrado em $SCRIPT_DIR" >&2
  echo "Crie-o a partir de .env.example antes de iniciar a API." >&2
  exit 1
fi

cd "$SCRIPT_DIR"

set -a
source "$ENV_FILE"
set +a

exec mvn spring-boot:run
