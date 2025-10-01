package com.enterprise.docsearch.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    
    private String query;
    private long totalHits;
    private int page;
    private int pageSize;
    private long tookMs;
    private List<SearchResult> results;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {
        private UUID documentId;
        private String title;
        private String snippet;
        private double score;
        private Map<String, Object> metadata;
        private List<String> highlights;
    }
}
