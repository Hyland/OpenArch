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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;

class Resilience4jDropwizardMetricsBinderTest {

    @Test
    void bindsExistingAndFutureResilience4jMetrics() throws Throwable {
        MetricRegistry dropwizardRegistry = new MetricRegistry();
        @SuppressWarnings("unchecked")
        ObjectProvider<RetryRegistry> emptyRetryProvider = mock(ObjectProvider.class);
        when(emptyRetryProvider.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        @SuppressWarnings("unchecked")
        ObjectProvider<CircuitBreakerRegistry> emptyCircuitBreakerProvider = mock(ObjectProvider.class);
        when(emptyCircuitBreakerProvider.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        @SuppressWarnings("unchecked")
        ObjectProvider<TimeLimiterRegistry> emptyTimeLimiterProvider = mock(ObjectProvider.class);
        when(emptyTimeLimiterProvider.orderedStream()).thenReturn(java.util.stream.Stream.empty());

        Resilience4jDropwizardMetricsBinder binder = new Resilience4jDropwizardMetricsBinder(dropwizardRegistry,
                emptyRetryProvider, emptyCircuitBreakerProvider, emptyTimeLimiterProvider);

        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();

        binder.bindRetryRegistry(retryRegistry);
        binder.bindCircuitBreakerRegistry(circuitBreakerRegistry);
        binder.bindTimeLimiterRegistry(timeLimiterRegistry);

        Retry retry = retryRegistry.retry("idpTokenFetch", RetryConfig.custom()
                                                                 .maxAttempts(3)
                                                                 .waitDuration(Duration.ofMillis(1))
                                                                 .retryExceptions(IOException.class)
                                                                 .build());
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("evStoreClient",
                CircuitBreakerConfig.custom().minimumNumberOfCalls(2).slidingWindowSize(2).build());
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter("asyncFetch");

        AtomicInteger attempts = new AtomicInteger();
        retry.executeCheckedSupplier(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IOException("transient");
            }
            return "ok";
        });
        circuitBreaker.transitionToOpenState();
        timeLimiter.onSuccess();

        assertGaugeValue(dropwizardRegistry,
                MetricName.build(Resilience4jDropwizardMetricsBinder.RETRY_CALLS_METRIC)
                          .tagged("name", "idpTokenFetch", "kind", "successful_with_retry"), 1L);
        assertGaugeValue(dropwizardRegistry,
                MetricName.build(Resilience4jDropwizardMetricsBinder.CIRCUIT_BREAKER_STATE_METRIC)
                          .tagged("name", "evStoreClient", "state", "open"), 1L);
        assertGaugeValue(dropwizardRegistry,
                MetricName.build(Resilience4jDropwizardMetricsBinder.CIRCUIT_BREAKER_STATE_METRIC)
                          .tagged("name", "evStoreClient", "state", "closed"), 0L);
        assertGaugeValue(dropwizardRegistry,
                MetricName.build(Resilience4jDropwizardMetricsBinder.TIME_LIMITER_CALLS_METRIC)
                          .tagged("name", "asyncFetch", "kind", "successful"), 1L);
    }

    @Test
    void removesAndRebindsMetricsWhenRegistryEntryIsRecreated() {
        MetricRegistry dropwizardRegistry = new MetricRegistry();
        @SuppressWarnings("unchecked")
        ObjectProvider<RetryRegistry> emptyRetryProvider = mock(ObjectProvider.class);
        when(emptyRetryProvider.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        @SuppressWarnings("unchecked")
        ObjectProvider<CircuitBreakerRegistry> emptyCircuitBreakerProvider = mock(ObjectProvider.class);
        when(emptyCircuitBreakerProvider.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        @SuppressWarnings("unchecked")
        ObjectProvider<TimeLimiterRegistry> emptyTimeLimiterProvider = mock(ObjectProvider.class);
        when(emptyTimeLimiterProvider.orderedStream()).thenReturn(java.util.stream.Stream.empty());

        Resilience4jDropwizardMetricsBinder binder = new Resilience4jDropwizardMetricsBinder(dropwizardRegistry,
                emptyRetryProvider, emptyCircuitBreakerProvider, emptyTimeLimiterProvider);
        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();

        binder.bindRetryRegistry(retryRegistry);
        Retry first = retryRegistry.retry("openSearchApi", RetryConfig.custom().maxAttempts(2).build());
        retryRegistry.remove("openSearchApi");
        Retry second = retryRegistry.retry("openSearchApi", RetryConfig.custom().maxAttempts(4).build());

        assertThat(second).isNotSameAs(first);

        // Drive a call through second to verify the gauge is wired to the new instance
        assertThatThrownBy(() -> second.executeCheckedSupplier(() -> { throw new IOException("boom"); }))
                .isInstanceOf(IOException.class);

        // Verify the failed_with_retry gauge on the second instance is incremented
        assertGaugeValue(dropwizardRegistry,
                MetricName.build(Resilience4jDropwizardMetricsBinder.RETRY_CALLS_METRIC)
                          .tagged("name", "openSearchApi", "kind", "failed_with_retry"), 1L);
    }

    @SuppressWarnings("unchecked")
    private static void assertGaugeValue(MetricRegistry registry, MetricName metricName, long expectedValue) {
        Gauge<Long> gauge = (Gauge<Long>) registry.getGauges().get(metricName);
        assertThat(gauge).isNotNull();
        assertThat(gauge.getValue()).isEqualTo(expectedValue);
    }
}
