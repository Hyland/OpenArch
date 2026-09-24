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

import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import java.io.IOException;

import com.hyland.openarch.runtime.springboot.auth.RepositoryLoginFilter;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Opens a single transaction around the whole request and finishes it on the way out: commit on success, rollback on
 * failure. This gives every request exactly one transaction boundary, replacing the per-method {@code @Transactional}
 * interceptor for the common case.
 * <p>
 * A failure is signalled in one of two ways:
 * <ul>
 * <li>an exception escapes the handler chain entirely and is caught here, or</li>
 * <li>an exception handler turned one into an error response, which {@link DispatcherServlet} records in
 * {@link DispatcherServlet#EXCEPTION_ATTRIBUTE}.</li>
 * </ul>
 * An error status a handler returns deliberately, <em>without</em> throwing, is not a failure and commits.
 * The commit happens after the handler has rendered its response, so the response is buffered
 * ({@link BufferedResponseWrapper}) until the commit is known to have succeeded; otherwise a request whose changes
 * were rolled back at commit time (transaction timeout, concurrent update) would report success to the client. See
 * {@link #endRequestTransaction} for the cases where the body is too big to hold back.
 * <p>
 * Endpoints that need a different transaction timeout can replace the request transaction themselves (for example by
 * committing it and starting a new one with a custom timeout); this filter simply commits whatever transaction is
 * active when the request returns.
 * <p>
 * Ordered to run just inside {@link RepositoryLoginFilter} so the transaction commits
 * while the user is still logged in.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TransactionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TransactionFilter.class);

    /** Default value of {@link #responseBufferSize}. */
    public static final int DEFAULT_RESPONSE_BUFFER_SIZE = 64 * 1024; // 64 KB

    /**
     * Maximum number of response bytes held in memory while waiting for the request transaction to commit. Bodies
     * bigger than this, and bodies streamed from an asynchronous dispatch, are written straight through to the
     * wrapped response instead.
     */
    @Value("${hxpr.transaction.response.buffer.size:" + DEFAULT_RESPONSE_BUFFER_SIZE + "}")
    protected int responseBufferSize = DEFAULT_RESPONSE_BUFFER_SIZE;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Give the request its own transaction boundary even when a transaction is already active
        // (e.g. an ambient transaction started by a test harness). In that case commit it first,
        // run the request in a fresh transaction, then restore an ambient transaction afterwards.
        boolean txActive = TransactionHelper.isTransactionActive();
        boolean txStarted = false;
        boolean completedAbruptly = true;
        BufferedResponseWrapper buffered = new BufferedResponseWrapper(request, response, responseBufferSize);
        try {
            if (txActive) {
                TransactionHelper.commitOrRollbackTransaction();
            }
            txStarted = TransactionHelper.startTransaction();
            chain.doFilter(request, buffered);
            if (request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE) != null) {
                // The dispatcher resolved an exception into an error response, so the request failed even though
                // nothing propagated up to here. (Recorded for handlers that write the response themselves, which
                // is what returning a ResponseEntity from an @ExceptionHandler does.)
                TransactionHelper.setTransactionRollbackOnly();
            }
            completedAbruptly = false;
        } finally {
            try {
                endRequestTransaction(buffered, txStarted, completedAbruptly);
            } finally {
                if (txActive) {
                    // restore an ambient transaction for whatever started it
                    TransactionHelper.startTransaction();
                }
            }
        }
    }

    /**
     * Ends the request transaction, then releases the buffered response body.
     * <p>
     * If the commit fails, the buffered body is discarded and the failure rethrown, so that the container renders an
     * error response for a request whose changes were in fact rolled back. That is only possible while the body is
     * still {@linkplain BufferedResponseWrapper#isBodyReplaceable() replaceable}: a body that outgrew the buffer,
     * or one streamed asynchronously, has already reached the client and the failure can only be logged.
     */
    protected void endRequestTransaction(BufferedResponseWrapper buffered, boolean txStarted,
            boolean completedAbruptly) throws IOException {
        if (txStarted) {
            if (completedAbruptly) {
                TransactionHelper.setTransactionRollbackOnly();
            }
            try {
                TransactionHelper.commitOrRollbackTransaction();
            } catch (RuntimeException e) {
                if (completedAbruptly) {
                    // an exception is already on its way out, don't mask it with this one
                    log.error("Failed to roll back the request transaction", e);
                    return;
                }
                if (buffered.isBodyReplaceable()) {
                    // The container overwrites the status when it renders its error page; setting it here too keeps
                    // the response honest should anything swallow the failure before it gets there.
                    buffered.discardBody();
                    buffered.setStatus(SC_INTERNAL_SERVER_ERROR);
                } else {
                    log.error("Request transaction failed to commit but its response had already been sent,"
                            + " the client was told the request succeeded", e);
                }
                throw e;
            }
        }
        if (completedAbruptly) {
            // an exception is on its way out, leave the body buffered: the container renders the error response
            return;
        }
        buffered.copyBodyToResponse();
    }

}
