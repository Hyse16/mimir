# STEP 4 — Local AI revision loop progress

Last verified: 2026-08-26 (Asia/Seoul)

## Current slice

The first STEP 4 slice establishes the provider-independent text generation and grounding boundary. It does not yet expose an API or create an AI-generated draft version.

## Delivered

- `TextGenerationGateway` with provider-neutral draft, image-fact, and structured-result types
- Loopback-only `OllamaTextGenerationGateway` using `/api/chat`, temperature zero, and JSON Schema output
- Configurable local text model through `MIMIR_OLLAMA_TEXT_MODEL` (`qwen2.5:7b` by default)
- A three-minute provider timeout matching the existing local Vision boundary
- Exact `{{IMAGE:n}}` validation: every supplied image must appear once in display order
- Output length and tag boundary validation before business persistence
- Context grounding checks for prices, waiting, taste, service, orders, revisit intent, and visit weekdays
- Local Only rejection for non-loopback URLs and model identifiers containing `cloud`

## Database and API impact

- Database: none
- API: none
- Existing user-created versions and image-analysis jobs are unchanged

## Verification

Focused tests verify:

- Provider context and structured schema are sent to Ollama
- Structured title, body, and tags are mapped correctly
- Ordered image placeholders and context-grounded experience are accepted
- Missing or reordered placeholders are rejected
- Prices and personal experiences absent from user context are rejected
- Non-loopback URLs and cloud model identifiers are rejected

Run the focused slice:

```bash
cd backend
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew test \
  --tests com.mimir.ai.GeneratedDraftValidatorTest \
  --tests com.mimir.ai.OllamaTextGenerationGatewayTest
```

## Next STEP 4 slice

- Add a durable asynchronous draft-generation job that consumes the selected draft, user context, and successful image analyses
- Persist accepted output as a new `AI_GENERATED` draft version without overwriting history
- Move the post through `GENERATING` to `REVIEW_REQUIRED`, including failure recovery
- Expose revision instruction, progress, and version selection through Flet and the backoffice
- Exercise the exact production text contract against local Ollama before enabling the UI action

The grounding validator is a deliberate defense layer, not a proof that every possible hallucination can be recognized. New unsupported-claim families found in live evaluation must become regression cases before expanding the generation surface.
