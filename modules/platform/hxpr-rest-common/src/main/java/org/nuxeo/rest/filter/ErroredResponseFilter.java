/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Component
@ConditionalOnProperty(name = ErroredResponseFilter.LOG_ERRORED_RESPONSE_ENABLED, havingValue = "true")
// Outermost of the application filters, so that the status observed is the one the client is actually served,
// including what NuxeoLoginFilter and TransactionFilter turn a failure into on their way out.
@Order(Ordered.LOWEST_PRECEDENCE - 30)
public class ErroredResponseFilter extends OncePerRequestFilter {

    public static final String LOG_ERRORED_RESPONSE_ENABLED = "hxpr.log.errored.response.enabled";

    public static final String LOG_ERRORED_RESPONSE_PAYLOAD_LIMIT = "hxpr.log.errored.response.payload.limit";

    public static final int DEFAULT_PAYLOAD_LIMIT = 1024;

    private static final Logger log = LogManager.getLogger(ErroredResponseFilter.class);

    private static final List<Integer> FILTERED_SC = Arrays.asList(HttpStatus.SC_BAD_REQUEST,
            HttpStatus.SC_INTERNAL_SERVER_ERROR);

    // Pattern to match request bodies containing Authorization headers to prevent logging payload containing credentials
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile("^\\s*#?\\s*authorization\\s*:", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Value("${" + LOG_ERRORED_RESPONSE_PAYLOAD_LIMIT + ":" + DEFAULT_PAYLOAD_LIMIT + "}")
    private int payloadLimit;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean hasRequestPayload = request.getInputStream() != null && request.getInputStream().available() > 0;
        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request, payloadLimit);
        try {
            filterChain.doFilter(cachedRequest, response);
        } catch (ServletException | IOException | RuntimeException e) {
            // Nothing set a status, the container will, and it won't dispatch back through this filter. The request
            // did fail though, so log its payload against the status the error dispatch is about to produce.
            if (hasRequestPayload) {
                logRequestPayload(request, cachedRequest, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
            throw e;
        }
        int statusCode = response.getStatus();
        if (!FILTERED_SC.contains(statusCode) || !hasRequestPayload) {
            // not the status code we are looking for OR no payload
            return;
        }
        logRequestPayload(request, cachedRequest, statusCode);
    }

    protected void logRequestPayload(HttpServletRequest request, ContentCachingRequestWrapper cachedRequest,
            int statusCode) {
        String capturedPayload = cachedRequest.getContentAsString();
        // The find method scans the input sequence looking for the next subsequence that matches the pattern.
        // As authorization can be one of the headers, it's needed to use find instead of matches which tries to match the entire input
        // Find authorization header and replace it with **** to prevent from logging payload which contains credentials
        if (AUTHORIZATION_PATTERN.matcher(capturedPayload).find()) {
            capturedPayload = "*****";
        } else if (capturedPayload.isEmpty()) {
            // the request payload has length > 0 but the captured payload is empty -> should not happen
            log.warn("Could not get payload for request that resulted in response with status code {}: {} {}",
                    statusCode, request.getMethod(), request.getRequestURI());
            return;
        } else {
            capturedPayload = capturedPayload.replaceAll("[\n\r]", "_");
        }
        log.warn("Request failed with status code {}: {} {} {}", statusCode, request.getMethod(),
                request.getRequestURI(), capturedPayload);
    }

}
