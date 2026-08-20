#!/usr/bin/env bash

set -euo pipefail

model="${1:-qwen2.5:7b}"
ollama_url="${OLLAMA_URL:-http://127.0.0.1:11434}"
prompt="${STEP0_TEXT_PROMPT:-한국어 한 문장으로만 답하세요: 로컬 우선 AI 워크스페이스의 장점은 무엇인가요?}"
max_tokens="${STEP0_TEXT_MAX_TOKENS:-120}"

for command_name in curl jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$command_name" >&2
    exit 1
  fi
done

if ! [[ "$max_tokens" =~ ^[0-9]+$ ]] || ((max_tokens < 1 || max_tokens > 2000)); then
  printf 'STEP0_TEXT_MAX_TOKENS must be an integer between 1 and 2000.\n' >&2
  exit 1
fi

payload="$({
  jq -n \
    --arg model "$model" \
    --arg prompt "$prompt" \
    --argjson max_tokens "$max_tokens" \
    '{
      model: $model,
      prompt: $prompt,
      stream: false,
      options: {temperature: 0, num_predict: $max_tokens}
    }'
})"

curl --fail --silent --show-error \
  "$ollama_url/api/generate" \
  --header 'Content-Type: application/json' \
  --data "$payload" |
  jq '{
    model,
    response,
    done_reason,
    total_seconds: (.total_duration / 1000000000),
    load_seconds: (.load_duration / 1000000000),
    prompt_tokens: .prompt_eval_count,
    output_tokens: .eval_count,
    generation_tokens_per_second: (
      if .eval_duration > 0 then .eval_count / (.eval_duration / 1000000000) else null end
    )
  }'
