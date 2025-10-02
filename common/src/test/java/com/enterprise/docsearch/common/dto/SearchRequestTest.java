package com.enterprise.docsearch.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchRequestTest {

    @Test
    void testSearchRequestBuilder() {
        SearchRequest request = SearchRequest.builder()
                .query("test query")
                .page(1)
                .size(20)
                .fuzzy(true)
                .highlight(false)
                .build();

        assertEquals("test query", request.getQuery());
        assertEquals(1, request.getPage());
        assertEquals(20, request.getSize());
        assertTrue(request.getFuzzy());
        assertFalse(request.getHighlight());
    }

    @Test
    void testSearchRequestDefaults() {
        SearchRequest request = SearchRequest.builder()
                .query("test")
                .build();

        assertEquals("test", request.getQuery());
        assertEquals(1, request.getPage());  // Default is 1
        assertEquals(10, request.getSize()); // Default is 10
        assertNull(request.getFuzzy());      // Default is null
        assertNull(request.getHighlight());  // Default is null
    }

    @Test
    void testSearchRequestSetters() {
        SearchRequest request = new SearchRequest();
        request.setQuery("search term");
        request.setPage(2);
        request.setSize(50);
        request.setFuzzy(true);
        request.setHighlight(true);

        assertEquals("search term", request.getQuery());
        assertEquals(2, request.getPage());
        assertEquals(50, request.getSize());
        assertTrue(request.getFuzzy());
        assertTrue(request.getHighlight());
    }

    @Test
    void testSearchRequestEquality() {
        SearchRequest request1 = SearchRequest.builder()
                .query("test")
                .page(1)
                .size(10)
                .build();

        SearchRequest request2 = SearchRequest.builder()
                .query("test")
                .page(1)
                .size(10)
                .build();

        assertEquals(request1, request2);
    }
}