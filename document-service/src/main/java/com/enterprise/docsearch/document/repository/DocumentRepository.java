package com.enterprise.docsearch.document.repository;

import com.enterprise.docsearch.common.model.DocumentStatus;
import com.enterprise.docsearch.document.entity.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    
    Optional<DocumentEntity> findByDocumentIdAndTenantId(UUID documentId, String tenantId);
    
    Page<DocumentEntity> findByTenantId(String tenantId, Pageable pageable);
    
    List<DocumentEntity> findByTenantIdAndStatus(String tenantId, DocumentStatus status);
    
    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.tenantId = :tenantId")
    long countByTenantId(String tenantId);
    
    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.tenantId = :tenantId AND d.status = :status")
    long countByTenantIdAndStatus(String tenantId, DocumentStatus status);
    
    void deleteByDocumentIdAndTenantId(UUID documentId, String tenantId);
}
