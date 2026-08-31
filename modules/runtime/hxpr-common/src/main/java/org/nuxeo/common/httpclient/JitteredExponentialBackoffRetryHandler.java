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
package org.nuxeo.common.httpclient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.protocol.HttpContext;

public class JitteredExponentialBackoffRetryHandler implements HttpRequestRetryHandler {

    private static final int MAX_TRY = 3;

    private static final Set<Integer> RETRIABLE_STATUS_IDEMPOTENT = Set.of(408, 425, 429, 500, 502, 503, 504);

    private static final Set<Integer> RETRIABLE_STATUS_NON_IDEMPOTENT = Set.of(408, 425, 429);

    private static final double JITTER_FACTOR = 0.5;

    private final Random random = ThreadLocalRandom.current();

    @Override
    public boolean retryRequest(IOException ex, int executionCount, HttpContext ctx) {
        if (executionCount > MAX_TRY) {
            return false;
        }
        if (ex.getCause() instanceof CallNotPermittedException) {
            return false;
        }
        if (ex instanceof HttpResponseException hre) {
            HttpRequest req = HttpClientContext.adapt(ctx).getRequest();
            Set<Integer> allowed = isIdempotentMethod(req) ? RETRIABLE_STATUS_IDEMPOTENT
                    : RETRIABLE_STATUS_NON_IDEMPOTENT;
            return allowed.contains(hre.getStatusCode());
        }
        return ex instanceof SocketTimeoutException || ex instanceof ConnectTimeoutException
                || ex instanceof ConnectException;
    }

    private boolean isIdempotentMethod(HttpRequest req) {
        String method = req.getRequestLine().getMethod();
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)
                || "TRACE".equalsIgnoreCase(method);
    }

    public long getRetryInterval(int executionCount) {
        double factor = 1.0 + (random.nextDouble() * 2 - 1) * JITTER_FACTOR;
        return (long) (executionCount * factor);
    }

}
