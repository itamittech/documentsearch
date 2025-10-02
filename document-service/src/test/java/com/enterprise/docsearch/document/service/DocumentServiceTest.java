package com.enterprise.docsearch.document.service;

import com.enterprise.docsearch.common.context.TenantContext;
import com.enterprise.docsearch.common.model.Document;
import com.enterprise.docsearch.common.model.DocumentStatus;
import com.enterprise.docsearch.document.entity.DocumentEntity;
import com.enterprise.docsearch.document.messaging.DocumentMessagePublisher;
import com.enterprise.docsearch.document.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMessagePublisher messagePublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
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

        DocumentEntity savedEntity = DocumentEntity.builder()
                .documentId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.PENDING)
                .fileSizeBytes(12L)
                .metadataJson("{\"author\":\"John Doe\"}")
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"author\":\"John Doe\"}");
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(savedEntity);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(metadata);

        // When
        Document result = documentService.createDocument(inputDoc);

        // Then
        assertNotNull(result);
        assertEquals(savedEntity.getDocumentId(), result.getDocumentId());
        assertEquals(TENANT_ID, result.getTenantId());
        assertEquals("Test Document", result.getTitle());
        assertEquals(DocumentStatus.PENDING, result.getStatus());

        ArgumentCaptor<DocumentEntity> entityCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(entityCaptor.capture());
        assertEquals(TENANT_ID, entityCaptor.getValue().getTenantId());

        verify(messagePublisher).publishIndexMessage(any(Document.class));
    }

    @Test
    void testGetDocument() {
        // Given
        UUID documentId = UUID.randomUUID();
        DocumentEntity entity = DocumentEntity.builder()
                .documentId(documentId)
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.INDEXED)
                .build();

        when(documentRepository.findByDocumentIdAndTenantId(documentId, TENANT_ID))
                .thenReturn(Optional.of(entity));

        // When
        Optional<Document> result = documentService.getDocument(documentId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(documentId, result.get().getDocumentId());
        assertEquals("Test Document", result.get().getTitle());
        verify(documentRepository).findByDocumentIdAndTenantId(documentId, TENANT_ID);
    }

    @Test
    void testGetDocumentNotFound() {
        // Given
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findByDocumentIdAndTenantId(documentId, TENANT_ID))
                .thenReturn(Optional.empty());

        // When
        Optional<Document> result = documentService.getDocument(documentId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testListDocuments() {
        // Given
        List<DocumentEntity> entities = Arrays.asList(
                DocumentEntity.builder()
                        .documentId(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .title("Doc 1")
                        .content("Content 1")
                        .status(DocumentStatus.INDEXED)
                        .build(),
                DocumentEntity.builder()
                        .documentId(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .title("Doc 2")
                        .content("Content 2")
                        .status(DocumentStatus.PENDING)
                        .build()
        );

        Pageable pageable = PageRequest.of(0, 10);
        Page<DocumentEntity> entityPage = new PageImpl<>(entities, pageable, entities.size());

        when(documentRepository.findByTenantId(TENANT_ID, pageable)).thenReturn(entityPage);

        // When
        Page<Document> result = documentService.listDocuments(pageable);

        // Then
        assertEquals(2, result.getContent().size());
        assertEquals("Doc 1", result.getContent().get(0).getTitle());
        assertEquals("Doc 2", result.getContent().get(1).getTitle());
        verify(documentRepository).findByTenantId(TENANT_ID, pageable);
    }

    @Test
    void testDeleteDocument() {
        // Given
        UUID documentId = UUID.randomUUID();
        DocumentEntity entity = DocumentEntity.builder()
                .documentId(documentId)
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.INDEXED)
                .build();

        when(documentRepository.findByDocumentIdAndTenantId(documentId, TENANT_ID))
                .thenReturn(Optional.of(entity));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(entity);

        // When
        documentService.deleteDocument(documentId);

        // Then
        ArgumentCaptor<DocumentEntity> entityCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(entityCaptor.capture());
        assertEquals(DocumentStatus.DELETED, entityCaptor.getValue().getStatus());

        verify(messagePublisher).publishDeleteMessage(documentId, TENANT_ID);
    }

    @Test
    void testDeleteDocumentNotFound() {
        // Given
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findByDocumentIdAndTenantId(documentId, TENANT_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> documentService.deleteDocument(documentId));
    }

    @Test
    void testUpdateDocumentStatus() {
        // Given
        UUID documentId = UUID.randomUUID();
        DocumentEntity entity = DocumentEntity.builder()
                .documentId(documentId)
                .tenantId(TENANT_ID)
                .title("Test Document")
                .status(DocumentStatus.PENDING)
                .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(entity));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(entity);

        // When
        documentService.updateDocumentStatus(documentId, DocumentStatus.INDEXED);

        // Then
        ArgumentCaptor<DocumentEntity> entityCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(entityCaptor.capture());
        assertEquals(DocumentStatus.INDEXED, entityCaptor.getValue().getStatus());
        assertNotNull(entityCaptor.getValue().getIndexedAt());
    }

    @Test
    void testUpdateDocumentStatusNotFound() {
        // Given
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // When
        documentService.updateDocumentStatus(documentId, DocumentStatus.INDEXED);

        // Then
        verify(documentRepository, never()).save(any(DocumentEntity.class));
    }

    @Test
    void testCreateDocumentWithoutMetadata() {
        // Given
        Document inputDoc = Document.builder()
                .title("Test Document")
                .content("Test Content")
                .build();

        DocumentEntity savedEntity = DocumentEntity.builder()
                .documentId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.PENDING)
                .fileSizeBytes(12L)
                .build();

        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(savedEntity);

        // When
        Document result = documentService.createDocument(inputDoc);

        // Then
        assertNotNull(result);
        verify(documentRepository).save(any(DocumentEntity.class));
        verify(messagePublisher).publishIndexMessage(any(Document.class));
    }

    @Test
    void testContentHashCalculation() {
        // Given
        Document inputDoc = Document.builder()
                .title("Test")
                .content("Test Content")
                .build();

        DocumentEntity savedEntity = DocumentEntity.builder()
                .documentId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .title("Test")
                .content("Test Content")
                .status(DocumentStatus.PENDING)
                .contentHash("somehash")
                .build();

        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(savedEntity);

        // When
        Document result = documentService.createDocument(inputDoc);

        // Then
        ArgumentCaptor<DocumentEntity> entityCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(entityCaptor.capture());
        assertNotNull(entityCaptor.getValue().getContentHash());
    }
}