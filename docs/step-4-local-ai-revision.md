# STEP 4 — Local AI revision loop progress

Last verified: 2026-08-31 (Asia/Seoul)

## Current slice

STEP 4 now has a provider-independent generation boundary, a durable asynchronous revision path, constrained partial regeneration, and explicit linked revision turns in both user interfaces.

## Delivered

- `TextGenerationGateway` with provider-neutral draft, image-fact, and structured-result types
- Loopback-only `OllamaTextGenerationGateway` using `/api/chat`, temperature zero, and JSON Schema output
- Configurable local text model through `MIMIR_OLLAMA_TEXT_MODEL` (`qwen2.5:7b` by default)
- A three-minute provider timeout matching the existing local Vision boundary
- Exact `{{IMAGE:n}}` validation: every supplied image must appear once in display order
- Generated `FULL` and `BODY` drafts must contain grounded prose in addition to image placeholders
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
- Paginated PostgreSQL-backed revision-turn history with instruction, base/result version, outcome, error code, and timestamps
- Flet and Backoffice audit views that show revision outcomes without exposing internal job IDs
- Constrained `FULL`, `TITLE`, `BODY`, and `TAGS` regeneration targets in both workspaces
- Server-side preservation of every non-target field, independent of provider output
- Target-specific grounding and placeholder checks before creating an immutable version
- Revision-turn audit entries that record which field set was requested
- Explicit `previousTurnId` linkage for follow-up requests whose base version is the completed result of the linked turn
- Same-post, completed-result, and exact result/base validation before accepting a linked turn
- Previous revision instructions supplied only as workflow context; neither prior instructions nor prior model output become factual sources
- Automatic safe linkage and human-readable linked-turn audit context in Flet and Backoffice
- Server-owned draft source: user version requests always create `USER_EDIT`, while only the generation service creates `AI_GENERATED`

## Database and API impact

- Database: append-only V7 migration extends `ai_jobs` and `ai_job_events`; V8 records the constrained generation target and backfills existing draft jobs as `FULL`; V9 adds a draft-only `previous_turn_id` self-reference without reusing image-retry ancestry
- API: `POST /api/v1/blog-posts/{postId}/draft-generation-jobs`
  - Request accepts `target`: `FULL`, `TITLE`, `BODY`, or `TAGS`; omitted values default to `FULL`
  - Request accepts nullable `previousTurnId`; linked turns must belong to the same post, be completed, and have produced the submitted base version
  - Non-target title, body, and tags are copied from the immutable base version on the server
- Existing API reused for restoration: `POST /api/v1/blog-posts/{postId}/versions/{versionId}/select`
- API: `GET /api/v1/blog-posts/{postId}/draft-generation-jobs?page=0&size=20`
- Revision history responses expose nullable `previousTurnId`; shared job responses continue to expose nullable `baseVersionId`, `resultVersionId`, and `errorCode`
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
- PostgreSQL integration verifies revision turns remain newest-first with completed and failed outcomes
- Flet API/UI tests verify revision instructions and outcomes are visible without rendering internal job IDs
- Validator tests cover each partial target, preservation of non-target fields, body placeholder order, tag normalization, and target-specific unsupported-claim rejection
- PostgreSQL integration verifies a title-only job changes only the title and persists `TITLE` in its audit turn
- PostgreSQL integration verifies valid linked turns and rejects cross-post, failed, image-analysis, and result/base-mismatched links
- Migration coverage verifies the V9 draft-only foreign key, nullable first turns, self-link rejection, and image-job isolation
- HTTP, Python, and UI tests verify optional linkage, exact-result auto-linking, and unlinked user-edited versions
- Six production-contract `qwen2.5:7b` scenarios completed in 32.83 seconds across `FULL`, `TITLE`, `BODY`, and `TAGS`, including unsupported claims in a prior instruction

The live evidence in `docs/evidence/step-4-text-gateway.json` confirms Korean structured output, target-specific server preservation, two- and three-image placeholder order, exact grounded values, and completion within the three-minute Gateway timeout. The hostile sparse-context sample intentionally receives `REJECT`: `qwen2.5:7b` still follows an instruction to embellish unsupported atmosphere and quality, so the server-side validator remains a required defense rather than an optional fallback.

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

## Next development step

- STEP 5 — Naver Publish Assistant: preview, copy/export payload, user-opened editor handoff, and safe manual fallback

The grounding validator is a deliberate defense layer, not a proof that every possible hallucination can be recognized. New unsupported-claim families found in live evaluation must become regression cases before expanding the generation surface.
