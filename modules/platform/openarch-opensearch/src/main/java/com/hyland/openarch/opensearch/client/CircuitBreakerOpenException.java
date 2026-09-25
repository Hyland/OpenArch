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
package com.hyland.openarch.opensearch.client;

import org.apache.hc.core5.http.HttpStatus;
import com.hyland.openarch.common.RetryAfterHint;
import com.hyland.openarch.ecm.core.api.RepositoryException;

/**
 * Thrown when the circuit breaker guarding OpenSearch is open, so the call was refused without being attempted.
 * <p>
 * The hint advertises the breaker's whole configured open duration rather than the time still remaining, which
 * resilience4j does not expose: over-advertising is safe, under-advertising invites a retry storm against a
 * breaker that is still open. Without it the response would carry the controller's fixed two seconds for an
 * outage that lasts as long as the breaker stays open.
 * <p>
 * The counterpart of {@code CircuitBreakerOpenException} on the graph side, which states its 503 and its hint
 * the same way.
 */
public class CircuitBreakerOpenException extends RepositoryException implements RetryAfterHint {

    private final int retryAfterSeconds;

    public CircuitBreakerOpenException(String message, Throwable cause, long waitDurationInOpenStateMs) {
        super(message, cause, HttpStatus.SC_SERVICE_UNAVAILABLE);
        this.retryAfterSeconds = (int) Math.max(1, Math.ceilDiv(waitDurationInOpenStateMs, 1000));
    }

    @Override
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
