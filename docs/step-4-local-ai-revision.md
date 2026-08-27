# STEP 4 — Local AI revision loop progress

Last verified: 2026-08-27 (Asia/Seoul)

## Current slice

STEP 4 now has a provider-independent generation boundary and a durable asynchronous path from a selected draft to a new AI-generated version.

## Delivered

- `TextGenerationGateway` with provider-neutral draft, image-fact, and structured-result types
- Loopback-only `OllamaTextGenerationGateway` using `/api/chat`, temperature zero, and JSON Schema output
- Configurable local text model through `MIMIR_OLLAMA_TEXT_MODEL` (`qwen2.5:7b` by default)
- A three-minute provider timeout matching the existing local Vision boundary
- Exact `{{IMAGE:n}}` validation: every supplied image must appear once in display order
- Output length and tag boundary validation before business persistence
- Context grounding checks for prices, waiting, taste, service, orders, revisit intent, and visit weekdays
- Local Only rejection for non-loopback URLs and model identifiers containing `cloud`
- Flyway `V7__add_draft_generation_jobs.sql` with draft job inputs, result version, error code, and generation stages
- Asynchronous `BLOG_DRAFT_GENERATION` jobs on the existing single local-AI executor and durable SSE event stream
- Context assembly from the selected draft, user fact memo, current asset order, and latest successful per-image analyses
- Successful output persisted as a new immutable `AI_GENERATED` version and selected under `REVIEW_REQUIRED`
- Stale base-version detection after inference; stale or cancelled output is never persisted
- Backoffice revision instruction, live progress, cancellation, terminal refresh, and error-code display
- Flet revision workspace with instruction validation, unsaved-edit protection, polling, cancellation, and completed-version reload
- Python API client support for starting, polling, and cancelling draft-generation jobs
- Server-owned draft source: user version requests always create `USER_EDIT`, while only the generation service creates `AI_GENERATED`

## Database and API impact

- Database: append-only V7 migration extends `ai_jobs` and `ai_job_events`; existing rows remain valid
- API: `POST /api/v1/blog-posts/{postId}/draft-generation-jobs`
- Shared job responses now expose nullable `baseVersionId`, `resultVersionId`, and `errorCode`
- Existing image-analysis endpoints and event-resume behavior are unchanged

## Verification

Focused tests verify:

- Provider context and structured schema are sent to Ollama
- Structured title, body, and tags are mapped correctly
- Ordered image placeholders and context-grounded experience are accepted
- Missing or reordered placeholders are rejected
- Prices and personal experiences absent from user context are rejected
- Non-loopback URLs and cloud model identifiers are rejected
- PostgreSQL integration covers successful AI version creation, missing image analysis, stale base versions, provider failure, and cancellation after inference
- Flet tests cover revision start, unsaved-edit blocking, cancellation, polling completion, and first-save enablement
- The exact production text prompt and JSON Schema passed a local `qwen2.5:7b` run in 15.00 seconds, including 5.55 seconds model load time

The live evidence in `docs/evidence/step-4-text-gateway.json` confirms Korean structured output, ordered `{{IMAGE:1}}` and `{{IMAGE:2}}` placement, no unsupported price/wait/service/order claims, and completion within the three-minute Gateway timeout.

Run the focused slice:

```bash
cd backend
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew test \
  --tests com.mimir.ai.GeneratedDraftValidatorTest \
  --tests com.mimir.ai.OllamaTextGenerationGatewayTest
```

Run the live local-only contract:

```bash
TEXT_RESULT_PATH=docs/evidence/step-4-text-gateway.json \
  ./scripts/step4/verify-text-gateway.sh qwen2.5:7b
```

## Next STEP 4 slice

- Add version-to-version comparison in both workspaces
- Add a deliberate restore action distinct from merely viewing an older version
- Expand live grounding regression samples before supporting freer conversational revision turns

The grounding validator is a deliberate defense layer, not a proof that every possible hallucination can be recognized. New unsupported-claim families found in live evaluation must become regression cases before expanding the generation surface.
