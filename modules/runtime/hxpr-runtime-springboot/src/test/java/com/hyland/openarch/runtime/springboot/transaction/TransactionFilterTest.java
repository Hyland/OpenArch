/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.springboot.transaction;

import static jakarta.servlet.http.HttpServletResponse.SC_CREATED;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.ETAG;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.runtime.springboot.config.HxprRuntimeSpringbootAutoConfiguration;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import com.hyland.openarch.runtime.transaction.TransactionRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

/**
 * Unit tests for {@link TransactionFilter}, the single transaction boundary of every request.
 * <p>
 * The filter is driven directly rather than through {@code MockMvc}, so that the response can be inspected at the
 * exact moments that matter: while the handler is still running, and once the transaction is over.
 */
@SpringBootTest(classes = HxprRuntimeSpringbootAutoConfiguration.class)
class TransactionFilterTest {

    protected static final String BODY = "{\"id\":\"1234\"}";

    protected static final String ERROR_BODY = "{\"status\":404}";

    protected static final String CORS_HEADER = "Access-Control-Allow-Origin";

    @Autowired
    TransactionFilter filter;

    protected int defaultResponseBufferSize;

    @BeforeEach
    void before() {
        defaultResponseBufferSize = filter.responseBufferSize;
        assertFalse(TransactionHelper.isTransactionActive(), "leftover transaction");
    }

    @AfterEach
    void after() {
        filter.responseBufferSize = defaultResponseBufferSize;
        if (TransactionHelper.isTransactionActive()) {
            TransactionHelper.setTransactionRollbackOnly();
            TransactionHelper.commitOrRollbackTransaction();
        }
    }

    @Test
    void testCommitsThenWritesTheBody() throws Exception {
        var recorder = new StatusRecorder();
        var response = new MockHttpServletResponse();
        doFilter(response, res -> {
            assertTrue(TransactionHelper.isTransactionActive(), "the request runs in a transaction");
            TransactionHelper.registerSynchronization(recorder);
            res.setStatus(SC_CREATED);
            res.getOutputStream().write(BODY.getBytes(UTF_8));
            // the body is held back until the transaction is known to have committed
            assertEquals(0, response.getContentAsByteArray().length);
        });

        assertEquals(Status.STATUS_COMMITTED, recorder.status);
        assertEquals(SC_CREATED, response.getStatus());
        assertEquals(BODY, response.getContentAsString());
        assertFalse(TransactionHelper.isTransactionActive());
    }

    @Test
    void testHandlerFailureRollsBackAndDropsTheBody() throws Exception {
        var recorder = new StatusRecorder();
        var response = new MockHttpServletResponse();

        var thrown = assertThrows(IllegalStateException.class, () -> doFilter(response, res -> {
            TransactionHelper.registerSynchronization(recorder);
            res.getOutputStream().write(BODY.getBytes(UTF_8));
            throw new IllegalStateException("boom");
        }));

        assertEquals("boom", thrown.getMessage());
        assertEquals(Status.STATUS_ROLLEDBACK, recorder.status);
        // nothing was sent, the container is free to render an error response
        assertEquals(0, response.getContentAsByteArray().length);
        assertFalse(TransactionHelper.isTransactionActive());
    }

    @Test
    void testRollbackOnlyStillSendsTheErrorBody() throws Exception {
        var recorder = new StatusRecorder();
        var response = new MockHttpServletResponse();
        doFilter(response, res -> {
            TransactionHelper.registerSynchronization(recorder);
            // anything may fail its own request without throwing
            TransactionHelper.setTransactionRollbackOnly();
            res.setStatus(SC_NOT_FOUND);
            res.getOutputStream().write(ERROR_BODY.getBytes(UTF_8));
        });

        assertEquals(Status.STATUS_ROLLEDBACK, recorder.status);
        assertEquals(SC_NOT_FOUND, response.getStatus());
        assertEquals(ERROR_BODY, response.getContentAsString());
    }

