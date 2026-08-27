# STEP 4 — Local AI revision loop progress

Last verified: 2026-08-27 (Asia/Seoul)

## Current slice

STEP 4 now has a provider-independent generation boundary, a durable asynchronous revision path, and explicit comparison and restoration workflows in both user interfaces.

## Delivered

- `TextGenerationGateway` with provider-neutral draft, image-fact, and structured-result types
- Loopback-only `OllamaTextGenerationGateway` using `/api/chat`, temperature zero, and JSON Schema output
- Configurable local text model through `MIMIR_OLLAMA_TEXT_MODEL` (`qwen2.5:7b` by default)
- A three-minute provider timeout matching the existing local Vision boundary
- Exact `{{IMAGE:n}}` validation: every supplied image must appear once in display order
- Output length and tag boundary validation before business persistence
- Context grounding checks for prices, waiting, taste, service, orders, revisit intent, and visit weekdays
- Exact grounding for price values, Korean/ISO dates, weekdays, and numeric wait durations
- Conservative rejection of unsupported taste, service, recommendation, atmosphere, comfort, and quality opinions
- Prompt precedence that prevents revision instructions from overriding grounding rules
- Local Only rejection for non-loopback URLs and model identifiers containing `cloud`
- Flyway `V7__add_draft_generation_jobs.sql` with draft job inputs, result version, error code, and generation stages
- Asynchronous `BLOG_DRAFT_GENERATION` jobs on the existing single local-AI executor and durable SSE event stream
- Context assembly from the selected draft, user fact memo, current asset order, and latest successful per-image analyses
- Successful output persisted as a new immutable `AI_GENERATED` version and selected under `REVIEW_REQUIRED`
- Stale base-version detection after inference; stale or cancelled output is never persisted
- Backoffice revision instruction, live progress, cancellation, terminal refresh, and error-code display
- Flet revision workspace with instruction validation, unsaved-edit protection, polling, cancellation, and completed-version reload
- Python API client support for starting, polling, and cancelling draft-generation jobs
- Flet unified version diff that never changes the selected version during comparison
- Backoffice side-by-side version comparison with changed-field summary and direct history shortcuts
- Deliberate restore controls: Flet requires an explicit confirmation checkbox and Backoffice uses a confirmation dialog
- Server-owned draft source: user version requests always create `USER_EDIT`, while only the generation service creates `AI_GENERATED`

## Database and API impact

- Database: append-only V7 migration extends `ai_jobs` and `ai_job_events`; existing rows remain valid
- API: `POST /api/v1/blog-posts/{postId}/draft-generation-jobs`
- Existing API reused for restoration: `POST /api/v1/blog-posts/{postId}/versions/{versionId}/select`
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
- Flet tests cover revision start, unsaved-edit blocking, cancellation, polling completion, first-save enablement, non-mutating comparison, and confirmed restoration
- Validator regressions cover changed prices and weekdays, invented dates and wait durations, unsupported taste/recommendation/service/atmosphere claims, and equivalent Korean/ISO dates
- Three production-contract `qwen2.5:7b` scenarios completed in 24.69 seconds with their expected grounding decisions

The live evidence in `docs/evidence/step-4-text-gateway.json` confirms Korean structured output, two- and three-image placeholder order, exact grounded values, and completion within the three-minute Gateway timeout. The hostile sparse-context sample intentionally receives `REJECT`: `qwen2.5:7b` still follows an instruction to embellish unsupported atmosphere and quality, so the server-side validator remains a required defense rather than an optional fallback.

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

- Persist revision-turn instructions and outcomes so freer conversational editing has an auditable history
- Add constrained partial-regeneration targets only after each target has its own grounding regression coverage

The grounding validator is a deliberate defense layer, not a proof that every possible hallucination can be recognized. New unsupported-claim families found in live evaluation must become regression cases before expanding the generation surface.
