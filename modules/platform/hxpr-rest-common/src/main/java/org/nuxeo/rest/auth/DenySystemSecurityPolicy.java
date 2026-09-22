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
package org.nuxeo.rest.auth;

import static org.nuxeo.ecm.core.api.security.SecurityConstants.SYSTEM_USERNAME;

import java.io.IOException;

import com.hyland.openarch.runtime.springboot.security.SpringSecurityHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security policy that denies login for system user on the Rest layer.
 *
 * @since TODO
 */
@Component
public class DenySystemSecurityPolicy extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!SpringSecurityHelper.isAnonymous() && SYSTEM_USERNAME.equals(SpringSecurityHelper.getPrincipalId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "System user is not allowed to login");
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
