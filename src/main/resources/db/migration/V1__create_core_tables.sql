CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_app_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_knowledge_base_owner
        FOREIGN KEY (owner_id)
        REFERENCES app_user (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_base_owner_id
    ON knowledge_base (owner_id);

CREATE TABLE document (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_knowledge_base
        FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_base (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_document_status
        CHECK (status IN ('UPLOADED', 'PARSING', 'READY', 'FAILED')),
    CONSTRAINT ck_document_size
        CHECK (size_bytes >= 0)
);

CREATE INDEX idx_document_knowledge_base_id
    ON document (knowledge_base_id);

CREATE TABLE document_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_chunk_document
        FOREIGN KEY (document_id)
        REFERENCES document (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_document_chunk_position
        UNIQUE (document_id, chunk_index),
    CONSTRAINT ck_document_chunk_index
        CHECK (chunk_index >= 0),
    CONSTRAINT ck_document_chunk_token_count
        CHECK (token_count IS NULL OR token_count >= 0)
);

CREATE INDEX idx_document_chunk_document_id
    ON document_chunk (document_id);

CREATE TABLE chat_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_session_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chat_session_user_id
    ON chat_session (user_id);

CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    message_role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id)
        REFERENCES chat_session (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_chat_message_role
        CHECK (message_role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);

CREATE INDEX idx_chat_message_session_id
    ON chat_message (session_id);

CREATE TABLE work_order (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_work_order_status
        CHECK (status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_work_order_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

CREATE INDEX idx_work_order_user_id
    ON work_order (user_id);

CREATE INDEX idx_work_order_status
    ON work_order (status);