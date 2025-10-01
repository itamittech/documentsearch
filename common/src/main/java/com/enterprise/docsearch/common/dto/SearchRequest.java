package com.enterprise.docsearch.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {
    
    @NotBlank(message = "Query is required")
    private String query;
    
    @Builder.Default
    @Min(value = 1, message = "Page must be at least 1")
    private int page = 1;
    
    @Builder.Default
    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 100, message = "Size must not exceed 100")
    private int size = 10;
    
    private String[] fields;
    
    private Boolean fuzzy;
    
    private Boolean highlight;
}
