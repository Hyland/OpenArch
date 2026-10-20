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

package com.hyland.openarch.runtime.httpclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CyclicBarrier;
import java.util.function.IntPredicate;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.sun.net.httpserver.HttpServer;

import com.hyland.openarch.runtime.api.Framework;

public class TestRepositoryRetryHandler {

    protected final RepositoryRetryHandler handler = RepositoryRetryHandler.withDefaults();

    @Test
    public void additionalResponsePredicateRespectsBudgetOptOutAndBackoff() {
        RepositoryRetryHandler configured = spy(new RepositoryRetryHandler(1, 1, 0, true));
        IntPredicate predicate = mock(IntPredicate.class);
        when(predicate.test(401)).thenReturn(true);
        HttpClientContext context = (HttpClientContext) contextFor("POST");
        context.setAttribute(RepositoryRetryHandler.RETRY_RESPONSE_PREDICATE_CONTEXT_ATTRIBUTE, predicate);
        HttpResponseException unauthorized = new HttpResponseException(401, "Unauthorized");

        assertTrue(configured.retryRequest(unauthorized, 1, context));
        verify(configured).getRetryInterval(1);
        assertFalse(configured.retryRequest(unauthorized, 2, context));
        context.setAttribute(RepositoryRetryHandler.RETRY_DISABLED_CONTEXT_ATTRIBUTE, true);
        assertFalse(configured.retryRequest(unauthorized, 1, context));
        context.removeAttribute(RepositoryRetryHandler.RETRY_DISABLED_CONTEXT_ATTRIBUTE);
        assertTrue(configured.retryRequest(new HttpResponseException(503, "Unavailable"), 1, context));
        assertFalse(configured.retryRequest(new IOException("unrelated"), 1, context));
        verify(predicate).test(401);
        verify(predicate, never()).test(503);
    }

    @Test
    public void factoriesSelectNonIdempotentResponseRetryPolicy() {
        HttpResponseException error = new HttpResponseException(503, "Service Unavailable");
        assertFalse(RepositoryRetryHandler.withDefaults().retryRequest(error, 1, contextFor("POST")));
        assertTrue(RepositoryRetryHandler.withRetryNonIdempotentResponses()
                .retryRequest(error, 1, contextFor("POST")));
    }

    @Test
    public void nonIdempotentResponseRetriesAreExplicitAndRespectOptOut() {
        RepositoryRetryHandler configured = new RepositoryRetryHandler(1, 1, 0, true);
        for (int status : new int[] { 408, 425, 429, 500, 502, 503, 504 }) {
            HttpResponseException error = new HttpResponseException(status, "error");
            HttpClientContext context = (HttpClientContext) contextFor("POST");
            assertTrue(configured.retryRequest(error, 1, context));
            assertFalse(configured.retryRequest(error, 2, context));
            context.setAttribute(RepositoryRetryHandler.RETRY_DISABLED_CONTEXT_ATTRIBUTE, true);
            assertFalse(configured.retryRequest(error, 1, context));
        }
        for (int status : new int[] { 400, 401, 403, 422 }) {
            assertFalse(configured.retryRequest(new HttpResponseException(status, "error"), 1, contextFor("POST")));
        }
    }

    @Test
    public void helperConfiguresDefaultsAndOptionalResponseRetries() {
        for (boolean retryResponses : new boolean[] { true, false }) {
            HttpClientBuilder builder = mock(HttpClientBuilder.class);
            try (MockedStatic<HttpClients> clients = mockStatic(HttpClients.class)) {
                clients.when(HttpClients::custom).thenReturn(builder);
                assertEquals(builder, retryResponses ? HttpClientHelper.custom() : HttpClientHelper.customWithoutRetryResponses());
                verify(builder).setRetryHandler(any(RepositoryRetryHandler.class));
                verify(builder).setDefaultRequestConfig(any(RequestConfig.class));
                verify(builder).setMaxConnTotal(50);
                verify(builder).setMaxConnPerRoute(50);
                verify(builder, retryResponses ? org.mockito.Mockito.times(1) : never())
                        .setServiceUnavailableRetryStrategy(any(ServiceUnavailableRetryStrategy.class));
            }
        }
    }

