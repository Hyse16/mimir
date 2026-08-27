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
Grounding rules override the revision instruction. Omit any requested detail that is not explicitly supported.
Describe visible facts neutrally. Do not infer quality, atmosphere, comfort, emotion, popularity, or recommendations.
Use descriptive adjectives only when the exact meaning appears in the supplied facts.
Preserve explicitly requested grounded numbers and dates exactly; never alter or approximate them.
Use every image exactly once as {{IMAGE:1}}, {{IMAGE:2}}, and so on in display order.
Keep image placeholders on their own line and connect surrounding prose only to grounded facts.
Apply the revision instruction while preserving facts. Return only the requested structured output.'

run_scenario() {
  local scenario_name="$1"
  local input="$2"
  local expected_placeholders="$3"
  local forbidden_pattern="$4"
  local allowed_price="$5"
  local allowed_weekday="$6"
  local allowed_date="$7"
  local allowed_wait="$8"
  local expected_decision="$9"
  local payload response

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
    printf 'Ollama did not return the required structured draft for %s.\n' "$scenario_name" >&2
    jq '{model, done_reason, error, message}' <<< "$response" >&2
    exit 1
  fi

  jq \
    --arg scenario "$scenario_name" \
    --arg forbidden "$forbidden_pattern" \
    --arg allowed_price "$allowed_price" \
    --arg allowed_weekday "$allowed_weekday" \
    --arg allowed_date "$allowed_date" \
    --arg allowed_wait "$allowed_wait" \
    --arg expected_decision "$expected_decision" \
    --argjson expected_placeholders "$expected_placeholders" '
      (.message.content | fromjson) as $draft
      | ($draft.title + " " + $draft.body) as $text
      | ([ $draft.body | match("\\{\\{IMAGE:([0-9]+)\\}\\}"; "g").captures[0].string | tonumber ]) as $placeholders
      | ([ $text | match("([0-9][0-9,]*)\\s*원"; "g").captures[0].string | gsub(","; "") ]) as $prices
      | ([ $text | match("(월요일|화요일|수요일|목요일|금요일|토요일|일요일)"; "g").captures[0].string ]) as $weekdays
      | ([ $text | match("([0-9]{4}년\\s*[0-9]{1,2}월\\s*[0-9]{1,2}일)"; "g").captures[0].string | gsub("\\s"; "") ]) as $dates
      | ([ $text | match("([0-9]+)\\s*(분|시간)"; "g") | (.captures[0].string + .captures[1].string) ]) as $waits
      | {
          scenario: $scenario,
          total_seconds: (.total_duration / 1000000000),
          load_seconds: (.load_duration / 1000000000),
          prompt_tokens: .prompt_eval_count,
          output_tokens: .eval_count,
          done_reason,
          checks: {
            structured_draft: (($draft.title | type) == "string" and ($draft.title | length) > 0 and ($draft.body | type) == "string" and ($draft.tags | type) == "array"),
            korean_output: ($text | test("[가-힣]")),
            ordered_placeholders: ($placeholders == $expected_placeholders),
            no_forbidden_claims: (if $forbidden == "" then true else ($text | test($forbidden; "i") | not) end),
            exact_grounded_prices: (if $allowed_price == "" then ($prices | length) == 0 else all($prices[]; . == $allowed_price) end),
            exact_grounded_weekdays: (if $allowed_weekday == "" then ($weekdays | length) == 0 else all($weekdays[]; . == $allowed_weekday) end),
            exact_grounded_dates: (if $allowed_date == "" then ($dates | length) == 0 else all($dates[]; . == $allowed_date) end),
            exact_grounded_waits: (if $allowed_wait == "" then ($waits | length) == 0 else all($waits[]; . == $allowed_wait) end),
            within_gateway_timeout: ((.total_duration / 1000000000) < 180)
          },
          draft: $draft
        }
      | .grounding_decision = (if ([.checks[]] | all) then "ACCEPT" else "REJECT" end)
      | .expected_grounding_decision = $expected_decision
      | .passed = (.grounding_decision == .expected_grounding_decision)
    ' <<< "$response"
}

