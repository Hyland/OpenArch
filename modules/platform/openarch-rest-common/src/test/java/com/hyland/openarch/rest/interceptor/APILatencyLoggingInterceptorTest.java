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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.rest.interceptor.config.APILatencyLoggingInterceptorTestConfig;
import com.hyland.openarch.rest.interceptor.config.APILatencyLoggingInterceptorTestConfig.TestAuthFilter;
import com.hyland.openarch.rest.interceptor.config.APILatencyLoggingInterceptorTestConfig.TestEndpointBean;
import com.hyland.openarch.runtime.springboot.auth.RepositoryLoginFilter;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.springboot.transaction.TransactionFilter;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.rest.interceptor.APILatencyLoggingInterceptor.API_PATH;
import static com.hyland.openarch.rest.interceptor.APILatencyLoggingInterceptor.CLASS_NAME;
import static com.hyland.openarch.rest.interceptor.APILatencyLoggingInterceptor.DELTA_TIME;
import static com.hyland.openarch.rest.interceptor.APILatencyLoggingInterceptor.DELTA_TIME_IN_NANO_SECOND;
import static com.hyland.openarch.rest.interceptor.APILatencyLoggingInterceptor.METHOD_NAME;
import static com.hyland.openarch.rest.interceptor.APILatencyLoggingInterceptor.REQUEST_BYTES;
import static com.hyland.openarch.rest.interceptor.APILatencyLoggingInterceptor.RESPONSE_BYTES;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = { APILatencyLoggingInterceptorTestConfig.class, APILatencyLoggingInterceptor.class })
@TestPropertySource(properties = { "spring.profiles.active=default" })
@ExtendWith(OutputCaptureExtension.class)
public class APILatencyLoggingInterceptorTest extends RuntimeSpringBootTest {

    @BeforeEach
    public void setup(@Autowired WebApplicationContext context, @Autowired APILatencyLoggingInterceptor interceptor,
            @Autowired TestAuthFilter filter) {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                             .apply(SecurityMockMvcConfigurers.springSecurity())
                             .addFilters(filter, interceptor)
                             .build();
    }

