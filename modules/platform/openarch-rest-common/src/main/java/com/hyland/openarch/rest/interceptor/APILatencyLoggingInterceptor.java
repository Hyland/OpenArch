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
package com.hyland.openarch.rest.interceptor;

import static com.hyland.openarch.logging.LogMarkers.BYPASS_CONDENSING;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import com.hyland.openarch.runtime.springboot.auth.RepositoryLoginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Interceptor class to log the execution time of all authenticated REST endpoints.
 */
@Component
@Order(1) // Run first to start the timer
public class APILatencyLoggingInterceptor extends OncePerRequestFilter implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(APILatencyLoggingInterceptor.class);

    public static final String DELTA_TIME = "deltaTime";

    public static final String DELTA_TIME_IN_NANO_SECOND = "deltaTimeNano";

    public static final String API_PATH = "apiPath";

    public static final String METHOD_NAME = "methodName";

    public static final String CLASS_NAME = "className";

    public static final String REQUEST_START_TIME_ATTRIBUTE = "APILatencyLoggingInterceptor.startTime";

    public static final String REQUEST_END_TIME_ATTRIBUTE = "APILatencyLoggingInterceptor.endTime";

    private static final String REQUEST_HANDLER_ATTRIBUTE = "APILatencyLoggingInterceptor.handler";

    private static final String REQUEST_RESPONSE_ATTRIBUTE = "APILatencyLoggingInterceptor.response";

    private static final String FILTER_FINISHED_ATTRIBUTE = "APILatencyLoggingInterceptor.filterFinished";

    public static final String REQUEST_BYTES = "requestBytes";

    public static final String RESPONSE_BYTES = "responseBytes";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        request.setAttribute(REQUEST_START_TIME_ATTRIBUTE, System.nanoTime());
        ByteCountingResponseWrapper countingResponse = new ByteCountingResponseWrapper(response);
        request.setAttribute(REQUEST_RESPONSE_ATTRIBUTE, countingResponse);
        try {
            filterChain.doFilter(request, countingResponse);
        } finally {
            request.setAttribute(FILTER_FINISHED_ATTRIBUTE, true);
            // Logged here, not in afterCompletion, because the transaction filter only writes the buffered body out
            // after afterCompletion has run. A streamed response is still being written, so afterCompletion logs it.
            if (!request.isAsyncStarted()) {
                logLatency(request);
            }
        }
    }

    @Override // We don't use try/catch around doFilter() as we need access to the method/ class info
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        if (!isAuthenticated(request)) {
            return;
        }
        if (handler instanceof HandlerMethod handlerMethod) {
            request.setAttribute(REQUEST_END_TIME_ATTRIBUTE, System.nanoTime());
            request.setAttribute(REQUEST_HANDLER_ATTRIBUTE, handlerMethod);
            if (request.getAttribute(FILTER_FINISHED_ATTRIBUTE) != null) {
                logLatency(request);
            }
        }
    }

    private void logLatency(HttpServletRequest request) {
        if (!isAuthenticated(request)
                || !(request.getAttribute(REQUEST_HANDLER_ATTRIBUTE) instanceof HandlerMethod handlerMethod)) {
            return;
        }
        long startTime = (long) request.getAttribute(REQUEST_START_TIME_ATTRIBUTE);
        long endTime = (long) request.getAttribute(REQUEST_END_TIME_ATTRIBUTE);
        long deltaTimeNano = endTime - startTime;
        Method method = handlerMethod.getMethod();
        String apiPath = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        ByteCountingResponseWrapper countingResponse =
                (ByteCountingResponseWrapper) request.getAttribute(REQUEST_RESPONSE_ATTRIBUTE);
        logWithArguments(deltaTimeNano, apiPath, method.getName(), method.getDeclaringClass().getName(),
                request.getContentLengthLong(), countingResponse.getBytesWritten());
    }

    /*
     * Authenticated status is no longer tied to the @Authenticated annotation instead we check our auth filters have
     * run successfully
     */
    protected boolean isAuthenticated(HttpServletRequest request) {
        Boolean isAuthenticated = (Boolean) request.getAttribute(RepositoryLoginFilter.AUTHENTICATED_ATTRIBUTE);
        return isAuthenticated != null && isAuthenticated;
    }

    private void logWithArguments(long deltaTimeNano, String apiPath, String methodName, String className,
            long requestBytes, long responseBytes) {
        long deltaTime = TimeUnit.NANOSECONDS.toMillis(deltaTimeNano);
        LOGGER.atInfo()
              .addMarker(BYPASS_CONDENSING)
              .setMessage("Time taken to process in ms {}")
              .addArgument(deltaTime)
              .addKeyValue(DELTA_TIME, deltaTime)
              .addKeyValue(DELTA_TIME_IN_NANO_SECOND, deltaTimeNano)
              .addKeyValue(API_PATH, apiPath)
              .addKeyValue(METHOD_NAME, methodName)
              .addKeyValue(CLASS_NAME, className)
              .addKeyValue(REQUEST_BYTES, requestBytes)
              .addKeyValue(RESPONSE_BYTES, responseBytes)
              .log();
    }

    /**
     * Counts the bytes written to the response body so the latency log can be normalized by payload size. Uploads are
     * measured by the request Content-Length instead, which is -1 when the client does not send one.
     */
    static class ByteCountingResponseWrapper extends HttpServletResponseWrapper {

        private long bytesWritten;

        private ServletOutputStream outputStream;

        ByteCountingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (outputStream == null) {
                outputStream = new ByteCountingServletOutputStream(super.getOutputStream());
            }
            return outputStream;
        }

        long getBytesWritten() {
            return bytesWritten;
        }

        private class ByteCountingServletOutputStream extends ServletOutputStream {

            private final ServletOutputStream delegate;

            ByteCountingServletOutputStream(ServletOutputStream delegate) {
                this.delegate = delegate;
            }

            @Override
            public void write(int b) throws IOException {
                delegate.write(b);
                bytesWritten++;
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                delegate.write(b, off, len);
                bytesWritten += len;
            }

            @Override
            public void flush() throws IOException {
                delegate.flush();
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }

            @Override
            public boolean isReady() {
                return delegate.isReady();
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                delegate.setWriteListener(writeListener);
            }
        }
    }
}
