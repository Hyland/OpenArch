/*
 * (C) Copyright 2021 - 2024 Hyland (http://hyland.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.runtime.springboot.auth;

import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;

import javax.security.auth.login.LoginException;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.springboot.security.SpringSecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class NuxeoLoginFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(NuxeoLoginFilter.class);

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
        try (var _ = Framework.loginUser(userId)) {
            request.setAttribute(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
            filterChain.doFilter(request, response);
        } catch (LoginException e) {
            log.info(e.getMessage(), e);
            response.setStatus(SC_UNAUTHORIZED);
        } catch (NuxeoException e) {
            if (log.isDebugEnabled()) {
                log.debug(e.getMessage(), e);
            }
            response.setStatus(e.getStatusCode());
        }
    }

}
