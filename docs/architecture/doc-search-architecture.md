# Distributed Document Search Service - Architecture Design

## 1. High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Load Balancer (NGINX)                          │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
                ┌────────────────┴────────────────┐
                │                                 │
┌───────────────▼──────────────┐   ┌─────────────▼──────────────┐
│   API Gateway Service        │   │   API Gateway Service       │
│   (Spring Cloud Gateway)     │   │   (Spring Cloud Gateway)    │
│   - Rate Limiting            │   │   - Rate Limiting           │
│   - Tenant Validation        │   │   - Tenant Validation       │
│   - Request Routing          │   │   - Request Routing         │
└───────────────┬──────────────┘   └─────────────┬───────────────┘
                │                                 │
                └────────────────┬────────────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        │                        │                        │
┌───────▼────────┐     ┌────────▼─────────┐    ┌────────▼─────────┐
│ Search Service │     │  Index Service   │    │  Document        │
│ (Spring Boot)  │     │  (Spring Boot)   │    │  Service         │
│                │     │                  │    │  (Spring Boot)   │
│ - Query Proc.  │     │ - Doc Indexing   │    │  - CRUD Ops      │
│ - Cache Check  │     │ - Async Process  │    │  - Metadata Mgmt │
│ - Result Rank  │     │ - Bulk Index     │    │  - Validation    │
└───────┬────────┘     └────────┬─────────┘    └────────┬─────────┘
        │                       │                        │
        │              ┌────────▼─────────┐             │
        │              │   RabbitMQ       │             │
        │              │  Message Queue   │             │
        │              │  - indexing.queue│             │
        │              │  - delete.queue  │             │
        │              └──────────────────┘             │
        │                                                │
┌───────▼──────────────────────────────────────────────▼─────────┐
│                     Redis Cache Cluster                         │
│  - Search Results Cache (TTL: 5min)                            │
│  - Document Metadata Cache (TTL: 30min)                        │
│  - Rate Limit Counters (TTL: 1min)                             │
└────────────────────────────────────────────────────────────────┘
        │                                                │
┌───────▼────────────────────┐         ┌───────────────▼──────────┐
│  Elasticsearch Cluster     │         │  PostgreSQL Database     │
│  (3+ nodes)                │         │  (Primary + Replicas)    │
│                            │         │                          │
│  Index per Tenant:         │         │  Tables:                 │
│  - docs_tenant_123         │         │  - documents             │
│  - docs_tenant_456         │         │  - tenants               │
│                            │         │  - api_keys              │
│  Sharding: By tenant_id    │         │  - audit_logs            │
│  Replicas: 2 per shard     │         │                          │
└────────────────────────────┘         └──────────────────────────┘
```

## 2. Data Flow Diagrams

### 2.1 Document Indexing Flow

```
Client Request
     │
     ▼
API Gateway (Validate Tenant, Rate Limit)
     │
     ▼
Document Service
     │
     ├─► PostgreSQL (Save Metadata)
     │   └─► Return Document ID
     │
     ▼
Publish to RabbitMQ (indexing.queue)
     │
     ▼
Index Service (Consumer)
     │
     ├─► Transform Document
     ├─► Extract Full Text
     │
     ▼
Elasticsearch (Index to tenant-specific index)
     │
     ▼
Invalidate Cache (if exists)
     │
     ▼
Return Success Response
```

### 2.2 Search Query Flow

```
Client Search Request
     │
     ▼
API Gateway (Validate Tenant, Rate Limit)
     │
     ▼
Search Service
     │
     ├─► Check Redis Cache (Key: tenant_id:query_hash)
     │   │
     │   ├─► Cache HIT → Return Cached Results
     │   │
     │   └─► Cache MISS ↓
     │
     ▼
Elasticsearch Query (tenant-specific index)
     │
     ├─► Full-text Search
     ├─► Relevance Scoring
     ├─► Apply Filters
     │
     ▼
Enrich Results (fetch metadata from PostgreSQL)
     │
     ▼
Cache Results in Redis (TTL: 5 minutes)
     │
     ▼
