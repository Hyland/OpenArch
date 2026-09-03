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

package org.nuxeo.common.httpclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Random;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;

public class TestJitteredExponentialBackoffRetryHandler {

    protected final JitteredExponentialBackoffRetryHandler handler = new JitteredExponentialBackoffRetryHandler();

    @Test
    public void doesNotRetryWhenMaxTryExceeded() {
        HttpContext ctx = contextFor("GET");
        HttpResponseException ex = new HttpResponseException(500, "Internal Server Error");

        assertFalse(handler.retryRequest(ex, 4, ctx));
    }

    @Test
    public void doesNotRetryWhenCircuitBreakerRejects() {
        HttpContext ctx = contextFor("GET");
        CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
        when(circuitBreaker.getName()).thenReturn("test-cb");
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(circuitBreaker.getCircuitBreakerConfig()).thenReturn(CircuitBreakerConfig.ofDefaults());
        IOException ex = new IOException("wrapped",
                CallNotPermittedException.createCallNotPermittedException(circuitBreaker));

        assertFalse(handler.retryRequest(ex, 1, ctx));
    }

    @Test
    public void retriesIdempotentMethodOnRetriableStatus() {
        HttpContext ctx = contextFor("GET");
        for (int status : new int[] { 408, 425, 429, 500, 502, 503, 504 }) {
            HttpResponseException ex = new HttpResponseException(status, "error");
            assertTrue(handler.retryRequest(ex, 1, ctx), "expected retry for status " + status);
        }
    }

    @Test
    public void doesNotRetryIdempotentMethodOnNonRetriableStatus() {
        HttpContext ctx = contextFor("GET");
        HttpResponseException ex = new HttpResponseException(400, "Bad Request");

        assertFalse(handler.retryRequest(ex, 1, ctx));
    }

    @Test
    public void retriesNonIdempotentMethodOnlyOnRestrictedStatuses() {
        HttpContext ctx = contextFor("POST");
        for (int status : new int[] { 408, 425, 429 }) {
            HttpResponseException ex = new HttpResponseException(status, "error");
            assertTrue(handler.retryRequest(ex, 1, ctx), "expected retry for status " + status);
        }
    }

    @Test
    public void doesNotRetryNonIdempotentMethodOnServerErrorStatus() {
        HttpContext ctx = contextFor("POST");
        for (int status : new int[] { 500, 502, 503, 504 }) {
            HttpResponseException ex = new HttpResponseException(status, "error");
            assertFalse(handler.retryRequest(ex, 1, ctx), "expected no retry for status " + status);
        }
    }

    @Test
    public void retriesOnSocketTimeout() {
        assertTrue(handler.retryRequest(new SocketTimeoutException(), 1, contextFor("GET")));
    }

    @Test
    public void retriesOnConnectTimeout() {
        assertTrue(handler.retryRequest(new ConnectTimeoutException(), 1, contextFor("GET")));
    }

    @Test
    public void retriesOnConnectException() {
        assertTrue(handler.retryRequest(new ConnectException(), 1, contextFor("GET")));
    }

    @Test
    public void doesNotRetryOnUnrelatedIOException() {
        assertFalse(handler.retryRequest(new IOException("boom"), 1, contextFor("GET")));
    }

    @Test
    public void retryIntervalAppliesJitterWithinBounds() throws ReflectiveOperationException {
        int executionCount = 3;
        long base = 100L * (1L << (executionCount - 1));

        setRandom(handler, fixedRandom(0.0));
        assertEquals((long) (base * 0.5), handler.getRetryInterval(executionCount));

        setRandom(handler, fixedRandom(1.0));
        assertEquals((long) (base * 1.5), handler.getRetryInterval(executionCount));

        setRandom(handler, fixedRandom(0.5));
        assertEquals(base, handler.getRetryInterval(executionCount));    }

    @Test
    public void retryIntervalStaysWithinPredictedJitterBoundsForCounts1To10() {
        for (int executionCount = 1; executionCount <= 10; executionCount++) {
            long base = 100L * (1L << (executionCount - 1));
            long min = (long) (base * 0.5);
            long max = (long) (base * 1.5);
            long actual = handler.getRetryInterval(executionCount);
            assertTrue(actual >= min && actual <= max,
                    "expected interval for executionCount=" + executionCount + " to be between " + min + " and "
                            + max + " but was " + actual);
        }
    }

    private HttpContext contextFor(String method) {
        HttpRequest request = mock(HttpRequest.class);
        RequestLine requestLine = mock(RequestLine.class);
        when(requestLine.getMethod()).thenReturn(method);
        when(request.getRequestLine()).thenReturn(requestLine);

        HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        return context;
    }

    private Random fixedRandom(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    private void setRandom(JitteredExponentialBackoffRetryHandler target, Random random)
            throws ReflectiveOperationException {
        Field field = JitteredExponentialBackoffRetryHandler.class.getDeclaredField("random");
        field.setAccessible(true);
        field.set(target, random);
    }
}
