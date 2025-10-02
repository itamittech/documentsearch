package com.enterprise.docsearch.index.messaging;

import com.enterprise.docsearch.common.model.Document;
import com.enterprise.docsearch.index.service.IndexingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentMessageConsumer {
    
    private final IndexingService indexingService;
    private final ObjectMapper objectMapper;
    
    @RabbitListener(queues = "indexing.queue", concurrency = "5")
    public void handleIndexMessage(String messageJson) {
        try {
            log.info("Received index message: {}", messageJson);
            
            Map<String, Object> message = objectMapper.readValue(messageJson, Map.class);
            
            String operation = (String) message.get("operation");
            Map<String, Object> payload = (Map<String, Object>) message.get("payload");
            
            if ("index".equals(operation) && payload != null) {
                Document document = objectMapper.convertValue(payload, Document.class);
                indexingService.indexDocument(document);
                log.info("Successfully processed index message for document: {}", 
                        document.getDocumentId());
            }
            
        } catch (Exception e) {
            log.error("Error processing index message", e);
            throw new RuntimeException("Failed to process message", e);
        }
    }
    
    @RabbitListener(queues = "deletion.queue", concurrency = "3")
    public void handleDeleteMessage(String messageJson) {
        try {
            log.info("Received delete message: {}", messageJson);
            
            Map<String, Object> message = objectMapper.readValue(messageJson, Map.class);
            
            String operation = (String) message.get("operation");
            String documentIdStr = (String) message.get("document_id");
            String tenantId = (String) message.get("tenant_id");
            
            if ("delete".equals(operation)) {
                UUID documentId = UUID.fromString(documentIdStr);
                indexingService.deleteDocument(documentId, tenantId);
                log.info("Successfully processed delete message for document: {}", documentId);
            }
            
        } catch (Exception e) {
            log.error("Error processing delete message", e);
            throw new RuntimeException("Failed to process message", e);
        }
    }
}