    @Test
    void testResolvedExceptionRollsBackButKeepsTheErrorBody() throws Exception {
        var recorder = new StatusRecorder();
        var request = new MockHttpServletRequest("GET", "/test");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            TransactionHelper.registerSynchronization(recorder);
            // what the dispatcher records once an @ExceptionHandler has turned a failure into an error response
            req.setAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE, new IllegalStateException("boom"));
            ((HttpServletResponse) res).setStatus(SC_NOT_FOUND);
            res.getOutputStream().write(ERROR_BODY.getBytes(UTF_8));
        });

        assertEquals(Status.STATUS_ROLLEDBACK, recorder.status);
        assertEquals(SC_NOT_FOUND, response.getStatus());
        assertEquals(ERROR_BODY, response.getContentAsString());
    }

    @Test
    void testDeliberateErrorStatusStillCommits() throws Exception {
        var recorder = new StatusRecorder();
        var response = new MockHttpServletResponse();
        doFilter(response, res -> {
            TransactionHelper.registerSynchronization(recorder);
            // a handler answering with an error status of its own accord did not fail: its work stands
            res.setStatus(SC_NOT_FOUND);
            res.getOutputStream().write(ERROR_BODY.getBytes(UTF_8));
        });

        assertEquals(Status.STATUS_COMMITTED, recorder.status);
        assertEquals(SC_NOT_FOUND, response.getStatus());
        assertEquals(ERROR_BODY, response.getContentAsString());
    }

    @Test
    void testCommitFailureDiscardsTheBufferedBody() throws Exception {
        var response = new MockHttpServletResponse();
        // as an outer filter writes it, before the request reaches this filter
        response.setHeader(CORS_HEADER, "*");

        var thrown = assertThrows(TransactionRuntimeException.class, () -> doFilter(response, res -> {
            expireRequestTransaction();
            res.setStatus(SC_CREATED);
            res.setContentType("application/json");
            res.setHeader(ETAG, "\"v1\"");
            res.getOutputStream().write(BODY.getBytes(UTF_8));
        }));

        assertEquals("Unable to commit: Transaction timeout", thrown.getMessage());
        // The client must not be told the request succeeded: the buffered body is dropped and the failure left to
        // propagate. Headers are kept, since they may hold what outer filters put there (CORS, security), but the
        // success status is replaced so a swallowed failure still doesn't read as one.
        assertEquals(0, response.getContentAsByteArray().length);
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
        // nothing describing the body that was dropped may outlive it and mislabel the error the container renders
        assertNull(response.getHeader(CONTENT_TYPE));
        assertNull(response.getHeader(CONTENT_LENGTH));
        assertNull(response.getHeader(ETAG));
        // but what an outer filter wrote about the exchange survives: the error dispatch does not run those again
        assertEquals("*", response.getHeader(CORS_HEADER));
        assertFalse(TransactionHelper.isTransactionActive());
    }

    @Test
    void testCommitFailureCannotDiscardAStreamedBody() throws Exception {
        filter.responseBufferSize = 8;
        var response = new MockHttpServletResponse();

        assertThrows(TransactionRuntimeException.class, () -> doFilter(response, res -> {
            expireRequestTransaction();
            res.getOutputStream().write(BODY.getBytes(UTF_8));
        }));

        // the body outgrew the buffer and had already been sent: the failure can only be logged
        assertEquals(BODY, response.getContentAsString());
    }

    @Test
    void testBodyBiggerThanTheBufferIsStreamedThrough() throws Exception {
        filter.responseBufferSize = 8;
        var response = new MockHttpServletResponse();
        doFilter(response, res -> {
            res.getOutputStream().write(BODY.getBytes(UTF_8));
            // too big to hold back, it went straight to the container
            assertEquals(BODY, response.getContentAsString());
        });

        assertEquals(BODY, response.getContentAsString());
    }

    @Test
    void testAnnouncedContentLengthBiggerThanTheBufferIsStreamedThrough() throws Exception {
        filter.responseBufferSize = 8;
        var response = new MockHttpServletResponse();
        doFilter(response, res -> {
            res.addHeader(CONTENT_LENGTH, String.valueOf(BODY.length()));
            res.getOutputStream().write(BODY.getBytes(UTF_8));
            assertEquals(BODY, response.getContentAsString());
        });

        assertEquals(BODY, response.getContentAsString());
        assertEquals(String.valueOf(BODY.length()), response.getHeader(CONTENT_LENGTH));
    }

    @Test
    void testContentLengthFittingTheBufferIsStillBuffered() throws Exception {
        var response = new MockHttpServletResponse();
        doFilter(response, res -> {
            res.setContentLength(BODY.length());
            res.setContentLengthLong(BODY.length());
            res.getOutputStream().write(BODY.getBytes(UTF_8));
            assertEquals(0, response.getContentAsByteArray().length);
        });

        assertEquals(BODY, response.getContentAsString());
        assertEquals(String.valueOf(BODY.length()), response.getHeader(CONTENT_LENGTH));
    }

    /** A blob download: {@code ResponseEntity} headers first, then the body from an asynchronous dispatch. */
    @Test
    void testAsynchronousBodyIsNotBuffered() throws Exception {
        var request = new MockHttpServletRequest("GET", "/test");
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            ((HttpServletResponse) res).addHeader(CONTENT_LENGTH, String.valueOf(BODY.length()));
            req.startAsync();
            res.getOutputStream().write(BODY.getBytes(UTF_8));
            // streamed straight to the container, nothing held in memory
            assertEquals(BODY, response.getContentAsString());
        });

        assertEquals(BODY, response.getContentAsString());
        assertEquals(String.valueOf(BODY.length()), response.getHeader(CONTENT_LENGTH));
        assertFalse(TransactionHelper.isTransactionActive());
    }

    @Test
    void testAmbientTransactionIsRestored() throws Exception {
        var ambient = new StatusRecorder();
        TransactionHelper.startTransaction();
        TransactionHelper.registerSynchronization(ambient);

        doFilter(new MockHttpServletResponse(), res -> assertTrue(TransactionHelper.isTransactionActive()));

        // the ambient transaction was committed to make room for the request one, and a fresh one put back
        assertEquals(Status.STATUS_COMMITTED, ambient.status);
        assertTrue(TransactionHelper.isTransactionActive());
    }

    protected void doFilter(MockHttpServletResponse response, Handler handler) throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/test"), response, (req, res) -> {
            try {
                handler.handle((HttpServletResponse) res);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
    }

    /** What the handler chain does with the (wrapped) response. */
    @FunctionalInterface
    protected interface Handler {
        void handle(HttpServletResponse response) throws IOException, InterruptedException;
    }

    /** Replaces the request transaction with one that will have timed out by the time the filter commits it. */
    protected void expireRequestTransaction() throws InterruptedException {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction(1);
        Thread.sleep(2000); // NOSONAR
    }

    /** Records the outcome of the transaction it is registered on. */
    protected static class StatusRecorder implements Synchronization {

        protected int status = -1;

        @Override
        public void beforeCompletion() {
            // nothing to do
        }

        @Override
        public void afterCompletion(int status) {
            this.status = status;
        }
    }

}
