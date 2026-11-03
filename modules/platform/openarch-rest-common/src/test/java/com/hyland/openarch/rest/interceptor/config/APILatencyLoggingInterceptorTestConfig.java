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
package com.hyland.openarch.rest.interceptor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.hyland.openarch.rest.config.RestInterceptorConfiguration;
import com.hyland.openarch.rest.interceptor.APILatencyLoggingInterceptor;
import com.hyland.openarch.rest.swagger.annotation.Authenticated;
import com.hyland.openarch.runtime.springboot.auth.RepositoryLoginFilter;
import com.hyland.openarch.runtime.springboot.transaction.TransactionFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@TestConfiguration
@Import({RestInterceptorConfiguration.class})
public class APILatencyLoggingInterceptorTestConfig {

    public static Boolean authenticated = true;

    public static final byte[] DOWNLOAD_CONTENT = "0123456789".getBytes(StandardCharsets.UTF_8);

    // one byte more than the transaction filter buffers, so the body is written straight to the client
    public static final byte[] LARGE_CONTENT = new byte[TransactionFilter.DEFAULT_RESPONSE_BUFFER_SIZE + 1];

    @Authenticated
    @RequestMapping(path = "/test")
    @RestController
    public static class TestEndpointBean {

        @GetMapping("{id}")
        public void testMethod(HttpServletRequest request) {
            // do nothing
            assert request.getAttribute(RepositoryLoginFilter.AUTHENTICATED_ATTRIBUTE) != null;
            assert request.getAttribute(APILatencyLoggingInterceptor.REQUEST_START_TIME_ATTRIBUTE) != null;
        }

        @GetMapping("download")
        public void download(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.getOutputStream().write(DOWNLOAD_CONTENT);
        }

        @GetMapping("stream")
        public StreamingResponseBody stream() {
            return outputStream -> outputStream.write(DOWNLOAD_CONTENT);
        }

        @GetMapping("large")
        public void large(HttpServletResponse response) throws IOException {
            response.getOutputStream().write(LARGE_CONTENT);
        }

        @PostMapping("upload")
        public void upload() {
            // do nothing
        }
    }

    @Component
    public static class TestAuthFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
            request.setAttribute(RepositoryLoginFilter.AUTHENTICATED_ATTRIBUTE, authenticated);
            filterChain.doFilter(request, response);
        }

    }

}
