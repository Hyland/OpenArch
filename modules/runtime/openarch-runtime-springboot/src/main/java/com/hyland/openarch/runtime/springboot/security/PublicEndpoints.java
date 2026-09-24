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
package com.hyland.openarch.runtime.springboot.security;

import java.util.List;

/**
 * Common public endpoint paths that are permitted without authentication across all security configurations.
 */
public final class PublicEndpoints {

    public static final String ROOT = "/";

    public static final String HELLO = "/hello";

    public static final String VERSION = "/v";

    public static final String SLO = "/slo";

    public static final String ACTUATOR_INFO = "/actuator/info";

    public static final String ACTUATOR_HEALTH = "/actuator/health/**";

    public static final String SWAGGER_UI = "/swagger-ui/**";

    public static final String API_DOCS = "/v3/api-docs/**";

    public static final String API_DOCS_YAML = "/v3/api-docs.yaml";

    /**
     * Immutable list of all default public GET paths. Pass directly to
     * {@code requestMatchers(HttpMethod.GET, PublicEndpoints.ALL.toArray(String[]::new)).permitAll()} to permit
     * the standard set of unauthenticated endpoints in one call.
     */
    public static final List<String> ALL = List.of(ROOT, HELLO, VERSION, SLO, ACTUATOR_INFO, ACTUATOR_HEALTH,
            SWAGGER_UI, API_DOCS, API_DOCS_YAML);

    private PublicEndpoints() {
        // Constants class
    }
}
