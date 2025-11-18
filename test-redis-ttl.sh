#!/bin/bash

echo "======================================"
echo "Redis Event TTL 테스트"
echo "======================================"

# 테스트용 이벤트 ID
EVENT_ID="test-event-$(date +%s)"
KEY="batch:${EVENT_ID}:progress"

echo ""
echo "1. 테스트 이벤트 생성: $KEY"
docker exec -it redis-service redis-cli HSET "$KEY" total 100 completed 0 failed 0

echo ""
echo "2. TTL 60초 설정"
docker exec -it redis-service redis-cli EXPIRE "$KEY" 60

echo ""
echo "3. TTL 확인 (60초로 설정되었는지 확인)"
TTL=$(docker exec -it redis-service redis-cli TTL "$KEY" | tr -d '\r')
echo "   현재 TTL: ${TTL}초"

if [ "$TTL" -gt 50 ] && [ "$TTL" -le 60 ]; then
    echo "   ✅ TTL이 올바르게 설정되었습니다!"
else
    echo "   ❌ TTL 설정에 문제가 있을 수 있습니다."
fi

echo ""
echo "4. 키 내용 확인"
docker exec -it redis-service redis-cli HGETALL "$KEY"

echo ""
echo "5. 10초 대기 후 TTL 재확인"
sleep 10
TTL=$(docker exec -it redis-service redis-cli TTL "$KEY" | tr -d '\r')
echo "   10초 후 TTL: ${TTL}초"

echo ""
echo "======================================"
echo "테스트 완료!"
echo "======================================"
echo ""
echo "💡 참고:"
echo "   - TTL -1: 만료 시간 없음"
echo "   - TTL -2: 키가 존재하지 않음"
echo "   - TTL > 0: 남은 시간(초)"
echo ""
echo "🧹 테스트 키 삭제"
docker exec -it redis-service redis-cli DEL "$KEY"
echo "   삭제 완료: $KEY"

