#!/bin/bash

# 통합 실행 스크립트: Ollama + Spring Boot Client
# 이 스크립트는 Ollama 서버를 시작하고 Spring Boot 애플리케이션을 빌드/실행합니다.

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}🚀 Starting All Services${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# 필수 도구 확인
echo -e "${YELLOW}🔍 Checking prerequisites...${NC}"

# Ollama 설치 확인
if ! command -v ollama &> /dev/null; then
    echo -e "${RED}❌ Ollama is not installed!${NC}"
    echo "Please install Ollama from: https://ollama.ai"
    exit 1
fi
echo -e "${GREEN}✅ Ollama is installed${NC}"

# Java 설치 확인
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java is not installed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Java is installed ($(java -version 2>&1 | head -n 1))${NC}"

# Redis 실행 확인
if ! nc -z localhost 6379 2>/dev/null; then
    echo -e "${YELLOW}⚠️  Redis is not running on localhost:6379${NC}"
    echo "Please start Redis before running this script:"
    echo "  brew services start redis"
    echo "  or: redis-server"
    exit 1
fi
echo -e "${GREEN}✅ Redis is running on localhost:6379${NC}"

echo ""

# 1. Ollama 시작
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Step 1: Starting Ollama${NC}"
echo -e "${BLUE}=========================================${NC}"
bash "$PROJECT_ROOT/start-ollama.sh"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to start Ollama${NC}"
    exit 1
fi

echo ""

# 2. Spring Boot 빌드 및 실행
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Step 2: Building and Starting Spring Boot${NC}"
echo -e "${BLUE}=========================================${NC}"
bash "$PROJECT_ROOT/start-client.sh"

# 애플리케이션이 종료되면 정리
echo ""
echo -e "${YELLOW}=========================================${NC}"
echo -e "${YELLOW}🛑 Shutting down services...${NC}"
echo -e "${YELLOW}=========================================${NC}"

# Ollama 서버 종료 (선택사항 - 주석 처리됨)
# echo "Stopping Ollama server..."
# pkill -f "ollama serve" || true

echo -e "${GREEN}✅ All services stopped${NC}"

