package com.enterprise.docsearch.index.messaging;

import com.enterprise.docsearch.common.model.Document;
import com.enterprise.docsearch.common.model.DocumentStatus;
import com.enterprise.docsearch.index.service.IndexingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentMessageConsumerTest {

    @Mock
    private IndexingService indexingService;

    @InjectMocks
    private DocumentMessageConsumer messageConsumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        messageConsumer = new DocumentMessageConsumer(indexingService, objectMapper);
    }

    @Test
    void testHandleIndexMessageSuccess() throws Exception {
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

        doNothing().when(indexingService).indexDocument(any(Document.class));

        // When
        messageConsumer.handleIndexMessage(messageJson);

        // Then
        verify(indexingService).indexDocument(any(Document.class));
    }

    @Test
    void testHandleIndexMessageWithMetadata() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("author", "John Doe");

        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", documentId.toString());
        payload.put("tenantId", "tenant123");
        payload.put("title", "Test Document");
        payload.put("content", "Test Content");
        payload.put("metadata", metadata);

        Map<String, Object> message = new HashMap<>();
        message.put("operation", "index");
        message.put("payload", payload);

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleIndexMessage(messageJson);

        // Then
        verify(indexingService).indexDocument(any(Document.class));
    }

    @Test
    void testHandleIndexMessageWithInvalidOperation() throws Exception {
        // Given
        Map<String, Object> message = new HashMap<>();
        message.put("operation", "invalid");
        message.put("payload", null);

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleIndexMessage(messageJson);

        // Then
        verify(indexingService, never()).indexDocument(any(Document.class));
    }

    @Test
    void testHandleIndexMessageThrowsException() throws Exception {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", UUID.randomUUID().toString());

        Map<String, Object> message = new HashMap<>();
        message.put("operation", "index");
        message.put("payload", payload);

        String messageJson = objectMapper.writeValueAsString(message);

        doThrow(new RuntimeException("Indexing failed"))
                .when(indexingService).indexDocument(any(Document.class));

        // When & Then
        assertThrows(RuntimeException.class, () ->
                messageConsumer.handleIndexMessage(messageJson));
    }

    @Test
    void testHandleIndexMessageWithInvalidJson() {
        // Given
        String invalidJson = "{ invalid json }";

        // When & Then
        assertThrows(RuntimeException.class, () ->
                messageConsumer.handleIndexMessage(invalidJson));
    }

    @Test
    void testHandleDeleteMessageSuccess() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        String tenantId = "tenant123";

        Map<String, Object> message = new HashMap<>();
        message.put("operation", "delete");
        message.put("document_id", documentId.toString());
        message.put("tenant_id", tenantId);

        String messageJson = objectMapper.writeValueAsString(message);

        doNothing().when(indexingService).deleteDocument(documentId, tenantId);

        // When
        messageConsumer.handleDeleteMessage(messageJson);

        // Then
        verify(indexingService).deleteDocument(documentId, tenantId);
    }

    @Test
    void testHandleDeleteMessageWithInvalidOperation() throws Exception {
        // Given
        Map<String, Object> message = new HashMap<>();
        message.put("operation", "invalid");

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleDeleteMessage(messageJson);

        // Then
        verify(indexingService, never()).deleteDocument(any(UUID.class), anyString());
    }

    @Test
    void testHandleDeleteMessageThrowsException() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        String tenantId = "tenant123";

        Map<String, Object> message = new HashMap<>();
        message.put("operation", "delete");
        message.put("document_id", documentId.toString());
        message.put("tenant_id", tenantId);

        String messageJson = objectMapper.writeValueAsString(message);

        doThrow(new RuntimeException("Delete failed"))
                .when(indexingService).deleteDocument(documentId, tenantId);

        // When & Then
        assertThrows(RuntimeException.class, () ->
                messageConsumer.handleDeleteMessage(messageJson));
    }

    @Test
    void testHandleDeleteMessageWithInvalidUUID() throws Exception {
        // Given
        Map<String, Object> message = new HashMap<>();
        message.put("operation", "delete");
        message.put("document_id", "invalid-uuid");
        message.put("tenant_id", "tenant123");

        String messageJson = objectMapper.writeValueAsString(message);

        // When & Then
        assertThrows(RuntimeException.class, () ->
                messageConsumer.handleDeleteMessage(messageJson));
    }

    @Test
    void testHandleDeleteMessageWithInvalidJson() {
        // Given
        String invalidJson = "not a json";

        // When & Then
        assertThrows(RuntimeException.class, () ->
                messageConsumer.handleDeleteMessage(invalidJson));
    }

    @Test
    void testHandleIndexMessageWithNullPayload() throws Exception {
        // Given
        Map<String, Object> message = new HashMap<>();
        message.put("operation", "index");
        message.put("payload", null);

        String messageJson = objectMapper.writeValueAsString(message);

        // When
        messageConsumer.handleIndexMessage(messageJson);

        // Then
        verify(indexingService, never()).indexDocument(any(Document.class));
    }
}