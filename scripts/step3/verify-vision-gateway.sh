#!/usr/bin/env bash

set -euo pipefail

model="${1:-gemma4:latest}"
ollama_url="${OLLAMA_URL:-http://127.0.0.1:11434}"
result_path="${VISION_RESULT_PATH:-}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
fixture_dir="$repo_root/fixtures/step0/vision"
fixtures=(
  "$fixture_dir/basque-cheesecake.png"
  "$fixture_dir/iced-latte-croissant.png"
  "$fixture_dir/cafe-interior.png"
)

for command_name in base64 curl jq mktemp; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$command_name" >&2
    exit 1
  fi
done

for fixture in "${fixtures[@]}"; do
  if [[ ! -f "$fixture" ]]; then
    printf 'Vision fixture not found: %s\n' "$fixture" >&2
    exit 1
  fi
done

benchmark_tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/mimir-step3-vision.XXXXXX")"
trap 'rm -rf "$benchmark_tmp_dir"' EXIT

for index in "${!fixtures[@]}"; do
  base64 < "${fixtures[$index]}" | tr -d '\n' | jq -Rs '.' > "$benchmark_tmp_dir/image-$index.json"
done
jq -s '.' "$benchmark_tmp_dir"/image-*.json > "$benchmark_tmp_dir/images.json"

prompt='Analyze each image in order and return only grounded, visible facts.
Never infer prices, wait times, taste, service quality, visit dates, orders, opinions, or personal experience.
Use the least-specific visible label when an exact food, drink, product, or recipe subtype is uncertain.
Do not use speculative phrases to introduce details that are not directly visible.
Include every clearly visible salient object, including tableware and utensils.
Use the zero-based ordinal from the supplied image order. Keep descriptions concise and factual.
Write category, description, and object names in Korean. Preserve visible text exactly as shown.'

jq -n \
  --arg model "$model" \
  --arg prompt "$prompt" \
  --slurpfile images "$benchmark_tmp_dir/images.json" \
  '{
    model: $model,
    messages: [{role: "user", content: $prompt, images: $images[0]}],
    stream: false,
    format: {
      type: "object",
      additionalProperties: false,
      properties: {
        analyses: {
          type: "array",
          minItems: 1,
          maxItems: 4,
          items: {
            type: "object",
            additionalProperties: false,
            properties: {
              ordinal: {type: "integer", minimum: 0},
              category: {type: "string"},
              description: {type: "string"},
              objects: {type: "array", items: {type: "string"}},
              visibleText: {type: ["string", "null"]}
            },
            required: ["ordinal", "category", "description", "objects", "visibleText"]
          }
        }
      },
      required: ["analyses"]
    },
    options: {temperature: 0}
  }' > "$benchmark_tmp_dir/payload.json"

curl --fail --silent --show-error --max-time 180 \
  "$ollama_url/api/chat" \
  --header 'Content-Type: application/json' \
  --data-binary "@$benchmark_tmp_dir/payload.json" > "$benchmark_tmp_dir/response.json"

if ! jq -e '.message.content | fromjson | .analyses' "$benchmark_tmp_dir/response.json" >/dev/null; then
  printf 'Ollama did not return the required structured Vision response.\n' >&2
  jq '{model, done_reason, error, message}' "$benchmark_tmp_dir/response.json" >&2
  exit 1
fi

summary="$(jq '
  (.message.content | fromjson | .analyses) as $analyses
  | def combined($item): ([$item.category, $item.description] + $item.objects | join(" "));
  {
    model,
    fixture_count: 3,
    total_seconds: (.total_duration / 1000000000),
    load_seconds: (.load_duration / 1000000000),
    prompt_tokens: .prompt_eval_count,
    output_tokens: .eval_count,
    done_reason,
    checks: {
      structured_count_and_order: (($analyses | length) == 3 and ([$analyses[].ordinal] | sort) == [0, 1, 2]),
      korean_output: all($analyses[]; combined(.) | test("[가-힣]")),
      cheesecake_facts: (combined($analyses[0]) | test("케이크|치즈|cake|cheese"; "i") and test("접시|plate"; "i") and test("포크|fork"; "i")),
      beverage_croissant_facts: (combined($analyses[1]) | test("음료|라떼|커피|drink|latte|coffee"; "i") and test("크루아상|croissant"; "i")),
      cafe_interior_facts: (combined($analyses[2]) | test("카페|실내|인테리어|cafe|interior"; "i") and test("의자|chair"; "i") and test("테이블|탁자|식탁|table"; "i") and test("식물|화분|plant"; "i")),
      no_invented_visible_text: all($analyses[]; .visibleText == null or .visibleText == ""),
      no_forbidden_experience_claims: (combined({
        category: ($analyses | map(.category) | join(" ")),
        description: ($analyses | map(.description) | join(" ")),
        objects: ($analyses | map(.objects) | add)
      }) | test("가격|[0-9]+원|맛있|맛없|달콤|고소|대기|웨이팅|서비스|방문|주문|추천|카라멜|마키아토|price|cost|delicious|tasty|sweet|wait|service|visited|ordered|recommend|caramel|macchiato"; "i") | not),
      within_gateway_timeout: ((.total_duration / 1000000000) < 180)
    },
    analyses: $analyses
  }
  | .passed = ([.checks[]] | all)
' "$benchmark_tmp_dir/response.json")"

if [[ -n "$result_path" ]]; then
  mkdir -p "$(dirname "$result_path")"
  printf '%s\n' "$summary" > "$result_path"
fi

printf '%s\n' "$summary"
jq -e '.passed == true' <<< "$summary" >/dev/null
