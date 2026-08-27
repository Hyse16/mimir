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
| `POST` | `/api/v1/blog-posts/{postId}/versions` | Save a user edit as an immutable version |
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
  "visitContext": "일요일 오후에 친구와 방문"
}
```

The backend rejects a stale or unrelated `baseVersionId`. It never overwrites an existing version. When `visitContext` is present, the factual context and new draft version are committed in the same transaction. This endpoint always creates `USER_EDIT`; AI-generated versions use the server-owned generation path and are correlated through `ai_jobs.result_version_id` without storing secrets.

## Job resources

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/blog-posts/{postId}/generation-jobs` | Start structured image analysis |
| `POST` | `/api/v1/blog-posts/{postId}/draft-generation-jobs` | Generate a new grounded draft version from the selected base version |
| `GET` | `/api/v1/blog-posts/{postId}/draft-generation-jobs` | Page through persisted revision turns newest first |
| `GET` | `/api/v1/jobs/{jobId}` | Read durable status and counts |
| `POST` | `/api/v1/jobs/{jobId}/retry-failed` | Retry only failed items from a partial failure |
| `POST` | `/api/v1/jobs/{jobId}/cancel` | Request cancellation |
| `GET` | `/api/v1/jobs/{jobId}/events` | Receive SSE progress events |

## SSE event shape

```text
event: job-progress
id: 18
data: {"eventId":18,"jobId":"01J...","status":"RUNNING","stage":"IMAGE_ANALYSIS","totalItems":20,"processedItems":15,"failedItems":1,"progress":80,"occurredAt":"2026-08-20T14:10:00+09:00"}
```

Events are committed with the job state transition and are resumable with the `Last-Event-ID` request header. The durable job record remains authoritative after reconnect. A subset of image failures produces `PARTIAL_FAILED`; successful image analysis remains available and only failed items are eligible for targeted retry.

Cancellation is cooperative. A waiting job is cancelled immediately; a running provider call is allowed to finish its current batch, after which unstarted items become `CANCELLED`. Repeated cancellation of `CANCEL_REQUESTED` or `CANCELLED` jobs is idempotent. The backoffice consumes SSE through a same-origin streaming proxy, while both clients can still refresh the durable `GET /jobs/{jobId}` representation.

Draft-generation requests contain `baseVersionId` and a non-blank `revisionInstruction`. Every current image must have a successful structured analysis before the job starts. The job records its base and result version IDs, moves through `CONTEXT_ASSEMBLY` and `DRAFT_GENERATION`, and stores accepted output as a new `AI_GENERATED` version. If the selected version changes during inference, the generated output is discarded with `STALE_BASE_VERSION`. User version requests cannot assign `AI_GENERATED`; that source is reserved for the server generation path.

Revision-turn history is read from the same durable job records rather than duplicated into chat storage. Each item exposes the instruction, base and nullable result version IDs, lifecycle status, stage, nullable error code, and timestamps. The endpoint is paginated with a default size of 20 and maximum size of 100. Failed and cancelled turns remain visible because they are part of the audit trail.

## System status

`GET /api/v1/system/status` returns component availability and privacy-safe metadata for Flet and the backoffice. It does not return credentials, filesystem paths containing user names, raw prompts, browser profiles, cookies, or provider payloads.
