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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.transport.httpclient5.ResponseException;

import io.github.resilience4j.retry.Retry;

/**
 * Plain unit tests for {@link OpenSearchRetryFactory}: no Spring context and no Nuxeo runtime are needed, since
 * it is built on the {@code resilience4j-retry} core library used programmatically.
 */
class OpenSearchRetryFactoryTest {

    @Test
    void isRetryableReturnsTrueForIOException() {
        assertThat(OpenSearchRetryFactory.isRetryable(new IOException("boom"))).isTrue();
        assertThat(OpenSearchRetryFactory.isRetryable(new SocketTimeoutException("timeout"))).isTrue();
    }

    @Test
    void isRetryableReturnsTrueForTransientOpenSearchStatus() {
        OpenSearchException e = mock(OpenSearchException.class);
        when(e.status()).thenReturn(503);
        assertThat(OpenSearchRetryFactory.isRetryable(e)).isTrue();
    }

    @Test
    void isRetryableReturnsTrueForTransientResponseExceptionStatus() {
        ResponseException e = mock(ResponseException.class);
        when(e.status()).thenReturn(429);
        assertThat(OpenSearchRetryFactory.isRetryable(e)).isTrue();
    }

    @Test
    void isRetryableReturnsFalseForNonTransientOpenSearchStatus() {
        OpenSearchException e = mock(OpenSearchException.class);
        when(e.status()).thenReturn(404);
        assertThat(OpenSearchRetryFactory.isRetryable(e)).isFalse();
    }

    @Test
    void isRetryableReturnsFalseForNonTransientResponseExceptionStatus() {
        // ResponseException extends IOException: the HTTP status must be checked before the generic IOException
        // fallback, otherwise a non-transient status like a 409 version conflict would be misclassified as
        // retryable (regression test for HXCS-8624).
        ResponseException e = mock(ResponseException.class);
        when(e.status()).thenReturn(409);
        assertThat(OpenSearchRetryFactory.isRetryable(e)).isFalse();
    }

    @Test
    void isRetryableReturnsFalseForUnrelatedException() {
        assertThat(OpenSearchRetryFactory.isRetryable(new IllegalArgumentException("nope"))).isFalse();
    }

    @Test
    void retriesTransientFailureThenSucceeds() throws Throwable {
        Retry retry = OpenSearchRetryFactory.create(3, 1);
        AtomicInteger callCount = new AtomicInteger();

        String result = retry.executeCheckedSupplier(() -> {
            if (callCount.incrementAndGet() < 3) {
                throw new IOException("transient failure");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    void exhaustsRetriesThenInvokesErrorCallback() {
        Retry retry = OpenSearchRetryFactory.create(3, 1);
        AtomicInteger callCount = new AtomicInteger();
        AtomicInteger errorCallbackCount = new AtomicInteger();
        retry.getEventPublisher().onError(event -> errorCallbackCount.incrementAndGet());

        assertThatThrownBy(() -> retry.executeCheckedSupplier(() -> {
            callCount.incrementAndGet();
            throw new IOException("always fails");
        })).isInstanceOf(IOException.class);

        assertThat(callCount.get()).isEqualTo(3);
        assertThat(errorCallbackCount.get()).isEqualTo(1);
    }

    @Test
    void doesNotRetryNonRetryableException() {
        Retry retry = OpenSearchRetryFactory.create(3, 1);
        AtomicInteger callCount = new AtomicInteger();

        assertThatThrownBy(() -> retry.executeCheckedSupplier(() -> {
            callCount.incrementAndGet();
            throw new IllegalArgumentException("not retryable");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(callCount.get()).isEqualTo(1);
    }

}
