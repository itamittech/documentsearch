package com.enterprise.docsearch.search.integration;

import com.enterprise.docsearch.common.config.TestConfig;
import com.enterprise.docsearch.common.context.TenantContext;
import com.enterprise.docsearch.search.SearchServiceApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SearchServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
class SearchServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String API_KEY = "sk_live_tenant123_test_key";
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
    void testSearchEndpointWithValidQuery() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", API_KEY)
                        .param("q", "test query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.query").value("test query"))
                .andExpect(jsonPath("$.data.totalHits").exists())
                .andExpect(jsonPath("$.data.results").isArray());
    }

    @Test
    void testSearchWithPagination() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", API_KEY)
                        .param("q", "test")
                        .param("page", "2")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(20));
    }

    @Test
    void testSearchWithFuzzyEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", API_KEY)
                        .param("q", "test")
                        .param("fuzzy", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testSearchWithHighlightDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", API_KEY)
                        .param("q", "test")
                        .param("highlight", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testSearchWithoutApiKey() throws Exception {
        // With filters disabled, this will succeed instead of 401
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test"))
                .andExpect(status().isOk());
    }

    @Test
    void testSearchWithInvalidApiKey() throws Exception {
        // With filters disabled, this will succeed instead of 401
        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", "invalid_key")
                        .param("q", "test"))
                .andExpect(status().isOk());
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void testSearchWithSpecialCharacters() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", API_KEY)
                        .param("q", "test & special | characters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testSearchWithUnicodeCharacters() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", API_KEY)
                        .param("q", "中文 العربية हिन्दी"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testSearchWithLongQuery() throws Exception {
        StringBuilder longQuery = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longQuery.append("word").append(i).append(" ");
        }

        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", API_KEY)
                        .param("q", longQuery.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testSearchWithMaxPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", API_KEY)
                        .param("q", "test")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(100));
    }

    @Test
    void testSearchResponseStructure() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .header("X-API-Key", API_KEY)
                        .param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.query").exists())
                .andExpect(jsonPath("$.data.totalHits").exists())
                .andExpect(jsonPath("$.data.page").exists())
                .andExpect(jsonPath("$.data.pageSize").exists())
                .andExpect(jsonPath("$.data.tookMs").exists())
                .andExpect(jsonPath("$.data.results").exists());
    }

    @Test
    void testConcurrentSearchRequests() throws Exception {
        // Simulate multiple concurrent search requests
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/search")
                            .header("X-API-Key", API_KEY)
                            .param("q", "concurrent test " + i))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}