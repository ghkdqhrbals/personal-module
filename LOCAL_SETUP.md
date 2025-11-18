# 🚀 로컬 실행 가이드

## 📋 사전 준비사항

### 1. Redis 설치 및 실행
```bash
# macOS (Homebrew)
brew install redis
brew services start redis

# 또는 직접 실행
redis-server

# 실행 확인
redis-cli ping
# 응답: PONG
```

### 2. Ollama 설치
```bash
# macOS
brew install ollama

# 또는 공식 사이트에서 다운로드
# https://ollama.ai
```

### 3. Java 17+ 확인
```bash
java -version
# java version "17" 이상이어야 함
```

---

## 🎯 통합 실행 (권장)

### 한 번에 모든 서비스 실행
```bash
./start-all.sh
```

이 스크립트는 다음을 자동으로 수행합니다:
1. ✅ **사전 준비사항 확인** (Java, Ollama, Redis)
2. 🚀 **Ollama 서버 시작** 및 gemma3 모델 로드
3. 🏗️ **Spring Boot 빌드** (client 모듈)
4. 🚀 **Spring Boot 실행**

### 실행 로그 예시
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
🚀 Starting Ollama Server
📦 Starting Ollama server...
✅ Ollama server is ready!
✅ gemma3 model already exists

=========================================
Step 2: Building and Starting Spring Boot
=========================================
🏗️  Building Spring Boot Client
📦 Building with Gradle...
BUILD SUCCESSFUL
🚀 Starting Spring Boot Client
```

---

## 🔧 개별 실행

### Ollama만 실행
```bash
./start-ollama.sh
```

**기능:**
- Ollama 서버 시작 (포트 11434)
- gemma3 모델 자동 다운로드 (없는 경우)
- 모델 준비 상태 확인

### Spring Boot만 실행
```bash
./start-client.sh
```

**기능:**
- Gradle 빌드 (테스트 제외)
- JAR 파일 자동 찾기
- Spring Boot 애플리케이션 실행

---

## 🛠️ 환경 변수 설정 (선택사항)

### Ollama 설정
```bash
# 다른 모델 사용
export OLLAMA_MODEL=llama2

# 다른 포트 사용
export OLLAMA_PORT=11435

# 병렬 처리 설정
export OLLAMA_NUM_PARALLEL=50          # 동시 처리 요청 수 (기본: 50)
export OLLAMA_MAX_LOADED_MODELS=1      # 동시 로드 모델 수 (기본: 1, 메모리 충분하면 증가 가능)
export OLLAMA_MAX_QUEUE=512            # 대기열 크기 (기본: 512)
export OLLAMA_KEEP_ALIVE=5m            # 모델 메모리 유지 시간 (기본: 5분)

# GPU 설정 (선택사항)
# export OLLAMA_NUM_GPU=1              # 사용할 GPU 수
```

### Spring 프로파일
```bash
# 로컬 프로파일 활성화
export SPRING_PROFILES_ACTIVE=local

# 특정 환경 변수 설정
export OPENAI_API_KEY=sk-...
export SERPAPI_KEY=...
```

---

## 🔍 문제 해결

### Redis 연결 실패
```bash
# Redis 실행 확인
redis-cli ping

# Redis 시작
brew services start redis
```

### Ollama 서버 시작 실패
```bash
# Ollama 로그 확인
tail -f /tmp/ollama-server.log

# 기존 Ollama 프로세스 종료
pkill -f "ollama serve"

# 다시 시작
./start-ollama.sh
```

### 포트 충돌
```bash
# 포트 사용 확인 (11434: Ollama, 8080: Spring Boot)
lsof -i :11434
lsof -i :8080

# 프로세스 종료
kill -9 <PID>
```

### 빌드 실패
```bash
# Gradle 캐시 삭제
./gradlew clean

# 의존성 다시 다운로드
./gradlew :client:build --refresh-dependencies
```

---

## 📊 서비스 접속

### Spring Boot Client
- **Base URL**: http://localhost:8080
- **Health Check**: http://localhost:8080/actuator/health
- **Paper API**: http://localhost:8080/api/v1/paper

### Ollama
- **Base URL**: http://localhost:11434
- **Models List**: http://localhost:11434/api/tags
- **Test**: 
  ```bash
  curl http://localhost:11434/api/generate -d '{
    "model": "gemma3",
    "prompt": "Hello!",
    "stream": false
  }'
  ```

### Redis
- **Host**: localhost
- **Port**: 6379
- **CLI**: `redis-cli`

---

## 🛑 서비스 중지

### Spring Boot 중지
```bash
# Ctrl + C 로 종료
```

### Ollama 중지
```bash
pkill -f "ollama serve"
```

### Redis 중지
```bash
brew services stop redis
# 또는: Ctrl + C (직접 실행한 경우)
```

### 모든 서비스 중지
```bash
# Spring Boot: Ctrl + C
# Ollama
pkill -f "ollama serve"
# Redis
brew services stop redis
```

---

## 💡 팁

### 백그라운드 실행
```bash
# Ollama 백그라운드 실행
nohup ./start-ollama.sh > ollama.log 2>&1 &

# Spring Boot 백그라운드 실행
nohup ./start-client.sh > client.log 2>&1 &
```

### 로그 모니터링
```bash
# Spring Boot 로그
tail -f client.log

# Ollama 로그
tail -f /tmp/ollama-server.log

# Redis 로그
tail -f /usr/local/var/log/redis.log
```

### 성능 모니터링
```bash
# CPU/메모리 사용량
top -pid $(pgrep -f "ollama serve")
top -pid $(pgrep -f "client.*jar")
```

