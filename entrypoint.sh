#!/bin/bash

set -e

echo "=========================================="
echo "Starting Ollama server with parallel processing enabled..."
echo "OLLAMA_NUM_PARALLEL: ${OLLAMA_NUM_PARALLEL:-default}"
echo "OLLAMA_MAX_LOADED_MODELS: ${OLLAMA_MAX_LOADED_MODELS:-default}"
echo "OLLAMA_MAX_QUEUE: ${OLLAMA_MAX_QUEUE:-default}"
echo "OLLAMA_FLASH_ATTENTION: ${OLLAMA_FLASH_ATTENTION:-default}"
echo "OLLAMA_KEEP_ALIVE: ${OLLAMA_KEEP_ALIVE:-default}"
echo "=========================================="

ollama serve &

# Ollama 서버가 준비될 때까지 대기
echo "Waiting for Ollama server to be ready..."
for i in {1..30}; do
    if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
        echo "Ollama server is ready!"
        break
    fi
    echo "Waiting... ($i/30)"
    sleep 2
done

# 환경변수에서 모델 이름 가져오기 (기본값: gemma3)
MODEL_NAME=${OLLAMA_MODEL:-gemma3}

echo "Checking for model: $MODEL_NAME"

# 모델이 이미 존재하는지 확인
if ollama list | grep -q "$MODEL_NAME"; then
    echo "Model $MODEL_NAME already exists"
else
    echo "Pulling model: $MODEL_NAME (this may take a few minutes)..."
    ollama pull "$MODEL_NAME"
    echo "Model $MODEL_NAME pulled successfully"
fi

# 모델 목록 출력
echo "Available models:"
ollama list

echo "Ollama is ready with model: $MODEL_NAME"
echo "Server running at http://localhost:11434"

# 백그라운드 프로세스 유지
wait

