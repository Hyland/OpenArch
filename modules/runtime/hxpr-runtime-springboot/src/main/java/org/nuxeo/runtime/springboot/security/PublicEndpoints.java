/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.runtime.springboot.security;

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
