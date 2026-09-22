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
package com.hyland.openarch.common.resilience;

import java.util.Objects;
import java.util.function.Consumer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/**
 * Shared resilience4j registries used both by Spring-managed annotations and by programmatic retry / circuit-breaker
 * factories, so all resilience constructs can be observed through the same metrics binding.
 * <p>
 * <b>Idempotent Lookup Semantics:</b> {@code create*} methods use idempotent lookup: repeated calls with the same
 * name return the exact same live instance, allowing callers to safely cache them in final fields. This ensures that
 * metrics published by the binder always track the same instance that consumers actually use.
 * <p>
 * If an existing instance must be replaced with a new config, use the explicit {@code remove*} methods and then call
 * {@code create*} again. This two-step replacement is intentional and documented to catch config-change scenarios that
 * need explicit handling (rather than implicit replacement that silently orphans cached instances).
 */
public final class Resilience4jRegistries {

    private static final RetryRegistry RETRY_REGISTRY = RetryRegistry.ofDefaults();

    private static final CircuitBreakerRegistry CIRCUIT_BREAKER_REGISTRY = CircuitBreakerRegistry.ofDefaults();

    private static final TimeLimiterRegistry TIME_LIMITER_REGISTRY = TimeLimiterRegistry.ofDefaults();

    protected static final String NAME_MUST_NOT_BE_NULL = "name must not be null";

    protected static final String CONFIG_MUST_NOT_BE_NULL = "config must not be null";

    private Resilience4jRegistries() {
        // utility class
    }

    public static RetryRegistry retryRegistry() {
        return RETRY_REGISTRY;
    }

    public static CircuitBreakerRegistry circuitBreakerRegistry() {
        return CIRCUIT_BREAKER_REGISTRY;
    }

    public static TimeLimiterRegistry timeLimiterRegistry() {
        return TIME_LIMITER_REGISTRY;
    }

    public static synchronized Retry createRetry(String name, RetryConfig config) {
        Objects.requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        Objects.requireNonNull(config, CONFIG_MUST_NOT_BE_NULL);
        return RETRY_REGISTRY.retry(name, config);
    }

    /**
     * Creates or retrieves a {@link Retry} instance from the shared registry. If the instance is newly created
     * (didn't exist in the registry), the provided listener attacher is invoked to set up event listeners.
     * This ensures listeners are attached exactly once, preventing duplicate listener stacking.
     * <p>
     * Thread-safe: the check-then-create-then-attach sequence is atomic.
     *
     * @param name retry name
     * @param config retry configuration
     * @param listenerAttacher callback to attach listeners, invoked only if the instance is newly created
     * @return the Retry instance (either newly created or existing)
     */
    public static synchronized Retry createRetryWithListener(String name, RetryConfig config,
            Consumer<Retry> listenerAttacher) {
        Objects.requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        Objects.requireNonNull(config, CONFIG_MUST_NOT_BE_NULL);
        Objects.requireNonNull(listenerAttacher, "listenerAttacher must not be null");

        boolean isNew = RETRY_REGISTRY.find(name).isEmpty();
        Retry retry = RETRY_REGISTRY.retry(name, config);

        if (isNew) {
            listenerAttacher.accept(retry);
        }
        return retry;
    }

    public static synchronized CircuitBreaker createCircuitBreaker(String name, CircuitBreakerConfig config) {
        Objects.requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        Objects.requireNonNull(config, CONFIG_MUST_NOT_BE_NULL);
        return CIRCUIT_BREAKER_REGISTRY.circuitBreaker(name, config);
    }

    /**
     * Creates or retrieves a {@link CircuitBreaker} instance from the shared registry. If the instance is newly created
     * (didn't exist in the registry), the provided listener attacher is invoked to set up event listeners.
     * This ensures listeners are attached exactly once, preventing duplicate listener stacking.
     * <p>
     * Thread-safe: the check-then-create-then-attach sequence is atomic.
     *
     * @param name circuit breaker name
     * @param config circuit breaker configuration
     * @param listenerAttacher callback to attach listeners, invoked only if the instance is newly created
     * @return the CircuitBreaker instance (either newly created or existing)
     */
    public static synchronized CircuitBreaker createCircuitBreakerWithListener(String name, CircuitBreakerConfig config,
            Consumer<CircuitBreaker> listenerAttacher) {
        Objects.requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        Objects.requireNonNull(config, CONFIG_MUST_NOT_BE_NULL);
        Objects.requireNonNull(listenerAttacher, "listenerAttacher must not be null");

        boolean isNew = CIRCUIT_BREAKER_REGISTRY.find(name).isEmpty();
        CircuitBreaker circuitBreaker = CIRCUIT_BREAKER_REGISTRY.circuitBreaker(name, config);

        if (isNew) {
            listenerAttacher.accept(circuitBreaker);
        }
        return circuitBreaker;
    }

    /**
     * Remove a Retry from the shared registry. Used to clean up test state and to implement explicit config
     * replacement (see class javadoc).
     */
    public static synchronized void removeRetry(String name) {
        Objects.requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        RETRY_REGISTRY.remove(name);
    }

    /**
     * Remove a CircuitBreaker from the shared registry. Used to clean up test state and to implement explicit config
     * replacement (see class javadoc).
     */
    public static synchronized void removeCircuitBreaker(String name) {
        Objects.requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        CIRCUIT_BREAKER_REGISTRY.remove(name);
    }
}
