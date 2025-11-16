#!/bin/bash

# 학술 논문 검색 API 테스트 스크립트

BASE_URL="http://localhost:8080"

echo "=========================================="
echo "학술 논문 검색 API 테스트"
echo "=========================================="
echo ""

# 1. 지원 카테고리 목록 조회
echo "1. 지원 카테고리 목록 조회"
echo "GET $BASE_URL/api/papers/categories"
curl -s "$BASE_URL/api/papers/categories" | jq '.'
echo ""
echo ""

# 2. Computer Science 카테고리의 주요 저널 조회
echo "2. Computer Science 카테고리의 주요 저널 조회 (Impact Factor >= 15)"
echo "GET $BASE_URL/api/papers/journals/Computer%20Science?minImpactFactor=15.0"
curl -s "$BASE_URL/api/papers/journals/Computer%20Science?minImpactFactor=15.0" | jq '.'
echo ""
echo ""

# 3. Computer Science 카테고리에서 논문 검색
echo "3. Computer Science 카테고리에서 논문 검색 (Impact Factor >= 10)"
echo "POST $BASE_URL/api/papers/search"
curl -s -X POST "$BASE_URL/api/papers/search" \
  -H "Content-Type: application/json" \
  -d '{
    "category": "Computer Science",
    "minImpactFactor": 10.0,
    "maxResults": 5
  }' | jq '.'
echo ""
echo ""

# 4. 특정 저널의 최신 논문 조회
echo "4. Nature 저널의 최신 논문 조회"
echo "GET $BASE_URL/api/papers/journal/Nature?maxResults=3"
curl -s "$BASE_URL/api/papers/journal/Nature?maxResults=3" | jq '.'
echo ""
echo ""

# 5. 논문 초록 요약 (OpenAI API 키가 설정된 경우에만 작동)
echo "5. 논문 초록 요약 테스트"
echo "POST $BASE_URL/api/papers/summarize"
curl -s -X POST "$BASE_URL/api/papers/summarize" \
  -H "Content-Type: application/json" \
  -d '{
    "abstract": "Deep learning has revolutionized the field of artificial intelligence in recent years. This paper presents a comprehensive survey of deep learning techniques and their applications in computer vision, natural language processing, and speech recognition. We discuss the fundamental architectures including convolutional neural networks, recurrent neural networks, and transformer models. Our analysis reveals that transformer-based models have achieved state-of-the-art results across multiple domains.",
    "maxLength": 50
  }' | jq '.'
echo ""
echo ""

echo "=========================================="
echo "테스트 완료!"
echo "=========================================="

