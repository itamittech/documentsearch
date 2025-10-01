-- Document Search Service - Database Schema
-- PostgreSQL 15+

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Tenants table
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    rate_limit_per_minute INTEGER DEFAULT 1000,
    storage_quota_gb INTEGER DEFAULT 100,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN DEFAULT TRUE,
    
    CONSTRAINT tenants_name_unique UNIQUE (name)
);

-- Create index on active tenants
CREATE INDEX idx_tenants_active ON tenants(is_active) WHERE is_active = TRUE;

-- Documents table
CREATE TABLE IF NOT EXISTS documents (
    document_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    content TEXT,
    metadata_json JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    file_size_bytes BIGINT,
    content_hash VARCHAR(64),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    indexed_at TIMESTAMP,
    
    CONSTRAINT documents_status_check CHECK (status IN ('PENDING', 'INDEXING', 'INDEXED', 'FAILED', 'DELETED'))
);

-- Indexes for documents table
CREATE INDEX idx_documents_tenant_created ON documents(tenant_id, created_at DESC);
CREATE INDEX idx_documents_tenant_status ON documents(tenant_id, status);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_hash ON documents(content_hash);
CREATE INDEX idx_documents_metadata ON documents USING GIN (metadata_json);

-- API Keys table
CREATE TABLE IF NOT EXISTS api_keys (
    key_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    api_key_hash VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    scopes TEXT[] DEFAULT ARRAY['read', 'write'],
    rate_limit_per_minute INTEGER,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Indexes for api_keys table
CREATE INDEX idx_api_keys_hash ON api_keys(api_key_hash);
CREATE INDEX idx_api_keys_tenant ON api_keys(tenant_id);
CREATE INDEX idx_api_keys_active ON api_keys(is_active) WHERE is_active = TRUE;

-- Audit logs table
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(tenant_id),
    user_id VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    ip_address INET,
    user_agent TEXT,
    request_payload JSONB,
    response_status INTEGER,
    timestamp TIMESTAMP DEFAULT NOW()
);

-- Indexes for audit_logs
CREATE INDEX idx_audit_logs_tenant_timestamp ON audit_logs(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);

-- Search analytics table (for popular queries)
CREATE TABLE IF NOT EXISTS search_analytics (
    analytics_id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    query_text TEXT NOT NULL,
    result_count INTEGER,
    search_time_ms INTEGER,
    cache_hit BOOLEAN DEFAULT FALSE,
    timestamp TIMESTAMP DEFAULT NOW()
);

-- Indexes for search_analytics
CREATE INDEX idx_search_analytics_tenant_timestamp ON search_analytics(tenant_id, timestamp DESC);
CREATE INDEX idx_search_analytics_query ON search_analytics(query_text);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for documents table
CREATE TRIGGER update_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger for tenants table
CREATE TRIGGER update_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert sample tenants
INSERT INTO tenants (tenant_id, name, rate_limit_per_minute, storage_quota_gb)
VALUES 
    ('550e8400-e29b-41d4-a716-446655440000', 'Demo Tenant 1', 1000, 100),
    ('550e8400-e29b-41d4-a716-446655440001', 'Demo Tenant 2', 2000, 200),
    ('550e8400-e29b-41d4-a716-446655440002', 'Demo Tenant 3', 500, 50)
ON CONFLICT (tenant_id) DO NOTHING;

-- Insert sample API keys (hashed)
-- Format: sk_live_tenant123_randomstring
-- Note: In production, these should be properly hashed using BCrypt or similar
INSERT INTO api_keys (tenant_id, api_key_hash, description)
VALUES 
    ('550e8400-e29b-41d4-a716-446655440000', 'sk_live_tenant123_abc123def456ghi789jkl012', 'Demo API Key 1'),
    ('550e8400-e29b-41d4-a716-446655440001', 'sk_live_tenant456_xyz789uvw456rst123opq456', 'Demo API Key 2'),
    ('550e8400-e29b-41d4-a716-446655440002', 'sk_live_tenant789_mno345pqr678stu901vwx234', 'Demo API Key 3')
