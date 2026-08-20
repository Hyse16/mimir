# STEP 1 — Project skeleton

Last verified: 2026-08-20 (Asia/Seoul)

## Outcome

The initial Flet application, Spring Boot business backend, Next.js backoffice, and PostgreSQL/pgvector database run as separate processes with explicit REST boundaries.

| Component | Locked baseline | Verification |
| --- | --- | --- |
| Flet application | Python 3.12.13, Flet 0.86.5 | Unit tests, bytecode compilation, hidden desktop startup, live backend status read |
| Spring Boot backend | Java 21, Spring Boot 4.1.0, Gradle 9.7.1 | Unit tests, application startup, REST and Actuator smoke checks |
| Next.js backoffice | Node 22, Next.js 16.3.1, React 19.2.8 | ESLint, TypeScript, production build, HTTP and server-rendered status check |
| PostgreSQL | PostgreSQL 17.11, pgvector 0.8.6 | Healthy Compose service, Flyway V1 history, extension version query |

## Boundaries delivered

- `application/` contains the user action workspace. It calls Spring Boot through the initial `/api/v1` contract and has no direct database access.
- `backend/` owns configuration, database connectivity, Flyway, privacy-safe system status, and future business modules.
- `backoffice/` provides the desktop-oriented operations shell and reads the same backend status at request time.
- `infra/compose.yaml` runs one PostgreSQL source of truth with pgvector. It does not introduce Redis, Kafka, a separate vector database, or cloud infrastructure.

## Start locally

Start PostgreSQL:

```bash
cd infra
docker compose up -d
```

Start Spring Boot:

```bash
cd backend
GRADLE_USER_HOME=.gradle-home ./gradlew bootRun
```

Start the Flet desktop application:

```bash
cd application
uv sync --dev
UV_CACHE_DIR=.uv-cache uv run flet run src/mimir_application/main.py
```

Start the backoffice:

```bash
cd backoffice
npm install
npm run dev
```

Defaults are local-only:

- Backend: `http://127.0.0.1:8080`
- Flet and backoffice API base: `http://127.0.0.1:8080/api/v1`
- Backoffice: `http://127.0.0.1:3000`
- PostgreSQL: `localhost:5432`, database and user `mimir`

The default database password is development-only and can be overridden with `MIMIR_DB_PASSWORD`. Production credentials must not use the checked-in fallback.

## Verify

```bash
cd application
UV_CACHE_DIR=.uv-cache uv run pytest
UV_CACHE_DIR=.uv-cache uv run python -m compileall -q src tests
```

```bash
cd backend
GRADLE_USER_HOME=.gradle-home ./gradlew test
```

```bash
cd backoffice
npm run lint
npm run typecheck
npm run build
```

```bash
cd infra
docker compose config --quiet
docker compose ps
```

## Database changes

Flyway migration `V1__enable_pgvector.sql` enables the `vector` extension. No business tables are introduced in this STEP. Blog tables begin in STEP 2.

## API changes

`GET /api/v1/system/status` returns only privacy-safe component metadata:

```json
{
  "status": "UP",
  "privacyMode": "LOCAL_ONLY",
  "components": {
    "database": "UP"
  }
}
```

The endpoint intentionally excludes connection strings, credentials, prompts, browser state, and local filesystem paths.

## Known gaps carried forward

- The blog action button and backoffice navigation are intentional disabled shells until STEP 2 introduces durable blog resources.
- Flet integration testing would provision the Flutter test host; STEP 1 uses focused Python unit tests and a hidden desktop startup smoke test.
- Authentication and multi-user authorization are outside the single-owner MVP skeleton.
- Browser automation and Playwright are not installed in STEP 1; the Naver publishing assistant belongs to STEP 5.
- Hermes Memory and Skill reliability remains deferred until the Hermes STEP.
