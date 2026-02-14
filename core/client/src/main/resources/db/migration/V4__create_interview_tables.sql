-- Interview Session tables
-- This migration adds support for AI Interview feature with conversation context

CREATE TABLE IF NOT EXISTS interview_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_name VARCHAR(255),
    cv_text TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    opening_question TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS interview_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    turn_number INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_session_turn (session_id, turn_number),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) 
        REFERENCES interview_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
