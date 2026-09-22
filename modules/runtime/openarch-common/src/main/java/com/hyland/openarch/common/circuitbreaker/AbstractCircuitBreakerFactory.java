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
package com.hyland.openarch.common.circuitbreaker;

import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.resilience.Resilience4jRegistries;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * Base class for building programmatic Resilience4j {@link CircuitBreaker} instances, sharing the sliding
 * window/failure-rate/wait-duration wiring and state-transition logging common to every call-protecting circuit
 * breaker in this codebase.
 * <p>
 * This is deliberately a plain, non-Spring hierarchy using the {@code resilience4j-circuitbreaker} core library
 * directly (as opposed to the {@code @CircuitBreaker} annotation, e.g. as used by
 * {@code com.hyland.hxpr.cfs.evstore.client.EVStoreClientImpl}): {@code @CircuitBreaker} only takes effect on
 * beans invoked through a Spring AOP proxy, while subclasses of this class are typically built directly with
 * {@code new} by the OpenArch OSGi runtime, so no Spring proxy is ever involved.
 *
 * @since 12.0
 */
public abstract class AbstractCircuitBreakerFactory {

    private static final int DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE = 3;

    private final Logger log = LogManager.getLogger(getClass());

    /**
     * Returns the name under which the {@link CircuitBreaker} is registered, e.g. for metrics.
     */
    protected abstract String getCircuitBreakerName();

    /**
     * Returns the human-readable label used in the state-transition log message, e.g. {@code "OpenSearch"}.
     */
    protected abstract String getServiceLabel();

    /**
     * Applies the exception-classification rule specific to the protected service, e.g. {@code recordException}
     * or {@code ignoreExceptions}, so that only failures representing a real service-availability issue (as
     * opposed to normal traffic, such as a validation error) count toward the failure rate.
     */
    protected abstract void customizeConfig(CircuitBreakerConfig.Builder builder);

    /**
     * Creates a {@link CircuitBreaker} with the given failure rate threshold, sliding window size, minimum number
     * of calls before the failure rate is evaluated, and wait duration once the breaker is open, logging on every
     * state transition. Listener attachment is gated on idempotent lookup semantics: the listener is only attached
     * if this invocation creates a new instance (i.e., the name wasn't already in the registry). Repeated calls with
     * the same name return the same instance without stacking duplicate listeners.
     */
    protected CircuitBreaker createCircuitBreaker(float failureRateThreshold, int slidingWindowSize,
            int minimumNumberOfCalls, long waitDurationInOpenStateMs) {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom()
                                                                    .failureRateThreshold(failureRateThreshold)
                                                                    .slidingWindowType(
                                                                            CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                                                    .slidingWindowSize(slidingWindowSize)
                                                                    .minimumNumberOfCalls(minimumNumberOfCalls)
                                                                    .waitDurationInOpenState(
                                                                            Duration.ofMillis(waitDurationInOpenStateMs))
                                                                    .permittedNumberOfCallsInHalfOpenState(
                                                                            DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE);
        customizeConfig(builder);

        // Use thread-safe createCircuitBreakerWithListener to atomically check-create-attach.
        // This prevents race conditions where two threads could both see isNew=true and attach duplicate listeners.
        return Resilience4jRegistries.createCircuitBreakerWithListener(getCircuitBreakerName(), builder.build(),
                circuitBreaker -> {
                    circuitBreaker.getEventPublisher().onStateTransition(event -> log.info(
                            "{} circuit breaker transitioning from {} to {}", getServiceLabel(),
                            event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
                });
    }

}
