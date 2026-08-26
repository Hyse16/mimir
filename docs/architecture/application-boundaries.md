# Application boundaries

Status: accepted for MVP implementation

Last updated: 2026-08-26

## Product split

Mimir has three user-facing and application boundaries.

| Boundary | Technology | Responsibility |
| --- | --- | --- |
| Main Application | Python 3.12+ and Flet | Natural-language requests, blog creation, iterative revision, calendar actions, progress, and explicit user approvals |
| Business Backend | Java 21+ and Spring Boot | Business rules, REST/SSE contracts, job coordination, persistence, privacy enforcement, and audit records |
| Web Backoffice | Next.js and TypeScript | Dense operational visibility, blog and version management, job monitoring, retry, and integration status |

PostgreSQL is the only source of truth for business data. The Flet application and Next.js backoffice do not maintain independent business stores or duplicate domain rules.

## Request flow

```text
Flet Main Application ─┐
                       ├─ REST / SSE ─> Spring Boot ─> PostgreSQL + pgvector
Next.js Backoffice ────┘                     │
                                             └─> AI Gateway ─> Ollama
```

The Flet application performs user-directed actions. The backoffice observes and manages the durable results of those actions. Both clients operate through stable backend contracts.

## Blog completion loop

1. The user selects up to 20 images and provides visit facts in the Flet application.
2. The backend stores the post, context, ordered assets, and a generation job.
3. Image analysis runs in configurable sequential batches of two to four images.
4. The AI Gateway generates a grounded draft using local Ollama by default.
5. Every user revision creates a new immutable draft version.
6. The Flet application shows the current draft and a comparison with the previous version.
7. The user explicitly selects the version to prepare for Naver.
8. The application produces a Naver preview, copy/export payload, and opens the editor at the user's request.
9. The user verifies the account and content and performs the final publish action.

## Browser automation boundary

Playwright is an optional local adapter owned by the Flet process. It is not a server-side integration and it is not part of the business persistence boundary.

Allowed MVP behavior:

- Open the Naver editor after an explicit user action.
- Assist with placing user-approved title, body, tags, and ordered images.
- Stop on authentication, CAPTCHA, rate limiting, access restriction, or an unknown editor state.
- Fall back to preview and copy/export without losing the approved draft.
- Require the user to perform the final publish action.

Excluded behavior:

- Automated or non-human access to ChatGPT or Claude consumer web interfaces.
- Scraping AI responses from consumer web interfaces.
- CAPTCHA, authentication, rate-limit, or service-protection bypasses.
- Server-side storage of browser cookies or consumer account credentials.
- Unattended Naver publishing.

AI providers connect only through the provider-independent AI Gateway. Local Ollama is the no-API-cost default. A remote provider can be added later only through an officially supported API and explicit privacy-mode configuration.

## Local browser data

Browser profiles, cookies, and session state remain in an application-specific local directory. They are never uploaded to Spring Boot, PostgreSQL, logs, diagnostics, or the backoffice. A browser automation failure records only non-sensitive metadata such as adapter type, stage, error category, and timestamp.

## MVP acceptance criteria

- Flet can create and revise a blog draft through Spring Boot without direct database access.
- Next.js can display the same persisted post, draft versions, assets, and job state.
- A post rejects image 21 while preserving valid support for counts 0, 1, 3, 10, and 20.
- Every revision remains recoverable as a separate version.
- Local Only prevents external AI and telemetry calls server-side.
- Naver preparation works even when Playwright is unavailable by using preview and copy/export.
- No browser credential or cookie appears in backend storage or logs.

## Official constraints considered

- Anthropic consumer terms prohibit automated or non-human access unless an API key or explicit permission applies: <https://www.anthropic.com/legal/consumer-terms>.
- OpenAI documents ChatGPT subscription and API-key authentication as supported Codex access paths: <https://learn.chatgpt.com/docs/auth>.
- Naver discontinued its Blog Write Open API after repeated mechanical posting policy violations: <https://developers.naver.com/notice/article/7527>.
- Flet supports packaging Python applications for desktop and mobile targets: <https://flet.dev/docs/publish/>.
- Flet requires Python 3.10 or later; this project targets Python 3.12: <https://flet.dev/docs/getting-started/installation/>.