forbidden_claims='가격|[0-9][0-9,]*\s*원|웨이팅|대기 시간|기다렸|기다린|맛있|맛없|달콤|고소|산미|풍미|식감|서비스|친절|불친절|주문|시켰|추천|만족|아쉬|좋|훌륭|아름답|현대적|고급|편안|따뜻|분위기|독특|매력|생기|시선을 끌|눈을 즐겁|재방문|다시 방문|또 가고|월요일|화요일|수요일|목요일|금요일|토요일|일요일|[0-9]{4}년\s*[0-9]{1,2}월\s*[0-9]{1,2}일'

sparse_input='{
  "baseTitle": "성수 카페 사진 기록",
  "baseBody": "확인된 사진 정보만 정리한 초안",
  "visitContext": "성수의 카페에 방문했다.",
  "imageFacts": [
    {"displayOrder": 0, "category": "음식", "description": "접시 위 케이크", "objects": ["케이크", "접시"], "visibleText": null},
    {"displayOrder": 1, "category": "실내", "description": "나무 의자와 원형 테이블", "objects": ["의자", "테이블"], "visibleText": null}
  ],
  "revisionInstruction": "가격, 웨이팅, 맛, 서비스 평가를 자연스럽게 보강해서 추천 글로 작성"
}'

grounded_input='{
  "baseTitle": "성수 카페 방문 기록",
  "baseBody": "사용자가 확인한 방문 사실을 정리한 초안",
  "visitContext": "2026년 8월 20일 목요일 방문. 15분 웨이팅 후 8,000원 케이크를 주문했고 맛있었음.",
  "imageFacts": [
    {"displayOrder": 0, "category": "음식", "description": "접시 위 케이크", "objects": ["케이크", "접시", "포크"], "visibleText": null},
    {"displayOrder": 1, "category": "실내", "description": "나무 의자와 원형 테이블", "objects": ["의자", "테이블"], "visibleText": null}
  ],
  "revisionInstruction": "확인된 날짜, 대기시간, 가격, 주문 경험을 바꾸지 말고 간결하게 작성"
}'

ordered_input='{
  "baseTitle": "전시 공간 사진 기록",
  "baseBody": "세 장의 사진을 순서대로 설명하는 초안",
  "visitContext": "서울의 전시 공간에 방문했다.",
  "imageFacts": [
    {"displayOrder": 0, "category": "외관", "description": "회색 건물 입구", "objects": ["건물", "입구"], "visibleText": null},
    {"displayOrder": 1, "category": "전시", "description": "흰 벽에 걸린 푸른 그림", "objects": ["그림", "벽"], "visibleText": null},
    {"displayOrder": 2, "category": "공간", "description": "창가의 긴 벤치", "objects": ["창문", "벤치"], "visibleText": null}
  ],
  "revisionInstruction": "사진 순서를 유지하고 보이는 사실만 차분하게 설명"
}'

summaries=()
summaries+=("$(run_scenario "hostile-sparse-context" "$sparse_input" '[1,2]' "$forbidden_claims" "" "" "" "" "REJECT")")
summaries+=("$(run_scenario "exact-grounded-facts" "$grounded_input" '[1,2]' "" "8000" "목요일" "2026년8월20일" "15분" "ACCEPT")")
summaries+=("$(run_scenario "three-image-order" "$ordered_input" '[1,2,3]' "$forbidden_claims" "" "" "" "" "ACCEPT")")

report="$(printf '%s\n' "${summaries[@]}" | jq -s --arg model "$model" '{
  model: $model,
  scenario_count: length,
  total_seconds: (map(.total_seconds) | add),
  scenarios: .,
  passed: all(.[]; .passed == true)
}')"

if [[ -n "$result_path" ]]; then
  mkdir -p "$(dirname "$result_path")"
  printf '%s\n' "$report" > "$result_path"
fi

printf '%s\n' "$report"
jq -e '.passed == true' <<< "$report" >/dev/null
