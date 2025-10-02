package com.enterprise.docsearch.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.enterprise.docsearch.common.model.Document;
import com.enterprise.docsearch.common.model.DocumentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IndexingService indexingService;

    private static final String TENANT_ID = "tenant123";

    @BeforeEach
    void setUp() {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        indexingService = new IndexingService(elasticsearchClient, new ObjectMapper());
    }

    @Test
    void testIndexDocumentSuccess() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("author", "John Doe");

        Document document = Document.builder()
                .documentId(documentId)
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .metadata(metadata)
                .status(DocumentStatus.PENDING)
                .build();

        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(true);
        when(indicesClient.exists(any(Function.class))).thenReturn(existsResponse);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.result()).thenReturn(Result.Created);
        when(elasticsearchClient.index(any(Function.class))).thenReturn(indexResponse);

        // When
        indexingService.indexDocument(document);

        // Then
        verify(elasticsearchClient).index(any(Function.class));
        verify(indicesClient).exists(any(Function.class));
    }

    @Test
    void testIndexDocumentCreatesIndexIfNotExists() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        Document document = Document.builder()
                .documentId(documentId)
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .build();

        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(false);
        when(indicesClient.exists(any(Function.class))).thenReturn(existsResponse);
        when(indicesClient.create(any(Function.class))).thenReturn(null);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.result()).thenReturn(Result.Created);
        when(elasticsearchClient.index(any(Function.class))).thenReturn(indexResponse);

        // When
        indexingService.indexDocument(document);

        // Then
        verify(indicesClient).create(any(Function.class));
        verify(elasticsearchClient).index(any(Function.class));
    }

    @Test
    void testIndexDocumentThrowsExceptionOnFailure() throws Exception {
        // Given
        Document document = Document.builder()
                .documentId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .build();

        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(true);
        when(indicesClient.exists(any(Function.class))).thenReturn(existsResponse);

        when(elasticsearchClient.index(any(Function.class))).thenThrow(new RuntimeException("ES error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> indexingService.indexDocument(document));
    }

    @Test
    void testBulkIndexDocuments() throws Exception {
        // Given
        List<Document> documents = Arrays.asList(
                Document.builder()
                        .documentId(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .title("Doc 1")
                        .content("Content 1")
                        .build(),
                Document.builder()
                        .documentId(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .title("Doc 2")
                        .content("Content 2")
                        .build()
        );

        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(true);
        when(indicesClient.exists(any(Function.class))).thenReturn(existsResponse);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(bulkResponse.items()).thenReturn(Collections.emptyList());
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        // When
        indexingService.bulkIndexDocuments(documents);

        // Then
        verify(elasticsearchClient).bulk(any(BulkRequest.class));
    }

    @Test
    void testBulkIndexDocumentsWithErrors() throws Exception {
        // Given
        List<Document> documents = Collections.singletonList(
                Document.builder()
                        .documentId(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .title("Doc 1")
                        .content("Content 1")
                        .build()
        );

        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(true);
        when(indicesClient.exists(any(Function.class))).thenReturn(existsResponse);

        BulkResponseItem errorItem = mock(BulkResponseItem.class);
        when(errorItem.error()).thenReturn(null);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(true);
        when(bulkResponse.items()).thenReturn(Collections.singletonList(errorItem));
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        // When
        indexingService.bulkIndexDocuments(documents);

        // Then
        verify(elasticsearchClient).bulk(any(BulkRequest.class));
    }

    @Test
    void testBulkIndexEmptyList() {
        // Given
        List<Document> documents = Collections.emptyList();

        // When
        indexingService.bulkIndexDocuments(documents);

        // Then
        verifyNoInteractions(elasticsearchClient);
    }

    @Test
    void testDeleteDocument() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(elasticsearchClient.delete(any(Function.class))).thenReturn(deleteResponse);

        // When
        indexingService.deleteDocument(documentId, TENANT_ID);

        // Then
        verify(elasticsearchClient).delete(any(Function.class));
    }

    @Test
    void testDeleteDocumentThrowsException() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        when(elasticsearchClient.delete(any(Function.class))).thenThrow(new RuntimeException("Delete failed"));

        // When & Then
        assertThrows(RuntimeException.class, () ->
                indexingService.deleteDocument(documentId, TENANT_ID));
    }

    @Test
    void testIndexDocumentWithMetadata() throws Exception {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("author", "Jane Doe");
        metadata.put("tags", Arrays.asList("test", "document"));

        Document document = Document.builder()
                .documentId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .metadata(metadata)
                .build();

        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(true);
        when(indicesClient.exists(any(Function.class))).thenReturn(existsResponse);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.result()).thenReturn(Result.Created);
        when(elasticsearchClient.index(any(Function.class))).thenReturn(indexResponse);

        // When
        indexingService.indexDocument(document);

        // Then
        verify(elasticsearchClient).index(any(Function.class));
    }

    @Test
    void testIndexDocumentWithoutMetadata() throws Exception {
        // Given
        Document document = Document.builder()
                .documentId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .title("Test Document")
                .content("Test Content")
                .build();

        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(true);
        when(indicesClient.exists(any(Function.class))).thenReturn(existsResponse);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.result()).thenReturn(Result.Created);
        when(elasticsearchClient.index(any(Function.class))).thenReturn(indexResponse);

        // When
        indexingService.indexDocument(document);

        // Then
        verify(elasticsearchClient).index(any(Function.class));
    }

    @Test
    void testIndexDocumentUpdatesExisting() throws Exception {
        // Given
        Document document = Document.builder()
                .documentId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .title("Updated Document")
                .content("Updated Content")
                .build();

        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(true);
        when(indicesClient.exists(any(Function.class))).thenReturn(existsResponse);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.result()).thenReturn(Result.Updated);
        when(elasticsearchClient.index(any(Function.class))).thenReturn(indexResponse);

        // When
        indexingService.indexDocument(document);

        // Then
        verify(elasticsearchClient).index(any(Function.class));
    }

    @Test
    void testBulkIndexCreatesIndexIfNotExists() throws Exception {
        // Given
        List<Document> documents = Collections.singletonList(
                Document.builder()
                        .documentId(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .title("Doc 1")
                        .content("Content 1")
                        .build()
        );

        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(false);
        when(indicesClient.exists(any(Function.class))).thenReturn(existsResponse);
        when(indicesClient.create(any(Function.class))).thenReturn(null);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(bulkResponse.items()).thenReturn(Collections.emptyList());
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        // When
        indexingService.bulkIndexDocuments(documents);

        // Then
        verify(indicesClient).create(any(Function.class));
        verify(elasticsearchClient).bulk(any(BulkRequest.class));
    }
}