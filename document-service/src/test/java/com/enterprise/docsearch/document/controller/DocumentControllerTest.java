package com.enterprise.docsearch.document.controller;

import com.enterprise.docsearch.common.config.TestConfig;
import com.enterprise.docsearch.common.context.TenantContext;
import com.enterprise.docsearch.common.dto.ApiResponse;
import com.enterprise.docsearch.common.model.Document;
import com.enterprise.docsearch.common.model.DocumentStatus;
import com.enterprise.docsearch.document.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.enterprise.docsearch.document.controller.DocumentController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DocumentController.class)
@ContextConfiguration(classes = {DocumentController.class, TestConfig.class})
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentService documentService;

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
    void testCreateDocument() throws Exception {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("author", "John Doe");

        Document inputDoc = Document.builder()
                .title("Test Document")
                .content("Test Content")
                .metadata(metadata)
                .build();

        UUID documentId = UUID.randomUUID();
        Document createdDoc = Document.builder()
                .documentId(documentId)
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.PENDING)
                .metadata(metadata)
                .build();

        when(documentService.createDocument(any(Document.class))).thenReturn(createdDoc);

        // When & Then
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDoc)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Document created successfully"))
                .andExpect(jsonPath("$.data.document_id").value(documentId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(documentService).createDocument(any(Document.class));
    }

    @Test
    void testGetDocument() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        Document document = Document.builder()
                .documentId(documentId)
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.INDEXED)
                .build();

        when(documentService.getDocument(documentId)).thenReturn(Optional.of(document));

        // When & Then
        mockMvc.perform(get("/api/v1/documents/{documentId}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.title").value("Test Document"));

        verify(documentService).getDocument(documentId);
    }

    @Test
    void testGetDocumentNotFound() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        when(documentService.getDocument(documentId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/documents/{documentId}", documentId))
                .andExpect(status().isNotFound());

        verify(documentService).getDocument(documentId);
    }

    @Test
    void testListDocuments() throws Exception {
        // Given
        List<Document> documents = Arrays.asList(
                Document.builder()
                        .documentId(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .title("Doc 1")
                        .content("Content 1")
                        .build(),
                Document.builder()
                        .documentId(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .title("Doc 2")
                        .content("Content 2")
                        .build()
        );

        Page<Document> page = new PageImpl<>(documents, PageRequest.of(0, 10), 2);
        when(documentService.listDocuments(any(PageRequest.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/documents")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));

        verify(documentService).listDocuments(any(PageRequest.class));
    }

    @Test
    void testListDocumentsWithDefaultPagination() throws Exception {
        // Given
        Page<Document> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(documentService.listDocuments(any(PageRequest.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(documentService).listDocuments(PageRequest.of(0, 10));
    }

    @Test
    void testDeleteDocument() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        doNothing().when(documentService).deleteDocument(documentId);

        // When & Then
        mockMvc.perform(delete("/api/v1/documents/{documentId}", documentId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Document deletion initiated"))
                .andExpect(jsonPath("$.data.document_id").value(documentId.toString()))
                .andExpect(jsonPath("$.data.status").value("deletion_queued"));

        verify(documentService).deleteDocument(documentId);
    }

    @Test
    void testCreateDocumentWithMinimalData() throws Exception {
        // Given
        Document inputDoc = Document.builder()
                .title("Minimal Doc")
                .content("Content")
                .build();

        UUID documentId = UUID.randomUUID();
        Document createdDoc = Document.builder()
                .documentId(documentId)
                .title("Minimal Doc")
                .content("Content")
                .status(DocumentStatus.PENDING)
                .build();

        when(documentService.createDocument(any(Document.class))).thenReturn(createdDoc);

        // When & Then
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDoc)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }
}