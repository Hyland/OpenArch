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

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.IntPredicate;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

import com.hyland.openarch.runtime.api.Framework;

/**
 * Shared Apache HttpClient retry policy using exponential backoff with jitter for transient I/O failures and
 * HTTP responses.
 * <p>
 * Retries connection failures and connect/socket timeouts. For idempotent methods (GET, HEAD, PUT, DELETE,
 * OPTIONS and TRACE), retries HTTP 408, 425, 429, 500, 502, 503 and 504; other methods retry only 408, 425 and 429.
 * An explicit opt-in allows all methods to use the broader status set when duplicate effects are acceptable.
 * Circuit-breaker rejections and requests marked with {@link #RETRY_DISABLED_CONTEXT_ATTRIBUTE} are not retried.
 * <p>
 * Use {@link HttpClientHelper#custom()} to create a builder with both I/O and response retries, or
 * {@link #configure(HttpClientBuilder)} to configure an existing builder. Installing this object
 * with {@code setRetryHandler} alone enables only exception-based retries. Clients with their own status-retry
 * policy can use {@link #configure(HttpClientBuilder, boolean)} with {@code false} to avoid nested response retries.
 * Exhausted response retries return the final response unchanged, leaving status and body handling to the caller.
 * <p>
 * I/O and response retries have independent retry counts. Backoff starts at the configured base delay and doubles
 * for each retry, with a random adjustment of up to the configured jitter fraction. Instances may be shared
 * between concurrent requests.
 * <p>
 * The factory methods read {@code hxpr.httpclient.retry.*} from {@link Framework}. Configuration also
 * applies {@code hxpr.httpclient.timeout.*} (milliseconds, default 5000) and {@code hxpr.httpclient.pool.*}
 * (default 50 total and per route). Spring callers can supply their environment's property lookup through
 * {@link HttpClientHelper} before Framework starts. Otherwise built-in defaults are used before initialization.
 * Client-specific timeout and pool settings may be applied to the builder after {@code configure}.
 */
public class RepositoryRetryHandler implements HttpRequestRetryHandler {

    /**
     * {@link HttpContext} attribute set to {@link Boolean#TRUE} to disable all retries for a request.
     */
    public static final String RETRY_DISABLED_CONTEXT_ATTRIBUTE =
            "com.hyland.openarch.runtime.httpclient.RepositoryRetryHandler.retryDisabled";

    /**
     * Optional {@link IntPredicate} context attribute adding retryable statuses within the shared budget and backoff.
     * Called only for statuses outside the standard policy when retries are allowed.
     * Must not execute HTTP requests, as the failed response is still open.
     */
    public static final String RETRY_RESPONSE_PREDICATE_CONTEXT_ATTRIBUTE =
            "com.hyland.openarch.runtime.httpclient.RepositoryRetryHandler.retryResponsePredicate";

    /** Default max retry attempts, matching the pre-configurable hardcoded value. */
    public static final int DEFAULT_MAX_TRY = 3;

    /** Default base delay (ms) applied before doubling per attempt, matching the pre-configurable hardcoded value. */
    public static final long DEFAULT_BASE_DELAY_MS = 100L;

    /** Default jitter factor, matching the pre-configurable hardcoded value. */
    public static final double DEFAULT_JITTER_FACTOR = 0.5;

    /**
     * Immutable retryable response statuses for idempotent requests and clients opting in for all methods.
     */
    public static final Set<Integer> RETRIABLE_STATUS_IDEMPOTENT = Set.of(408, 425, 429, 500, 502, 503, 504);

    private static final Set<Integer> RETRIABLE_STATUS_NON_IDEMPOTENT = Set.of(408, 425, 429);

    private final int maxTry;

    private final long baseDelayMs;

    private final double jitterFactor;

    private final boolean retryNonIdempotentResponses;

    private final Function<String, String> properties;

    /**
     * Reads retry settings from Framework properties, falling back to the built-in defaults when absent.
     */
    public static RepositoryRetryHandler withDefaults() {
        return new RepositoryRetryHandler(false);
    }

    /**
     * Reads the shared retry settings, applying the idempotent status policy to all methods.
     * Enable only when duplicate effects of non-idempotent requests are acceptable, such as token issuance.
     */
    public static RepositoryRetryHandler withRetryNonIdempotentResponses() {
        return new RepositoryRetryHandler(true);
    }

    protected RepositoryRetryHandler(boolean retryNonIdempotentResponses) {
        this(retryNonIdempotentResponses, null);
    }

    protected RepositoryRetryHandler(boolean retryNonIdempotentResponses, Function<String, String> properties) {
        this(intProperty("hxpr.httpclient.retry.max-attempts", DEFAULT_MAX_TRY, properties),
                longProperty("hxpr.httpclient.retry.base-delay-ms", DEFAULT_BASE_DELAY_MS, properties),
                doubleProperty("hxpr.httpclient.retry.jitter-factor", DEFAULT_JITTER_FACTOR, properties),
                retryNonIdempotentResponses, properties);
    }

