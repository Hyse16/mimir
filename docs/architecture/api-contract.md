# Initial REST and SSE contract

Status: STEP 0 contract baseline; implementation starts in STEP 1

## Contract ownership

Spring Boot owns the REST and SSE contract under `/api/v1`. Flet and Next.js are API clients and must treat every response as external input. Domain state is never inferred only from client navigation or local browser state.

All JSON uses UTF-8, ISO-8601 timestamps with offsets, stable string identifiers, and explicit nullable fields. Validation failures use field-level errors. Unknown internal exceptions are not returned to clients.

## Error envelope

```json
{
  "code": "BLOG_IMAGE_LIMIT_EXCEEDED",
  "message": "A blog post supports at most 20 images.",
  "fieldErrors": [
    {
      "field": "images",
      "reason": "MAX_20"
    }
  ],
  "traceId": "01J..."
}
```

The `traceId` correlates non-sensitive logs. Prompts, browser cookies, OAuth tokens, image bytes, and private message content are excluded from the error envelope and default logs.

## Blog resources

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/blog-posts` | Create a draft post and factual user context |
| `GET` | `/api/v1/blog-posts` | Search, filter, sort, and paginate posts |
| `GET` | `/api/v1/blog-posts/{postId}` | Read post detail, current version summary, and ordered assets |
| `PATCH` | `/api/v1/blog-posts/{postId}` | Update editable metadata and lifecycle state |
| `POST` | `/api/v1/blog-posts/{postId}/archive` | Archive without destructive deletion |
| `POST` | `/api/v1/blog-posts/{postId}/duplicate` | Copy the selected draft and factual context into an independent draft post |
| `POST` | `/api/v1/blog-posts/{postId}/assets` | Upload and append validated assets, enforcing the total limit of 20 |
| `PUT` | `/api/v1/blog-posts/{postId}/assets/order` | Persist complete display order |
| `DELETE` | `/api/v1/blog-posts/{postId}/assets/{assetId}` | Delete one original and its derivatives, then compact display order |
| `POST` | `/api/v1/blog-posts/{postId}/versions` | Save a user edit or AI revision as an immutable version |
| `GET` | `/api/v1/blog-posts/{postId}/versions` | List version summaries newest first |
| `GET` | `/api/v1/blog-posts/{postId}/versions/{versionId}` | Read one version |
| `POST` | `/api/v1/blog-posts/{postId}/versions/{versionId}/select` | Select a version as the current approved draft |
| `POST` | `/api/v1/blog-posts/{postId}/prepare-naver` | Build a preview and copy/export payload from the selected version |

Destructive blog-post deletion is excluded from the first vertical slice. Asset deletion is available with explicit confirmation; post archive remains reversible and keeps version and job history coherent.

Image uploads use multipart field `files`, allow JPEG, PNG, and WebP only, and enforce both declared-type/signature matching and the configured size limit. JPEG and PNG uploads are decoded with a pixel ceiling and receive optimized and Vision-analysis JPEG derivatives. Asset responses expose original dimensions, `derivativeStatus`, and nullable optimized/analysis variant metadata; storage keys remain internal. WebP is reported as `ORIGINAL_ONLY` until a portable codec dependency is approved. Ordering requests contain every current asset ID exactly once; partial or duplicate order lists are rejected.

## Revision request

```json
{
  "baseVersionId": "01J...",
  "title": "성수 카페 방문기",
  "body": "수정된 본문",
  "tags": ["성수", "카페"],
  "visitContext": "일요일 오후에 친구와 방문",
  "source": "USER_EDIT"
}
```

The backend rejects a stale or unrelated `baseVersionId`. It never overwrites an existing version. When `visitContext` is present, the factual context and new draft version are committed in the same transaction. AI-generated versions use `source: AI_GENERATED` and retain the provider route and job identifier as metadata without storing secrets.

## Job resources

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/blog-posts/{postId}/generation-jobs` | Start image analysis and grounded draft generation |
| `GET` | `/api/v1/jobs/{jobId}` | Read durable status and counts |
| `POST` | `/api/v1/jobs/{jobId}/retry-failed` | Retry only failed items from a partial failure |
| `POST` | `/api/v1/jobs/{jobId}/cancel` | Request cancellation |
| `GET` | `/api/v1/jobs/{jobId}/events` | Receive SSE progress events |

## SSE event shape

```text
event: job-progress
id: 18
data: {"jobId":"01J...","status":"RUNNING","total":20,"processed":15,"failed":1,"stage":"IMAGE_ANALYSIS","occurredAt":"2026-08-20T14:10:00+09:00"}
```

Events are resumable by event ID. The durable job record remains authoritative after reconnect. A subset of image failures produces `PARTIAL_FAILED`; successful image analysis remains available and only failed items are eligible for targeted retry.

## System status

`GET /api/v1/system/status` returns component availability and privacy-safe metadata for Flet and the backoffice. It does not return credentials, filesystem paths containing user names, raw prompts, browser profiles, cookies, or provider payloads.