Return Results to Client
```

## 3. Database & Storage Strategy

### 3.1 Elasticsearch (Primary Search Engine)

**Choice Rationale:**
- Native full-text search with BM25 relevance scoring
- Horizontal scalability through sharding
- Sub-second query performance for millions of documents
- Built-in support for fuzzy search, highlighting, and facets

**Index Strategy:**
- **Index per Tenant:** `docs_tenant_{tenant_id}`
- **Sharding:** 3 primary shards per tenant (for tenants with >1M docs)
- **Replicas:** 2 replicas per shard for high availability
- **Refresh Interval:** 5 seconds (balances indexing speed vs search freshness)

**Document Schema:**
```json
{
  "document_id": "uuid",
  "tenant_id": "string",
  "title": "text",
  "content": "text",
  "metadata": {
    "author": "keyword",
    "created_at": "date",
    "tags": ["keyword"]
  },
  "indexed_at": "date"
}
```

**Index Settings:**
- Analysis: Standard analyzer with lowercase filter
- Max result window: 10,000 documents
- Number of shards: Dynamic based on document volume

### 3.2 PostgreSQL (Metadata & Transactional Data)

**Purpose:**
- Store structured document metadata
- Tenant configuration and API key management
- Audit logs and usage statistics
- Transactional guarantees for critical operations

**Schema:**

```sql
-- Tenants table
CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    rate_limit_per_minute INTEGER DEFAULT 1000,
    storage_quota_gb INTEGER,
    created_at TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN DEFAULT TRUE
);

-- Documents table
CREATE TABLE documents (
    document_id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(tenant_id),
    title VARCHAR(500),
    file_path TEXT,
    file_size_bytes BIGINT,
    content_hash VARCHAR(64),
    status VARCHAR(50), -- indexed, pending, failed
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    indexed_at TIMESTAMP,
    INDEX idx_tenant_created (tenant_id, created_at),
    INDEX idx_status (status)
);

-- API Keys table
CREATE TABLE api_keys (
    key_id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(tenant_id),
    api_key_hash VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP
);

-- Audit logs
CREATE TABLE audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    tenant_id UUID,
    action VARCHAR(100),
    resource_type VARCHAR(50),
    resource_id UUID,
    user_id VARCHAR(255),
    timestamp TIMESTAMP DEFAULT NOW(),
    INDEX idx_tenant_timestamp (tenant_id, timestamp)
);
```

### 3.3 Redis (Cache Layer)

**Purpose:**
- Query result caching
- Rate limiting counters
- Session management
- Distributed locks

**Cache Strategy:**

| Cache Type | Key Pattern | TTL | Purpose |
|-----------|-------------|-----|---------|
| Search Results | `search:{tenant_id}:{query_hash}` | 5 min | Cache frequent queries |
| Document Metadata | `doc:{document_id}` | 30 min | Reduce DB load |
| Rate Limit | `ratelimit:{tenant_id}:{window}` | 1 min | Track API usage |
| Tenant Config | `tenant:{tenant_id}` | 1 hour | Cache tenant settings |

**Configuration:**
- Eviction Policy: LRU (Least Recently Used)
- Max Memory: 4GB per Redis instance
- Persistence: AOF (Append Only File) for durability

## 4. API Design

### Base URL
```
https://api.docsearch.example.com/v1
```

### Authentication
All requests require `X-API-Key` header with tenant-specific API key.

### 4.1 Index Document

```http
POST /documents
X-API-Key: {api_key}
Content-Type: application/json

{
  "title": "Q4 Financial Report",
  "content": "Full text content here...",
  "metadata": {
    "author": "John Doe",
    "tags": ["finance", "quarterly"],
    "created_at": "2025-09-30T10:00:00Z"
  }
}

Response 202 Accepted:
{
  "document_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "indexing",
  "message": "Document queued for indexing"
}
```

### 4.2 Search Documents

```http
GET /search?q=financial+report&page=1&size=10
X-API-Key: {api_key}

Response 200 OK:
{
  "query": "financial report",
  "total_hits": 1247,
  "page": 1,
  "page_size": 10,
  "took_ms": 45,
  "results": [
    {
      "document_id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Q4 Financial Report",
      "snippet": "...quarterly <em>financial</em> <em>report</em> shows...",
      "score": 8.5,
      "metadata": {
        "author": "John Doe",
        "created_at": "2025-09-30T10:00:00Z"
      }
    }
  ]
}
```

### 4.3 Get Document

```http
GET /documents/{document_id}
X-API-Key: {api_key}

Response 200 OK:
{
  "document_id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Q4 Financial Report",
  "content": "Full document content...",
  "metadata": {...},
  "created_at": "2025-09-30T10:00:00Z",
  "indexed_at": "2025-09-30T10:00:15Z"
}
```

### 4.4 Delete Document

```http
DELETE /documents/{document_id}
X-API-Key: {api_key}

Response 202 Accepted:
{
  "document_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "deletion_queued",
  "message": "Document queued for deletion"
}
```

### 4.5 Health Check

```http
GET /health

