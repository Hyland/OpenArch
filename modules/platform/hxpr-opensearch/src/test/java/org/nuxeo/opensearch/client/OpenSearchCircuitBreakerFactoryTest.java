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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.common.resilience.Resilience4jRegistries;
import org.opensearch.client.transport.httpclient5.ResponseException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * Plain unit tests for {@link OpenSearchCircuitBreakerFactory}: no Spring context and no Nuxeo runtime are needed,
 * since it is built on the {@code resilience4j-circuitbreaker} core library used programmatically.
 */
class OpenSearchCircuitBreakerFactoryTest {

    @BeforeEach
    @AfterEach
    void cleanRegistry() {
        Resilience4jRegistries.removeCircuitBreaker(OpenSearchCircuitBreakerFactory.CIRCUIT_BREAKER_NAME);
    }

    // Uses executeCheckedSupplier (not executeRunnable): Runnable#run cannot throw a checked IOException, and
    // isRetryable (used by OpenSearchCircuitBreakerFactory to decide what counts as a failure) treats IOException
    // as the transient failure case.
    private static void triggerFailure(CircuitBreaker circuitBreaker, Throwable failure) {
        assertThatThrownBy(() -> circuitBreaker.executeCheckedSupplier(() -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    void opensAfterMinimumNumberOfFailedCalls() {
        CircuitBreaker circuitBreaker = OpenSearchCircuitBreakerFactory.create(50.0f, 2, 2, 60_000);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        triggerFailure(circuitBreaker, new IOException("boom"));
        triggerFailure(circuitBreaker, new IOException("boom"));

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> circuitBreaker.executeRunnable(() -> {
        })).isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void ignoresNonRetryableExceptionsForFailureRate() {
        CircuitBreaker circuitBreaker = OpenSearchCircuitBreakerFactory.create(50.0f, 2, 2, 60_000);

        for (int i = 0; i < 5; i++) {
            triggerFailure(circuitBreaker, new IllegalArgumentException("not retryable"));
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void ignoresNonTransientResponseExceptionStatusForFailureRate() {
        // ResponseException extends IOException: a non-transient status like a 409 version conflict must not
        // be recorded as a circuit breaker failure.
        CircuitBreaker circuitBreaker = OpenSearchCircuitBreakerFactory.create(50.0f, 2, 2, 60_000);

        for (int i = 0; i < 5; i++) {
            ResponseException e = mock(ResponseException.class);
            when(e.status()).thenReturn(409);
            triggerFailure(circuitBreaker, e);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void transitionsToHalfOpenAfterWaitDurationElapses() throws InterruptedException {
        CircuitBreaker circuitBreaker = OpenSearchCircuitBreakerFactory.create(50.0f, 2, 2, 1);

        triggerFailure(circuitBreaker, new IOException("boom"));
        triggerFailure(circuitBreaker, new IOException("boom"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(50);
        circuitBreaker.executeRunnable(() -> {
        });

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void invokesStateTransitionCallback() {
        CircuitBreaker circuitBreaker = OpenSearchCircuitBreakerFactory.create(50.0f, 2, 2, 60_000);
        AtomicInteger transitionCount = new AtomicInteger();
        circuitBreaker.getEventPublisher().onStateTransition(event -> transitionCount.incrementAndGet());

        triggerFailure(circuitBreaker, new IOException("boom"));
        triggerFailure(circuitBreaker, new IOException("boom"));

        assertThat(transitionCount.get()).isEqualTo(1);
    }

    @Test
    void createDefaultUsesDefaultConfigValues() {
        CircuitBreaker circuitBreaker = OpenSearchCircuitBreakerFactory.createDefault();

        assertThat(circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold())
                .isEqualTo(OpenSearchCircuitBreakerFactory.DEFAULT_FAILURE_RATE_THRESHOLD);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize())
                .isEqualTo(OpenSearchCircuitBreakerFactory.DEFAULT_SLIDING_WINDOW_SIZE);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls())
                .isEqualTo(OpenSearchCircuitBreakerFactory.DEFAULT_MINIMUM_NUMBER_OF_CALLS);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(OpenSearchCircuitBreakerFactory.DEFAULT_WAIT_DURATION_IN_OPEN_STATE_MS);
    }

}
