#!/bin/bash

# Ollama 서버를 고성능 병렬 처리 설정으로 실행

set -euo pipefail

# 고성능 병렬 처리 설정
export OLLAMA_NUM_PARALLEL=10
export OLLAMA_MAX_LOADED_MODELS=2
export OLLAMA_MAX_QUEUE=64
export OLLAMA_KEEP_ALIVE=10m
export OLLAMA_PORT="${OLLAMA_PORT:-11434}"

echo "========================================="
echo "🚀 Starting Ollama (High Parallel Mode)"
echo "========================================="
echo "⚡ Parallel Requests: ${OLLAMA_NUM_PARALLEL}"
echo "⚡ Max Loaded Models: ${OLLAMA_MAX_LOADED_MODELS}"
echo "⚡ Max Queue Size: ${OLLAMA_MAX_QUEUE}"
echo ""

# start-ollama.sh 호출
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec "${SCRIPT_DIR}/start-ollama.sh"

