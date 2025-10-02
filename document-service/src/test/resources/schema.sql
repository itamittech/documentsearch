-- Schema for H2 test database
CREATE TABLE IF NOT EXISTS documents (
    document_id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content TEXT,
    metadata TEXT,
    status VARCHAR(50),
    file_size_bytes BIGINT,
    content_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    indexed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_created ON documents(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_status ON documents(status);