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