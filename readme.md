# Distributed Document Search Service

A high-performance, enterprise-grade distributed document search service built with Spring Boot, Elasticsearch, and microservices architecture.

## 🏗️ Architecture Overview

The system consists of three main microservices:

- **Document Service** (Port 8081): Handles document CRUD operations and metadata management
- **Search Service** (Port 8082): Performs full-text search with caching
- **Index Service** (Port 8083): Asynchronously indexes documents into Elasticsearch

### Infrastructure Components

- **PostgreSQL**: Stores document metadata and tenant information
- **Elasticsearch**: Provides full-text search capabilities
- **Redis**: Multi-layer caching for search results and rate limiting
- **RabbitMQ**: Message queue for asynchronous indexing operations

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### Running with Docker Compose

```bash
# Clone the repository
git clone https://github.com/yourorg/document-search-service.git
cd document-search-service

# Start all services
docker-compose up -d

# Check service health
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health
```

### Building from Source

```bash
# Build all modules
mvn clean install

# Run Document Service
cd document-service
mvn spring-boot:run

# Run Search Service (in new terminal)
cd search-service
mvn spring-boot:run

# Run Index Service (in new terminal)
cd index-service
mvn spring-boot:run
```

## 📡 API Endpoints

### Authentication

All API requests require an API key in the header:
```
X-API-Key: sk_live_tenant123_your_api_key_here
```

### Document Service (Port 8081)

#### Create Document
```bash
curl -X POST http://localhost:8081/api/v1/documents \
  -H "X-API-Key: sk_live_tenant123_abc" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Sample Document",
    "content": "This is the document content with searchable text.",
    "metadata": {
      "author": "John Doe",
      "tags": ["tech", "documentation"]
    }
  }'
```

Response:
```json
{
  "success": true,
  "message": "Document created successfully",
  "data": {
    "document_id": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING",
    "message": "Document queued for indexing"
  },
  "timestamp": "2025-10-01T12:00:00"
}
```

#### Get Document
```bash
curl -X GET http://localhost:8081/api/v1/documents/{documentId} \
  -H "X-API-Key: sk_live_tenant123_abc"
```

#### List Documents
```bash
curl -X GET "http://localhost:8081/api/v1/documents?page=0&size=10" \
  -H "X-API-Key: sk_live_tenant123_abc"
```

#### Delete Document
```bash
curl -X DELETE http://localhost:8081/api/v1/documents/{documentId} \
  -H "X-API-Key: sk_live_tenant123_abc"
```

### Search Service (Port 8082)

#### Search Documents
```bash
curl -X GET "http://localhost:8082/api/v1/search?q=sample+document&page=1&size=10" \
  -H "X-API-Key: sk_live_tenant123_abc"
```

Response:
```json
{
  "success": true,
  "data": {
    "query": "sample document",
    "totalHits": 42,
    "page": 1,
    "pageSize": 10,
    "tookMs": 45,
    "results": [
      {
        "documentId": "550e8400-e29b-41d4-a716-446655440000",
        "title": "Sample Document",
        "snippet": "This is the document content...",
        "score": 8.5,
        "metadata": {
          "author": "John Doe",
          "tags": ["tech", "documentation"]
        },
        "highlights": [
          "...the <em>document</em> content..."
        ]
      }
    ]
  }
}
```

Query Parameters:
- `q` (required): Search query text
- `page` (optional, default=1): Page number
- `size` (optional, default=10, max=100): Results per page
- `fuzzy` (optional, default=false): Enable fuzzy matching
- `highlight` (optional, default=true): Enable result highlighting

### Health Check (All Services)

```bash
curl http://localhost:8081/health
```

Response:
```json
{
  "status": "UP",
  "timestamp": "2025-10-01T12:00:00",
  "components": {
    "elasticsearch": {
      "status": "UP",
      "details": {}
    },
    "postgresql": {
      "status": "UP",
      "details": {}
    },
    "redis": {
      "status": "UP",
      "details": {}
    },
    "rabbitmq": {
      "status": "UP",
      "details": {}
    }
  }
}
```

## 🔐 Security Features

