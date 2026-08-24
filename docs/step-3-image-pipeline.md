# STEP 3 — Image pipeline progress

Last verified: 2026-08-24 (Asia/Seoul)

## Outcome

Blog posts can persist up to 20 ordered image assets. PostgreSQL owns asset metadata and order, while original bytes pass through a provider-independent storage boundary backed by the local filesystem for development.

## Delivered

- Flyway `V3__create_blog_assets.sql` with post ownership, stable order, size, type, storage-key, and lifecycle constraints
- `StorageProvider` boundary with a traversal-safe `LocalStorageProvider`
- JPEG, PNG, and WebP allowlist with declared-type and file-signature matching
- 15 MiB per-image default and 305 MiB multipart request limit, leaving multipart overhead above 20 maximum-sized files
- Server-generated storage keys; user filenames are sanitized metadata and never filesystem paths
- Transactional 20-image enforcement under a per-post database lock
- Complete-order validation, persistent reordering, deletion, and order compaction
- Ordered asset metadata in blog detail responses and the backoffice detail view

## API

- `POST /api/v1/blog-posts/{postId}/assets` accepts multipart field `files`
- `PUT /api/v1/blog-posts/{postId}/assets/order` accepts every current asset ID exactly once
- `DELETE /api/v1/blog-posts/{postId}/assets/{assetId}` removes one asset and compacts order

Defaults can be overridden with `MIMIR_STORAGE_LOCAL_ROOT` and `MIMIR_MAX_IMAGE_BYTES`. Local runtime data remains ignored under `data/`.

## Verification

Backend integration tests use PostgreSQL/pgvector and temporary local storage. They cover 0, 1, 3, 10, and 20 images, rejection of image 21, MIME-signature mismatch, path-like filenames, complete ordering, deletion, and order compaction.

The Python client tests and Next.js lint, typecheck, and production build verify that the extended blog detail response remains consumable across both clients.

## Remaining STEP 3 work

- Flet multi-image selection and upload progress
- Backoffice upload, deletion, and drag/drop ordering controls
- Original image decoding, resize, compression, and analysis-image derivatives
- Dimension and derivative metadata
- Structured per-image analysis, sequential vision batching, partial failure, and retry
