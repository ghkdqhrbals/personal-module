#!/bin/sh

# Ollama 서버 백그라운드로 시작
echo "Starting Ollama server..."
ollama serve &

# Ollama 서버가 준비될 때까지 대기
echo "Waiting for Ollama server to be ready..."
sleep 10

# gemma3 모델이 이미 존재하는지 확인
if ollama list | grep -q "gemma3"; then
    echo "gemma3 model already exists, skipping download"
else
    echo "Downloading gemma3 model..."
    ollama pull gemma3
fi

# 모델 준비 완료 확인
echo "Verifying gemma3 model..."
ollama list

echo "Ollama is ready with gemma3 model!"

# 백그라운드 프로세스 대기
wait