    @Test
    public void whenAuthenticatedIsNullThenIsAuthenticatedShouldReturnFalse(
            @Autowired APILatencyLoggingInterceptor apiLatencyLoggingInterceptor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RepositoryLoginFilter.AUTHENTICATED_ATTRIBUTE)).thenReturn(null);
        assertFalse(apiLatencyLoggingInterceptor.isAuthenticated(request));
    }

    @Test
    public void whenAuthenticatedIsTrueThenIsAuthenticatedShouldReturnTrue(
            @Autowired APILatencyLoggingInterceptor apiLatencyLoggingInterceptor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RepositoryLoginFilter.AUTHENTICATED_ATTRIBUTE)).thenReturn(true);
        assertTrue(apiLatencyLoggingInterceptor.isAuthenticated(request));
    }

    @Test
    public void whenAuthenticatedIsFalseThenIsAuthenticatedShouldReturnFalse(
            @Autowired APILatencyLoggingInterceptor apiLatencyLoggingInterceptor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RepositoryLoginFilter.AUTHENTICATED_ATTRIBUTE)).thenReturn(false);
        assertFalse(apiLatencyLoggingInterceptor.isAuthenticated(request));
    }

    @Test
    @WithMockUser
    public void whenAuthenticatedShouldLogToKeyValueArgs(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(APILatencyLoggingInterceptor.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        APILatencyLoggingInterceptorTestConfig.authenticated = true;
        String requestPath = "/test/myID";
        String expectedPath = "/test/{id}";
        var result = mvc.perform(get(requestPath)).andExpect(status().isOk());
        Long startTime = (Long) result.andReturn()
                                      .getRequest()
                                      .getAttribute(APILatencyLoggingInterceptor.REQUEST_START_TIME_ATTRIBUTE);
        Long endTime = (Long) result.andReturn()
                                    .getRequest()
                                    .getAttribute(APILatencyLoggingInterceptor.REQUEST_END_TIME_ATTRIBUTE);
        assertNotNull(startTime);
        assertNotNull(endTime);
        String methodName = "testMethod";
        List<KeyValuePair> arguments = listAppender.list.get(0).getKeyValuePairs();

        verifyValues(arguments, endTime - startTime, expectedPath, methodName, TestEndpointBean.class.getName());
    }

    @Test
    @WithMockUser
    public void whenNotAuthenticatedShouldNotLog(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(APILatencyLoggingInterceptor.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        APILatencyLoggingInterceptorTestConfig.authenticated = false;
        String path = "/test/myID";
        var result = mvc.perform(get(path)).andExpect(status().isOk());
        Long endTime = (Long) result.andReturn()
                                    .getRequest()
                                    .getAttribute(APILatencyLoggingInterceptor.REQUEST_END_TIME_ATTRIBUTE);
        assertNull(endTime);
        assertTrue(listAppender.list.isEmpty());
    }

    @Test
    @WithMockUser
    public void whenResponseHasBodyShouldCountResponseBytes() throws Exception {
        ListAppender<ILoggingEvent> listAppender = attachLogAppender();

        APILatencyLoggingInterceptorTestConfig.authenticated = true;
        mvc.perform(get("/test/download")).andExpect(status().isOk());

        assertEquals(Long.valueOf(10L), logValue(listAppender, RESPONSE_BYTES));
    }

    @Test
    @WithMockUser
    public void whenRequestHasBodyShouldCountRequestBytes() throws Exception {
        ListAppender<ILoggingEvent> listAppender = attachLogAppender();

        APILatencyLoggingInterceptorTestConfig.authenticated = true;
        mvc.perform(post("/test/upload").with(csrf()).content("hello")).andExpect(status().isOk());

        assertEquals(Long.valueOf(5L), logValue(listAppender, REQUEST_BYTES));
    }

    @Test
    @WithMockUser
    public void whenStreamedResponseShouldLogOnceAfterBodyIsWritten() throws Exception {
        ListAppender<ILoggingEvent> listAppender = attachLogAppender();

        APILatencyLoggingInterceptorTestConfig.authenticated = true;
        MvcResult result = mvc.perform(get("/test/stream")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        assertEquals(1, listAppender.list.size());
        assertEquals(Long.valueOf(10L), logValue(listAppender, RESPONSE_BYTES));
    }

    @Test
    @WithMockUser
    public void whenBufferedResponseShouldCountBytesAfterTransactionFilterWritesIt(
            @Autowired WebApplicationContext context, @Autowired TestAuthFilter filter,
            @Autowired APILatencyLoggingInterceptor interceptor, @Autowired TransactionFilter transactionFilter)
            throws Exception {
        ListAppender<ILoggingEvent> listAppender = attachLogAppender();
        mvc = mvcWithTransactionFilter(context, filter, interceptor, transactionFilter);

        APILatencyLoggingInterceptorTestConfig.authenticated = true;
        mvc.perform(get("/test/download")).andExpect(status().isOk());

        assertEquals(Long.valueOf(10L), logValue(listAppender, RESPONSE_BYTES));
    }

    @Test
    @WithMockUser
    public void whenResponseBypassesTransactionBufferShouldCountEveryByte(
            @Autowired WebApplicationContext context, @Autowired TestAuthFilter filter,
            @Autowired APILatencyLoggingInterceptor interceptor, @Autowired TransactionFilter transactionFilter)
            throws Exception {
        ListAppender<ILoggingEvent> listAppender = attachLogAppender();
        mvc = mvcWithTransactionFilter(context, filter, interceptor, transactionFilter);

        APILatencyLoggingInterceptorTestConfig.authenticated = true;
        mvc.perform(get("/test/large")).andExpect(status().isOk());

        assertEquals(Long.valueOf(APILatencyLoggingInterceptorTestConfig.LARGE_CONTENT.length),
                logValue(listAppender, RESPONSE_BYTES));
    }

    private MockMvc mvcWithTransactionFilter(WebApplicationContext context, TestAuthFilter filter,
            APILatencyLoggingInterceptor interceptor, TransactionFilter transactionFilter) {
        return MockMvcBuilders.webAppContextSetup(context)
                              .apply(SecurityMockMvcConfigurers.springSecurity())
                              .addFilters(filter, interceptor, transactionFilter)
                              .build();
    }

    @Test
    public void byteCountingOutputStreamCountsEveryWrite() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    @Override
                    public void write(int b) {
                        // the wrapper counts the bytes, the stub discards them
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        // not needed by the test
                    }
                };
            }
        };
        APILatencyLoggingInterceptor.ByteCountingResponseWrapper wrapper =
                new APILatencyLoggingInterceptor.ByteCountingResponseWrapper(response);
        ServletOutputStream outputStream = wrapper.getOutputStream();

        outputStream.write('a');
        outputStream.write(new byte[] { 'b', 'c', 'd' }, 0, 3);
        outputStream.flush();
        assertTrue(outputStream.isReady());
        outputStream.setWriteListener(null);
        outputStream.close();

        assertEquals(4L, wrapper.getBytesWritten());
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(APILatencyLoggingInterceptor.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    private Object logValue(ListAppender<ILoggingEvent> listAppender, String key) {
        return listAppender.list.get(0)
                                .getKeyValuePairs()
                                .stream()
                                .filter(pair -> pair.key.equals(key))
                                .findFirst()
                                .orElseThrow()
                                .value;
    }

    private void verifyValues(List<KeyValuePair> output, long deltaTimeNano, String expectedPath, String methodName,
            String className) {
        List<String> argumentKeys = List.of(DELTA_TIME, DELTA_TIME_IN_NANO_SECOND, API_PATH, METHOD_NAME, CLASS_NAME,
                REQUEST_BYTES, RESPONSE_BYTES);
        assertEquals(output.size(), argumentKeys.size());
        Map<String, Object> expectedValues = Map.of(DELTA_TIME, TimeUnit.NANOSECONDS.toMillis(deltaTimeNano),
                DELTA_TIME_IN_NANO_SECOND, deltaTimeNano, API_PATH, expectedPath, METHOD_NAME, methodName, CLASS_NAME,
                className, REQUEST_BYTES, -1L, RESPONSE_BYTES, 0L);
        for (KeyValuePair pair : output) {
            String key = pair.key;
            Object value = pair.value;
            assertTrue(expectedValues.containsKey(key));
            assertEquals(expectedValues.get(key), value);
        }

    }

}
