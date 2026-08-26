# STEP 3 — Image pipeline progress

Last verified: 2026-08-26 (Asia/Seoul)

## Outcome

Blog posts can persist and manage up to 20 ordered image assets from both workspaces. PostgreSQL owns asset metadata and order, while original bytes pass through a provider-independent storage boundary backed by the local filesystem for development.

## Delivered

- Flyway `V3__create_blog_assets.sql` with post ownership, stable order, size, type, storage-key, and lifecycle constraints
- `StorageProvider` boundary with a traversal-safe `LocalStorageProvider`
- JPEG, PNG, and WebP allowlist with declared-type and file-signature matching
- 15 MiB per-image default and 305 MiB multipart request limit, leaving multipart overhead above 20 maximum-sized files
- Server-generated storage keys; user filenames are sanitized metadata and never filesystem paths
- Transactional 20-image enforcement under a per-post database lock
- Complete-order validation, persistent reordering, deletion, and order compaction
- Flet multi-image selection and multipart upload with saved/selected image counts
- Backoffice multipart upload streamed through a Route Handler, deletion confirmation, and accessible up/down ordering controls
- Ordered asset metadata in blog detail responses and both client workspaces
- Real JPEG/PNG decoding with a configurable 40-megapixel safety ceiling
- Aspect-ratio-preserving optimized JPEG derivatives (2048px default) and Vision analysis JPEG derivatives (1280px default)
- Original and derivative dimensions, byte sizes, content types, processing status, and provider-abstracted storage keys
- Coordinated original/derivative cleanup on upload rollback and asset deletion
- Flyway `V5__create_image_analysis_jobs.sql` with durable jobs, per-asset attempts, and job-scoped structured result history
- Provider-independent Vision Gateway with a loopback-only Ollama adapter and JSON Schema structured output
- Configurable sequential Vision batches of 2–4 images (3 by default); 20 images are never sent in one request
- Durable `COMPLETED`, `PARTIAL_FAILED`, and `FAILED` outcomes with failed-item-only retry jobs
- Flyway `V6__add_ai_job_events_and_cancellation.sql` with transactionally durable progress events and cancellation states
- Resumable SSE progress using event IDs and `Last-Event-ID`; the backoffice updates without a page refresh
- Cooperative job cancellation: a running Vision batch finishes, then remaining items become `CANCELLED`
- Flet and backoffice controls for starting analysis, reading progress, viewing per-image results, retrying failures, and cancelling active jobs
- Reproducible live Gateway quality gate for Korean output, structured order, salient facts, invented text, unsupported experience claims, and the three-minute timeout

## API

- `POST /api/v1/blog-posts/{postId}/assets` accepts multipart field `files`
- `PUT /api/v1/blog-posts/{postId}/assets/order` accepts every current asset ID exactly once
- `DELETE /api/v1/blog-posts/{postId}/assets/{assetId}` removes one asset and compacts order
- `POST /api/v1/blog-posts/{postId}/generation-jobs` creates an asynchronous image-analysis job
- `GET /api/v1/jobs/{jobId}` returns durable counts, progress, item states, and structured results
- `POST /api/v1/jobs/{jobId}/retry-failed` creates a child job containing only failed items
- `POST /api/v1/jobs/{jobId}/cancel` idempotently requests cancellation for an active job
- `GET /api/v1/jobs/{jobId}/events` streams durable `job-progress` events and resumes after `Last-Event-ID`

Defaults can be overridden with `MIMIR_STORAGE_LOCAL_ROOT`, `MIMIR_MAX_IMAGE_BYTES`, `MIMIR_OPTIMIZED_IMAGE_MAX_DIMENSION`, `MIMIR_ANALYSIS_IMAGE_MAX_DIMENSION`, `MIMIR_OPTIMIZED_IMAGE_JPEG_QUALITY`, `MIMIR_ANALYSIS_IMAGE_JPEG_QUALITY`, and `MIMIR_IMAGE_MAX_PIXELS`. SSE polling and connection lifetime use `MIMIR_AI_EVENT_POLL_INTERVAL` and `MIMIR_AI_EVENT_STREAM_TIMEOUT`. Local runtime data remains ignored under `data/`.

JPEG and PNG assets are decoded and receive both derivatives during upload. WebP originals remain accepted as `ORIGINAL_ONLY`; adding a portable WebP codec requires an explicitly approved dependency and is intentionally deferred. Vision uses `MIMIR_VISION_BATCH_SIZE` (2–4), `MIMIR_OLLAMA_BASE_URL` (loopback HTTP only), and `MIMIR_OLLAMA_VISION_MODEL` (`gemma4:latest` by default). Local Only rejects model identifiers containing `cloud` so a loopback Ollama daemon cannot be configured to relay this workflow to Ollama Cloud.

## Verification

Backend integration tests use PostgreSQL/pgvector and temporary local storage. They cover 0, 1, 3, 10, and 20 images, rejection of image 21, MIME-signature mismatch, actual image decoding, bounded derivative dimensions, derivative storage and deletion, path-like filenames, complete ordering, deletion, order compaction, 3+2 sequential Vision batches, partial failure, active-job exclusion, failed-item-only retry, durable event ordering/resume, waiting cancellation, and cancellation after a running batch. A contract test verifies Ollama base64 image requests, structured result mapping, and Local Only URL enforcement without contacting a model.

The Python client tests verify multipart framing and asset response parsing. The Flet construction smoke test and Next.js lint, typecheck, and production build verify that image operations remain consumable across both clients.

The production Gateway prompt and JSON Schema were also exercised against the three checked-in non-sensitive fixtures with local `gemma4:latest`. The first quality run exposed an unsupported drink subtype, and a later run omitted a visible utensil. Tightening the prompt to prefer broad visible labels and include salient tableware resolved both failures. The final run passed every quality check in 48.44 seconds total, including 0.44 seconds model load time, with 921 prompt tokens and 258 output tokens. The machine-readable result is stored in `docs/evidence/step-3-vision-gateway.json`.

Reproduce the same live check without external provider fallback:

```bash
VISION_RESULT_PATH=docs/evidence/step-3-vision-gateway.json \
  ./scripts/step3/verify-vision-gateway.sh gemma4:latest
```

## Remaining STEP 3 work

- Portable WebP decoding and derivative generation
- Private acceptance sampling with user-owned blog photos; do not commit those images or raw outputs

## Upstream contract references

- [Ollama Vision](https://docs.ollama.com/capabilities/vision): REST image input is a base64-encoded `images` array
- [Ollama Structured Outputs](https://docs.ollama.com/capabilities/structured-outputs): Vision responses accept a JSON Schema through `format`
