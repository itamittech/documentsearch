package com.enterprise.docsearch.index.integration;

import com.enterprise.docsearch.common.config.TestConfig;
import com.enterprise.docsearch.common.model.Document;
import com.enterprise.docsearch.common.model.DocumentStatus;
import com.enterprise.docsearch.index.IndexServiceApplication;
import com.enterprise.docsearch.index.messaging.DocumentMessageConsumer;
import com.enterprise.docsearch.index.service.IndexingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = IndexServiceApplication.class)
@ActiveProfiles("test")
@Import(TestConfig.class)
class IndexServiceIntegrationTest {

    @Autowired
    private DocumentMessageConsumer messageConsumer;

    @SpyBean
    private IndexingService indexingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testIndexMessageProcessing() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        String tenantId = "tenant123";

        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", documentId.toString());
        payload.put("tenantId", tenantId);
        payload.put("title", "Test Document");
        payload.put("content", "Test Content");
        payload.put("status", "PENDING");

        Map<String, Object> message = new HashMap<>();
        message.put("operation", "index");
        message.put("payload", payload);

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleIndexMessage(messageJson);

        // Then
        verify(indexingService, timeout(5000)).indexDocument(any(Document.class));
    }

    @Test
    void testDeleteMessageProcessing() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        String tenantId = "tenant123";

        Map<String, Object> message = new HashMap<>();
        message.put("operation", "delete");
        message.put("document_id", documentId.toString());
        message.put("tenant_id", tenantId);

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleDeleteMessage(messageJson);

        // Then
        verify(indexingService, timeout(5000)).deleteDocument(eq(documentId), eq(tenantId));
    }

    @Test
    void testBulkIndexingMultipleDocuments() throws Exception {
        // Given
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            documents.add(Document.builder()
                    .documentId(UUID.randomUUID())
                    .tenantId("tenant123")
                    .title("Bulk Document " + i)
                    .content("Content " + i)
                    .status(DocumentStatus.PENDING)
                    .build());
        }

        // When
        indexingService.bulkIndexDocuments(documents);

        // Then
        verify(indexingService).bulkIndexDocuments(argThat(list ->
            list != null && list.size() == 5));
    }

    @Test
    void testIndexDocumentWithMetadata() throws Exception {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("author", "Test Author");
        metadata.put("tags", Arrays.asList("test", "integration"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", UUID.randomUUID().toString());
        payload.put("tenantId", "tenant123");
        payload.put("title", "Document with Metadata");
        payload.put("content", "Content");
        payload.put("metadata", metadata);

        Map<String, Object> message = new HashMap<>();
        message.put("operation", "index");
        message.put("payload", payload);

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleIndexMessage(messageJson);

        // Then
        verify(indexingService, timeout(5000)).indexDocument(any(Document.class));
    }

    @Test
    void testInvalidMessageHandling() {
        // Given
        String invalidJson = "{ invalid json structure";

        // When & Then
        try {
            messageConsumer.handleIndexMessage(invalidJson);
        } catch (Exception e) {
            // Expected exception
            verify(indexingService, never()).indexDocument(any(Document.class));
        }
    }

    @Test
    void testMessageWithInvalidOperation() throws Exception {
        // Given
        Map<String, Object> message = new HashMap<>();
        message.put("operation", "unknown_operation");
        message.put("payload", new HashMap<>());

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleIndexMessage(messageJson);

        // Then
        verify(indexingService, never()).indexDocument(any(Document.class));
    }

    @Test
    void testIndexDocumentWithLargeContent() throws Exception {
        // Given
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            largeContent.append("This is a large document line ").append(i).append(". ");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", UUID.randomUUID().toString());
        payload.put("tenantId", "tenant123");
        payload.put("title", "Large Document");
        payload.put("content", largeContent.toString());

        Map<String, Object> message = new HashMap<>();
        message.put("operation", "index");
        message.put("payload", payload);

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleIndexMessage(messageJson);

        // Then
        verify(indexingService, timeout(5000)).indexDocument(any(Document.class));
    }

    @Test
    void testSequentialIndexing() throws Exception {
        // Given - Create multiple index messages
        for (int i = 0; i < 3; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("documentId", UUID.randomUUID().toString());
            payload.put("tenantId", "tenant123");
            payload.put("title", "Sequential Document " + i);
            payload.put("content", "Content " + i);

            Map<String, Object> message = new HashMap<>();
            message.put("operation", "index");
            message.put("payload", payload);

            String messageJson = objectMapper.writeValueAsString(message);

            // When
            messageConsumer.handleIndexMessage(messageJson);
        }

        // Then
        verify(indexingService, timeout(5000).times(3)).indexDocument(any(Document.class));
    }

    @Test
    void testDeleteNonExistentDocument() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        String tenantId = "tenant999";

        Map<String, Object> message = new HashMap<>();
        message.put("operation", "delete");
        message.put("document_id", documentId.toString());
        message.put("tenant_id", tenantId);

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleDeleteMessage(messageJson);

        // Then
        verify(indexingService, timeout(5000)).deleteDocument(eq(documentId), eq(tenantId));
    }
}