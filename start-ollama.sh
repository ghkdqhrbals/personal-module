#!/bin/bash

# Ollama 서버를 백그라운드로 실행하고, 준비(헬스체크)만 확인합니다.
# - Docker 미사용, 로컬 실행
# - 병렬 처리 설정 지원

set -euo pipefail

OLLAMA_PORT="${OLLAMA_PORT:-11434}"

# 병렬 처리 설정 (환경변수로 제어 가능)
export OLLAMA_NUM_PARALLEL="${OLLAMA_NUM_PARALLEL:-50}"           # 동시 처리 요청 수 (기본: 50)
export OLLAMA_MAX_LOADED_MODELS="${OLLAMA_MAX_LOADED_MODELS:-1}"  # 동시에 로드할 모델 수 (기본: 1)
export OLLAMA_MAX_QUEUE="${OLLAMA_MAX_QUEUE:-512}"                # 대기열 최대 크기 (기본: 512)
export OLLAMA_KEEP_ALIVE="${OLLAMA_KEEP_ALIVE:-5m}"               # 모델 메모리 유지 시간 (기본: 5분)

# GPU 관련 설정 (선택사항)
# export OLLAMA_NUM_GPU=1                                         # 사용할 GPU 수
# export OLLAMA_GPU_OVERHEAD=0                                    # GPU 오버헤드 (MB)

echo "========================================="
echo "🚀 Starting Ollama Server (background)"
echo "========================================="
echo "Parallel Requests: ${OLLAMA_NUM_PARALLEL}"
echo "Max Loaded Models: ${OLLAMA_MAX_LOADED_MODELS}"
echo "Max Queue Size: ${OLLAMA_MAX_QUEUE}"
echo "Keep Alive: ${OLLAMA_KEEP_ALIVE}"
echo "Port: ${OLLAMA_PORT}"
echo ""

# 이미 실행 중인지 확인
if lsof -Pi :"${OLLAMA_PORT}" -sTCP:LISTEN -t >/dev/null 2>&1 ; then
  echo "✅ Ollama server is already running on port ${OLLAMA_PORT}"
  echo "   To apply new settings, stop existing server first:"
  echo "   pkill -f 'ollama serve'"
else
  echo "📦 Starting Ollama server in background with parallel settings..."
  nohup ollama serve > /tmp/ollama-server.log 2>&1 &
  OLLAMA_PID=$!
  echo "Ollama server started with PID: ${OLLAMA_PID}"
  echo "   - Logs: /tmp/ollama-server.log"
fi

# 헬스체크 (최대 30초 대기)
echo "⏳ Waiting for Ollama health (http://localhost:${OLLAMA_PORT}/api/tags) ..."
for i in {1..30}; do
  if curl -s "http://localhost:${OLLAMA_PORT}/api/tags" >/dev/null 2>&1; then
    echo "✅ Ollama server is healthy!"
    exit 0
  fi
  sleep 1
  if [ "$i" -eq 30 ]; then
    echo "❌ Ollama server did not become healthy within 30 seconds"
    echo "   - See logs: /tmp/ollama-server.log"
    exit 1
  fi
done
