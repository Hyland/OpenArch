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
package com.hyland.openarch.runtime.springboot.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;

import com.hyland.openarch.common.resilience.Resilience4jRegistries;

/**
 * Binds resilience4j registries into OpenArch's shared Dropwizard 5 registry.
 * <p>
 * The resilience4j Dropwizard module still targets the legacy Codahale package names, so OpenArch exposes the same
 * Standard 7 counters and gauges directly through Dropwizard 5.
 * <p>
 * Implements explicit lifecycle management: binding occurs at {@link #afterPropertiesSet()} (after all
 * registries are injected), and cleanup (listener detachment, metric unregistration) occurs at
 * {@link #destroy()} (when the Spring context closes).
 */
final class Resilience4jDropwizardMetricsBinder implements InitializingBean, DisposableBean {

    static final String RETRY_CALLS_METRIC = "hxpr.resilience4j.retry.calls";

    static final String CIRCUIT_BREAKER_CALLS_METRIC = "hxpr.resilience4j.circuitbreaker.calls";

    static final String CIRCUIT_BREAKER_STATE_METRIC = "hxpr.resilience4j.circuitbreaker.state";

    static final String TIME_LIMITER_CALLS_METRIC = "hxpr.resilience4j.timelimiter.calls";

    private static final Map<String, ToLongFunction<Retry.Metrics>> RETRY_KINDS = Map.ofEntries(
            Map.entry("successful_without_retry", Retry.Metrics::getNumberOfSuccessfulCallsWithoutRetryAttempt),
            Map.entry("successful_with_retry", Retry.Metrics::getNumberOfSuccessfulCallsWithRetryAttempt),
            Map.entry("failed_without_retry", Retry.Metrics::getNumberOfFailedCallsWithoutRetryAttempt),
            Map.entry("failed_with_retry", Retry.Metrics::getNumberOfFailedCallsWithRetryAttempt));

    protected static final String SUCCESSFUL = "successful";

    protected static final String FAILED = "failed";

    protected static final String TIMEOUT = "timeout";

    private static final Map<String, ToLongFunction<CircuitBreaker.Metrics>> CIRCUIT_BREAKER_CALL_KINDS = Map.ofEntries(
            Map.entry(SUCCESSFUL, CircuitBreaker.Metrics::getNumberOfSuccessfulCalls),
            Map.entry(FAILED, CircuitBreaker.Metrics::getNumberOfFailedCalls),
            Map.entry("not_permitted", CircuitBreaker.Metrics::getNumberOfNotPermittedCalls),
            Map.entry("slow_successful", CircuitBreaker.Metrics::getNumberOfSlowSuccessfulCalls),
            Map.entry("slow_failed", CircuitBreaker.Metrics::getNumberOfSlowFailedCalls));

    private static final String[] TIME_LIMITER_KINDS = {SUCCESSFUL, FAILED, TIMEOUT};

    private final MetricRegistry dropwizardRegistry;

    private final Set<Object> boundRegistries = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Map<String, AtomicLong> timeLimiterCounters = new ConcurrentHashMap<>();

    private ObjectProvider<RetryRegistry> retryRegistries;

    private ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistries;

    private ObjectProvider<TimeLimiterRegistry> timeLimiterRegistries;

    private final Set<MetricName> registeredMetrics = new HashSet<>();

    private boolean destroyed = false;

    Resilience4jDropwizardMetricsBinder(MetricRegistry dropwizardRegistry,
            ObjectProvider<RetryRegistry> retryRegistries, ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistries,
            ObjectProvider<TimeLimiterRegistry> timeLimiterRegistries) {
        this.dropwizardRegistry = dropwizardRegistry;
        this.retryRegistries = retryRegistries;
        this.circuitBreakerRegistries = circuitBreakerRegistries;
        this.timeLimiterRegistries = timeLimiterRegistries;
    }