### Multi-Tenancy
- **Index-per-tenant isolation**: Each tenant gets dedicated Elasticsearch indices
- **API Key Authentication**: Tenant extracted from API key format `sk_live_{tenant_id}_{random}`
- **Thread-local context**: Tenant context managed per request thread

### Rate Limiting
- **Limit**: 1000 requests per minute per tenant
- **Implementation**: Redis-based sliding window counter
- **Headers**: Rate limit info in response headers
- **Response**: 429 status code when limit exceeded

## 🎯 Key Features

### Asynchronous Indexing
- Documents are indexed asynchronously via RabbitMQ
- Indexing status tracked: `PENDING` → `INDEXING` → `INDEXED`
- Dead Letter Queues (DLQ) for failed messages
- Automatic retry with exponential backoff

### Multi-Layer Caching
1. **L1 - Application Cache (Caffeine)**
   - 10,000 entries per service
   - Tenant config, API keys
   
2. **L2 - Distributed Cache (Redis)**
   - Search results: 5 minutes TTL
   - Document metadata: 30 minutes TTL
   - Rate limit counters: 1 minute TTL

3. **L3 - Elasticsearch Query Cache**
   - Native ES filter and query caching

### Search Features
- Full-text search with BM25 relevance scoring
- Fuzzy matching for typo tolerance
- Multi-field search (title, content)
- Result highlighting
- Pagination support

## 📊 Monitoring & Observability

### Actuator Endpoints

All services expose Spring Boot Actuator endpoints:
- `/actuator/health` - Health status
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics
- `/actuator/info` - Application info

### API Documentation

Swagger UI available at:
- Document Service: http://localhost:8081/swagger-ui.html
- Search Service: http://localhost:8082/swagger-ui.html

## 🏛️ Database Schema

```sql
-- Tenants
CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    rate_limit_per_minute INTEGER DEFAULT 1000,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Documents
CREATE TABLE documents (
    document_id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(tenant_id),
    title VARCHAR(500),
    content TEXT,
    metadata_json JSONB,
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    indexed_at TIMESTAMP
);
```

## 🧪 Testing

### Sample API Key Format
```
sk_live_tenant123_randomstring123456789
sk_test_tenant456_randomstring987654321
```

### Load Testing with k6
```bash
# Install k6
brew install k6

# Run load test
k6 run loadtest.js
```

## 📦 Project Structure

```
document-search-service/
├── common/                    # Shared models and utilities
│   ├── model/
│   ├── dto/
│   ├── context/
│   └── filter/
├── document-service/          # Document CRUD service
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── messaging/
├── search-service/            # Search service
│   ├── controller/
│   ├── service/
│   └── config/
├── index-service/             # Indexing service
│   ├── service/
│   ├── messaging/
│   └── config/
├── docker-compose.yml
└── pom.xml
```

## 🚀 Performance Characteristics

### Targets
- **Search Latency**: <500ms (p95)
- **Throughput**: 1000+ searches/second
- **Index Capacity**: 10M+ documents per tenant
- **Cache Hit Rate**: >70% for search queries

### Scaling Strategy
- **Horizontal**: Add more service instances behind load balancer
- **Elasticsearch**: Add nodes and increase shards
- **Redis**: Cluster mode for distributed caching
- **RabbitMQ**: Add consumers for parallel processing

## 🛠️ Configuration

### Environment Variables

**Document Service:**
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/docsearch
SPRING_RABBITMQ_HOST=localhost
SPRING_DATA_REDIS_HOST=localhost
```

**Search Service:**
```bash
ELASTICSEARCH_HOST=localhost
SPRING_DATA_REDIS_HOST=localhost
```

**Index Service:**
```bash
ELASTICSEARCH_HOST=localhost
SPRING_RABBITMQ_HOST=localhost
```

## 📝 Development

### Adding a New Service

1. Create module in parent POM
2. Add dependency on `common` module
3. Implement service logic
4. Add to docker-compose.yml
5. Update documentation

### Running Tests
```bash
mvn test
```

### Code Coverage
```bash
mvn clean verify
```

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📄 License

This project is licensed under the MIT License.

## 🙏 Acknowledgments

- Spring Boot & Spring Cloud ecosystem
- Elasticsearch for powerful search capabilities
- Redis for caching layer
- RabbitMQ for reliable messaging
