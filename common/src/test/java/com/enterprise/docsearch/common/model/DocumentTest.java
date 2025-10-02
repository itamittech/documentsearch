package com.enterprise.docsearch.common.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTest {

    @Test
    void testDocumentBuilderWithAllFields() {
        UUID documentId = UUID.randomUUID();
        String tenantId = "tenant123";
        String title = "Test Document";
        String content = "This is test content";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("author", "John Doe");
        metadata.put("tags", new String[]{"test", "document"});

        LocalDateTime now = LocalDateTime.now();

        Document document = Document.builder()
                .documentId(documentId)
                .tenantId(tenantId)
                .title(title)
                .content(content)
                .metadata(metadata)
                .status(DocumentStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .contentHash("hash123")
                .fileSizeBytes(1024L)
                .build();

        assertEquals(documentId, document.getDocumentId());
        assertEquals(tenantId, document.getTenantId());
        assertEquals(title, document.getTitle());
        assertEquals(content, document.getContent());
        assertEquals(metadata, document.getMetadata());
        assertEquals(DocumentStatus.PENDING, document.getStatus());
        assertEquals(now, document.getCreatedAt());
        assertEquals(now, document.getUpdatedAt());
        assertEquals("hash123", document.getContentHash());
        assertEquals(1024L, document.getFileSizeBytes());
    }

    @Test
    void testDocumentBuilderWithMinimalFields() {
        Document document = Document.builder()
                .title("Minimal Document")
                .content("Minimal content")
                .build();

        assertNotNull(document);
        assertEquals("Minimal Document", document.getTitle());
        assertEquals("Minimal content", document.getContent());
        assertNull(document.getDocumentId());
        assertNull(document.getTenantId());
    }

    @Test
    void testDocumentEquality() {
        UUID documentId = UUID.randomUUID();

        Document doc1 = Document.builder()
                .documentId(documentId)
                .title("Test")
                .content("Content")
                .build();

        Document doc2 = Document.builder()
                .documentId(documentId)
                .title("Test")
                .content("Content")
                .build();

        assertEquals(doc1, doc2);
    }

    @Test
    void testDocumentSetters() {
        Document document = new Document();
        UUID documentId = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");

        document.setDocumentId(documentId);
        document.setTenantId("tenant123");
        document.setTitle("Updated Title");
        document.setContent("Updated Content");
        document.setMetadata(metadata);
        document.setStatus(DocumentStatus.INDEXED);

        assertEquals(documentId, document.getDocumentId());
        assertEquals("tenant123", document.getTenantId());
        assertEquals("Updated Title", document.getTitle());
        assertEquals("Updated Content", document.getContent());
        assertEquals(metadata, document.getMetadata());
        assertEquals(DocumentStatus.INDEXED, document.getStatus());
    }
}