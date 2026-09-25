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
package com.hyland.openarch.rest.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(classes = {
        ErroredResponseFilter.class }, properties = ErroredResponseFilter.LOG_ERRORED_RESPONSE_ENABLED + "=true")
@ExtendWith({ OutputCaptureExtension.class })
class ErroredResponseFilterTest extends RuntimeSpringBootTest {

    @MockitoSpyBean
    ErroredResponseFilter erroredResponseFilter;

    private static final String PAYLOAD = "{ \"foo\": \"bar\" }";

    private void runFilter(String payload, int statusCode) {
        // initialize mocks
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        try {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("http://example.com/base");
            when(request.getInputStream()).thenReturn(
                    new ServletInputStreamImpl(new ByteArrayInputStream(payload.getBytes())));
            when(request.getContentLength()).thenReturn(payload.length());
            when(response.getStatus()).thenReturn(statusCode);
            Mockito.doAnswer(invocation -> {
                ServletRequest cachedRequest = invocation.getArgument(0);
                cachedRequest.getInputStream().readAllBytes();
                return null;
            }).when(filterChain).doFilter(any(), any());
        } catch (Exception e) {
            fail("Failed to set up the mocks before running the filter", e);
        }
        // run the filter
        assertThatCode(() -> erroredResponseFilter.doFilter(request, response, filterChain)).doesNotThrowAnyException();
    }

    /**
     * Runs the filter with a chain that consumes the payload and then fails, as the request transaction failing to
     * commit does, and checks that the original failure is the one that comes back out.
     */
    private void runFilterThrowing(String payload, RuntimeException failure) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        try {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("http://example.com/base");
            when(request.getInputStream()).thenReturn(
                    new ServletInputStreamImpl(new ByteArrayInputStream(payload.getBytes())));
            when(request.getContentLength()).thenReturn(payload.length());
            // the handler had answered successfully: nothing has set an error status, the container will
            when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
            Mockito.doAnswer(invocation -> {
                ServletRequest cachedRequest = invocation.getArgument(0);
                cachedRequest.getInputStream().readAllBytes();
                throw failure;
            }).when(filterChain).doFilter(any(), any());
        } catch (Exception e) {
            fail("Failed to set up the mocks before running the filter", e);
        }
        // run the filter
        assertThatThrownBy(() -> erroredResponseFilter.doFilter(request, response, filterChain)).isSameAs(failure);
    }

    @Test
    void testExceptionEscapingTheChainIsLoggedAsAnInternalError(CapturedOutput output) {
        runFilterThrowing(PAYLOAD, new IllegalStateException("commit failed"));
        assertThat(output).contains(
                "Request failed with status code 500: POST http://example.com/base { \"foo\": \"bar\" }");
    }

    @Test
    void testOk(CapturedOutput output) {
        runFilter(PAYLOAD, HttpStatus.SC_OK);
        assertThat(output).doesNotContain("\"Request failed with status code");
    }

    @Test
    void testBadRequest(CapturedOutput output) {
        runFilter(PAYLOAD, HttpStatus.SC_BAD_REQUEST);
        assertThat(output).contains(
                "Request failed with status code 400: POST http://example.com/base { \"foo\": \"bar\" }");
    }

    @Test
    void testNotFound(CapturedOutput output) {
        runFilter(PAYLOAD, HttpStatus.SC_NOT_FOUND);
        assertThat(output).doesNotContain("\"Request failed with status code");
    }

    @Test
    void testInternalServerError(CapturedOutput output) {
        runFilter(PAYLOAD, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        assertThat(output).contains(
                "Request failed with status code 500: POST http://example.com/base { \"foo\": \"bar\" }");
    }

    @Test
    void testInternalServerErrorMalformedPayload(CapturedOutput output) {
        runFilter("malformed", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        assertThat(output).contains("Request failed with status code 500: POST http://example.com/base malformed");
    }

    @Test
    void testInternalServerErrorLongPayload(CapturedOutput output) {
        String value = RandomStringUtils.secure().nextAlphabetic(ErroredResponseFilter.DEFAULT_PAYLOAD_LIMIT);
        String payload = String.format("{ \"foo\": \"%s\" }", value);
        runFilter(payload, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        String halfPayload = payload.substring(0, payload.length() / 2);
        assertThat(output).doesNotContain(payload).contains(halfPayload);
    }

    @Test
    void testShouldHidePayloadContainingAuthorization(CapturedOutput output) {
        runFilter("#Authorization:", HttpStatus.SC_BAD_REQUEST);
        assertThat(output).contains("Request failed with status code 400: POST http://example.com/base *****");
    }

    @Test
    void testShouldHidePayloadContainingAuthorizationWithSpaces(CapturedOutput output) {
        runFilter(" # Authorization  : ", HttpStatus.SC_BAD_REQUEST);
        assertThat(output).contains("Request failed with status code 400: POST http://example.com/base *****");
    }

    @Test
    void testShouldHidePayloadContainingAuthorizationWithAdditionalLines(CapturedOutput output) {
        runFilter(" #  additional comment \n # Authorization  : ", HttpStatus.SC_BAD_REQUEST);
        assertThat(output).contains("Request failed with status code 400: POST http://example.com/base *****");
    }

    /**
     * Mock {@link ServletInputStream} implementation that wraps a {@link ByteArrayInputStream}. Is accessed by the
     * {@link ErroredResponseFilter} to read the request payload.
     */
    private static class ServletInputStreamImpl extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        public ServletInputStreamImpl(ByteArrayInputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public int available() {
            return inputStream.available();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() > 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // doesn't need to do anything
        }

    }

}
