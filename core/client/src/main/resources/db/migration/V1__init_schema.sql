-- 초기 스키마 생성

-- Paper 테이블
CREATE TABLE IF NOT EXISTS paper (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    arxiv_id VARCHAR(32),
    title VARCHAR(255),
    author TEXT,
    published_at DATE,
    search_date DATE,
    summarized_at TIMESTAMP(6),
    url VARCHAR(255),
    journal VARCHAR(255),
    impact_factor DOUBLE,
    summary TEXT,
    novelty VARCHAR(255),
    INDEX idx_arxiv_id (arxiv_id),
    INDEX idx_search_date (search_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Subscribe 테이블
CREATE TABLE IF NOT EXISTS subscribes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    subscribe_type VARCHAR(50) NOT NULL,
    activated BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    INDEX idx_name (name),
    INDEX idx_activated (activated)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Users 테이블
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255),
    name VARCHAR(255),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- OAuth Provider 테이블
CREATE TABLE IF NOT EXISTS oauth_provider (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    kind VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6),
    UNIQUE KEY uk_kind_provider_id (kind, provider_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

