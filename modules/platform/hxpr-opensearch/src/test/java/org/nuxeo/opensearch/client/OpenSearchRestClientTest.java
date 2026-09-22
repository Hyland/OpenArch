/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.hyland.openarch.common.resilience.Resilience4jRegistries;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

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
        cleanRegistry();
        // Fast retry config (2 attempts, 1ms wait) and fast-tripping circuit breaker config (window/min calls of
        // 2) keep retry- and circuit-breaker-related tests quick while still exercising the real Resilience4j
        // retry loop and circuit breaker.
        restClient = spy(new OpenSearchRestClient(null, new JacksonJsonpMapper(), null,
                OpenSearchRetryFactory.create(2, 1), OpenSearchCircuitBreakerFactory.create(50.0f, 2, 2, 1)));
        // Inject the mock client
        restClient.client = mockClient;
    }

    /**
     * Builds a real, spied {@link OpenSearchException} with the given message, so its message can be overridden
     * while its {@link Throwable} internals (e.g. captured stack trace) stay properly initialized.
     * <p>
     * A raw {@code mock(OpenSearchException.class)} bypasses {@link Throwable}'s constructor entirely, leaving
     * its internal state incomplete; wrapping a genuinely constructed instance in a spy keeps that state intact
     * while still allowing {@link OpenSearchException#getMessage()} (and, via {@code statusCode}, {@code status()})
     * to be overridden for the test.
     */
    private static OpenSearchException newOpenSearchException(String message, int statusCode) {
        ErrorResponse response = ErrorResponse.of(
                b -> b.error(e -> e.type("exception").reason("original reason")).status(statusCode));
        OpenSearchException exception = spy(new OpenSearchException(response));
        doReturn(message).when(exception).getMessage();
        return exception;
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
        OpenSearchException osException = newOpenSearchException(originalMessage, 0);

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
        OpenSearchException osException = newOpenSearchException(normalMessage, 0);

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

        // IOException is retryable: with the test's 2-attempt retry config, the mock is invoked twice
        // (initial attempt + 1 retry) before the retries are exhausted and NuxeoException is thrown.
        verify(mockClient, times(2)).index(any(IndexRequest.class));
    }

    @Test
    void testIndexRetriesTransientIOExceptionThenSucceeds() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        when(mockClient.index(any(IndexRequest.class))).thenThrow(new IOException("transient network error"))
                                                        .thenReturn(mockIndexResponse);

        // When
        IndexResponse response = restClient.index(request);

        // Then
        assertEquals(mockIndexResponse, response);
        verify(mockClient, times(2)).index(any(IndexRequest.class));
    }

    @Test
    void testIndexDoesNotRetryNonTransientOpenSearchStatus() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        OpenSearchException osException = newOpenSearchException("Not found", 404);
        when(mockClient.index(any(IndexRequest.class))).thenThrow(osException);

        // When/Then
        assertThrows(NuxeoException.class, () -> restClient.index(request));

        // 404 is not part of the transient status set, so the call is not retried.
        verify(mockClient, times(1)).index(any(IndexRequest.class));
    }

    @Test
    void testIndexHandlesNullExceptionMessage() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        OpenSearchException osException = newOpenSearchException(null, 0);

        when(mockClient.index(any(IndexRequest.class))).thenThrow(osException);

        assertThrows(NuxeoException.class, () -> restClient.index(request));
    }

    @Test
    void testIndexSanitizesMultiplePreviewOccurrences() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        String originalMessage = "Error 1. Preview of field's value: 'sensitive1'. Error 2. Preview of field's value: 'sensitive2'";
        OpenSearchException osException = newOpenSearchException(originalMessage, 0);

        when(mockClient.index(any(IndexRequest.class))).thenThrow(osException);

        NuxeoException exception = assertThrows(NuxeoException.class, () -> restClient.index(request));
        assertFalse(exception.getMessage().contains("sensitive1"));
        assertFalse(exception.getMessage().contains("sensitive2"));
    }

    @Test
    void testCircuitBreakerOpensAfterRepeatedFailuresAndFailsFast() throws Exception {
        // Given: a long wait-in-open-state (unlike the fast-tripping one from setUp()) so the breaker stays open
        // for the whole test instead of racing into HALF_OPEN before the second call below is made.
        // Clean the registry first to remove the setUp() instance so create() builds a new one with this test's config.
        cleanRegistry();
        restClient = spy(new OpenSearchRestClient(null, new JacksonJsonpMapper(), null,
                OpenSearchRetryFactory.create(2, 1), OpenSearchCircuitBreakerFactory.create(50.0f, 2, 2, 60_000)));
        restClient.client = mockClient;

        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        when(mockClient.index(any(IndexRequest.class))).thenThrow(new IOException("Network error"));

        // When: the first call exhausts its 2 retry attempts, both recorded as failures by the circuit breaker
        // (window size 2, minimum calls 2), tripping it open.
        assertThrows(NuxeoException.class, () -> restClient.index(request));
        verify(mockClient, times(2)).index(any(IndexRequest.class));

        // Then: the circuit breaker is now open, so a second call fails fast without reaching the transport again.
        NuxeoException exception = assertThrows(NuxeoException.class, () -> restClient.index(request));
        assertTrue(exception.getCause() instanceof CallNotPermittedException);
        verify(mockClient, times(2)).index(any(IndexRequest.class));
    }

    @Test
    void testCircuitBreakerIgnoresNonRetryableStatusForFailureRate() throws Exception {
        // Given
        IndexRequest<DocumentDTO> request = IndexRequest.of(
                b -> b.index(TEST_INDEX).id(DOC_ID).document(new DocumentDTO()));

        OpenSearchException notFound = newOpenSearchException("Not found", 404);
        when(mockClient.index(any(IndexRequest.class))).thenThrow(notFound);

        // When/Then: a 404 is not a transient failure (see OpenSearchRetryFactory#isRetryable), so repeated
        // occurrences must never trip the circuit breaker.
        for (int i = 0; i < 5; i++) {
            assertThrows(NuxeoException.class, () -> restClient.index(request));
        }
        verify(mockClient, times(5)).index(any(IndexRequest.class));
    }

    @AfterEach
    void tearDown() {
        cleanRegistry();
    }

    private static void cleanRegistry() {
        Resilience4jRegistries.removeRetry(OpenSearchRetryFactory.RETRY_NAME);
        Resilience4jRegistries.removeCircuitBreaker(OpenSearchCircuitBreakerFactory.CIRCUIT_BREAKER_NAME);
    }
}
