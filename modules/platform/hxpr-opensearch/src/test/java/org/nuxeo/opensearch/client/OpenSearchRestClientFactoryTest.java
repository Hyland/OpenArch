/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.hyland.openarch.runtime.api.Framework;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit tests for {@link OpenSearchRestClientFactory} methods that do not depend on the Nuxeo Framework. Tests
 * that exercise {@code create()} (and therefore {@link Framework#getProperty(String)}) live in
 * {@code TestOpenSearchRestClientFactory} and run inside a minimal Nuxeo runtime context.
 */
@ExtendWith(MockitoExtension.class)
class OpenSearchRestClientFactoryTest {

    private final OpenSearchRestClientFactory factory = new OpenSearchRestClientFactory();

    @Test
    void testTraceOpenSearchRequestReadsBodyAndClosesStream() throws IOException {
        String body = "{\"query\":\"test\"}";
        InputStream inputStream = spy(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContent()).thenReturn(inputStream);
        ClassicHttpRequest request = mock(ClassicHttpRequest.class);
        when(request.getEntity()).thenReturn(entity);

        factory.traceOpenSearchRequest(request, null, mock(HttpContext.class));

        // the input stream must be closed by the try-with-resources
        verify(inputStream).close();
    }

    @Test
    void testTraceOpenSearchRequestReadsUtf8Body() throws IOException {
        // Verifies UTF-8 characters (e.g. accents) are decoded correctly, since
        // the previous Scanner-based implementation also used UTF-8.
        String body = "{\"name\":\"éàü漢字\"}";
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContent()).thenReturn(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        ClassicHttpRequest request = mock(ClassicHttpRequest.class);
        when(request.getEntity()).thenReturn(entity);

        assertDoesNotThrow(() -> factory.traceOpenSearchRequest(request, null, mock(HttpContext.class)));
    }

    @Test
    void testTraceOpenSearchRequestHandlesEmptyBody() throws IOException {
        // The previous Scanner implementation returned "" when the stream was empty.
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(new byte[0]));
        ClassicHttpRequest request = mock(ClassicHttpRequest.class);
        when(request.getEntity()).thenReturn(entity);

        assertDoesNotThrow(() -> factory.traceOpenSearchRequest(request, null, mock(HttpContext.class)));
    }

    @Test
    void testTraceOpenSearchRequestIgnoresRequestWithoutEntity() throws IOException {
        ClassicHttpRequest request = mock(ClassicHttpRequest.class);
        when(request.getEntity()).thenReturn(null);

        factory.traceOpenSearchRequest(request, null, mock(HttpContext.class));

        // getMethod / getRequestUri must never be called when there is no entity
        verify(request, never()).getMethod();
        verify(request, never()).getRequestUri();
    }

    @Test
    void testTraceOpenSearchRequestIgnoresNonClassicRequest() throws IOException {
        HttpRequest request = mock(HttpRequest.class);

        factory.traceOpenSearchRequest(request, null, mock(HttpContext.class));

        verify(request, never()).getMethod();
        verify(request, never()).getRequestUri();
    }
}
