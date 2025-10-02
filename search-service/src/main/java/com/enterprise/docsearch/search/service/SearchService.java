package com.enterprise.docsearch.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.enterprise.docsearch.common.context.TenantContext;
import com.enterprise.docsearch.common.dto.SearchResponse.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {
    
    private final ElasticsearchClient elasticsearchClient;
    private final ObjectMapper objectMapper;
    
    private static final String INDEX_PREFIX = "docs_tenant_";
    
    @Cacheable(value = "searchResults", key = "#tenantId + ':' + #query + ':' + #page + ':' + #size")
    public com.enterprise.docsearch.common.dto.SearchResponse search(
            String query, int page, int size, boolean fuzzy, boolean highlight) {
        
        String tenantId = TenantContext.getTenantId();
        String indexName = INDEX_PREFIX + tenantId;
        
        log.info("Searching in index {} for query: {}", indexName, query);
        
        long startTime = System.currentTimeMillis();
        
        try {
            Query searchQuery = fuzzy 
                    ? buildFuzzyQuery(query)
                    : buildStandardQuery(query);
            
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexName)
                    .query(searchQuery)
                    .from((page - 1) * size)
                    .size(size)
                    .highlight(h -> h
                            .fields("title", hf -> hf)
                            .fields("content", hf -> hf.numberOfFragments(1).fragmentSize(150))
                    )
            );
            
            SearchResponse<JsonNode> response = elasticsearchClient.search(
                    searchRequest, 
                    JsonNode.class
            );
            
            long tookMs = System.currentTimeMillis() - startTime;
            
            return buildSearchResponse(query, response, page, size, tookMs);
            
        } catch (Exception e) {
            log.error("Error performing search", e);
            throw new RuntimeException("Search failed", e);
        }
    }
    
    private Query buildStandardQuery(String queryText) {
        return Query.of(q -> q
                .multiMatch(m -> m
                        .query(queryText)
                        .fields("title^2", "content")
                        .fuzziness("AUTO")
                )
        );
    }
    
    private Query buildFuzzyQuery(String queryText) {
        return Query.of(q -> q
                .multiMatch(m -> m
                        .query(queryText)
                        .fields("title^2", "content")
                        .fuzziness("2")
                )
        );
    }
    
    private com.enterprise.docsearch.common.dto.SearchResponse buildSearchResponse(
            String query,
            SearchResponse<JsonNode> esResponse,
            int page,
            int size,
            long tookMs) {
        
        HitsMetadata<JsonNode> hits = esResponse.hits();
        
        List<SearchResult> results = hits.hits().stream()
                .map(this::mapToSearchResult)
                .collect(Collectors.toList());
        
        return com.enterprise.docsearch.common.dto.SearchResponse.builder()
                .query(query)
                .totalHits(hits.total().value())
                .page(page)
                .pageSize(size)
                .tookMs(tookMs)
                .results(results)
                .build();
    }
    
    private SearchResult mapToSearchResult(Hit<JsonNode> hit) {
        JsonNode source = hit.source();
        
        String title = source.has("title") ? source.get("title").asText() : "";
        String content = source.has("content") ? source.get("content").asText() : "";
        
        // Create snippet from content (first 200 chars)
        String snippet = content.length() > 200 
                ? content.substring(0, 200) + "..." 
                : content;
        
        // Extract highlights if available
        List<String> highlights = new ArrayList<>();
        if (hit.highlight() != null && !hit.highlight().isEmpty()) {
            hit.highlight().values().forEach(highlightList -> 
                highlights.addAll(highlightList)
            );
        }
        
        Map<String, Object> metadata = new HashMap<>();
        if (source.has("metadata")) {
            JsonNode metadataNode = source.get("metadata");
            metadata = objectMapper.convertValue(metadataNode, Map.class);
        }
        
        return SearchResult.builder()
                .documentId(UUID.fromString(source.get("document_id").asText()))
                .title(title)
                .snippet(snippet)
                .score(hit.score() != null ? hit.score() : 0.0)
                .metadata(metadata)
                .highlights(highlights)
                .build();
    }
}
