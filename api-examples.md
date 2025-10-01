# API Examples - Document Search Service

Complete collection of API requests for testing the Document Search Service.

## Authentication

All requests require an API key header. Format: `sk_live_{tenant_id}_{random_string}`

```bash
export API_KEY="sk_live_tenant123_abc123def456"
export DOC_SERVICE="http://localhost:8081"
export SEARCH_SERVICE="http://localhost:8082"
```

---

## Document Operations

### 1. Create a Document

```bash
curl -X POST ${DOC_SERVICE}/api/v1/documents \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Introduction to Microservices",
    "content": "Microservices are an architectural style that structures an application as a collection of loosely coupled services. This approach allows for better scalability, maintainability, and faster development cycles.",
    "metadata": {
      "author": "Jane Smith",
      "category": "Technology",
      "tags": ["microservices", "architecture", "software-engineering"],
      "created_date": "2025-10-01"
    }
  }'
```

Expected Response:
```json
{
  "success": true,
  "message": "Document created successfully",
  "data": {
    "document_id": "123e4567-e89b-12d3-a456-426614174000",
    "status": "PENDING",
    "message": "Document queued for indexing"
  },
  "timestamp": "2025-10-01T10:30:00"
}
```

### 2. Create Multiple Documents (Batch)

```bash
# Document 1: Spring Boot Tutorial
curl -X POST ${DOC_SERVICE}/api/v1/documents \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Spring Boot Best Practices",
    "content": "Spring Boot makes it easy to create stand-alone, production-grade Spring based applications. This guide covers best practices for building robust Spring Boot applications including proper configuration management, error handling, and testing strategies.",
    "metadata": {
      "author": "John Developer",
      "category": "Programming",
      "tags": ["spring-boot", "java", "best-practices"]
    }
  }'

# Document 2: Database Design
curl -X POST ${DOC_SERVICE}/api/v1/documents \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "PostgreSQL Performance Tuning",
    "content": "PostgreSQL is a powerful open-source relational database. This article discusses various performance tuning techniques including index optimization, query planning, connection pooling, and configuration parameters.",
    "metadata": {
      "author": "Database Admin",
      "category": "Database",
      "tags": ["postgresql", "performance", "optimization"]
    }
  }'

# Document 3: API Design
curl -X POST ${DOC_SERVICE}/api/v1/documents \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "RESTful API Design Principles",
    "content": "REST (Representational State Transfer) is an architectural style for designing networked applications. This guide covers key principles of REST including resource naming, HTTP methods, status codes, versioning, and HATEOAS.",
    "metadata": {
      "author": "API Architect",
      "category": "API Design",
      "tags": ["rest", "api", "design-patterns"]
    }
  }'

# Document 4: Elasticsearch Guide
curl -X POST ${DOC_SERVICE}/api/v1/documents \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Elasticsearch Query DSL Tutorial",
    "content": "Elasticsearch provides a powerful Query DSL (Domain Specific Language) based on JSON. Learn about match queries, term queries, bool queries, aggregations, and how to build complex search functionality.",
    "metadata": {
      "author": "Search Engineer",
      "category": "Search Technology",
      "tags": ["elasticsearch", "search", "full-text"]
    }
  }'

# Document 5: Docker & Containers
curl -X POST ${DOC_SERVICE}/api/v1/documents \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Docker Containerization Guide",
    "content": "Docker enables developers to package applications with all dependencies into standardized units called containers. This comprehensive guide covers Docker images, containers, volumes, networks, and orchestration with Docker Compose.",
    "metadata": {
      "author": "DevOps Engineer",
      "category": "DevOps",
      "tags": ["docker", "containers", "devops"]
    }
  }'
```

### 3. Get Document by ID

```bash
# Replace with actual document ID from create response
export DOCUMENT_ID="123e4567-e89b-12d3-a456-426614174000"

curl -X GET ${DOC_SERVICE}/api/v1/documents/${DOCUMENT_ID} \
  -H "X-API-Key: ${API_KEY}"
```

### 4. List All Documents

```bash
# First page
curl -X GET "${DOC_SERVICE}/api/v1/documents?page=0&size=10" \
  -H "X-API-Key: ${API_KEY}"

# Second page
curl -X GET "${DOC_SERVICE}/api/v1/documents?page=1&size=10" \
  -H "X-API-Key: ${API_KEY}"
```

### 5. Delete Document

```bash
curl -X DELETE ${DOC_SERVICE}/api/v1/documents/${DOCUMENT_ID} \
  -H "X-API-Key: ${API_KEY}"
```

---

## Search Operations

Wait 5-10 seconds after creating documents for indexing to complete, then run search queries.

### 1. Basic Search

```bash
# Search for "microservices"
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=microservices" \
  -H "X-API-Key: ${API_KEY}"

# Search for "Spring Boot"
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=Spring+Boot" \
  -H "X-API-Key: ${API_KEY}"

# Search for "database"
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=database" \
  -H "X-API-Key: ${API_KEY}"
```

### 2. Search with Pagination

```bash
# Get first 5 results
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=spring&page=1&size=5" \
  -H "X-API-Key: ${API_KEY}"

# Get next 5 results
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=spring&page=2&size=5" \
  -H "X-API-Key: ${API_KEY}"
```

