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

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.protocol.HttpContext;

public class JitteredExponentialBackoffRetryHandler implements HttpRequestRetryHandler {

    /** Default max retry attempts, matching the pre-configurable hardcoded value. */
    public static final int DEFAULT_MAX_TRY = 3;

    /** Default base delay (ms) applied before doubling per attempt, matching the pre-configurable hardcoded value. */
    public static final long DEFAULT_BASE_DELAY_MS = 100L;

    /** Default jitter factor, matching the pre-configurable hardcoded value. */
    public static final double DEFAULT_JITTER_FACTOR = 0.5;

    private static final Set<Integer> RETRIABLE_STATUS_IDEMPOTENT = Set.of(408, 425, 429, 500, 502, 503, 504);

    private static final Set<Integer> RETRIABLE_STATUS_NON_IDEMPOTENT = Set.of(408, 425, 429);

    private final int maxTry;

    private final long baseDelayMs;

    private final double jitterFactor;

    /**
     * Builds a handler using the default max attempts ({@value #DEFAULT_MAX_TRY}), base delay
     * ({@value #DEFAULT_BASE_DELAY_MS}ms) and jitter factor ({@value #DEFAULT_JITTER_FACTOR}).
     */
    public JitteredExponentialBackoffRetryHandler() {
        this(DEFAULT_MAX_TRY, DEFAULT_BASE_DELAY_MS, DEFAULT_JITTER_FACTOR);
    }

    /**
     * Builds a handler with configurable retry behaviour, so callers can wire it up from application properties
     * (e.g. {@code hxpr.httpclient.retry.max-attempts}, {@code hxpr.httpclient.retry.base-delay-ms},
     * {@code hxpr.httpclient.retry.jitter-factor}).
     *
     * @param maxTry maximum number of attempts (including the initial one); must be strictly positive
     * @param baseDelayMs base delay in milliseconds applied before doubling per attempt; must be strictly positive
     * @param jitterFactor fraction of the base delay randomly added/subtracted; must be between 0 and 1 inclusive
     */
    public JitteredExponentialBackoffRetryHandler(int maxTry, long baseDelayMs, double jitterFactor) {
        if (maxTry <= 0) {
            throw new IllegalArgumentException("maxTry must be strictly positive (was " + maxTry + ")");
        }
        if (baseDelayMs <= 0) {
            throw new IllegalArgumentException("baseDelayMs must be strictly positive (was " + baseDelayMs + ")");
        }
        if (jitterFactor < 0 || jitterFactor > 1) {
            throw new IllegalArgumentException(
                    "jitterFactor must be between 0 and 1 inclusive (was " + jitterFactor + ")");
        }
        this.maxTry = maxTry;
        this.baseDelayMs = baseDelayMs;
        this.jitterFactor = jitterFactor;
    }

    @Override
    public boolean retryRequest(IOException ex, int executionCount, HttpContext ctx) {
        if (executionCount > maxTry) {
            return false;
        }
        if (ex.getCause() instanceof CallNotPermittedException) {
            return false;
        }
        boolean shouldRetry;
        if (ex instanceof HttpResponseException hre) {
            HttpRequest req = HttpClientContext.adapt(ctx).getRequest();
            Set<Integer> allowed = isIdempotentMethod(req) ? RETRIABLE_STATUS_IDEMPOTENT
                    : RETRIABLE_STATUS_NON_IDEMPOTENT;
            shouldRetry = allowed.contains(hre.getStatusCode());
        } else {
            shouldRetry = ex instanceof SocketTimeoutException || ex instanceof ConnectTimeoutException
                || ex instanceof ConnectException;
        }
        if (shouldRetry) {
            long retryInterval = getRetryInterval(executionCount);
            try {
                Thread.sleep(retryInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return shouldRetry;
    }

    private boolean isIdempotentMethod(HttpRequest req) {
        String method = req.getRequestLine().getMethod();
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)
                || "TRACE".equalsIgnoreCase(method);
    }

    public long getRetryInterval(int executionCount) {
        Random random = ThreadLocalRandom.current();
        int exp = Math.clamp(executionCount - 1L, 0, 30);
        long baseDelay = baseDelayMs * (1L << exp);
        double factor = 1.0 + (random.nextDouble() * 2 - 1) * jitterFactor;
        return (long) (baseDelay * factor);
    }

}
