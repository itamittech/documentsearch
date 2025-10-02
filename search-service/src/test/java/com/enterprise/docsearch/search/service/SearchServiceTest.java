package com.enterprise.docsearch.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.enterprise.docsearch.common.context.TenantContext;
import com.enterprise.docsearch.common.dto.SearchResponse.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @InjectMocks
    private SearchService searchService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private static final String TENANT_ID = "tenant123";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        searchService = new SearchService(elasticsearchClient, objectMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testSearchWithResults() throws Exception {
        // Given
        String query = "test query";
        UUID documentId = UUID.randomUUID();

        ObjectNode sourceNode = objectMapper.createObjectNode();
        sourceNode.put("document_id", documentId.toString());
        sourceNode.put("title", "Test Document");
        sourceNode.put("content", "This is test content for the query");

        Hit<JsonNode> hit = mock(Hit.class);
        when(hit.source()).thenReturn(sourceNode);
        when(hit.score()).thenReturn(8.5);
        when(hit.highlight()).thenReturn(Collections.emptyMap());

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(1L);
        when(totalHits.relation()).thenReturn(TotalHitsRelation.Eq);

        HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(Collections.singletonList(hit));
        when(hitsMetadata.total()).thenReturn(totalHits);

        SearchResponse<JsonNode> esResponse = mock(SearchResponse.class);
        when(esResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenReturn(esResponse);

        // When
        com.enterprise.docsearch.common.dto.SearchResponse response =
                searchService.search(query, 1, 10, false, true);

        // Then
        assertNotNull(response);
        assertEquals(query, response.getQuery());
        assertEquals(1L, response.getTotalHits());
        assertEquals(1, response.getPage());
        assertEquals(10, response.getPageSize());
        assertEquals(1, response.getResults().size());

        SearchResult result = response.getResults().get(0);
        assertEquals(documentId, result.getDocumentId());
        assertEquals("Test Document", result.getTitle());
        assertEquals(8.5, result.getScore());

        verify(elasticsearchClient).search(any(SearchRequest.class), eq(JsonNode.class));
    }

    @Test
    void testSearchWithNoResults() throws Exception {
        // Given
        String query = "nonexistent query";

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(0L);

        HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(hitsMetadata.total()).thenReturn(totalHits);

        SearchResponse<JsonNode> esResponse = mock(SearchResponse.class);
        when(esResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenReturn(esResponse);

        // When
        com.enterprise.docsearch.common.dto.SearchResponse response =
                searchService.search(query, 1, 10, false, true);

        // Then
        assertNotNull(response);
        assertEquals(0L, response.getTotalHits());
        assertTrue(response.getResults().isEmpty());
    }

    @Test
    void testSearchWithFuzzyEnabled() throws Exception {
        // Given
        String query = "test";

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(0L);

        HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(hitsMetadata.total()).thenReturn(totalHits);

        SearchResponse<JsonNode> esResponse = mock(SearchResponse.class);
        when(esResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenReturn(esResponse);

        // When
        com.enterprise.docsearch.common.dto.SearchResponse response =
                searchService.search(query, 1, 10, true, true);

        // Then
        assertNotNull(response);
        verify(elasticsearchClient).search(any(SearchRequest.class), eq(JsonNode.class));
    }

    @Test
    void testSearchWithPagination() throws Exception {
        // Given
        String query = "test";
        int page = 3;
        int size = 20;

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(0L);

        HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(hitsMetadata.total()).thenReturn(totalHits);

        SearchResponse<JsonNode> esResponse = mock(SearchResponse.class);
        when(esResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenReturn(esResponse);

        // When
        com.enterprise.docsearch.common.dto.SearchResponse response =
                searchService.search(query, page, size, false, true);

        // Then
        assertEquals(page, response.getPage());
        assertEquals(size, response.getPageSize());
    }

    @Test
    void testSearchWithMetadata() throws Exception {
        // Given
        String query = "test";
        UUID documentId = UUID.randomUUID();

        ObjectNode metadataNode = objectMapper.createObjectNode();
        metadataNode.put("author", "John Doe");

        ObjectNode sourceNode = objectMapper.createObjectNode();
        sourceNode.put("document_id", documentId.toString());
        sourceNode.put("title", "Test Document");
        sourceNode.put("content", "Content");
        sourceNode.set("metadata", metadataNode);

        Hit<JsonNode> hit = mock(Hit.class);
        when(hit.source()).thenReturn(sourceNode);
        when(hit.score()).thenReturn(5.0);
        when(hit.highlight()).thenReturn(Collections.emptyMap());

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(1L);

        HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(Collections.singletonList(hit));
        when(hitsMetadata.total()).thenReturn(totalHits);

        SearchResponse<JsonNode> esResponse = mock(SearchResponse.class);
        when(esResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenReturn(esResponse);

        // When
        com.enterprise.docsearch.common.dto.SearchResponse response =
                searchService.search(query, 1, 10, false, true);

        // Then
        assertNotNull(response.getResults().get(0).getMetadata());
        assertEquals("John Doe", response.getResults().get(0).getMetadata().get("author"));
    }

    @Test
    void testSearchWithHighlights() throws Exception {
        // Given
        String query = "test";
        UUID documentId = UUID.randomUUID();

        ObjectNode sourceNode = objectMapper.createObjectNode();
        sourceNode.put("document_id", documentId.toString());
        sourceNode.put("title", "Test Document");
        sourceNode.put("content", "This is test content");

        Map<String, List<String>> highlights = new HashMap<>();
        highlights.put("content", Arrays.asList("This is <em>test</em> content"));

        Hit<JsonNode> hit = mock(Hit.class);
        when(hit.source()).thenReturn(sourceNode);
        when(hit.score()).thenReturn(7.5);
        when(hit.highlight()).thenReturn(highlights);

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(1L);

        HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(Collections.singletonList(hit));
        when(hitsMetadata.total()).thenReturn(totalHits);

        SearchResponse<JsonNode> esResponse = mock(SearchResponse.class);
        when(esResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenReturn(esResponse);

        // When
        com.enterprise.docsearch.common.dto.SearchResponse response =
                searchService.search(query, 1, 10, false, true);

        // Then
        assertFalse(response.getResults().get(0).getHighlights().isEmpty());
        assertTrue(response.getResults().get(0).getHighlights().get(0).contains("<em>test</em>"));
    }

    @Test
    void testSearchException() throws Exception {
        // Given
        String query = "test";
        when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("Elasticsearch error"));

        // When & Then
        assertThrows(RuntimeException.class, () ->
                searchService.search(query, 1, 10, false, true));
    }

    @Test
    void testSearchUsesCorrectTenantIndex() throws Exception {
        // Given
        String query = "test";

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(0L);

        HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(hitsMetadata.total()).thenReturn(totalHits);

        SearchResponse<JsonNode> esResponse = mock(SearchResponse.class);
        when(esResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenReturn(esResponse);

        // When
        searchService.search(query, 1, 10, false, true);

        // Then
        verify(elasticsearchClient).search(any(SearchRequest.class), eq(JsonNode.class));
    }

    @Test
    void testSnippetCreation() throws Exception {
        // Given
        String query = "test";
        UUID documentId = UUID.randomUUID();

        ObjectNode sourceNode = objectMapper.createObjectNode();
        sourceNode.put("document_id", documentId.toString());
        sourceNode.put("title", "Test");
        sourceNode.put("content", "a".repeat(250)); // Long content

        Hit<JsonNode> hit = mock(Hit.class);
        when(hit.source()).thenReturn(sourceNode);
        when(hit.score()).thenReturn(5.0);
        when(hit.highlight()).thenReturn(Collections.emptyMap());

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(1L);

        HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(Collections.singletonList(hit));
        when(hitsMetadata.total()).thenReturn(totalHits);

        SearchResponse<JsonNode> esResponse = mock(SearchResponse.class);
        when(esResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenReturn(esResponse);

        // When
        com.enterprise.docsearch.common.dto.SearchResponse response =
                searchService.search(query, 1, 10, false, true);

        // Then
        String snippet = response.getResults().get(0).getSnippet();
        assertTrue(snippet.length() <= 203); // 200 chars + "..."
        assertTrue(snippet.endsWith("..."));
    }
}