#!/usr/bin/env bash

set -euo pipefail

model="${1:-qwen2.5:7b}"
runs="${STEP0_TEXT_RUNS:-3}"
result_path="${STEP0_TEXT_RESULT_PATH:-}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! [[ "$runs" =~ ^[0-9]+$ ]] || ((runs < 2 || runs > 10)); then
  printf 'STEP0_TEXT_RUNS must be an integer between 2 and 10.\n' >&2
  exit 1
fi

suite_tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/mimir-text.XXXXXX")"
trap 'rm -rf "$suite_tmp_dir"' EXIT

for ((run_number = 1; run_number <= runs; run_number++)); do
  "$script_dir/benchmark-text.sh" "$model" > "$suite_tmp_dir/run-$run_number.json"
  jq -n \
    --argjson run "$run_number" \
    --argjson total "$runs" \
    --argjson seconds "$(jq '.total_seconds' "$suite_tmp_dir/run-$run_number.json")" \
    '{progress: {run: $run, total: $total, seconds: $seconds}}' >&2
done

summary="$(jq -s \
  --arg model "$model" \
  '{
    model: $model,
    run_count: length,
    total_seconds: (map(.total_seconds) | add),
    average_seconds: (map(.total_seconds) | add / length),
    minimum_seconds: (map(.total_seconds) | min),
    maximum_seconds: (map(.total_seconds) | max),
    average_generation_tokens_per_second: (map(.generation_tokens_per_second) | add / length),
    runs: .
  }' "$suite_tmp_dir"/run-*.json)"

if [[ -n "$result_path" ]]; then
  mkdir -p "$(dirname "$result_path")"
  printf '%s\n' "$summary" > "$result_path"
fi

printf '%s\n' "$summary"
