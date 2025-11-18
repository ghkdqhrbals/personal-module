#!/bin/bash

# Spring Boot Client 애플리케이션 빌드 및 실행 스크립트
# - Docker 없이 로컬에서 실행

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
MODULE="client"

# Ollama URL 환경변수 기본값 설정 (Spring Boot는 OLLAMA_URL -> ollama.url 매핑)
export OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"

echo "========================================="
echo "🏗️  Building Spring Boot Client"
echo "========================================="
echo "Project Root: $PROJECT_ROOT"
echo "Module: $MODULE"
echo "Ollama URL: $OLLAMA_URL"
echo ""

cd "$PROJECT_ROOT"

# Gradle 빌드 (테스트 제외)
echo "📦 Building with Gradle..."
./gradlew :$MODULE:clean :$MODULE:build -x test

echo ""
echo "========================================="
echo "🚀 Starting Spring Boot Client"
echo "========================================="

# 1차: 모듈 디렉터리 내에서 JAR 찾기
cd "$PROJECT_ROOT/$MODULE"
JAR_FILE=$(find build/libs -type f \( -name "${MODULE}-*.jar" -o -name "*.jar" \) ! -name "*-plain.jar" | head -n 1 || true)

# 2차: 프로젝트 전체에서 JAR 탐색 (백업 경로)
if [[ -z "${JAR_FILE}" ]]; then
  cd "$PROJECT_ROOT"
  JAR_FILE=$(find "$PROJECT_ROOT/$MODULE/build/libs" -type f -name "*.jar" ! -name "*-plain.jar" -print 2>/dev/null | head -n 1 || true)
fi

if [[ -z "${JAR_FILE}" ]]; then
  echo "⚠️  No runnable JAR found. Falling back to Gradle bootRun..."
  echo "   - Tip: You can create a bootJar with './gradlew :$MODULE:bootJar'"
  cd "$PROJECT_ROOT"
  exec ./gradlew :$MODULE:bootRun -x test
fi

echo "Running: $JAR_FILE"

# 환경변수 설정 (필요 시)
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

# JAR 실행
exec java -jar "$JAR_FILE"
