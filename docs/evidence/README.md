# STEP 0 Evidence

This directory stores reproducible machine-generated summaries from local-only validation runs.

- `step-0-vision-10.json` — ten images processed as sequential batches of 3, 3, 3, and 1
- `step-0-vision-20.json` — twenty images processed as sequential batches of 3, 3, 3, 3, 3, 3, and 2
- `step-0-hermes-ollama-usage.json` — successful Hermes-to-Ollama plain-text usage record
- `step-0-text-warm.json` — three-run cold/warm Korean text baseline
- `step-0-text-long-form.json` — repeated long-form Korean Naver blog generation sample
- `step-0-hermes-memory-write.json` — current Hermes Memory tool-call attempt; not a passing artifact until a pending or committed record is independently verified

The vision count runs cycle three known fixtures to validate batching, order, structured output, and throughput. They do not replace later accuracy tests with 10 and 20 distinct representative blog photos.
