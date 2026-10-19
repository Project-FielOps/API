# Backend para E2E real

Esta infraestrutura é separada do ambiente `dev`:

- Postgres 16 em projeto Docker `fieldops-e2e`, porta local `55433` e volume
  `fieldops_e2e_pgdata`;
- API Spring Boot no perfil `e2e`, limitada a `127.0.0.1:8080` por padrão;
- seed de usuários, clientes, locais e equipamentos descartáveis;
- e-mail `noop`, armazenamento local em `target/e2e-evidence` e segredos
  explícitos de desenvolvimento local;
- readiness em `/actuator/health/readiness`, incluindo a conexão com o banco.

O wrapper executa um comando filho depois que Postgres e API estão prontos e
limpa o processo da API e os containers ao terminar. Para uma execução
determinística, use `--reset`, que remove somente o volume do projeto E2E.

## Executar Playwright com API real

No diretório `API/`:

```bash
./scripts/e2e/run-with-api.sh --reset -- \
  npm --prefix ../interface-administrativa run e2e
```

O comando não altera os specs Playwright. Ele apenas garante a infraestrutura
real disponível em `http://127.0.0.1:8080` durante a execução.

Para uma suíte específica:

```bash
./scripts/e2e/run-with-api.sh --reset -- \
  npx --prefix ../interface-administrativa playwright test e2e/auth.spec.ts
```

## Variáveis explícitas

Os defaults abaixo são exclusivos do ambiente E2E local e não são credenciais
de nenhum ambiente real. Podem ser substituídos no shell:

```bash
export FIELDOPS_E2E_DB_PASSWORD='fieldops_e2e_local_only'
export FIELDOPS_E2E_SEED_PASSWORD='FieldOps-E2E-Local-Only-2026!'
# Alias consumido pelos helpers Playwright; o wrapper define este valor automaticamente.
export FIELDOPS_E2E_PASSWORD="$FIELDOPS_E2E_SEED_PASSWORD"
export FIELDOPS_E2E_JWT_SECRET='fieldops-e2e-local-secret-change-me-01234567890123456789012345678901234567890123456789'
export FIELDOPS_E2E_API_PORT=8080
export FIELDOPS_E2E_DB_PORT=55433
```

O seed cria:

| Papel | E-mail |
|---|---|
| ADMIN | `admin@fieldops.local` |
| SUPERVISOR | `supervisor@fieldops.local` |
| TECHNICIAN | `technician@fieldops.local` |
| CLIENT_VIEWER | `viewer@fieldops.local` |

Todos usam `FIELDOPS_E2E_SEED_PASSWORD`. O banco é criado pelo Flyway na
primeira subida e o seed é executado somente no perfil `e2e`.

## Comandos de suporte

```bash
# Verificar readiness sem executar a suíte
curl --fail http://127.0.0.1:8080/actuator/health/readiness

# Remover somente o ambiente E2E
./scripts/e2e/reset.sh
```

## Limitações e riscos

- O seed atual não cria modelo publicado nem inspeção; fluxos que dependem
  desses registros continuam exigindo preparação adicional.
- O wrapper requer Docker, curl, Java 21 e Maven no PATH. Se existir o JDK
  `/usr/lib/jvm/java-21-temurin-jdk`, ele será selecionado automaticamente;
  caso contrário, configure `JAVA_HOME` explicitamente.
- A porta `8080` precisa estar livre; o Postgres E2E usa `55433` para não
  conflitar com o Postgres de desenvolvimento em `5433`.
- O wrapper encerra os containers ao sair, mas o volume é mantido salvo quando
  `--reset` não é usado. Nenhum segredo real deve ser colocado nas variáveis.
