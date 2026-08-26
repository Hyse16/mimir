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

## API

- `POST /api/v1/blog-posts/{postId}/assets` accepts multipart field `files`
- `PUT /api/v1/blog-posts/{postId}/assets/order` accepts every current asset ID exactly once
- `DELETE /api/v1/blog-posts/{postId}/assets/{assetId}` removes one asset and compacts order

Defaults can be overridden with `MIMIR_STORAGE_LOCAL_ROOT`, `MIMIR_MAX_IMAGE_BYTES`, `MIMIR_OPTIMIZED_IMAGE_MAX_DIMENSION`, `MIMIR_ANALYSIS_IMAGE_MAX_DIMENSION`, `MIMIR_OPTIMIZED_IMAGE_JPEG_QUALITY`, `MIMIR_ANALYSIS_IMAGE_JPEG_QUALITY`, and `MIMIR_IMAGE_MAX_PIXELS`. Local runtime data remains ignored under `data/`.

JPEG and PNG assets are decoded and receive both derivatives during upload. WebP originals remain accepted as `ORIGINAL_ONLY`; adding a portable WebP codec requires an explicitly approved dependency and is intentionally deferred.

## Verification

Backend integration tests use PostgreSQL/pgvector and temporary local storage. They cover 0, 1, 3, 10, and 20 images, rejection of image 21, MIME-signature mismatch, actual image decoding, bounded derivative dimensions, derivative storage and deletion, path-like filenames, complete ordering, deletion, and order compaction.

The Python client tests verify multipart framing and asset response parsing. The Flet construction smoke test and Next.js lint, typecheck, and production build verify that image operations remain consumable across both clients.

## Remaining STEP 3 work

- Portable WebP decoding and derivative generation
- Structured per-image analysis, sequential vision batching, partial failure, and retry
