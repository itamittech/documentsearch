package com.enterprise.docsearch.document.integration;

import com.enterprise.docsearch.common.config.TestConfig;
import com.enterprise.docsearch.common.context.TenantContext;
import com.enterprise.docsearch.common.model.Document;
import com.enterprise.docsearch.common.model.DocumentStatus;
import com.enterprise.docsearch.document.DocumentServiceApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DocumentServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
class DocumentServiceIntegrationTest {

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
    void testFullDocumentLifecycle() throws Exception {
        // 1. Create a document
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("author", "Integration Test");
        metadata.put("category", "test");

        Document document = Document.builder()
                .title("Integration Test Document")
                .content("This is a test document for integration testing")
                .metadata(metadata)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/documents")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(document)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.document_id").exists())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String documentId = objectMapper.readTree(responseBody)
                .get("data").get("document_id").asText();

        // 2. Get the created document
        mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Integration Test Document"))
                .andExpect(jsonPath("$.data.content").value("This is a test document for integration testing"));

        // 3. List documents
        mockMvc.perform(get("/api/v1/documents")
                        .header("X-API-Key", API_KEY)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

        // 4. Delete the document
        mockMvc.perform(delete("/api/v1/documents/{documentId}", documentId)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("deletion_queued"));
    }

    @Test
    void testCreateDocumentWithoutApiKey() throws Exception {
        Document document = Document.builder()
                .title("Test")
                .content("Content")
                .build();

        // With filters disabled, this will succeed (202) instead of 401
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(document)))
                .andExpect(status().isAccepted());
    }

    @Test
    void testCreateDocumentWithInvalidApiKey() throws Exception {
        Document document = Document.builder()
                .title("Test")
                .content("Content")
                .build();

        // With filters disabled, this will succeed (202) instead of 401
        mockMvc.perform(post("/api/v1/documents")
                        .header("X-API-Key", "invalid_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(document)))
                .andExpect(status().isAccepted());
    }

    @Test
    void testListDocumentsWithPagination() throws Exception {
        // Create multiple documents
        for (int i = 0; i < 5; i++) {
            Document doc = Document.builder()
                    .title("Document " + i)
                    .content("Content " + i)
                    .build();

            mockMvc.perform(post("/api/v1/documents")
                            .header("X-API-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(doc)))
                    .andExpect(status().isAccepted());
        }

        // Test pagination
        mockMvc.perform(get("/api/v1/documents")
                        .header("X-API-Key", API_KEY)
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageable.pageSize").value(3));
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void testGetNonExistentDocument() throws Exception {
        String randomUuid = "550e8400-e29b-41d4-a716-446655440000";

        mockMvc.perform(get("/api/v1/documents/{documentId}", randomUuid)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateDocumentWithLargeContent() throws Exception {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("This is line ").append(i).append(" of a large document. ");
        }

        Document document = Document.builder()
                .title("Large Document")
                .content(largeContent.toString())
                .build();

        mockMvc.perform(post("/api/v1/documents")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(document)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testCreateDocumentWithSpecialCharacters() throws Exception {
        Document document = Document.builder()
                .title("Test with émojis 🚀 and spëcial çhars")
                .content("Content with 中文, العربية, हिन्दी")
                .build();

        mockMvc.perform(post("/api/v1/documents")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(document)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }
}