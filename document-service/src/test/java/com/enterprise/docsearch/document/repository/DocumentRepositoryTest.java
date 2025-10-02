package com.enterprise.docsearch.document.repository;

import com.enterprise.docsearch.common.model.DocumentStatus;
import com.enterprise.docsearch.document.entity.DocumentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testSaveDocument() {
        // Given
        DocumentEntity entity = DocumentEntity.builder()
                .tenantId("tenant123")
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.PENDING)
                .fileSizeBytes(100L)
                .build();

        // When
        DocumentEntity saved = documentRepository.save(entity);

        // Then
        assertNotNull(saved.getDocumentId());
        assertEquals("tenant123", saved.getTenantId());
        assertEquals("Test Document", saved.getTitle());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void testFindByDocumentIdAndTenantId() {
        // Given
        DocumentEntity entity = DocumentEntity.builder()
                .tenantId("tenant123")
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.INDEXED)
                .build();

        DocumentEntity saved = entityManager.persistAndFlush(entity);

        // When
        Optional<DocumentEntity> found = documentRepository.findByDocumentIdAndTenantId(
                saved.getDocumentId(), "tenant123");

        // Then
        assertTrue(found.isPresent());
        assertEquals(saved.getDocumentId(), found.get().getDocumentId());
        assertEquals("Test Document", found.get().getTitle());
    }

    @Test
    void testFindByDocumentIdAndTenantIdNotFound() {
        // Given
        UUID randomId = UUID.randomUUID();

        // When
        Optional<DocumentEntity> found = documentRepository.findByDocumentIdAndTenantId(
                randomId, "tenant123");

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByDocumentIdAndTenantIdWrongTenant() {
        // Given
        DocumentEntity entity = DocumentEntity.builder()
                .tenantId("tenant123")
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.INDEXED)
                .build();

        DocumentEntity saved = entityManager.persistAndFlush(entity);

        // When
        Optional<DocumentEntity> found = documentRepository.findByDocumentIdAndTenantId(
                saved.getDocumentId(), "tenant456");

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByTenantId() {
        // Given
        DocumentEntity entity1 = DocumentEntity.builder()
                .tenantId("tenant123")
                .title("Doc 1")
                .content("Content 1")
                .status(DocumentStatus.INDEXED)
                .build();

        DocumentEntity entity2 = DocumentEntity.builder()
                .tenantId("tenant123")
                .title("Doc 2")
                .content("Content 2")
                .status(DocumentStatus.PENDING)
                .build();

        DocumentEntity entity3 = DocumentEntity.builder()
                .tenantId("tenant456")
                .title("Doc 3")
                .content("Content 3")
                .status(DocumentStatus.INDEXED)
                .build();

        entityManager.persist(entity1);
        entityManager.persist(entity2);
        entityManager.persist(entity3);
        entityManager.flush();

        // When
        Page<DocumentEntity> page = documentRepository.findByTenantId("tenant123", PageRequest.of(0, 10));

        // Then
        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(e -> "tenant123".equals(e.getTenantId())));
    }

    @Test
    void testFindByTenantIdWithPagination() {
        // Given
        for (int i = 0; i < 15; i++) {
            DocumentEntity entity = DocumentEntity.builder()
                    .tenantId("tenant123")
                    .title("Doc " + i)
                    .content("Content " + i)
                    .status(DocumentStatus.INDEXED)
                    .build();
            entityManager.persist(entity);
        }
        entityManager.flush();

        // When
        Page<DocumentEntity> page1 = documentRepository.findByTenantId("tenant123", PageRequest.of(0, 10));
        Page<DocumentEntity> page2 = documentRepository.findByTenantId("tenant123", PageRequest.of(1, 10));

        // Then
        assertEquals(15, page1.getTotalElements());
        assertEquals(10, page1.getContent().size());
        assertEquals(5, page2.getContent().size());
    }

    @Test
    void testUpdateDocument() {
        // Given
        DocumentEntity entity = DocumentEntity.builder()
                .tenantId("tenant123")
                .title("Original Title")
                .content("Original Content")
                .status(DocumentStatus.PENDING)
                .build();

        DocumentEntity saved = entityManager.persistAndFlush(entity);

        // When
        saved.setTitle("Updated Title");
        saved.setStatus(DocumentStatus.INDEXED);
        DocumentEntity updated = documentRepository.save(saved);
        entityManager.flush();

        // Then
        assertEquals("Updated Title", updated.getTitle());
        assertEquals(DocumentStatus.INDEXED, updated.getStatus());
    }

    @Test
    void testDocumentWithMetadata() {
        // Given
        DocumentEntity entity = DocumentEntity.builder()
                .tenantId("tenant123")
                .title("Test Document")
                .content("Test Content")
                .status(DocumentStatus.PENDING)
                .metadataJson("{\"author\":\"John Doe\",\"tags\":[\"test\"]}")
                .build();

        // When
        DocumentEntity saved = documentRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        DocumentEntity found = documentRepository.findById(saved.getDocumentId()).orElseThrow();

        // Then
        assertNotNull(found.getMetadataJson());
        assertTrue(found.getMetadataJson().contains("John Doe"));
    }
}