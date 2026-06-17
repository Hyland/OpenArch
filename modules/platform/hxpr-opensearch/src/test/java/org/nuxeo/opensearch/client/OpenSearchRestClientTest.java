/*
 * (C) Copyright 2025 Hyland (https://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nuxeo.opensearch.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;

@ExtendWith(MockitoExtension.class)
class OpenSearchRestClientTest {

    public static final String TEST_INDEX = "test-index";

    public static final String DOC_ID = "doc-id";

    @Mock
    private OpenSearchClient mockClient;

    @Mock
    private IndexResponse mockIndexResponse;

    private OpenSearchRestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = spy(new OpenSearchRestClient(null, new JacksonJsonpMapper(), null));
        // Inject the mock client
        restClient.client = mockClient;
    }

    @Test
    void testIndexSuccessWithoutTimeout() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        when(mockClient.index(any(IndexRequest.class))).thenReturn(mockIndexResponse);

        // When
        IndexResponse response = restClient.index(request);

        // Then
        assertNotNull(response);
        assertEquals(mockIndexResponse, response);
        verify(mockClient).index(any(IndexRequest.class));
    }

    @Test
    void testIndexSuccessWithTimeout() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()).timeout(t -> t.time("30s")));

        when(mockClient.index(any(IndexRequest.class))).thenReturn(mockIndexResponse);

        // When
        IndexResponse response = restClient.index(request);

        // Then
        assertNotNull(response);
        verify(mockClient).index(any(IndexRequest.class));
    }

    @Test
    void testIndexSanitizesExceptionMessageWithPII() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        String originalMessage = "failed to parse. Preview of field's value: '{phone=123-456-7890, email=test@example.com}'";
        OpenSearchException osException = mock(OpenSearchException.class);
        when(osException.getMessage()).thenReturn(originalMessage);

        when(mockClient.index(any(IndexRequest.class))).thenThrow(osException);

        // When/Then
        NuxeoException exception = assertThrows(NuxeoException.class, () -> restClient.index(request));
        assertNotNull(exception.getMessage());
        assertFalse(exception.getMessage().contains("phone=123-456-7890"));
        assertFalse(exception.getMessage().contains("email=test@example.com"));
        assertTrue(exception.getMessage().contains("[REDACTED]"));
    }

    @Test
    void testIndexDoesNotSanitizeNormalExceptionMessage() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        String normalMessage = "Connection timeout error";
        OpenSearchException osException = mock(OpenSearchException.class);
        when(osException.getMessage()).thenReturn(normalMessage);

        when(mockClient.index(any(IndexRequest.class))).thenThrow(osException);

        // When/Then
        NuxeoException exception = assertThrows(NuxeoException.class, () -> restClient.index(request));
        assertEquals(normalMessage, exception.getMessage());
    }

    @Test
    void testIndexHandlesIOException() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        IOException ioException = new IOException("Network error");
        when(mockClient.index(any(IndexRequest.class))).thenThrow(ioException);

        assertThrows(NuxeoException.class, () -> restClient.index(request));
    }

    @Test
    void testIndexHandlesNullExceptionMessage() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        OpenSearchException osException = mock(OpenSearchException.class);
        when(osException.getMessage()).thenReturn(null);

        when(mockClient.index(any(IndexRequest.class))).thenThrow(osException);

        assertThrows(NuxeoException.class, () -> restClient.index(request));
    }

    @Test
    void testIndexSanitizesMultiplePreviewOccurrences() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        String originalMessage = "Error 1. Preview of field's value: 'sensitive1'. Error 2. Preview of field's value: 'sensitive2'";
        OpenSearchException osException = mock(OpenSearchException.class);
        when(osException.getMessage()).thenReturn(originalMessage);

        when(mockClient.index(any(IndexRequest.class))).thenThrow(osException);

        NuxeoException exception = assertThrows(NuxeoException.class, () -> restClient.index(request));
        assertFalse(exception.getMessage().contains("sensitive1"));
        assertFalse(exception.getMessage().contains("sensitive2"));
    }
}
