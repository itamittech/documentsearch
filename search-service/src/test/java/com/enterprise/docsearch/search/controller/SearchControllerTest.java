package com.enterprise.docsearch.search.controller;

import com.enterprise.docsearch.common.config.TestConfig;
import com.enterprise.docsearch.common.context.TenantContext;
import com.enterprise.docsearch.common.dto.SearchResponse;
import com.enterprise.docsearch.common.dto.SearchResponse.SearchResult;
import com.enterprise.docsearch.search.controller.SearchController;
import com.enterprise.docsearch.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SearchController.class)
@ContextConfiguration(classes = {SearchController.class, TestConfig.class})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SearchService searchService;

    private static final String TENANT_ID = "tenant123";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testSearchWithResults() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        SearchResult result = SearchResult.builder()
                .documentId(documentId)
                .title("Test Document")
                .snippet("This is a test snippet")
                .score(8.5)
                .metadata(new HashMap<>())
                .highlights(Arrays.asList("test <em>highlight</em>"))
                .build();

        SearchResponse response = SearchResponse.builder()
                .query("test query")
                .totalHits(1L)
                .page(1)
                .pageSize(10)
                .tookMs(50L)
                .results(Collections.singletonList(result))
                .build();

        when(searchService.search(anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test query")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.query").value("test query"))
                .andExpect(jsonPath("$.data.totalHits").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.results[0].documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.results[0].title").value("Test Document"));

        verify(searchService).search("test query", 1, 10, false, true);
    }

    @Test
    void testSearchWithDefaultParameters() throws Exception {
        // Given
        SearchResponse response = SearchResponse.builder()
                .query("test")
                .totalHits(0L)
                .page(1)
                .pageSize(10)
                .tookMs(10L)
                .results(Collections.emptyList())
                .build();

        when(searchService.search(anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(searchService).search("test", 1, 10, false, true);
    }

    @Test
    void testSearchWithFuzzyEnabled() throws Exception {
        // Given
        SearchResponse response = SearchResponse.builder()
                .query("test")
                .totalHits(0L)
                .page(1)
                .pageSize(10)
                .tookMs(10L)
                .results(Collections.emptyList())
                .build();

        when(searchService.search(anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test")
                        .param("fuzzy", "true"))
                .andExpect(status().isOk());

        verify(searchService).search("test", 1, 10, true, true);
    }

    @Test
    void testSearchWithHighlightDisabled() throws Exception {
        // Given
        SearchResponse response = SearchResponse.builder()
                .query("test")
                .totalHits(0L)
                .page(1)
                .pageSize(10)
                .tookMs(10L)
                .results(Collections.emptyList())
                .build();

        when(searchService.search(anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test")
                        .param("highlight", "false"))
                .andExpect(status().isOk());

        verify(searchService).search("test", 1, 10, false, false);
    }

    @Test
    void testSearchWithCustomPagination() throws Exception {
        // Given
        SearchResponse response = SearchResponse.builder()
                .query("test")
                .totalHits(100L)
                .page(5)
                .pageSize(20)
                .tookMs(75L)
                .results(Collections.emptyList())
                .build();

        when(searchService.search(anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test")
                        .param("page", "5")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(5))
                .andExpect(jsonPath("$.data.pageSize").value(20));

        verify(searchService).search("test", 5, 20, false, true);
    }

    @Test
    void testSearchWithMultipleResults() throws Exception {
        // Given
        List<SearchResult> results = Arrays.asList(
                SearchResult.builder()
                        .documentId(UUID.randomUUID())
                        .title("Doc 1")
                        .snippet("Snippet 1")
                        .score(9.0)
                        .metadata(new HashMap<>())
                        .highlights(Collections.emptyList())
                        .build(),
                SearchResult.builder()
                        .documentId(UUID.randomUUID())
                        .title("Doc 2")
                        .snippet("Snippet 2")
                        .score(7.5)
                        .metadata(new HashMap<>())
                        .highlights(Collections.emptyList())
                        .build()
        );

        SearchResponse response = SearchResponse.builder()
                .query("test")
                .totalHits(2L)
                .page(1)
                .pageSize(10)
                .tookMs(30L)
                .results(results)
                .build();

        when(searchService.search(anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(2))
                .andExpect(jsonPath("$.data.results[0].title").value("Doc 1"))
                .andExpect(jsonPath("$.data.results[1].title").value("Doc 2"));
    }

    @Test
    void testSearchWithNoResults() throws Exception {
        // Given
        SearchResponse response = SearchResponse.builder()
                .query("nonexistent")
                .totalHits(0L)
                .page(1)
                .pageSize(10)
                .tookMs(15L)
                .results(Collections.emptyList())
                .build();

        when(searchService.search(anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalHits").value(0))
                .andExpect(jsonPath("$.data.results").isEmpty());
    }

    @Test
    void testSearchWithMaxSize() throws Exception {
        // Given
        SearchResponse response = SearchResponse.builder()
                .query("test")
                .totalHits(1000L)
                .page(1)
                .pageSize(100)
                .tookMs(100L)
                .results(Collections.emptyList())
                .build();

        when(searchService.search(anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(100));
    }

    @Test
    void testSearchServiceError() throws Exception {
        // Given
        when(searchService.search(anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("Search failed"));

        // When & Then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test"))
                .andExpect(status().is5xxServerError());
    }
}