    /**
     * Builds a handler with configurable retry behaviour, so callers can wire it up from application properties
     * (e.g. {@code hxpr.httpclient.retry.max-attempts}, {@code hxpr.httpclient.retry.base-delay-ms},
     * {@code hxpr.httpclient.retry.jitter-factor}).
     *
     * @param maxTry maximum number of attempts (including the initial one); must be strictly positive
     * @param baseDelayMs base delay in milliseconds applied before doubling per attempt; must be strictly positive
     * @param jitterFactor fraction of the base delay randomly added/subtracted; must be between 0 and 1 inclusive
     * @param retryNonIdempotentResponses whether to apply the idempotent status policy to all methods;
     *            enable only when duplicate request effects are acceptable
     */
    protected RepositoryRetryHandler(int maxTry, long baseDelayMs, double jitterFactor,
            boolean retryNonIdempotentResponses) {
        this(maxTry, baseDelayMs, jitterFactor, retryNonIdempotentResponses, null);
    }

    private RepositoryRetryHandler(int maxTry, long baseDelayMs, double jitterFactor,
            boolean retryNonIdempotentResponses, Function<String, String> properties) {
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
        this.retryNonIdempotentResponses = retryNonIdempotentResponses;
        this.properties = properties;
    }

    /**
     * Installs both I/O and response retries. Exhausted response retries return the final response unchanged.
     * Apache maintains independent execution counts for I/O and response retries.
     */
    public HttpClientBuilder configure(HttpClientBuilder builder) {
        return configure(builder, true);
    }

    /**
     * Allows clients with their own status-retry policy to install only I/O retries.
     */
    public HttpClientBuilder configure(HttpClientBuilder builder, boolean retryResponses) {
        int maxTotal = intProperty("hxpr.httpclient.pool.max-total", 50, properties);
        int maxPerRoute = intProperty("hxpr.httpclient.pool.max-per-route", 50, properties);
        if (maxTotal <= 0 || maxPerRoute <= 0) {
            throw new IllegalArgumentException("hxpr.httpclient.pool.max-total and "
                    + "hxpr.httpclient.pool.max-per-route must be strictly positive");
        }
        builder.setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(intProperty("hxpr.httpclient.timeout.connect", 5000, properties))
                .setConnectionRequestTimeout(intProperty("hxpr.httpclient.timeout.connection-request", 5000, properties))
                .setSocketTimeout(intProperty("hxpr.httpclient.timeout.socket", 5000, properties))
                .build());
        builder.setMaxConnTotal(maxTotal);
        builder.setMaxConnPerRoute(maxPerRoute);
        builder.setRetryHandler(this);
        if (retryResponses) {
            builder.setServiceUnavailableRetryStrategy(new ServiceUnavailableRetryStrategy() {
                // Apache's interval callback has no context; isolate each execution's delay by thread.
                private final ThreadLocal<Long> retryInterval = new ThreadLocal<>();

                @Override
                public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
                    retryInterval.remove();
                    boolean retry = shouldRetry(
                            new HttpResponseException(response.getStatusLine().getStatusCode(),
                                    response.getStatusLine().getReasonPhrase()),
                            executionCount, context);
                    if (retry) {
                        retryInterval.set(RepositoryRetryHandler.this.getRetryInterval(executionCount));
                    }
                    return retry;
                }

                @Override
                public long getRetryInterval() {
                    // Apache closes the failed response before requesting the delay and sleeping.
                    Long interval = retryInterval.get();
                    retryInterval.remove();
                    return interval == null ? 0 : interval;
                }
            });
        }
        return builder;
    }

    private static int intProperty(String name, int defaultValue, Function<String, String> properties) {
        if (properties == null) {
            return Framework.isInitialized() ? Framework.getIntProperty(name, defaultValue) : defaultValue;
        }
        try {
            return Integer.parseInt(properties.apply(name));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long longProperty(String name, long defaultValue, Function<String, String> properties) {
        if (properties == null) {
            return Framework.isInitialized() ? Framework.getLongProperty(name, defaultValue) : defaultValue;
        }
        try {
            return Long.parseLong(properties.apply(name));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double doubleProperty(String name, double defaultValue, Function<String, String> properties) {
        if (properties == null && !Framework.isInitialized()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(properties == null ? Framework.getProperty(name) : properties.apply(name));
        } catch (NullPointerException | NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean retryRequest(IOException ex, int executionCount, HttpContext ctx) {
        if (!shouldRetry(ex, executionCount, ctx)) {
            return false;
        }
        try {
            Thread.sleep(getRetryInterval(executionCount));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean shouldRetry(IOException ex, int executionCount, HttpContext ctx) {
        HttpClientContext context = HttpClientContext.adapt(ctx);
        if (Boolean.TRUE.equals(context.getAttribute(RETRY_DISABLED_CONTEXT_ATTRIBUTE))) {
            return false;
        }
        if (executionCount > maxTry) {
            return false;
        }
        if (ex.getCause() instanceof CallNotPermittedException) {
            // The circuit breaker rejected the call; fail fast instead of retrying against an open circuit.
            return false;
        }
        boolean shouldRetry;
        if (ex instanceof HttpResponseException hre) {
            HttpRequest request = context.getRequest();
            Set<Integer> allowed = retryNonIdempotentResponses || isIdempotentMethod(request) ? RETRIABLE_STATUS_IDEMPOTENT
                    : RETRIABLE_STATUS_NON_IDEMPOTENT;
            shouldRetry = allowed.contains(hre.getStatusCode());
            if (!shouldRetry && context.getAttribute(RETRY_RESPONSE_PREDICATE_CONTEXT_ATTRIBUTE)
                    instanceof IntPredicate predicate) {
                shouldRetry = predicate.test(hre.getStatusCode());
            }
        } else {
            shouldRetry = ex instanceof SocketTimeoutException || ex instanceof ConnectTimeoutException
                || ex instanceof ConnectException;
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
