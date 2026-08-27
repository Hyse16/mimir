#!/usr/bin/env bash

set -euo pipefail

model="${1:-qwen2.5:7b}"
ollama_url="${OLLAMA_URL:-http://127.0.0.1:11434}"
result_path="${TEXT_RESULT_PATH:-}"

for command_name in curl jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$command_name" >&2
    exit 1
  fi
done

prompt='Write a Korean Naver blog draft using only the supplied user context, existing draft, and visible image facts.
Never invent prices, wait times, taste, service quality, visit dates, orders, opinions, or personal experiences.
Use every image exactly once as {{IMAGE:1}}, {{IMAGE:2}}, and so on in display order.
Keep image placeholders on their own line and connect surrounding prose only to grounded facts.
Apply the revision instruction while preserving facts. Return only the requested structured output.'

input='{
  "baseTitle": "성수 카페 기록",
  "baseBody": "사진과 사실 메모를 바탕으로 작성할 초안",
  "visitContext": "지난 토요일 친구와 성수에서 방문. 바스크 치즈케이크가 제일 맛있었음. 커피는 산미가 조금 강했음.",
  "imageFacts": [
    {"displayOrder": 0, "category": "음식", "description": "접시 위 바스크 치즈케이크", "objects": ["케이크", "접시", "포크"], "visibleText": null},
    {"displayOrder": 1, "category": "실내", "description": "원형 테이블과 나무 의자가 있는 카페 실내", "objects": ["테이블", "의자", "화분"], "visibleText": null}
  ],
  "revisionInstruction": "사실을 유지하고 편안한 존댓말로 간결하게 작성"
}'

payload="$(jq -n \
  --arg model "$model" \
  --arg content "$prompt
INPUT_JSON:
$input" \
  '{
    model: $model,
    messages: [{role: "user", content: $content}],
    stream: false,
    format: {
      type: "object",
      additionalProperties: false,
      properties: {
        title: {type: "string"},
        body: {type: "string"},
        tags: {type: "array", items: {type: "string"}, maxItems: 30}
      },
      required: ["title", "body", "tags"]
    },
    options: {temperature: 0}
  }')"

response="$(curl --fail --silent --show-error --max-time 180 \
  "$ollama_url/api/chat" \
  --header 'Content-Type: application/json' \
  --data-binary "$payload")"

if ! jq -e '.message.content | fromjson' <<< "$response" >/dev/null; then
  printf 'Ollama did not return the required structured draft.\n' >&2
  jq '{model, done_reason, error, message}' <<< "$response" >&2
  exit 1
fi

summary="$(jq '
  (.message.content | fromjson) as $draft
  | ([ $draft.body | match("\\{\\{IMAGE:([0-9]+)\\}\\}"; "g").captures[0].string | tonumber ]) as $placeholders
  | {
      model,
      total_seconds: (.total_duration / 1000000000),
      load_seconds: (.load_duration / 1000000000),
      prompt_tokens: .prompt_eval_count,
      output_tokens: .eval_count,
      done_reason,
      checks: {
        structured_draft: (($draft.title | type) == "string" and ($draft.title | length) > 0 and ($draft.body | type) == "string" and ($draft.tags | type) == "array"),
        korean_output: (($draft.title + " " + $draft.body) | test("[가-힣]")),
        ordered_placeholders: ($placeholders == [1, 2]),
        no_unsupported_price_wait_service_order: (($draft.title + " " + $draft.body) | test("가격|[0-9]+[,.]?[0-9]*\\s*원|웨이팅|대기 시간|서비스|친절|불친절|주문|시켰|재방문|다시 방문"; "i") | not),
        within_gateway_timeout: ((.total_duration / 1000000000) < 180)
      },
      draft: $draft
    }
  | .passed = ([.checks[]] | all)
' <<< "$response")"

if [[ -n "$result_path" ]]; then
  mkdir -p "$(dirname "$result_path")"
  printf '%s\n' "$summary" > "$result_path"
fi

printf '%s\n' "$summary"
jq -e '.passed == true' <<< "$summary" >/dev/null
