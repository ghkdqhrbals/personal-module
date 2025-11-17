#!/bin/bash

# Ollama 동시 요청 테스트 엔드포인트 테스트 스크립트
# 사용법: ./test-ollama-parallel-endpoint.sh

BASE_URL="${BASE_URL:-http://localhost:8080}"
ENDPOINT="${BASE_URL}/api/chat/test-ollama-parallel"

echo "========================================="
echo "Ollama 동시 요청 테스트"
echo "========================================="
echo "URL: ${ENDPOINT}"
echo ""

# GET 요청으로 테스트 실행
echo "요청 보내는 중..."
RESPONSE=$(curl -s -X GET "${ENDPOINT}" \
  -H "Content-Type: application/json")

# 응답 확인
if [ $? -eq 0 ]; then
    echo "✅ 요청 성공!"
    echo ""
    echo "응답 내용:"
    echo "${RESPONSE}" | jq '.'
    echo ""

    # 요약 정보 추출
    TOTAL=$(echo "${RESPONSE}" | jq -r '.totalRequests')
    SUCCESS=$(echo "${RESPONSE}" | jq -r '.successCount')
    FAILURE=$(echo "${RESPONSE}" | jq -r '.failureCount')
    TOTAL_TIME=$(echo "${RESPONSE}" | jq -r '.totalTimeMs')
    AVG_TIME=$(echo "${RESPONSE}" | jq -r '.averageTimeMs')

    echo "========================================="
    echo "📊 테스트 결과 요약"
    echo "========================================="
    echo "총 요청 수: ${TOTAL}"
    echo "성공: ${SUCCESS}"
    echo "실패: ${FAILURE}"
    echo "총 소요 시간: ${TOTAL_TIME}ms"
    echo "평균 응답 시간: ${AVG_TIME}ms"
    echo "========================================="
else
    echo "❌ 요청 실패!"
    echo "${RESPONSE}"
fi

