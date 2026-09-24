/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.springboot.auth;

import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;

import javax.security.auth.login.LoginException;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.api.login.LoginContext;
import com.hyland.openarch.runtime.springboot.security.SpringSecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter that logs in the authorized user while doing processing.
 */
@Component
// Explicit order so the request-scoped TransactionFilter can sit just inside this filter, i.e. the transaction
// commits while the user is still logged in.
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class RepositoryLoginFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RepositoryLoginFilter.class);

    public static final String AUTHENTICATED_ATTRIBUTE = "HxP.AUTHENTICATED";
    public static final String PUBSUB_AUTHENTICATED = "hxpJwtAPIFactory.AUTHENTICATED";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return (request.getAttribute(PUBSUB_AUTHENTICATED) != null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (Framework.isInitialized() && !SpringSecurityHelper.isAnonymous()) {
            doFilterWithLogin(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    protected void doFilterWithLogin(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        String userId = SpringSecurityHelper.getPrincipalId();
        // Only a failure of the login itself is turned into a status here. What the request throws afterwards is for
        // the REST exception handler, or failing that the container, to render: swallowing it would answer with a
        // bare status and no body, and would silently drop failures raised by the filters we wrap (the request
        // transaction commit, in particular).
        LoginContext loginContext;
        try {
            loginContext = Framework.loginUser(userId);
        } catch (LoginException e) {
            log.info(e.getMessage(), e);
            response.sendError(SC_UNAUTHORIZED);
            return;
        } catch (RepositoryException e) {
            log.debug(e.getMessage(), e);
            response.sendError(e.getStatusCode());
            return;
        }
        try (loginContext) {
            request.setAttribute(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
            filterChain.doFilter(request, response);
        }
    }

}
