package com.enterprise.docsearch.common.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
public class HealthController {
    
    private final Map<String, HealthIndicator> healthIndicators;
    
    public HealthController(Map<String, HealthIndicator> healthIndicators) {
        this.healthIndicators = healthIndicators;
    }
    
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        Map<String, ComponentHealth> components = new HashMap<>();
        boolean allHealthy = true;
        
        for (Map.Entry<String, HealthIndicator> entry : healthIndicators.entrySet()) {
            String name = entry.getKey().replace("HealthIndicator", "");
            Health health = entry.getValue().health();
            
            ComponentHealth componentHealth = ComponentHealth.builder()
                    .status(health.getStatus().getCode())
                    .details(health.getDetails())
                    .build();
            
            components.put(name, componentHealth);
            
            if (!"UP".equals(health.getStatus().getCode())) {
                allHealthy = false;
            }
        }
        
        HealthResponse response = HealthResponse.builder()
                .status(allHealthy ? "UP" : "DOWN")
                .timestamp(LocalDateTime.now())
                .components(components)
                .build();
        
        return allHealthy 
                ? ResponseEntity.ok(response) 
                : ResponseEntity.status(503).body(response);
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthResponse {
        private String status;
        private LocalDateTime timestamp;
        private Map<String, ComponentHealth> components;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentHealth {
        private String status;
        private Map<String, Object> details;
    }
}
