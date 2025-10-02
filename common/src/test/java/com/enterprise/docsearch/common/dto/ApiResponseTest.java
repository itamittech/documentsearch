package com.enterprise.docsearch.common.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void testSuccessResponseWithData() {
        String data = "Test Data";
        ApiResponse<String> response = ApiResponse.success(data);

        assertTrue(response.isSuccess());
        assertEquals(data, response.getData());
        assertNull(response.getMessage());
        assertNull(response.getError());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testSuccessResponseWithMessageAndData() {
        String message = "Operation successful";
        Map<String, Object> data = Map.of("key", "value");
        ApiResponse<Map<String, Object>> response = ApiResponse.success(message, data);

        assertTrue(response.isSuccess());
        assertEquals(message, response.getMessage());
        assertEquals(data, response.getData());
        assertNull(response.getError());
    }

    @Test
    void testErrorResponse() {
        String errorMessage = "Something went wrong";
        ApiResponse<Object> response = ApiResponse.error(errorMessage);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void testErrorResponseWithDetails() {
        String errorMessage = "Validation failed";
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code("VALIDATION_ERROR")
                .details("Invalid field value")
                .path("/api/v1/documents")
                .build();

        ApiResponse<Object> response = ApiResponse.error(errorMessage, errorDetails);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getMessage());
        assertNotNull(response.getError());
        assertEquals("VALIDATION_ERROR", response.getError().getCode());
        assertEquals("Invalid field value", response.getError().getDetails());
        assertEquals("/api/v1/documents", response.getError().getPath());
    }

    @Test
    void testBuilderPattern() {
        LocalDateTime timestamp = LocalDateTime.now();
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("Custom message")
                .data("Custom data")
                .timestamp(timestamp)
                .build();

        assertTrue(response.isSuccess());
        assertEquals("Custom message", response.getMessage());
        assertEquals("Custom data", response.getData());
        assertEquals(timestamp, response.getTimestamp());
    }

    @Test
    void testErrorDetailsBuilder() {
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code("ERROR_CODE")
                .details("Error details here")
                .path("/api/path")
                .build();

        assertEquals("ERROR_CODE", errorDetails.getCode());
        assertEquals("Error details here", errorDetails.getDetails());
        assertEquals("/api/path", errorDetails.getPath());
    }

    @Test
    void testErrorResponseMessageInMainObject() {
        String errorMessage = "Not found";
        ApiResponse<Object> response = ApiResponse.error(errorMessage);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getMessage());
        assertNull(response.getError());
    }

    @Test
    void testErrorDetailsWithOnlyCode() {
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code("INTERNAL_ERROR")
                .build();

        assertEquals("INTERNAL_ERROR", errorDetails.getCode());
        assertNull(errorDetails.getDetails());
        assertNull(errorDetails.getPath());
    }
}