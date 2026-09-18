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
package com.hyland.openarch.opensearch.client;

import com.hyland.openarch.common.circuitbreaker.AbstractCircuitBreakerFactory;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * Builds programmatic Resilience4j {@link CircuitBreaker} instances used to protect calls to the OpenSearch REST
 * API from {@link OpenSearchRestClient}.
 * <p>
 * {@link OpenSearchRestClient} is built directly with {@code new} by {@link OpenSearchRestClientFactory} (itself
 * created by the OpenArch OSGi runtime) and by unit tests, so no Spring AOP proxy is ever involved; see
 * {@link AbstractCircuitBreakerFactory} for why this rules out the {@code @CircuitBreaker} annotation.
 */
public final class OpenSearchCircuitBreakerFactory extends AbstractCircuitBreakerFactory {

    public static final String CIRCUIT_BREAKER_NAME = "openSearchApi";

    public static final float DEFAULT_FAILURE_RATE_THRESHOLD = 50.0f;

    public static final int DEFAULT_SLIDING_WINDOW_SIZE = 10;

    public static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 5;

    public static final long DEFAULT_WAIT_DURATION_IN_OPEN_STATE_MS = 30_000;

    private static final OpenSearchCircuitBreakerFactory INSTANCE = new OpenSearchCircuitBreakerFactory();

    private OpenSearchCircuitBreakerFactory() {
        // singleton
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
        return INSTANCE.createCircuitBreaker(failureRateThreshold, slidingWindowSize, minimumNumberOfCalls,
                waitDurationInOpenStateMs);
    }

    /**
     * Creates a {@link CircuitBreaker} using the default failure rate threshold, sliding window size, minimum
     * number of calls and wait duration in open state.
     */
    public static CircuitBreaker createDefault() {
        return create(DEFAULT_FAILURE_RATE_THRESHOLD, DEFAULT_SLIDING_WINDOW_SIZE, DEFAULT_MINIMUM_NUMBER_OF_CALLS,
                DEFAULT_WAIT_DURATION_IN_OPEN_STATE_MS);
    }

    @Override
    protected String getCircuitBreakerName() {
        return CIRCUIT_BREAKER_NAME;
    }

    @Override
    protected String getServiceLabel() {
        return "OpenSearch";
    }

    @Override
    protected void customizeConfig(CircuitBreakerConfig.Builder builder) {
        builder.recordException(OpenSearchRetryFactory::isRetryable);
    }

}