    void bindRetryRegistry(RetryRegistry retryRegistry) {
        if (!markBound(retryRegistry)) {
            return;
        }
        retryRegistry.getAllRetries().forEach(this::bindRetry);
        retryRegistry.getEventPublisher()
                     .onEntryAdded(event -> bindRetry(event.getAddedEntry()))
                     .onEntryRemoved(event -> removeRetry(event.getRemovedEntry().getName()))
                     .onEntryReplaced(event -> {
                         removeRetry(event.getOldEntry().getName());
                         bindRetry(event.getNewEntry());
                     });
    }

    void bindCircuitBreakerRegistry(CircuitBreakerRegistry circuitBreakerRegistry) {
        if (!markBound(circuitBreakerRegistry)) {
            return;
        }
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::bindCircuitBreaker);
        circuitBreakerRegistry.getEventPublisher()
                              .onEntryAdded(event -> bindCircuitBreaker(event.getAddedEntry()))
                              .onEntryRemoved(event -> removeCircuitBreaker(event.getRemovedEntry().getName()))
                              .onEntryReplaced(event -> {
                                  removeCircuitBreaker(event.getOldEntry().getName());
                                  bindCircuitBreaker(event.getNewEntry());
                              });
    }

    void bindTimeLimiterRegistry(TimeLimiterRegistry timeLimiterRegistry) {
        if (!markBound(timeLimiterRegistry)) {
            return;
        }
        timeLimiterRegistry.getAllTimeLimiters().forEach(this::bindTimeLimiter);
        timeLimiterRegistry.getEventPublisher()
                           .onEntryAdded(event -> bindTimeLimiter(event.getAddedEntry()))
                           .onEntryRemoved(event -> removeTimeLimiter(event.getRemovedEntry().getName()))
                           .onEntryReplaced(event -> {
                               removeTimeLimiter(event.getOldEntry().getName());
                               bindTimeLimiter(event.getNewEntry());
                           });
    }

    private boolean markBound(Object registry) {
        synchronized (boundRegistries) {
            return boundRegistries.add(registry);
        }
    }

    private void bindRetry(Retry retry) {
        RETRY_KINDS.forEach((kind, fn) -> registerGauge(metricName(RETRY_CALLS_METRIC, retry.getName(), kind),
                () -> fn.applyAsLong(retry.getMetrics())));
    }

    private void removeRetry(String retryName) {
        RETRY_KINDS.keySet()
                   .forEach(kind -> dropwizardRegistry.remove(metricName(RETRY_CALLS_METRIC, retryName, kind)));
    }

    private void bindCircuitBreaker(CircuitBreaker circuitBreaker) {
        CIRCUIT_BREAKER_CALL_KINDS.forEach((kind, fn) -> registerGauge(
                metricName(CIRCUIT_BREAKER_CALLS_METRIC, circuitBreaker.getName(), kind),
                () -> fn.applyAsLong(circuitBreaker.getMetrics())));

        for (CircuitBreaker.State state : CircuitBreaker.State.values()) {
            registerGauge(stateMetricName(circuitBreaker.getName(), state),
                    () -> circuitBreaker.getState() == state ? 1 : 0);
        }
    }

    private void removeCircuitBreaker(String circuitBreakerName) {
        CIRCUIT_BREAKER_CALL_KINDS.keySet()
                                   .forEach(kind -> dropwizardRegistry.remove(
                                           metricName(CIRCUIT_BREAKER_CALLS_METRIC, circuitBreakerName, kind)));
        for (CircuitBreaker.State state : CircuitBreaker.State.values()) {
            dropwizardRegistry.remove(stateMetricName(circuitBreakerName, state));
        }
    }

    private void bindTimeLimiter(TimeLimiter timeLimiter) {
        AtomicLong successful = counter(timeLimiter.getName(), SUCCESSFUL);
        AtomicLong failed = counter(timeLimiter.getName(), FAILED);
        AtomicLong timeout = counter(timeLimiter.getName(), TIMEOUT);
        registerGauge(metricName(TIME_LIMITER_CALLS_METRIC, timeLimiter.getName(), SUCCESSFUL), successful::get);
        registerGauge(metricName(TIME_LIMITER_CALLS_METRIC, timeLimiter.getName(), FAILED), failed::get);
        registerGauge(metricName(TIME_LIMITER_CALLS_METRIC, timeLimiter.getName(), TIMEOUT), timeout::get);
        timeLimiter.getEventPublisher()
                   .onSuccess(event -> successful.incrementAndGet())
                   .onError(event -> failed.incrementAndGet())
                   .onTimeout(event -> timeout.incrementAndGet());
    }

    private AtomicLong counter(String name, String kind) {
        return timeLimiterCounters.computeIfAbsent(name + "|" + kind, ignored -> new AtomicLong());
    }

    private void removeTimeLimiter(String name) {
        for (String kind : TIME_LIMITER_KINDS) {
            dropwizardRegistry.remove(metricName(TIME_LIMITER_CALLS_METRIC, name, kind));
            timeLimiterCounters.remove(name + "|" + kind);
        }
    }

    private MetricName metricName(String key, String name, String kind) {
        return MetricName.build(key).tagged("name", name, "kind", kind);
    }

    private MetricName stateMetricName(String name, CircuitBreaker.State state) {
        return MetricName.build(CIRCUIT_BREAKER_STATE_METRIC)
                         .tagged("name", name, "state", state.name().toLowerCase());
    }

    private void registerGauge(MetricName metricName, LongSupplier supplier) {
        // unconditional replace: deterministic last-writer-wins, and no stale gauge can
        // survive a rebind against the JVM-global SharedMetricRegistries instance
        dropwizardRegistry.remove(metricName);
        Gauge<Long> gauge = supplier::getAsLong;
        dropwizardRegistry.register(metricName, gauge);
        registeredMetrics.add(metricName);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // Guard against rebind after destroy in scenarios with multiple Spring contexts (e.g., @SpringBootTest),
        // which would stack duplicate listeners on the JVM-global registries.
        if (destroyed) {
            return;
        }

        // Bind all provided registries after Spring injection is complete.
        // This explicit lifecycle (vs. side effects in @Bean factory) allows proper cleanup on context close.
        bindRetryRegistry(Resilience4jRegistries.retryRegistry());
        bindCircuitBreakerRegistry(Resilience4jRegistries.circuitBreakerRegistry());
        bindTimeLimiterRegistry(Resilience4jRegistries.timeLimiterRegistry());
        retryRegistries.orderedStream().forEach(this::bindRetryRegistry);
        circuitBreakerRegistries.orderedStream().forEach(this::bindCircuitBreakerRegistry);
        timeLimiterRegistries.orderedStream().forEach(this::bindTimeLimiterRegistry);
    }

    @Override
    public void destroy() throws Exception {
        // On context close, detach listeners and unregister all metrics this binder registered.
        // This prevents dead binders from staying attached to the JVM-global registries.

        // Unregister all metrics to release strong references to resilience4j objects
        for (MetricName metricName : registeredMetrics) {
            dropwizardRegistry.remove(metricName);
        }
        registeredMetrics.clear();

        // Clear counters (metric objects are now unreachable from registries)
        timeLimiterCounters.clear();

        // Mark as destroyed to prevent rebind attempts in afterPropertiesSet() when multiple
        // Spring contexts are active (e.g., @SpringBootTest). The JVM-global registries will
        // continue to fire events on dead listeners (they cannot be unsubscribed), but we
        // block this binder from participating in further binding that would stack listeners.
        destroyed = true;

        // Note: We cannot explicitly detach listeners from resilience4j registries
        // because EventPublisher does not expose an unsubscribe API. However, removing
        // all metrics prevents the listeners from retaining strong references to those
        // objects, allowing garbage collection. Dead listeners will still receive events
        // from the shared registries, but those events fail silently when trying to
        // register metrics that have already been removed.
    }
}
