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
package com.hyland.openarch.runtime.httpclient;

import java.util.function.Function;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

/**
 * Creates Apache HttpClient builders with repository retry, timeout and connection-pool defaults from
 * {@code hxpr.httpclient.*} Framework properties or an explicit property lookup for Spring startup.
 * Callers can override builder settings before building and must close the client when it is no longer needed.
 */
public final class HttpClientHelper {

    private HttpClientHelper() {
    }

    /**
     * Creates a builder with both I/O and HTTP-response retries enabled.
     */
    public static HttpClientBuilder custom() {
        return custom(true);
    }

    /**
     * Uses an explicit property lookup (e.g. Spring {@code Environment::getProperty}) before Framework starts.
     * A {@code null} lookup uses Framework properties.
     */
    public static HttpClientBuilder custom(Function<String, String> properties) {
        return new RepositoryRetryHandler(false, properties).configure(HttpClients.custom());
    }

    /**
     * Creates a builder with I/O retries only, for callers that already have their own status-retry policy.
     */
    public static HttpClientBuilder customWithoutRetryResponses() {
        return custom(false);
    }

    private static HttpClientBuilder custom(boolean retryResponses) {
        return RepositoryRetryHandler.withDefaults().configure(HttpClients.custom(), retryResponses);
    }

    /**
     * Creates a builder that also retries non-idempotent responses on 500/502/503/504.
     * Use only when duplicate request effects are acceptable, such as issuing an additional IDP token.
     */
    public static HttpClientBuilder customWithNonIdempotentResponseRetries() {
        return RepositoryRetryHandler.withRetryNonIdempotentResponses().configure(HttpClients.custom());
    }

    /**
     * Enables non-idempotent response retries using an explicit property lookup, or Framework if {@code null}.
     */
    public static HttpClientBuilder customWithNonIdempotentResponseRetries(Function<String, String> properties) {
        return new RepositoryRetryHandler(true, properties).configure(HttpClients.custom());
    }
}