Response 200 OK:
{
  "status": "UP",
  "timestamp": "2025-10-01T12:00:00Z",
  "components": {
    "elasticsearch": {
      "status": "UP",
      "cluster_health": "green",
      "response_time_ms": 12
    },
    "postgresql": {
      "status": "UP",
      "response_time_ms": 5
    },
    "redis": {
      "status": "UP",
      "response_time_ms": 2
    },
    "rabbitmq": {
      "status": "UP",
      "queue_depth": 23
    }
  }
}
```

## 5. Consistency Model & Trade-offs

### Consistency Model: **Eventual Consistency**

**Rationale:**
Given the requirements for high throughput (1000+ searches/sec) and sub-second latency, we adopt eventual consistency for indexing operations while maintaining strong consistency for critical metadata.

**Implementation:**

1. **Document Indexing (Eventual)**
   - Write to PostgreSQL (immediate, ACID)
   - Asynchronously index to Elasticsearch via RabbitMQ
   - Indexing completes within 1-5 seconds typically
   - Status tracking: `pending` → `indexing` → `indexed`

2. **Search Queries (Read-your-writes for same client)**
   - Cache invalidation on document updates
   - Elasticsearch refresh interval: 5 seconds
   - Clients may not see newly indexed docs for up to 5 seconds

3. **Metadata Operations (Strong Consistency)**
   - PostgreSQL transactions for document CRUD
   - Immediate visibility of metadata changes

**Trade-offs:**

| Aspect | Choice | Benefit | Cost |
|--------|--------|---------|------|
| Async Indexing | Eventual consistency | High write throughput, non-blocking | Slight indexing delay |
| Cache Layer | Stale reads possible | Sub-50ms response time | May serve outdated results |
| ES Refresh | 5-second window | Better indexing performance | Delayed search visibility |
| Multi-Index | Index per tenant | Perfect tenant isolation | Higher cluster overhead |

## 6. Caching Strategy

### 6.1 Multi-Layer Caching

```
Request → L1: Application Cache (Caffeine) → L2: Redis → Database/ES
           ↓ 50ms hit                          ↓ 5ms hit    ↓ 50-200ms
```

### 6.2 Cache Layers

**Layer 1 - Application Cache (Caffeine)**
- **Purpose:** In-memory cache for frequently accessed data
- **Size:** 10,000 entries per service instance
- **Use Cases:**
  - Tenant configuration (1 hour TTL)
  - API key validation results (15 min TTL)
  - Common search query results (5 min TTL)

**Layer 2 - Distributed Cache (Redis)**
- **Purpose:** Shared cache across all service instances
- **Use Cases:**
  - Search results: 5-minute TTL
  - Document metadata: 30-minute TTL
  - Rate limit counters: Sliding window
  - Session data: 24-hour TTL

**Layer 3 - Elasticsearch Query Cache**
- **Native ES caching:** Filter cache, query result cache
- **Automatic management by Elasticsearch**

### 6.3 Cache Invalidation Strategy

**Write-Through Pattern:**
```java
1. Update PostgreSQL (source of truth)
2. Invalidate specific Redis cache keys
3. Publish invalidation event to RabbitMQ
4. All service instances evict from L1 cache
```

**Cache Keys:**
```
Pattern: {resource_type}:{tenant_id}:{identifier}:{version}

Examples:
- search:tenant_123:hash_abc123:v1
- doc:550e8400-e29b-41d4-a716-446655440000:v2
- tenant:tenant_123:config:v1
```

### 6.4 Cache Warming

- Preload top 1000 frequent queries per tenant at startup
- Background job refreshes popular cache entries before expiry
- Circuit breaker prevents cache stampede

## 7. Message Queue Architecture (RabbitMQ)

### 7.1 Queue Design

```
Exchange: document.topic (Topic Exchange)

Queues:
├─ indexing.queue
│  ├─ Routing Key: document.index.*
│  ├─ Consumers: 5 Index Service instances
│  └─ DLQ: indexing.dlq (Dead Letter Queue)
│
├─ deletion.queue
│  ├─ Routing Key: document.delete.*
│  ├─ Consumers: 3 Index Service instances
│  └─ DLQ: deletion.dlq
│
└─ reindex.queue
   ├─ Routing Key: document.reindex.*
   ├─ Consumers: 2 Index Service instances (low priority)
   └─ DLQ: reindex.dlq