### 3. Fuzzy Search (Typo Tolerance)

```bash
# Search with typo: "microsrevices" instead of "microservices"
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=microsrevices&fuzzy=true" \
  -H "X-API-Key: ${API_KEY}"

# Search with typo: "datbase" instead of "database"
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=datbase&fuzzy=true" \
  -H "X-API-Key: ${API_KEY}"
```

### 4. Search with Highlighting Disabled

```bash
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=performance&highlight=false" \
  -H "X-API-Key: ${API_KEY}"
```

### 5. Complex Searches

```bash
# Search for API design concepts
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=REST+API+design&page=1&size=10&highlight=true" \
  -H "X-API-Key: ${API_KEY}"

# Search for performance optimization
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=performance+optimization+tuning" \
  -H "X-API-Key: ${API_KEY}"

# Search for containers and Docker
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=docker+containers+orchestration" \
  -H "X-API-Key: ${API_KEY}"
```

---

## Health Checks

### Check All Service Health

```bash
# Document Service Health
curl http://localhost:8081/health | jq

# Search Service Health
curl http://localhost:8082/health | jq

# Index Service Health
curl http://localhost:8083/health | jq
```

### Check Individual Component Health

```bash
# Check Elasticsearch
curl http://localhost:8082/actuator/health/elasticsearch | jq

# Check PostgreSQL
curl http://localhost:8081/actuator/health/db | jq

# Check Redis
curl http://localhost:8081/actuator/health/redis | jq

# Check RabbitMQ
curl http://localhost:8081/actuator/health/rabbit | jq
```

---

## Rate Limiting Tests

Test rate limiting by sending multiple requests rapidly:

```bash
# Send 10 requests in quick succession
for i in {1..10}; do
  echo "Request $i:"
  curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=test" \
    -H "X-API-Key: ${API_KEY}" \
    -w "\nStatus: %{http_code}\n\n"
done

# Send 1100 requests to trigger rate limit (limit is 1000/min)
for i in {1..1100}; do
  curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=test" \
    -H "X-API-Key: ${API_KEY}" \
    -w "%{http_code} " \
    -o /dev/null -s
done
```

Expected response when rate limited:
```json
{
  "success": false,
  "message": "Rate limit exceeded. Maximum 1000 requests per minute allowed.",
  "timestamp": "2025-10-01T10:45:00"
}
```

---

## Error Scenarios

### 1. Missing API Key

```bash
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=test"
```

Expected: 401 Unauthorized

### 2. Invalid API Key Format

```bash
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=test" \
  -H "X-API-Key: invalid_key_format"
```

Expected: 401 Unauthorized

### 3. Document Not Found

```bash
curl -X GET ${DOC_SERVICE}/api/v1/documents/00000000-0000-0000-0000-000000000000 \
  -H "X-API-Key: ${API_KEY}"
```

Expected: 404 Not Found

### 4. Invalid Query Parameters

```bash
# Page size exceeds maximum
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=test&size=200" \
  -H "X-API-Key: ${API_KEY}"
```

Expected: 400 Bad Request

---

## Performance Testing

### Measure Search Response Time

```bash
curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=microservices" \
  -H "X-API-Key: ${API_KEY}" \
  -w "\n\nTotal Time: %{time_total}s\n" \
  -o response.json

# View response
cat response.json | jq
```

### Cache Performance Test

```bash
# First request (cache miss)
echo "First request (cold cache):"
time curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=spring" \
  -H "X-API-Key: ${API_KEY}" \
  -o /dev/null -s

# Second request (cache hit)
echo "Second request (warm cache):"
time curl -X GET "${SEARCH_SERVICE}/api/v1/search?q=spring" \
  -H "X-API-Key: ${API_KEY}" \
  -o /dev/null -s
```

---

## Cleanup

### Delete All Test Documents

```bash
# Get all documents
DOCS=$(curl -s -X GET "${DOC_SERVICE}/api/v1/documents?size=100" \
  -H "X-API-Key: ${API_KEY}" | jq -r '.data.content[].documentId')

# Delete each document
for doc_id in $DOCS; do
  echo "Deleting document: $doc_id"
  curl -X DELETE ${DOC_SERVICE}/api/v1/documents/${doc_id} \
    -H "X-API-Key: ${API_KEY}"
done
```

---

## Monitoring

### View Metrics

```bash
# Application metrics
curl http://localhost:8081/actuator/metrics | jq

# JVM memory
curl http://localhost:8081/actuator/metrics/jvm.memory.used | jq

# HTTP requests count
curl http://localhost:8081/actuator/metrics/http.server.requests | jq

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus
```

---

## Notes

- **Indexing Delay**: Documents take 5-10 seconds to index after creation
- **Cache TTL**: Search results cached for 5 minutes
- **Rate Limit**: 1000 requests per minute per tenant
- **Max Page Size**: 100 documents per page
- **Search Timeout**: 30 seconds default

## Troubleshooting

If searches return no results:
1. Wait 10 seconds for indexing to complete
2. Check index service logs: `docker logs index-service`
3. Verify Elasticsearch health: `curl http://localhost:9200/_cluster/health`
4. Check RabbitMQ queue: Open http://localhost:15672 (guest/guest)
