package com.enterprise.docsearch.search.controller;

import com.enterprise.docsearch.common.dto.ApiResponse;
import com.enterprise.docsearch.common.dto.SearchResponse;
import com.enterprise.docsearch.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Search", description = "Document search endpoints")
@SecurityRequirement(name = "apiKey")
public class SearchController {
    
    private final SearchService searchService;
    
    @GetMapping
    @Operation(summary = "Search documents", description = "Performs full-text search across documents")
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean fuzzy,
            @RequestParam(defaultValue = "true") boolean highlight) {
        
        log.info("Search request - query: {}, page: {}, size: {}", q, page, size);
        
        SearchResponse response = searchService.search(q, page, size, fuzzy, highlight);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
