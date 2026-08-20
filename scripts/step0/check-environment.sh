#!/usr/bin/env bash

set -u

status=0

section() {
  printf '\n== %s ==\n' "$1"
}

run_optional() {
  local label="$1"
  shift
  printf '%-18s ' "$label"
  if command -v "$1" >/dev/null 2>&1; then
    "$@" 2>&1 || status=1
  else
    printf 'not installed\n'
    status=1
  fi
}

run_info() {
  local label="$1"
  shift
  printf '%-18s ' "$label"
  if command -v "$1" >/dev/null 2>&1; then
    "$@" 2>&1 || true
  else
    printf 'not installed (optional for STEP 0)\n'
  fi
}

section "Host"
printf 'OS                 %s %s\n' "$(uname -s)" "$(uname -m)"
if [[ "$(uname -s)" == "Darwin" ]]; then
  printf 'Memory bytes       '
  sysctl -n hw.memsize 2>/dev/null || printf 'unavailable\n'
fi

section "Toolchain"
printf '%-18s ' "Python"
if command -v python3 >/dev/null 2>&1; then
  python3 --version 2>&1
  if ! python3 -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)'; then
    printf 'Python 3.10 or newer is required by Flet (project target: 3.12).\n'
    status=1
  fi
else
  printf 'not installed\n'
  status=1
fi
run_optional "Java" java -version
run_optional "Java compiler" javac -version
run_optional "Node.js" node --version
run_optional "npm" npm --version

section "Containers"
run_optional "Docker CLI" docker --version
if command -v docker >/dev/null 2>&1; then
  printf '%-18s ' "Docker daemon"
  docker info --format '{{.ServerVersion}}' 2>&1 || status=1
  printf '%-18s ' "Docker Compose"
  docker compose version 2>&1 || status=1
fi

section "Local AI"
run_optional "Ollama" ollama --version
if command -v ollama >/dev/null 2>&1; then
  printf '%-18s\n' "Ollama models"
  ollama list 2>&1 || status=1
fi
printf '%-18s ' "Hermes"
if command -v hermes >/dev/null 2>&1; then
  if command -v perl >/dev/null 2>&1; then
    perl -e 'alarm shift; exec @ARGV' 10 hermes --version 2>&1 || {
      printf 'version check timed out or failed\n'
      status=1
    }
  else
    hermes --version 2>&1 || status=1
  fi
else
  printf 'not installed\n'
  status=1
fi

section "Database client"
run_info "PostgreSQL" psql --version

printf '\nProbe exit status: %d\n' "$status"
exit "$status"
