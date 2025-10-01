package com.enterprise.docsearch.document.controller;

import com.enterprise.docsearch.common.dto.ApiResponse;
import com.enterprise.docsearch.common.model.Document;
import com.enterprise.docsearch.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Document management endpoints")
@SecurityRequirement(name = "apiKey")
public class DocumentController {
    
    private final DocumentService documentService;
    
    @PostMapping
    @Operation(summary = "Index a new document", description = "Creates and queues a document for indexing")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDocument(
            @Valid @RequestBody Document document) {
        
        log.info("Received request to create document: {}", document.getTitle());
        
        Document created = documentService.createDocument(document);
        
        Map<String, Object> response = Map.of(
            "document_id", created.getDocumentId(),
            "status", created.getStatus(),
            "message", "Document queued for indexing"
        );
        
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Document created successfully", response));
    }
    
    @GetMapping("/{documentId}")
    @Operation(summary = "Get document details", description = "Retrieves a document by ID")
    public ResponseEntity<ApiResponse<Document>> getDocument(
            @PathVariable UUID documentId) {
        
        log.info("Fetching document: {}", documentId);
        
        return documentService.getDocument(documentId)
                .map(doc -> ResponseEntity.ok(ApiResponse.success(doc)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    @Operation(summary = "List documents", description = "Lists all documents for the tenant")
    public ResponseEntity<ApiResponse<Page<Document>>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        log.info("Listing documents - page: {}, size: {}", page, size);
        
        Page<Document> documents = documentService.listDocuments(PageRequest.of(page, size));
        
        return ResponseEntity.ok(ApiResponse.success(documents));
    }
    
    @DeleteMapping("/{documentId}")
    @Operation(summary = "Delete document", description = "Marks a document for deletion")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteDocument(
            @PathVariable UUID documentId) {
        
        log.info("Deleting document: {}", documentId);
        
        documentService.deleteDocument(documentId);
        
        Map<String, Object> response = Map.of(
            "document_id", documentId,
            "status", "deletion_queued",
            "message", "Document queued for deletion"
        );
        
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Document deletion initiated", response));
    }
}
