package com.enterprise.docsearch.document.service;

import com.enterprise.docsearch.common.context.TenantContext;
import com.enterprise.docsearch.common.model.Document;
import com.enterprise.docsearch.common.model.DocumentStatus;
import com.enterprise.docsearch.document.entity.DocumentEntity;
import com.enterprise.docsearch.document.messaging.DocumentMessagePublisher;
import com.enterprise.docsearch.document.repository.DocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {
    
    private final DocumentRepository documentRepository;
    private final DocumentMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public Document createDocument(Document document) {
        String tenantId = TenantContext.getTenantId();
        log.info("Creating document for tenant: {}", tenantId);
        
        DocumentEntity entity = DocumentEntity.builder()
                .tenantId(tenantId)
                .title(document.getTitle())
                .content(document.getContent())
                .status(DocumentStatus.PENDING)
                .fileSizeBytes((long) document.getContent().length())
                .contentHash(calculateHash(document.getContent()))
                .build();
        
        if (document.getMetadata() != null) {
            try {
                entity.setMetadataJson(objectMapper.writeValueAsString(document.getMetadata()));
            } catch (JsonProcessingException e) {
                log.error("Error serializing metadata", e);
            }
        }
        
        DocumentEntity saved = documentRepository.save(entity);
        
        // Publish indexing message
        Document indexDoc = mapToDocument(saved);
        messagePublisher.publishIndexMessage(indexDoc);
        
        log.info("Document created with ID: {}", saved.getDocumentId());
        return indexDoc;
    }
    
    @Cacheable(value = "documents", key = "#documentId")
    @Transactional(readOnly = true)
    public Optional<Document> getDocument(UUID documentId) {
        String tenantId = TenantContext.getTenantId();
        log.debug("Fetching document {} for tenant {}", documentId, tenantId);
        
        return documentRepository.findByDocumentIdAndTenantId(documentId, tenantId)
                .map(this::mapToDocument);
    }
    
    @Transactional(readOnly = true)
    public Page<Document> listDocuments(Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        return documentRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToDocument);
    }
    
    @CacheEvict(value = "documents", key = "#documentId")
    @Transactional
    public void deleteDocument(UUID documentId) {
        String tenantId = TenantContext.getTenantId();
        log.info("Deleting document {} for tenant {}", documentId, tenantId);
        
        DocumentEntity entity = documentRepository.findByDocumentIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        entity.setStatus(DocumentStatus.DELETED);
        documentRepository.save(entity);
        
        // Publish deletion message
        messagePublisher.publishDeleteMessage(documentId, tenantId);
        
        log.info("Document marked for deletion: {}", documentId);
    }
    
    @Transactional
    public void updateDocumentStatus(UUID documentId, DocumentStatus status) {
        documentRepository.findById(documentId).ifPresent(entity -> {
            entity.setStatus(status);
            if (status == DocumentStatus.INDEXED) {
                entity.setIndexedAt(LocalDateTime.now());
            }
            documentRepository.save(entity);
        });
    }
    
    private Document mapToDocument(DocumentEntity entity) {
        Document doc = Document.builder()
                .documentId(entity.getDocumentId())
                .tenantId(entity.getTenantId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .indexedAt(entity.getIndexedAt())
                .contentHash(entity.getContentHash())
                .fileSizeBytes(entity.getFileSizeBytes())
                .build();
        
        if (entity.getMetadataJson() != null) {
            try {
                doc.setMetadata(objectMapper.readValue(entity.getMetadataJson(), Map.class));
            } catch (JsonProcessingException e) {
                log.error("Error deserializing metadata", e);
            }
        }
        
        return doc;
    }
    
    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error calculating hash", e);
            return null;
        }
    }
}