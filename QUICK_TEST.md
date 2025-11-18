# Quick Test Guide

## 빠른 테스트 방법

### 1. 통합 실행 테스트
```bash
# 프로젝트 루트에서 실행
cd /Users/ghkdqhrbals/personal/mod
./start-all.sh
```

### 2. Ollama만 테스트
```bash
# 병렬 처리 설정과 함께 실행
export OLLAMA_NUM_PARALLEL=100
export OLLAMA_MAX_LOADED_MODELS=2
./start-ollama.sh

# 다른 터미널에서 단일 요청 테스트
curl http://localhost:11434/api/generate -d '{
  "model": "gemma3",
  "prompt": "What is machine learning?",
  "stream": false
}'

# 병렬 요청 테스트 (50개 동시 요청)
for i in {1..50}; do
  curl -s http://localhost:11434/api/generate -d '{
    "model": "gemma3",
    "prompt": "Test request '$i'",
    "stream": false
  }' &
done
wait
echo "All parallel requests completed"
```

### 3. Spring Boot만 테스트
```bash
# Ollama가 이미 실행 중이어야 함
./start-client.sh

# 다른 터미널에서 API 테스트
curl http://localhost:8080/actuator/health
```

### 4. Redis Stream 테스트
```bash
# Redis에 메시지 추가
redis-cli XADD paper:summary:stream '*' payload '{"abstract":"Test paper abstract","maxLength":150}'

# 로그에서 처리 확인
# Spring Boot 콘솔에서 "[STREAM] Received message" 로그 확인
```

## 체크리스트

- [ ] Redis 실행 확인: `redis-cli ping` → `PONG`
- [ ] Ollama 설치 확인: `ollama --version`
- [ ] Java 버전 확인: `java -version` (17+)
- [ ] 스크립트 실행 권한: `ls -l start-*.sh` (rwxr-xr-x)
- [ ] 포트 사용 가능: 8080 (Spring), 11434 (Ollama), 6379 (Redis)

## 예상 출력

### start-all.sh 성공시
```
=========================================
🚀 Starting All Services
=========================================

🔍 Checking prerequisites...
✅ Ollama is installed
✅ Java is installed (java version "17.0.9")
✅ Redis is running on localhost:6379

=========================================
Step 1: Starting Ollama
=========================================
✅ Ollama server is ready!
✅ gemma3 model already exists

=========================================
Step 2: Building and Starting Spring Boot
=========================================
BUILD SUCCESSFUL in 10s
Running: build/libs/client-0.1.0.jar

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

Started ClientApplication in 5.123 seconds
```

## 문제 발생시

### Redis 연결 실패
```bash
brew services start redis
```

### Ollama 모델 다운로드 실패
```bash
# 수동으로 다운로드
ollama pull gemma3
```

### 빌드 실패
```bash
# 깨끗한 빌드
./gradlew clean :client:build -x test
```

### 포트 충돌
```bash
# 사용 중인 프로세스 확인
lsof -i :8080
lsof -i :11434

# 프로세스 종료
kill -9 <PID>
```