```

### 7.2 Message Format

```json
{
  "message_id": "uuid",
  "tenant_id": "tenant_123",
  "document_id": "550e8400-e29b-41d4-a716-446655440000",
  "operation": "index|delete|reindex",
  "payload": {
    "title": "Document Title",
    "content": "Full text...",
    "metadata": {}
  },
  "timestamp": "2025-10-01T12:00:00Z",
  "retry_count": 0,
  "max_retries": 3
}
```

### 7.3 Reliability Features

- **Acknowledgment Mode:** Manual ACK after successful ES indexing
- **Prefetch Count:** 10 messages per consumer
- **Dead Letter Queue:** Failed messages after 3 retries
- **Message Persistence:** Durable queues and persistent messages
- **Retry Strategy:** Exponential backoff (1s, 5s, 15s)

## 8. Multi-Tenancy & Data Isolation

### 8.1 Tenant Isolation Strategy: **Index-Per-Tenant**

**Approach:**
Each tenant gets dedicated Elasticsearch index(es), providing strong isolation while maintaining performance.

**Benefits:**
- ✅ Complete data isolation
- ✅ Independent scaling per tenant
- ✅ Granular access control
- ✅ Easy tenant deletion
- ✅ Per-tenant index configuration

**Implementation:**

```java
// Index naming convention
String indexName = "docs_tenant_" + tenantId;

// Tenant resolution from API key
@Component
public class TenantContext {
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    
    public static void setTenantId(String tenantId) {
        currentTenant.set(tenantId);
    }
    
    public static String getTenantId() {
        return currentTenant.get();
    }
}
```

### 8.2 Tenant Identification

**Primary Method:** API Key Header
```http
X-API-Key: sk_live_tenant123_abc...xyz
```

**API Key Format:**
- Prefix: `sk_live_` or `sk_test_`
- Tenant identifier embedded
- Cryptographically secure random string
- Hashed before storage (SHA-256)

### 8.3 Security Measures

1. **Request Validation Filter**
   ```java
   @Component
   public class TenantValidationFilter extends OncePerRequestFilter {
       // Extract API key → Validate → Set TenantContext
       // Reject requests with invalid/expired keys
   }
   ```

2. **Query-Level Isolation**
   ```java
   // All ES queries automatically scoped to tenant index
   SearchRequest request = new SearchRequest("docs_tenant_" + tenantId);
   ```

3. **Rate Limiting Per Tenant**
   ```java
   // Redis-based sliding window rate limiter
   String key = "ratelimit:" + tenantId + ":" + currentMinute;
   // Limit: 1000 requests/minute per tenant (configurable)
   ```

### 8.4 Tenant Lifecycle Management

- **Onboarding:** Provision index, generate API keys, set quotas
- **Scaling:** Automatically increase shards when doc count > 10M
- **Offboarding:** Soft delete (mark inactive) → Hard delete after 90 days
- **Monitoring:** Per-tenant metrics dashboard

## 9. Scalability & Performance Characteristics

### 9.1 Current Prototype Capacity

| Metric | Target | Approach |
|--------|--------|----------|
| Total Documents | 10M+ | Elasticsearch sharding |
| Concurrent Searches | 1000/sec | Horizontal scaling + caching |
| Search Latency (p95) | <500ms | Redis cache + ES optimization |
| Index Throughput | 500 docs/sec | RabbitMQ async processing |
| Tenant Isolation | 100% | Index-per-tenant model |

### 9.2 Horizontal Scaling Strategy

**API Services (Stateless):**
- Deploy behind load balancer
- Auto-scale based on CPU/memory (K8s HPA)
- Target: 70% CPU utilization

**Elasticsearch Cluster:**
- Add nodes for increased query/index capacity
- Rebalance shards automatically
- Minimum 3 nodes for production

**Redis Cluster:**
- Sentinel mode for HA (3+ nodes)
- Cluster mode for horizontal scaling
- Read replicas for read-heavy workloads

**RabbitMQ:**
- Clustered deployment (3+ nodes)
- Quorum queues for high availability
- Consumer scaling independent of producers

### 9.3 Performance Optimizations

1. **Query Optimization**
   - Use filters instead of queries where possible (cached)
   - Limit result size and pagination depth
   - Use `_source` filtering to reduce payload

2. **Indexing Optimization**
   - Bulk indexing (batch size: 1000 documents)
   - Disable replicas during bulk import
   - Increase refresh interval during heavy indexing

3. **Cache Hit Ratio Target**
   - Search queries: >70% cache hit rate
   - Document metadata: >85% cache hit rate
   - Monitor and tune TTL based on access patterns

---

## Summary

This architecture provides:

✅ **Sub-second search** through Elasticsearch + Redis caching  
✅ **Multi-tenancy** via index-per-tenant isolation  
✅ **Horizontal scalability** across all components  
✅ **High availability** through replication and clustering  
✅ **Asynchronous processing** via RabbitMQ for indexing  
✅ **Strong security** through API key validation and tenant isolation  
✅ **Production-grade monitoring** with health checks and dependency tracking

**Next Steps:**
1. Implement Spring Boot prototype with core services
2. Set up Docker Compose for local development
3. Create sample data and API request collection
4. Document production readiness requirements