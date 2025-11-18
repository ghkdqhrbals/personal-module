-- Event Store 테이블 생성
CREATE TABLE IF NOT EXISTS event_store (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_aggregate_id ON event_store(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_event_type ON event_store(event_type);
CREATE INDEX IF NOT EXISTS idx_timestamp ON event_store(timestamp);

-- Aggregate ID와 Version의 조합으로 unique 제약 추가 (동시성 제어)
CREATE UNIQUE INDEX IF NOT EXISTS idx_aggregate_version ON event_store(aggregate_id, version);

-- 이벤트 조회 성능을 위한 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_aggregate_type_version ON event_store(aggregate_id, event_type, version);

