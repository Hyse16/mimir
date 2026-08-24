# STEP 2 — Blog CRUD progress

Last verified: 2026-08-24 (Asia/Seoul)

## Outcome

The first durable blog workflow is operational across the Flet application, Spring Boot backend, Next.js backoffice, and PostgreSQL. User edits create immutable draft versions; the selected version and factual context remain server-owned.

## Delivered

- PostgreSQL tables for posts, factual context, immutable draft versions, and ordered version tags
- Create, search, filter, sort, paginate, detail, metadata update, version save/select, duplicate, and archive operations
- Stale-version rejection before any draft or factual-context mutation
- Flet creation plus recent-post loading and existing-draft revision
- Backoffice dashboard, URL-backed list filters, preview, version history, editing, status management, duplication, and archive confirmation

## Verify

```bash
cd application
UV_CACHE_DIR=.uv-cache uv run pytest
UV_CACHE_DIR=.uv-cache uv run python -m compileall -q src tests
```

```bash
cd backend
GRADLE_USER_HOME=.gradle-home ./gradlew test --rerun-tasks
```

```bash
cd backoffice
npm run lint
npm run typecheck
npm run build
```

## Database changes

Flyway migration `V2__create_blog_domain.sql` adds `blog_posts`, `blog_contexts`, `blog_draft_versions`, and `blog_draft_version_tags`. PostgreSQL remains the only business-data source of truth.

## API changes

The implemented blog API covers create, list, detail, patch, archive, duplicate, version creation, and version selection under `/api/v1/blog-posts`.

Saving a version may include `visitContext`; the backend commits it with the new immutable version in one transaction. A stale `baseVersionId` changes neither the version history nor factual context.

## Remaining STEP 2 decisions

- The STEP 2/3 `BlogAsset` boundary is resolved by introducing asset persistence, safe original storage, and ordering as the first STEP 3 slice. Optimization and analysis remain in STEP 3.
- `develop.md` lists deletion confirmation, while the accepted API contract excludes destructive deletion from the first vertical slice. Archive is implemented and preserves version history; destructive deletion remains intentionally unavailable.
- Browser-level automation is not installed. Current backoffice evidence is lint, TypeScript, production build, and backend integration coverage.
