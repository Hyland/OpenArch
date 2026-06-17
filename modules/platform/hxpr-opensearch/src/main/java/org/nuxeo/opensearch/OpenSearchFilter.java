/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     "Guillaume Renard"
 */

package org.nuxeo.opensearch;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.nuxeo.opensearch.listener.OpenSearchInlineListener;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to toggle synchronous OpenSearch indexing.
 *
 * @since 8.3
 */
@Component
public class OpenSearchFilter extends OncePerRequestFilter {

    public static final String OPENSEARCH_SYNC_FLAG = "nx-opensearch-sync";

    boolean opensearchSync;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            opensearchSync = Boolean.parseBoolean(request.getHeader(OPENSEARCH_SYNC_FLAG));
            if (opensearchSync) {
                OpenSearchInlineListener.useSyncIndexing.set(true);
            }
            filterChain.doFilter(request, response);
        } finally {
            if (opensearchSync) {
                OpenSearchInlineListener.useSyncIndexing.set(false);
            }
        }
    }
}
