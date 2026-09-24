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
package org.nuxeo.rest.interceptor;

import static com.hyland.openarch.logging.LogMarkers.BYPASS_CONDENSING;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import com.hyland.openarch.runtime.springboot.auth.NuxeoLoginFilter;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        request.setAttribute(REQUEST_START_TIME_ATTRIBUTE, System.nanoTime());
        filterChain.doFilter(request, response);
    }

    @Override // We don't use try/catch around doFilter() as we need access to the method/ class info
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        if (!isAuthenticated(request)) {
            return;
        }
        if (handler instanceof HandlerMethod handlerMethod) {
            long endTime = System.nanoTime();
            long startTime = (long) request.getAttribute(REQUEST_START_TIME_ATTRIBUTE);
            long deltaTimeNano = endTime - startTime;
            request.setAttribute(REQUEST_END_TIME_ATTRIBUTE, endTime);
            Method method = handlerMethod.getMethod();
            String apiPath = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            logWithArguments(deltaTimeNano, apiPath, method.getName(),
                    method.getDeclaringClass().getName());
        }
    }

    /*
     * Authenticated status is no longer tied to the @Authenticated annotation instead we check our auth filters have
     * run successfully
     */
    protected boolean isAuthenticated(HttpServletRequest request) {
        Boolean isAuthenticated = (Boolean) request.getAttribute(NuxeoLoginFilter.AUTHENTICATED_ATTRIBUTE);
        return isAuthenticated != null && isAuthenticated;
    }

    private void logWithArguments(long deltaTimeNano, String apiPath, String methodName, String className) {
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
              .log();
    }
}
