/*
 * (C) Copyright 2024 Hyland (http://hyland.com/) and others.
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
package org.nuxeo.rest.interceptor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.nuxeo.rest.config.RestInterceptorConfiguration;
import org.nuxeo.rest.interceptor.APILatencyLoggingInterceptor;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.runtime.springboot.auth.NuxeoLoginFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

@TestConfiguration
@Import({RestInterceptorConfiguration.class})
public class APILatencyLoggingInterceptorTestConfig {

    public static Boolean authenticated = true;

    @Authenticated
    @RequestMapping(path = "/test")
    @RestController
    public static class TestEndpointBean {

        @GetMapping("{id}")
        public void testMethod(HttpServletRequest request) {
            // do nothing
            assert request.getAttribute(NuxeoLoginFilter.AUTHENTICATED_ATTRIBUTE) != null;
            assert request.getAttribute(APILatencyLoggingInterceptor.REQUEST_START_TIME_ATTRIBUTE) != null;
        }
    }

    @Component
    public static class TestAuthFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
            request.setAttribute(NuxeoLoginFilter.AUTHENTICATED_ATTRIBUTE, authenticated);
            filterChain.doFilter(request, response);
        }

    }

}