ON CONFLICT (api_key_hash) DO NOTHING;

-- Create view for tenant statistics
CREATE OR REPLACE VIEW tenant_statistics AS
SELECT 
    t.tenant_id,
    t.name AS tenant_name,
    COUNT(DISTINCT d.document_id) AS total_documents,
    COUNT(DISTINCT d.document_id) FILTER (WHERE d.status = 'INDEXED') AS indexed_documents,
    COUNT(DISTINCT d.document_id) FILTER (WHERE d.status = 'PENDING') AS pending_documents,
    SUM(d.file_size_bytes) AS total_storage_bytes,
    MAX(d.created_at) AS last_document_created,
    COUNT(DISTINCT a.log_id) AS total_api_calls
FROM tenants t
LEFT JOIN documents d ON t.tenant_id = d.tenant_id
LEFT JOIN audit_logs a ON t.tenant_id = a.tenant_id
WHERE t.is_active = TRUE
GROUP BY t.tenant_id, t.name;

-- Create view for popular searches
CREATE OR REPLACE VIEW popular_searches AS
SELECT 
    tenant_id,
    query_text,
    COUNT(*) AS search_count,
    AVG(search_time_ms) AS avg_search_time_ms,
    SUM(CASE WHEN cache_hit THEN 1 ELSE 0 END)::FLOAT / COUNT(*) AS cache_hit_ratio
FROM search_analytics
WHERE timestamp > NOW() - INTERVAL '7 days'
GROUP BY tenant_id, query_text
ORDER BY search_count DESC
LIMIT 100;

-- Cleanup function for old audit logs (retention: 90 days)
CREATE OR REPLACE FUNCTION cleanup_old_audit_logs()
RETURNS void AS $$
BEGIN
    DELETE FROM audit_logs
    WHERE timestamp < NOW() - INTERVAL '90 days';
    
    RAISE NOTICE 'Cleaned up audit logs older than 90 days';
END;
$$ LANGUAGE plpgsql;

-- Cleanup function for old search analytics (retention: 30 days)
CREATE OR REPLACE FUNCTION cleanup_old_search_analytics()
RETURNS void AS $$
BEGIN
    DELETE FROM search_analytics
    WHERE timestamp < NOW() - INTERVAL '30 days';
    
    RAISE NOTICE 'Cleaned up search analytics older than 30 days';
END;
$$ LANGUAGE plpgsql;

-- Grant permissions (adjust as needed)
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO docsearch_app;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO docsearch_app;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO docsearch_app;

-- Comments for documentation
COMMENT ON TABLE tenants IS 'Stores tenant/organization information';
COMMENT ON TABLE documents IS 'Stores document metadata; actual content indexed in Elasticsearch';
COMMENT ON TABLE api_keys IS 'Stores hashed API keys for tenant authentication';
COMMENT ON TABLE audit_logs IS 'Audit trail of all API operations';
COMMENT ON TABLE search_analytics IS 'Analytics data for search queries';

COMMENT ON COLUMN documents.metadata_json IS 'JSONB column for flexible document metadata';
COMMENT ON COLUMN documents.content_hash IS 'SHA-256 hash of content for deduplication';
COMMENT ON COLUMN api_keys.scopes IS 'Array of permissions: read, write, admin';

-- Success message
DO $$
BEGIN
    RAISE NOTICE 'Schema created successfully!';
    RAISE NOTICE 'Sample tenants and API keys have been inserted.';
    RAISE NOTICE 'Use the following API keys for testing:';
    RAISE NOTICE '  Tenant 1: sk_live_tenant123_abc123def456ghi789jkl012';
    RAISE NOTICE '  Tenant 2: sk_live_tenant456_xyz789uvw456rst123opq456';
    RAISE NOTICE '  Tenant 3: sk_live_tenant789_mno345pqr678stu901vwx234';
END $$;
