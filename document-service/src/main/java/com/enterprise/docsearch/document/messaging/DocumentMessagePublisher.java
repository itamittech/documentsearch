package com.enterprise.docsearch.document.messaging;

import com.enterprise.docsearch.common.model.Document;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentMessagePublisher {
    
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String EXCHANGE = "document.topic";
    private static final String INDEX_ROUTING_KEY = "document.index";
    private static final String DELETE_ROUTING_KEY = "document.delete";
    
    public void publishIndexMessage(Document document) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("message_id", UUID.randomUUID().toString());
            message.put("tenant_id", document.getTenantId());
            message.put("document_id", document.getDocumentId().toString());
            message.put("operation", "index");
            message.put("payload", document);
            message.put("timestamp", LocalDateTime.now().toString());
            message.put("retry_count", 0);
            message.put("max_retries", 3);
            
            String messageJson = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(EXCHANGE, INDEX_ROUTING_KEY, messageJson);
            
            log.info("Published index message for document: {}", document.getDocumentId());
        } catch (JsonProcessingException e) {
            log.error("Error publishing index message", e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }
    
    public void publishDeleteMessage(UUID documentId, String tenantId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("message_id", UUID.randomUUID().toString());
            message.put("tenant_id", tenantId);
            message.put("document_id", documentId.toString());
            message.put("operation", "delete");
            message.put("timestamp", LocalDateTime.now().toString());
            
            String messageJson = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(EXCHANGE, DELETE_ROUTING_KEY, messageJson);
            
            log.info("Published delete message for document: {}", documentId);
        } catch (JsonProcessingException e) {
            log.error("Error publishing delete message", e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }
}
