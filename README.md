# Mimir

Mimir is a local-first personal AI workspace. The project starts with an AI-assisted blog workflow and is designed to grow into a private workspace for content, knowledge, schedules, mail, news, and investment research.

## Status

The repository has completed the executable skeleton, core **STEP 2: Blog CRUD**, the main **STEP 3: Image Pipeline**, and **STEP 4: Local AI Revision Loop**. Durable drafts and editing are available alongside validated local image storage, ordered derivatives, structured local Vision analysis, resumable SSE progress, provider-independent text generation, constrained partial regeneration, linked revision turns, and pre-persistence grounding checks.

## Planned architecture

- Main application: Python and Flet action workspace for blog generation, iterative revision, and future calendar, mail, news, and investment workflows
- Web frontend: Next.js, React, and TypeScript
- Primary operations UI: desktop-first responsive web backoffice for blog review, editing, AI job monitoring, and skill approval
- Backend: Java 21+, Spring Boot modular monolith
- Data: PostgreSQL as the source of truth, with pgvector for retrieval
- Local model runtime: Ollama
- Agent layer: Hermes Agent
- Integration: REST, SSE, and MCP
- Deployment path: local storage and Docker first, with optional AWS infrastructure later

The application keeps provider-specific AI code behind an AI Gateway. Hermes provides agent, memory, skill, and tool orchestration; it does not replace the application database. Obsidian may be added later as an optional human-editable export layer.

Playwright is limited to an optional, local, user-approved Naver editor assistant. Mimir does not automate or scrape ChatGPT or Claude consumer web interfaces, bypass site protections, or publish to Naver without the user's final action. See [application boundaries](docs/architecture/application-boundaries.md).

## Privacy

`Local Only` is a hard boundary. When enabled, prompts, images, embeddings, telemetry, and generated content must not be sent to an external AI or cloud fallback provider.

## Development evidence

The accepted application boundaries are recorded in [docs/architecture/application-boundaries.md](docs/architecture/application-boundaries.md). Current machine and model findings are recorded separately in [docs/step-0-environment.md](docs/step-0-environment.md).

STEP 1 versions, run commands, verification, database impact, and known gaps are recorded in [docs/step-1-project-skeleton.md](docs/step-1-project-skeleton.md).

Current STEP 2 delivery, verification, and remaining contract decisions are recorded in [docs/step-2-blog-crud.md](docs/step-2-blog-crud.md).

Current STEP 3 storage, upload, and ordering behavior is recorded in [docs/step-3-image-pipeline.md](docs/step-3-image-pipeline.md).

Current STEP 4 text generation and grounding behavior is recorded in [docs/step-4-local-ai-revision.md](docs/step-4-local-ai-revision.md).

## STEP 0 model probes

Run the reproducible environment probe:

```bash
./scripts/step0/check-environment.sh
```

Run the local text benchmark after Ollama is available:

```bash
./scripts/step0/benchmark-text.sh qwen2.5:7b
```

Run a repeatable warm-run suite:

```bash
./scripts/step0/benchmark-text-suite.sh qwen2.5:7b
```

Run the local vision benchmark with the checked-in non-sensitive fixtures:

```bash
./scripts/step0/benchmark-vision.sh gemma4:latest
```

Hermes Memory and Skill reliability is deferred until after the core blog workflow and does not block the skeleton.

## Repository layout

```text
docs/          Public technical notes and validation results
application/   Python and Flet action application
backend/       Java and Spring Boot business backend
backoffice/    Next.js operational backoffice
infra/         PostgreSQL and pgvector local runtime
scripts/step0  Reproducible local environment and model probes
fixtures/step0 Synthetic, non-sensitive benchmark inputs
```

## License

No license has been selected yet. Until a license is added, all rights are reserved by the repository owner.
