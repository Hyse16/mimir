# STEP 0 — Local AI environment validation

Last checked: 2026-08-20 (Asia/Seoul)

## Goal

Record the local-first AI capabilities and limitations of the target machine before creating the Flet, Spring Boot, and Next.js applications. The environment evidence informs provider selection, but Hermes Memory and Skill reliability does not block the core blog skeleton.

## Target machine

| Item | Observed value | Status |
| --- | --- | --- |
| OS / architecture | macOS (`Darwin`), arm64 | Pass |
| Processor | Apple M1 Pro | Pass |
| Unified memory | 16 GiB | Pass |
| Python | 3.9.13 | Does not meet the Flet application target; install Python 3.12 in STEP 1 |
| Java | OpenJDK 21.0.12 | Pass |
| Node.js | 22.23.1 | Pass |
| npm | 10.9.8 | Pass |
| Docker CLI / daemon | 29.2.1 / 29.2.1 | Pass after starting Docker Desktop |
| Docker Compose | 5.0.2 | Pass |
| PostgreSQL CLI | Not installed | Not required until STEP 1 |
| Ollama | 0.30.10 | Pass after starting the server |
| Hermes Agent | 0.18.2 | Text connection passed; tool-call validation pending |

## Observed Ollama models and candidates

| Model | Size | Capabilities observed | STEP 0 use |
| --- | ---: | --- | --- |
| `qwen3:4b` | 2.5 GB | completion, tools, thinking | Rejected as current text default; bounded requests returned no final response |
| `qwen2.5:7b` | 4.7 GB | text completion | Current text baseline |
| `qwen2.5:7b-64k` | 4.7 GB | text completion | Long-context candidate; not benchmarked |
| `gemma4:latest` | 9.6 GB on disk | completion, vision, audio, tools, thinking | Vision and Hermes candidate |
| `qwen3.5:9b` | Not installed; download interrupted after the architecture change | vision, tools, thinking | Deferred Hermes tool-call candidate |

## Text baseline

Prompt: one Korean sentence explaining the advantage of a local-first AI workspace. Temperature was `0`, with a maximum of 120 output tokens.

| Model | Result | Total | Load | Output | Generation rate |
| --- | --- | ---: | ---: | ---: | ---: |
| `qwen2.5:7b` | Coherent Korean sentence in two cold-load runs | 5.87–6.55 s | 4.11–4.52 s | 36 tokens | 20.35–24.36 tokens/s |
| `qwen3:4b` | No final response before token limit | 3.28 s | 0.17 s | 120 thinking tokens | 40.84 tokens/s |

The 7B model is only a short-text speed baseline. A fresh three-run suite completed in 6.03 seconds cold and 1.66 seconds warm at an average 24.41 generated tokens/second.

The repeated long-form Korean Naver blog test averaged 16.81 seconds, but failed the hallucination policy. Both deterministic runs invented an unspecified small cafe, cream and fruit in the cheesecake, interior atmosphere, and recommendation language. `qwen2.5:7b` must not be selected as the blog-generation default without a stronger grounding or validation layer.

## Vision baseline

Synthetic, non-sensitive cafe fixtures are checked in under `fixtures/step0/vision`. They cover a Basque cheesecake, an iced latte with a croissant, and an empty cafe interior. The benchmark cycles these fixtures for throughput boundaries; it does not claim 10 or 20 distinct-scene accuracy.

| Image count | Sequential batches | Model time | Result |
| ---: | --- | ---: | --- |
| 1 | 1 | 6.99 s | Structured result returned; visible plate, dessert, and fork identified |
| 3 | 3 | 18.35 s | Input order preserved; all three fixture scenes identified |
| 10 | 3 + 3 + 3 + 1 | 51.88 s | All four batches completed with valid structured results |
| 20 | 3 + 3 + 3 + 3 + 3 + 3 + 2 | 99.13 s | All seven batches completed with valid structured results |

The benchmark disables model thinking output so the token budget is reserved for the required structured result. It sends at most three images per request and never sends all 20 images together.

## Hermes findings

Hermes is configured with provider `ollama-launch` and base URL `http://127.0.0.1:11434/v1`.

Hermes initially rejected the local runtime because Ollama loaded only 32,768 context tokens. Adding `model.ollama_num_ctx: 65536` aligned the actual runtime context with Hermes's 64K minimum. A subsequent `qwen2.5:7b` one-shot returned a non-empty Korean response and wrote a usage artifact with `completed: true`, `failed: false`, and one API call.

Plain text transport is therefore passed. Agentic tool use is not passed: `qwen2.5:7b` and `qwen3:4b` emitted tool-call JSON as normal text, while `gemma4:latest` did not create a Memory write or pending approval record. The remaining local tool-call evaluation is deferred until Hermes is introduced after the core blog workflow.

`hermes doctor` also reported:

- Configuration was migrated from schema v30 to v33 after a Hermes state snapshot.
- Deprecated `delegation.max_async_children` was removed by the official migration.
- Two unknown top-level keys remain and are ignored by Hermes.
- Optional web/UI workspaces report build-tool dependency advisories.
- Skills Hub has not been initialized.

`memory.write_approval` and `skills.write_approval` are now enabled. The configuration is correct, but the approval flow remains unverified until a local model performs a real tool call and creates a pending record.

Do not run automatic configuration repair or updates without reviewing the backup and diff because Hermes configuration is user-level state outside this repository.

## Remaining validation matrix

| Check | Required evidence | Status |
| --- | --- | --- |
| Text cold/warm performance | Multiple measured runs, RAM, Korean long-form quality | Speed pass; long-form grounding failed |
| Vision: 1 image | Structured description against expected facts | Pass with synthetic fixture |
| Vision: 3 images | Sequential batch timing and input order | Pass |
| Vision: 10 images | Batched timing and structured results | Pass: 4 batches, 51.88 s |
| Vision: 20 images | Batches of 2–4; never one 20-image request | Pass: 7 batches, 99.13 s |
| Hermes → Ollama | Non-empty response plus usage evidence | Pass for plain text |
| Hermes Memory | Write, new-session recall, and correction test | Deferred; current small models failed |
| Hermes Skill | Create and execute a test skill | Deferred; blocked on reliable local tool calling |
| Skill approval | Proposed modification remains staged until approval | Configuration verified; runtime behavior deferred |

## Environment baseline acceptance criteria

- Select one text model and one vision model that fit within 16 GiB without unacceptable memory pressure.
- Record cold load time, warm inference time, peak memory, and Korean quality for the selected models.
- Complete representative image tests at 1, 3, 10, and 20 images using configurable sequential batches of 2–4.
- Record Hermes-to-Ollama text results and explicitly preserve Memory/Skill failures for the later Hermes STEP.
- Record exact commands, versions, failures, and recovery steps without external AI fallback.
- Do not select `qwen2.5:7b` as the blog writer without a grounding and validation layer.

## Reproduce current checks

```bash
./scripts/step0/check-environment.sh
./scripts/step0/benchmark-text.sh qwen2.5:7b
./scripts/step0/benchmark-text-suite.sh qwen2.5:7b
./scripts/step0/benchmark-vision.sh gemma4:latest
```

The scripts do not install software, start GUI applications, change Hermes configuration, or download models.
