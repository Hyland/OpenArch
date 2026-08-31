/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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

import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * Builds programmatic Resilience4j {@link CircuitBreaker} instances used to protect calls to the OpenSearch REST
 * API from {@link OpenSearchRestClient}.
 * <p>
 * This is deliberately a plain, non-Spring class using the {@code resilience4j-circuitbreaker} core library
 * directly (as opposed to the {@code @CircuitBreaker} annotation, e.g. as used by
 * {@code com.hyland.hxpr.cfs.evstore.client.EVStoreClientImpl}): {@code @CircuitBreaker} only takes effect on
 * beans invoked through a Spring AOP proxy, while {@link OpenSearchRestClient} is built directly with {@code new}
 * by {@link OpenSearchRestClientFactory} (itself created by the Nuxeo OSGi runtime) and by unit tests, so no
 * Spring proxy is ever involved.
 *
 * @since 12.0
 */
public final class OpenSearchCircuitBreakerFactory {

    private static final Logger log = LogManager.getLogger(OpenSearchCircuitBreakerFactory.class);

    public static final String CIRCUIT_BREAKER_NAME = "openSearchApi";

    public static final float DEFAULT_FAILURE_RATE_THRESHOLD = 50.0f;

    public static final int DEFAULT_SLIDING_WINDOW_SIZE = 10;

    public static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 5;

    public static final long DEFAULT_WAIT_DURATION_IN_OPEN_STATE_MS = 30_000;

    private static final int DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE = 3;

    private OpenSearchCircuitBreakerFactory() {
        // utility class
    }

    /**
     * Creates a {@link CircuitBreaker} with the given failure rate threshold, sliding window size, minimum number
     * of calls before the failure rate is evaluated, and wait duration once the breaker is open, logging on every
     * state transition.
     * <p>
     * Only exceptions considered transient by {@link OpenSearchRetryFactory#isRetryable} (an {@link
     * java.io.IOException} or a transient HTTP status) are recorded as failures: an exception carrying a
     * non-transient status, e.g. a 404 not found reported as an {@code OpenSearchException}, represents normal
     * traffic rather than a service availability issue, so counting it would open the circuit in response to
     * normal traffic rather than a real outage.
     */
    public static CircuitBreaker create(float failureRateThreshold, int slidingWindowSize, int minimumNumberOfCalls,
            long waitDurationInOpenStateMs) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                                                           .failureRateThreshold(failureRateThreshold)
                                                           .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                                           .slidingWindowSize(slidingWindowSize)
                                                           .minimumNumberOfCalls(minimumNumberOfCalls)
                                                           .waitDurationInOpenState(
                                                                   Duration.ofMillis(waitDurationInOpenStateMs))
                                                           .permittedNumberOfCallsInHalfOpenState(
                                                                   DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE)
                                                           .recordException(OpenSearchRetryFactory::isRetryable)
                                                           .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of(CIRCUIT_BREAKER_NAME, config);
        circuitBreaker.getEventPublisher().onStateTransition(event -> log.warn(
                "OpenSearch circuit breaker transitioning from {} to {}",
                event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
        return circuitBreaker;
    }

    /**
     * Creates a {@link CircuitBreaker} using the default failure rate threshold, sliding window size, minimum
     * number of calls and wait duration in open state.
     */
    public static CircuitBreaker createDefault() {
        return create(DEFAULT_FAILURE_RATE_THRESHOLD, DEFAULT_SLIDING_WINDOW_SIZE, DEFAULT_MINIMUM_NUMBER_OF_CALLS,
                DEFAULT_WAIT_DURATION_IN_OPEN_STATE_MS);
    }

}
