#!/usr/bin/env bash

set -euo pipefail

model="${1:-gemma4:latest}"
image_count="${VISION_IMAGE_COUNT:-3}"
batch_size="${VISION_BATCH_SIZE:-3}"
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

if ! [[ "$image_count" =~ ^[0-9]+$ ]] || ((image_count < 1 || image_count > 20)); then
  printf 'VISION_IMAGE_COUNT must be an integer between 1 and 20.\n' >&2
  exit 1
fi

if ! [[ "$batch_size" =~ ^[0-9]+$ ]] || ((batch_size < 2 || batch_size > 4)); then
  printf 'VISION_BATCH_SIZE must be an integer between 2 and 4.\n' >&2
  exit 1
fi

for fixture in "${fixtures[@]}"; do
  if [[ ! -f "$fixture" ]]; then
    printf 'Vision fixture not found: %s\n' "$fixture" >&2
    exit 1
  fi
done

benchmark_tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/mimir-vision.XXXXXX")"
trap 'rm -rf "$benchmark_tmp_dir"' EXIT

results_path="$benchmark_tmp_dir/results.ndjson"
processed=0
batch_number=0

while ((processed < image_count)); do
  remaining=$((image_count - processed))
  current_batch_size="$batch_size"
  if ((remaining < current_batch_size)); then
    current_batch_size="$remaining"
  fi

  batch_number=$((batch_number + 1))
  batch_dir="$benchmark_tmp_dir/batch-$batch_number"
  mkdir -p "$batch_dir"

  for ((batch_offset = 0; batch_offset < current_batch_size; batch_offset++)); do
    fixture_index=$(((processed + batch_offset) % ${#fixtures[@]}))
    base64 < "${fixtures[$fixture_index]}" | tr -d '\n' |
      jq -Rs '.' > "$batch_dir/image-$batch_offset.json"
  done

  jq -s '.' "$batch_dir"/image-*.json > "$batch_dir/images.json"

  prompt="$(printf '입력된 %d개 이미지를 순서대로 분석하세요. 각 이미지마다 displayOrder(1부터 시작), category, description, objects, visibleText를 반환하세요. 사진으로 확인할 수 없는 경험, 가격, 날짜, 맛 평가는 추측하지 마세요.' "$current_batch_size")"

  jq -n \
    --arg model "$model" \
    --arg prompt "$prompt" \
    --slurpfile images "$batch_dir/images.json" \
    '{
      model: $model,
      messages: [{role: "user", content: $prompt, images: $images[0]}],
      stream: false,
      think: false,
      format: {
        type: "object",
        properties: {
          images: {
            type: "array",
            items: {
              type: "object",
              properties: {
                displayOrder: {type: "integer"},
                category: {type: "string"},
                description: {type: "string"},
                objects: {type: "array", items: {type: "string"}},
                visibleText: {type: ["string", "null"]}
              },
              required: ["displayOrder", "category", "description", "objects", "visibleText"]
            }
          }
        },
        required: ["images"]
      },
      options: {temperature: 0, num_predict: 600},
      keep_alive: "10m"
    }' > "$batch_dir/payload.json"

  curl --fail --silent --show-error \
    "$ollama_url/api/chat" \
    --header 'Content-Type: application/json' \
    --data-binary "@$batch_dir/payload.json" > "$batch_dir/response.json"

  if ! batch_result="$(jq -e --argjson expected_count "$current_batch_size" '
    (.message.content | fromjson) as $analysis
    | select(($analysis.images | length) == $expected_count)
    | {
        model,
        expected_images: $expected_count,
        analysis: $analysis.images,
        done_reason,
        total_seconds: (.total_duration / 1000000000),
        load_seconds: (.load_duration / 1000000000),
        prompt_tokens: .prompt_eval_count,
        output_tokens: .eval_count
      }
  ' "$batch_dir/response.json")"; then
    printf 'Vision response validation failed for batch %d.\n' "$batch_number" >&2
    jq '{model, done_reason, error, message}' "$batch_dir/response.json" >&2
    exit 1
  fi

  printf '%s\n' "$batch_result" >> "$results_path"
  jq -n \
    --argjson batch "$batch_number" \
    --argjson processed "$((processed + current_batch_size))" \
    --argjson total "$image_count" \
    --argjson seconds "$(jq '.total_seconds' <<< "$batch_result")" \
    '{progress: {batch: $batch, processed: $processed, total: $total, seconds: $seconds}}' >&2

  processed=$((processed + current_batch_size))
done

summary="$(jq -s \
  --arg model "$model" \
  --argjson image_count "$image_count" \
  --argjson batch_size "$batch_size" \
  '{
    model: $model,
    image_count: $image_count,
    configured_batch_size: $batch_size,
    batch_count: length,
    total_seconds: (map(.total_seconds) | add),
    batches: .
  }' "$results_path")"

if [[ -n "$result_path" ]]; then
  mkdir -p "$(dirname "$result_path")"
  printf '%s\n' "$summary" > "$result_path"
fi

printf '%s\n' "$summary"
