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

import java.io.IOException;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.resilience.Resilience4jRegistries;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.transport.httpclient5.ResponseException;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Builds programmatic Resilience4j {@link Retry} instances used to retry calls to the OpenSearch REST API from
 * {@link OpenSearchRestClient}.
 * <p>
 * This is deliberately a plain, non-Spring class using the {@code resilience4j-retry} core library directly
 * (as opposed to the {@code @Retry} annotation, e.g. as used by
 * {@code com.hyland.hxpr.runtime.pubsub.idp.APITokenExchangeClient}): {@code @Retry} only takes effect on
 * beans invoked through a Spring AOP proxy, while {@link OpenSearchRestClient} is built directly with
 * {@code new} by {@link OpenSearchRestClientFactory} (itself created by the Nuxeo OSGi runtime) and by unit
 * tests, so no Spring proxy is ever involved.
 *
 * @since 12.0
 */
public final class OpenSearchRetryFactory {

    private static final Logger log = LogManager.getLogger(OpenSearchRetryFactory.class);

    public static final String RETRY_NAME = "openSearchApi";

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    public static final long DEFAULT_WAIT_DURATION_MS = 200;

    private static final double BACKOFF_MULTIPLIER = 2.0;

    private static final double JITTER_FACTOR = 0.5;

    // HTTP statuses considered transient and therefore worth retrying, mirroring the idempotent set used by
    // org.nuxeo.common.httpclient.JitteredExponentialBackoffRetryHandler
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(408, 425, 429, 500, 502, 503, 504);

    private OpenSearchRetryFactory() {
        // utility class
    }

    /**
     * Creates a {@link Retry} with the given attempt count and initial wait duration (exponential backoff with
     * jitter is applied between attempts), logging an error once all attempts have been exhausted. Listener attachment
     * is gated on idempotent lookup semantics: the listener is only attached if this invocation creates a new instance
     * (i.e., the name wasn't already in the registry). Repeated calls with the same name return the same instance
     * without stacking duplicate listeners.
     */
    public static Retry create(int maxAttempts, long waitDurationMs) {
        RetryConfig config = RetryConfig.custom()
                                        .maxAttempts(maxAttempts)
                                        .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                                                waitDurationMs, BACKOFF_MULTIPLIER, JITTER_FACTOR))
                                        .retryOnException(OpenSearchRetryFactory::isRetryable)
                                        .build();

        // Use thread-safe createRetryWithListener to atomically check-create-attach.
        // This prevents race conditions where two threads could both see isNew=true and attach duplicate listeners.
        return Resilience4jRegistries.createRetryWithListener(RETRY_NAME, config, retry -> {
            // Callback invoked once the number of retries has been reached: RetryOnErrorEvent is only published
            // when the current attempt count reaches maxAttempts (i.e. all retries are exhausted).
            // Note: the last throwable is deliberately NOT passed as a trailing log parameter, since Log4j2 would
            // then attempt to render its stack trace; this can throw an NPE for exceptions whose native
            // stack trace was never filled in (e.g. mocked exceptions in unit tests).
            retry.getEventPublisher().onError(event -> {
                Throwable last = event.getLastThrowable();
                log.error("OpenSearch API call failed after exhausting all {} attempt(s), giving up: {}: {}",
                        event.getNumberOfRetryAttempts() + 1, last.getClass().getName(), last.getMessage());
            });
        });
    }

    /**
     * Creates a {@link Retry} using the default attempt count and wait duration.
     */
    public static Retry createDefault() {
        return create(DEFAULT_MAX_ATTEMPTS, DEFAULT_WAIT_DURATION_MS);
    }

    /**
     * @return {@code true} if the given exception is considered transient and worth retrying: an
     *         {@link OpenSearchException} / {@link ResponseException} carrying a transient HTTP status, or any
     *         other {@link IOException} (network/connection failures) that carries no HTTP status at all.
     *         <p>
     *         {@link ResponseException} itself extends {@link IOException}, so the HTTP status must be checked
     *         first: otherwise every {@link ResponseException} - including non-transient ones like a 404 or a
     *         409 version conflict - would be misclassified as a transient network failure.
     */
    static boolean isRetryable(Throwable t) {
        int status = extractStatus(t);
        if (status != -1) {
            return RETRYABLE_STATUS.contains(status);
        }
        return t instanceof IOException;
    }

    private static int extractStatus(Throwable t) {
        if (t instanceof OpenSearchException ose) {
            return ose.status();
        }
        if (t instanceof ResponseException re) {
            return re.status();
        }
        return -1;
    }

}
