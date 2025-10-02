package com.enterprise.docsearch.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.enterprise.docsearch.common.model.Document;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingService {
    
    private final ElasticsearchClient elasticsearchClient;
    private final ObjectMapper objectMapper;
    
    private static final String INDEX_PREFIX = "docs_tenant_";
    
    public void indexDocument(Document document) {
        String indexName = INDEX_PREFIX + document.getTenantId();
        
        try {
            // Ensure index exists
            ensureIndexExists(indexName);
            
            // Prepare document for indexing
            Map<String, Object> esDocument = prepareDocumentForIndexing(document);
            
            // Index document
            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(indexName)
                    .id(document.getDocumentId().toString())
                    .document(esDocument)
            );
            
            if (response.result() == Result.Created || response.result() == Result.Updated) {
                log.info("Successfully indexed document {} in index {}", 
                        document.getDocumentId(), indexName);
            } else {
                log.warn("Unexpected result while indexing document: {}", response.result());
            }
            
        } catch (Exception e) {
            log.error("Error indexing document {}", document.getDocumentId(), e);
            throw new RuntimeException("Failed to index document", e);
        }
    }
    
    public void bulkIndexDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return;
        }
        
        String tenantId = documents.get(0).getTenantId();
        String indexName = INDEX_PREFIX + tenantId;
        
        try {
            ensureIndexExists(indexName);
            
            List<BulkOperation> operations = new ArrayList<>();
            
            for (Document doc : documents) {
                Map<String, Object> esDocument = prepareDocumentForIndexing(doc);
                
                BulkOperation operation = BulkOperation.of(b -> b
                        .index(idx -> idx
                                .index(indexName)
                                .id(doc.getDocumentId().toString())
                                .document(esDocument)
                        )
                );
                operations.add(operation);
            }
            
            BulkRequest bulkRequest = BulkRequest.of(b -> b
                    .operations(operations)
            );
            
            BulkResponse response = elasticsearchClient.bulk(bulkRequest);
            
            if (response.errors()) {
                log.error("Bulk indexing had errors");
                response.items().forEach(item -> {
                    if (item.error() != null) {
                        log.error("Error indexing document: {}", item.error().reason());
                    }
                });
            } else {
                log.info("Successfully bulk indexed {} documents", documents.size());
            }
            
        } catch (Exception e) {
            log.error("Error during bulk indexing", e);
            throw new RuntimeException("Failed to bulk index documents", e);
        }
    }
    
    public void deleteDocument(UUID documentId, String tenantId) {
        String indexName = INDEX_PREFIX + tenantId;
        
        try {
            elasticsearchClient.delete(d -> d
                    .index(indexName)
                    .id(documentId.toString())
            );
            
            log.info("Successfully deleted document {} from index {}", documentId, indexName);
            
        } catch (Exception e) {
            log.error("Error deleting document {}", documentId, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }
    
    private void ensureIndexExists(String indexName) {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(indexName)))
                    .value();
            
            if (!exists) {
                createIndex(indexName);
            }
        } catch (Exception e) {
            log.error("Error checking index existence", e);
            throw new RuntimeException("Failed to check index", e);
        }
    }
    
    private void createIndex(String indexName) {
        try {
            String mappings = """
                {
                  "properties": {
                    "document_id": { "type": "keyword" },
                    "tenant_id": { "type": "keyword" },
                    "title": { 
                      "type": "text",
                      "analyzer": "standard",
                      "fields": {
                        "keyword": { "type": "keyword" }
                      }
                    },
                    "content": { 
                      "type": "text",
                      "analyzer": "standard"
                    },
                    "metadata": { "type": "object" },
                    "indexed_at": { "type": "date" }
                  }
                }
                """;
            
            String settings = """
                {
                  "number_of_shards": 3,
                  "number_of_replicas": 2,
                  "refresh_interval": "5s"
                }
                """;
            
            elasticsearchClient.indices().create(c -> c
                    .index(indexName)
                    .withJson(new StringReader(mappings))
                    .settings(s -> s.withJson(new StringReader(settings)))
            );
            
            log.info("Created index: {}", indexName);
            
        } catch (Exception e) {
            log.error("Error creating index {}", indexName, e);
            throw new RuntimeException("Failed to create index", e);
        }
    }
    
    private Map<String, Object> prepareDocumentForIndexing(Document document) {
        Map<String, Object> esDocument = new HashMap<>();
        esDocument.put("document_id", document.getDocumentId().toString());
        esDocument.put("tenant_id", document.getTenantId());
        esDocument.put("title", document.getTitle());
        esDocument.put("content", document.getContent());
        esDocument.put("metadata", document.getMetadata() != null ? document.getMetadata() : new HashMap<>());
        esDocument.put("indexed_at", LocalDateTime.now().toString());
        
        return esDocument;
    }
}
