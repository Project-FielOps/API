# FieldOps API

API REST do FieldOps — plataforma de planejamento, execução e revisão de inspeções técnicas em campo.
Implementa as regras de negócio, autenticação, persistência e auditoria descritas na especificação do
projeto (`../docs/fieldops-spec.md`, seções 09 a 12).

Stack: Java 21, Spring Boot 3.3, Spring Security (JWT), Spring Data JPA, PostgreSQL 16, Flyway,
springdoc-openapi (Swagger UI), Testcontainers para os testes de integração.

## Pré-requisitos

- Java 21 (`brew install openjdk@21`)
- Maven 3.9+ (`brew install maven`)
- Docker (para o Postgres de desenvolvimento e para os testes com Testcontainers)

Se o Java 21 instalado via Homebrew não estiver no `PATH` por padrão:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

## Subindo o banco de dados

```bash
docker compose up -d
```

Sobe um Postgres 16 em `localhost:5433` (banco `fieldops`, usuário `fieldops`, senha `fieldops_dev_pw`
— ver `docker-compose.yml`). As migrações Flyway (`src/main/resources/db/migration`) rodam
automaticamente na subida da aplicação.

## Rodando a API

```bash
mvn spring-boot:run
```

ou, para rodar o jar empacotado:

```bash
mvn -DskipTests package
java -jar target/fieldops-api-0.1.0.jar
```

A API sobe em `http://localhost:8080`. Documentação interativa (Swagger UI) em
`http://localhost:8080/swagger-ui.html`.

## Usuários de desenvolvimento (seed)

Em ambiente `dev` (perfil ativo por padrão, ver `application-dev.yml`), na primeira subida com o
banco vazio a aplicação cria automaticamente:

| Papel | E-mail | Senha |
|---|---|---|
| ADMIN | `admin@fieldops.local` | `Admin@12345` |
| SUPERVISOR | `supervisor@fieldops.local` | `Supervisor@123` |
| TECHNICIAN | `technician@fieldops.local` | `Technician@123` |
| CLIENT_VIEWER | `viewer@fieldops.local` | `Viewer@12345` |

Além de 2 clientes, 2 locais e 2 equipamentos de exemplo. O seed é idempotente (só roda se não houver
usuários) e pode ser desativado com `fieldops.seed.enabled=false`.

## Testes

```bash
mvn test
```

Os testes de integração (`src/test/java/com/fieldops/**/*Test.java`) sobem o contexto Spring completo
contra um Postgres descartável via Testcontainers (`jdbc:tc:postgresql:...` em
`application-test.yml`) — não é necessário ter o `docker compose up` rodando à parte para os testes,
o Testcontainers gerencia seu próprio container. Cobrem:

- `AuthenticationTest` — login, credenciais inválidas, usuário inativo, 401 sem token, 403 fora do
  perfil, `/auth/me`.
- `TemplateVersioningTest` — publicação bloqueada sem itens, publicação com sucesso, imutabilidade da
  versão publicada, validação de item.
- `InspectionWorkflowTest` — fluxo completo: cadastro → modelo → publicação → agendamento → snapshot →
  início → bloqueios de submissão (item obrigatório, observação e evidência exigidas) → submissão →
  revisão → reprovação com motivo → correção → aprovação → bloqueio pós-aprovação → auditoria.

> Nota sobre o ambiente local: se o Testcontainers não conseguir detectar o Docker Desktop no macOS
> ("Could not find a valid Docker environment"), crie `~/.testcontainers.properties` com
> `docker.host=unix:///Users/<seu-usuario>/.docker/run/docker.sock` — foi o que resolveu neste ambiente.

## Estrutura do projeto

Organização por contexto de negócio (`com.fieldops.<contexto>`), cada um com
`controller/ application/ domain/ repository/ dto/ mapper/`, conforme seção 11.6 da especificação:

```
auth/ user/ client/ site/ equipment/ template/ inspection/ evidence/ review/ audit/ dashboard/ shared/
```

## Contrato da API

Prefixo `/api/v1`, autenticação Bearer (JWT), paginação via `page`/`size`/`sort`, corpo de erro
padronizado (`ApiError`: `timestamp, status, error, code, message, path, details`). O contrato
completo, navegável, está no Swagger UI; um resumo textual está na seção 12 de
`../docs/fieldops-spec.md`.

Endpoints fora do escopo deste build (ficam documentados no modelo de dados mas não implementados
aqui, pois pertencem ao app mobile — fora do pedido de "front e back"): `/api/v1/mobile/**` e
`/api/v1/mobile/sync/**`.