    @Test
    public void readsRetryTimeoutAndPoolPropertiesFromFramework() {
        Map<String, String> properties = Map.of(
                "hxpr.httpclient.retry.max-attempts", "1",
                "hxpr.httpclient.retry.base-delay-ms", "7",
                "hxpr.httpclient.retry.jitter-factor", "0",
                "hxpr.httpclient.timeout.connect", "1000",
                "hxpr.httpclient.timeout.connection-request", "2000",
                "hxpr.httpclient.timeout.socket", "3000",
                "hxpr.httpclient.pool.max-total", "80",
                "hxpr.httpclient.pool.max-per-route", "40");
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(Framework::isInitialized).thenReturn(true);
            framework.when(() -> Framework.getProperty(anyString()))
                    .thenAnswer(invocation -> properties.get(invocation.getArgument(0)));
            framework.when(() -> Framework.getIntProperty(anyString(), anyInt()))
                    .thenAnswer(invocation -> Integer.parseInt(properties.get(invocation.getArgument(0))));
            framework.when(() -> Framework.getLongProperty(anyString(), anyLong()))
                    .thenAnswer(invocation -> Long.parseLong(properties.get(invocation.getArgument(0))));
            RepositoryRetryHandler configured = RepositoryRetryHandler.withDefaults();
            assertEquals(7, configured.getRetryInterval(1));
            assertFalse(configured.retryRequest(new ConnectException(), 2, contextFor("GET")));

            HttpClientBuilder builder = mock(HttpClientBuilder.class);
            configured.configure(builder);
            ArgumentCaptor<RequestConfig> config = ArgumentCaptor.forClass(RequestConfig.class);
            verify(builder).setDefaultRequestConfig(config.capture());
            assertEquals(1000, config.getValue().getConnectTimeout());
            assertEquals(2000, config.getValue().getConnectionRequestTimeout());
            assertEquals(3000, config.getValue().getSocketTimeout());
            verify(builder).setMaxConnTotal(80);
            verify(builder).setMaxConnPerRoute(40);
        }
    }

    @Test
    public void rejectsNonPositivePoolProperties() {
        for (String property : new String[] { "hxpr.httpclient.pool.max-total", "hxpr.httpclient.pool.max-per-route" }) {
            for (String value : new String[] { "0", "-1" }) {
                try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
                    framework.when(Framework::isInitialized).thenReturn(true);
                    framework.when(() -> Framework.getIntProperty(anyString(), anyInt()))
                            .thenAnswer(invocation -> property.equals(invocation.getArgument(0))
                                    ? Integer.parseInt(value) : invocation.getArgument(1));
                    framework.when(() -> Framework.getLongProperty(anyString(), anyLong()))
                            .thenAnswer(invocation -> invocation.getArgument(1));
                    assertThrows(IllegalArgumentException.class,
                            () -> RepositoryRetryHandler.withDefaults().configure(HttpClients.custom()));
                }
            }
        }
    }

    @Test
    public void responseRetriesDeferBackoffToApache() {
        HttpClientBuilder builder = mock(HttpClientBuilder.class);
        RepositoryRetryHandler configured = spy(new RepositoryRetryHandler(3, 1, 0, false));
        configured.configure(builder);
        verify(builder).setRetryHandler(configured);
        verify(builder).setMaxConnTotal(50);
        verify(builder).setMaxConnPerRoute(50);
        ArgumentCaptor<RequestConfig> config = ArgumentCaptor.forClass(RequestConfig.class);
        verify(builder).setDefaultRequestConfig(config.capture());
        assertEquals(5000, config.getValue().getConnectTimeout());
        assertEquals(5000, config.getValue().getConnectionRequestTimeout());
        assertEquals(5000, config.getValue().getSocketTimeout());
        ArgumentCaptor<ServiceUnavailableRetryStrategy> strategy =
                ArgumentCaptor.forClass(ServiceUnavailableRetryStrategy.class);
        verify(builder).setServiceUnavailableRetryStrategy(strategy.capture());
        assertTrue(strategy.getValue().retryRequest(
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 503, "Unavailable"), 2, contextFor("GET")));
        verify(configured).getRetryInterval(2);
        assertEquals(2, strategy.getValue().getRetryInterval());
        assertEquals(0, strategy.getValue().getRetryInterval());
    }

    @Test
    public void responseRetryIntervalsAreIsolatedBetweenThreads() throws Exception {
        HttpClientBuilder builder = mock(HttpClientBuilder.class);
        new RepositoryRetryHandler(3, 7, 0, false).configure(builder);
        ArgumentCaptor<ServiceUnavailableRetryStrategy> captor =
                ArgumentCaptor.forClass(ServiceUnavailableRetryStrategy.class);
        verify(builder).setServiceUnavailableRetryStrategy(captor.capture());
        ServiceUnavailableRetryStrategy strategy = captor.getValue();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                assertTrue(strategy.retryRequest(new BasicHttpResponse(HttpVersion.HTTP_1_1, 503, "Unavailable"),
                        1, contextFor("GET")));
                barrier.await(5, TimeUnit.SECONDS);
                return strategy.getRetryInterval();
            });
            var second = executor.submit(() -> {
                assertTrue(strategy.retryRequest(new BasicHttpResponse(HttpVersion.HTTP_1_1, 503, "Unavailable"),
                        2, contextFor("GET")));
                barrier.await(5, TimeUnit.SECONDS);
                return strategy.getRetryInterval();
            });
            assertEquals(7L, first.get(5, TimeUnit.SECONDS));
            assertEquals(14L, second.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void responseBackoffReleasesConnectionBeforeWaiting() throws Exception {
        CountDownLatch retryScheduled = new CountDownLatch(1);
        CountDownLatch releaseErrorBody = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/retry", exchange -> {
            try (exchange) {
                if (attempts.incrementAndGet() == 1) {
                    exchange.sendResponseHeaders(503, 1024);
                    exchange.getResponseBody().write(1);
                    exchange.getResponseBody().flush();
                    try {
                        releaseErrorBody.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    exchange.sendResponseHeaders(200, -1);
                }
            }
        });
        server.createContext("/other", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, -1);
            }
        });
        server.start();
        RepositoryRetryHandler configured = new RepositoryRetryHandler(1, 2000, 0, false) {
            @Override
            public long getRetryInterval(int executionCount) {
                retryScheduled.countDown();
                return super.getRetryInterval(executionCount);
            }
        };
        try (CloseableHttpClient client = configured.configure(HttpClients.custom())
                .setMaxConnTotal(1).setMaxConnPerRoute(1)
                .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(500)
                        .setSocketTimeout(5000).build()).build();
                var executor = Executors.newSingleThreadExecutor()) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var retrying = executor.submit(() -> {
                try (CloseableHttpResponse response = client.execute(new HttpGet(base + "/retry"))) {
                    return response.getStatusLine().getStatusCode();
                }
            });
            assertTrue(retryScheduled.await(5, TimeUnit.SECONDS));
            try (CloseableHttpResponse response = client.execute(new HttpGet(base + "/other"))) {
                assertEquals(200, response.getStatusLine().getStatusCode());
                assertEquals(1, attempts.get(), "Other requests must run while the first request is backing off");
            } finally {
                releaseErrorBody.countDown();
            }
            assertEquals(200, retrying.get(10, TimeUnit.SECONDS));
        } finally {
            releaseErrorBody.countDown();
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

    @Test
    public void retriesActualResponsesAndPreservesFinalResponse() throws IOException {
        for (int status : new int[] { 408, 425, 429, 500, 502, 503, 504 }) {
            assertResponseAttempts("GET", status, false, true, false, 2, status);
            assertResponseAttempts("GET", status, false, true, true, 2, 200);
        }
        for (int status : new int[] { 408, 425, 429 }) {
            assertResponseAttempts("POST", status, false, true, false, 2, status);
        }
        for (int status : new int[] { 400, 401, 422, 500, 502, 503, 504 }) {
            assertResponseAttempts("POST", status, false, true, false, 1, status);
        }
        assertResponseAttempts("GET", 400, false, true, false, 1, 400);
        assertResponseAttempts("GET", 503, true, true, false, 1, 503);
        assertResponseAttempts("GET", 503, false, false, false, 1, 503);
    }

    private void assertResponseAttempts(String method, int status, boolean disabled, boolean retryResponses,
            boolean recover, int expectedAttempts, int expectedStatus) throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                int count = attempts.incrementAndGet();
                byte[] body = "response body".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(recover && count > 1 ? 200 : status, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try (CloseableHttpClient client = new RepositoryRetryHandler(1, 1, 0, false)
                .configure(HttpClients.custom(), retryResponses).setMaxConnPerRoute(1).build()) {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            HttpRequestBase request;
            if ("POST".equals(method)) {
                HttpPost post = new HttpPost(uri);
                post.setEntity(new StringEntity("request body"));
                request = post;
            } else {
                request = new HttpGet(uri);
            }
            HttpClientContext context = HttpClientContext.create();
            context.setAttribute(RepositoryRetryHandler.RETRY_DISABLED_CONTEXT_ATTRIBUTE, disabled);
            try (CloseableHttpResponse response = client.execute(request, context)) {
                assertEquals(expectedStatus, response.getStatusLine().getStatusCode());
                assertEquals("response body", EntityUtils.toString(response.getEntity()));
            }
            assertEquals(expectedAttempts, attempts.get(), method + " " + status);
        } finally {
            server.stop(0);
        }
    }

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
    public void retryIntervalAppliesJitterWithinBounds() {
        int executionCount = 3;
        long base = 100L * (1L << (executionCount - 1));

        ThreadLocalRandom randomAtMin = fixedRandom(0.0);
        ThreadLocalRandom randomAtMax = fixedRandom(1.0);
        ThreadLocalRandom randomAtMid = fixedRandom(0.5);

        try (MockedStatic<ThreadLocalRandom> threadLocalRandom = mockStatic(ThreadLocalRandom.class)) {
            threadLocalRandom.when(ThreadLocalRandom::current).thenReturn(randomAtMin);
            assertEquals((long) (base * 0.5), handler.getRetryInterval(executionCount));

            threadLocalRandom.when(ThreadLocalRandom::current).thenReturn(randomAtMax);
            assertEquals((long) (base * 1.5), handler.getRetryInterval(executionCount));

            threadLocalRandom.when(ThreadLocalRandom::current).thenReturn(randomAtMid);
            assertEquals(base, handler.getRetryInterval(executionCount));
        }
    }

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

    @Test
    public void appliesConfiguredMaxTryBaseDelayAndJitterFactor() {
        RepositoryRetryHandler configured = new RepositoryRetryHandler(1, 200L, 0.0, false);
        HttpResponseException ex = new HttpResponseException(500, "Internal Server Error");

        // maxTry=1: an initial attempt (executionCount=1) is allowed, a first retry (executionCount=2) is not
        assertTrue(configured.retryRequest(ex, 1, contextFor("GET")));
        assertFalse(configured.retryRequest(ex, 2, contextFor("GET")));

        // jitterFactor=0.0 means no randomisation: the interval is exactly the base delay
        assertEquals(200L, configured.getRetryInterval(1));
    }

    @Test
    public void rejectsNonPositiveMaxTry() {
        assertThrows(IllegalArgumentException.class, () -> new RepositoryRetryHandler(0, 100L, 0.5, false));
    }

    @Test
    public void rejectsNonPositiveBaseDelay() {
        assertThrows(IllegalArgumentException.class, () -> new RepositoryRetryHandler(3, 0L, 0.5, false));
    }

    @Test
    public void rejectsJitterFactorOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new RepositoryRetryHandler(3, 100L, -0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryRetryHandler(3, 100L, 1.1, false));
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

    private ThreadLocalRandom fixedRandom(double value) {
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        when(random.nextDouble()).thenReturn(value);
        return random;
    }
}
