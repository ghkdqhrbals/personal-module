#!/bin/bash

echo "======================================"
echo "Ollama 병렬 처리 테스트"
echo "======================================"

# 현재 Ollama 설정 확인
echo ""
echo "1. Ollama 컨테이너 환경변수 확인:"
docker exec ollama env | grep OLLAMA

echo ""
echo "2. 병렬 요청 테스트 (50개 동시 요청):"
echo ""

for i in {1..2}; do
  (

    curl -s -X POST http://localhost:11434/api/generate \
      -H "Content-Type: application/json" \
      -d '{
        "model": "gemma3",
        "prompt": "Say hello in one word ",
        "stream": false
      }' > /dev/null


    echo "요청 #$i 완료"
  ) &
done

# 모든 백그라운드 프로세스 대기
wait

echo ""
echo "======================================"
echo "병렬 테스트 완료!"
echo "======================================"

