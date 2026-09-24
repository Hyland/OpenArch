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

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_ENCODING;
import static org.springframework.http.HttpHeaders.CONTENT_LANGUAGE;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.CONTENT_RANGE;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpHeaders.LAST_MODIFIED;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Holds the response body back until {@link TransactionFilter} has committed the request transaction, so that a
 * commit failure can still be turned into an error response instead of being reported to the client as a success.
 * <p>
 * Buffering is bounded and best-effort. It is abandoned, and the body written straight through to the wrapped
 * response, as soon as any of the following happens:
 * <ul>
 * <li>the response announces a {@code Content-Length} bigger than the buffer size,</li>
 * <li>more than the buffer size has been written,</li>
 * <li>the body is written from an asynchronous dispatch, which is how blob downloads
 * ({@code StreamingResponseBody}) stream their content.</li>
 * </ul>
 * Downloads therefore never pay for the buffer, and no response can pin more than the buffer size in memory. Once
 * buffering has been abandoned the response commits as usual and its body can no longer be replaced, so a commit
 * failure is only logged, exactly as it would be without any buffering at all.
 * <p>
 * {@link #getWriter()} is inherited unchanged from {@link ContentCachingResponseWrapper}, which buffers it without a
 * size bound; nothing in the REST layers writes a response body through the writer.
 */
public class BufferedResponseWrapper extends ContentCachingResponseWrapper {

    protected final HttpServletRequest request;

    protected final int bufferSize;

    protected ServletOutputStream bufferedStream;

    /** Written by the request thread and by the asynchronous dispatch that may take the response over. */
    protected volatile boolean bufferAbandoned;

    public BufferedResponseWrapper(HttpServletRequest request, HttpServletResponse response, int bufferSize) {
        super(response);
        this.request = request;
        this.bufferSize = bufferSize;
    }

    protected HttpServletResponse httpResponse() {
        return (HttpServletResponse) getResponse();
    }

    /**
     * Headers that describe the body rather than the exchange, matched case-insensitively. They must not outlive the
     * body they describe.
     * <p>
     * These are the representation metadata and validator fields of
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-8">RFC 9110 section 8</a>, plus {@code Content-Range}
     * (<a href="https://www.rfc-editor.org/rfc/rfc9110#section-14.4">RFC 9110 section 14.4</a>, under range requests
     * rather than section 8) and {@code Content-Disposition}, which RFC 9110 does not define at all
     * (<a href="https://www.rfc-editor.org/rfc/rfc6266">RFC 6266</a>).
     * <p>
     * {@code Content-Length} is listed for completeness only: the cache holds it back and it never reaches the
     * wrapped response until {@link #copyBodyToResponse()} writes the body out.
     */
    protected static final Set<String> REPRESENTATION_HEADERS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        REPRESENTATION_HEADERS.addAll(List.of(CONTENT_TYPE, CONTENT_LENGTH, CONTENT_ENCODING, CONTENT_LANGUAGE,
                CONTENT_LOCATION, CONTENT_DISPOSITION, CONTENT_RANGE, ETAG, LAST_MODIFIED));
    }

    /**
     * Whether the response body is still entirely buffered, i.e. whether {@link #reset()} can still discard it and
     * let a different response be written in its place.
     */
    public boolean isBodyReplaceable() {
        return !bufferAbandoned && !request.isAsyncStarted() && !getResponse().isCommitted();
    }

    /**
     * Throws the buffered body away, along with the headers that described it, so that a different response can be
     * written in its place. Headers outer filters wrote about the exchange itself (CORS, security) are kept: the
     * container renders its error page from an error dispatch, which the filters that produce those skip.
     * <p>
     * Implemented as a {@link #reset()} that puts back what is worth keeping, because the servlet API has no way to
     * remove a single header and {@code setHeader(name, null)} is not a portable substitute for one.
     */
    public void discardBody() {
        Map<String, List<String>> kept = new LinkedHashMap<>();
        for (String name : getHeaderNames()) {
            if (!REPRESENTATION_HEADERS.contains(name)) {
                kept.put(name, new ArrayList<>(getHeaders(name)));
            }
        }
        reset();
        kept.forEach((name, values) -> values.forEach(value -> addHeader(name, value)));
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the request has gone asynchronous the body is not complete: the dispatch that streams it owns the response
     * from here on, so buffering is only given up, which lets what has been buffered out ahead of the rest.
     */
    @Override
    public void copyBodyToResponse() throws IOException {
        if (request.isAsyncStarted()) {
            abandonBuffer();
        } else {
            super.copyBodyToResponse();
        }
    }

    /**
     * Stops buffering: whatever has been buffered so far is written to the wrapped response, and everything
     * written from now on goes straight through to it.
     * <p>
     * Synchronized because the request thread and the asynchronous dispatch it handed the response to can both get
     * here, and the buffer must be written out exactly once.
     */
    protected synchronized void abandonBuffer() throws IOException {
        if (bufferAbandoned) {
            return;
        }
        // a Content-Length held back by the cache is only written along with a non-empty body
        String contentLength = getHeader(CONTENT_LENGTH);
        bufferAbandoned = true;
        // not complete: what follows is still to be written, so don't derive a Content-Length from the buffer
        copyBodyToResponse(false);
        if (contentLength != null && httpResponse().getHeader(CONTENT_LENGTH) == null) {
            httpResponse().setContentLengthLong(Long.parseLong(contentLength));
        }
    }

    @SuppressWarnings("resource") // not ours to close
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (bufferedStream == null) {
            bufferedStream = new BufferedOutputStream(super.getOutputStream());
        }
        return bufferedStream;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (bufferAbandoned) {
            getResponse().flushBuffer();
        }
        // else no-op, like ContentCachingResponseWrapper: the body is written out by TransactionFilter once the
        // transaction has committed
    }

    // ContentCachingResponseWrapper grows its buffer to the announced Content-Length and rejects anything above
    // Integer.MAX_VALUE. A body that big is never going to be buffered, so let it bypass the buffer entirely.

    @Override
    public void setContentLength(int len) {
        if (bypassBuffer(len)) {
            httpResponse().setContentLength(len);
        } else {
            super.setContentLength(len);
        }
    }

    @Override
    public void setContentLengthLong(long len) {
        if (bypassBuffer(len)) {
            httpResponse().setContentLengthLong(len);
        } else {
            // ContentCachingResponseWrapper narrows this back to setContentLength(int), which lands on the
            // override above and takes the non-bypassing branch again; it must not delegate here in turn
            super.setContentLengthLong(len);
        }
    }

    @Override
    public void setHeader(String name, String value) {
        if (bypassBufferForContentLength(name, parseContentLength(value))) {
            httpResponse().setHeader(name, value);
        } else {
            super.setHeader(name, value);
        }
    }

    @Override
    public void addHeader(String name, String value) {
        if (bypassBufferForContentLength(name, parseContentLength(value))) {
            httpResponse().addHeader(name, value);
        } else {
            super.addHeader(name, value);
        }
    }

    @Override
    public void setIntHeader(String name, int value) {
        if (bypassBufferForContentLength(name, value)) {
            httpResponse().setIntHeader(name, value);
        } else {
            super.setIntHeader(name, value);
        }
    }

    @Override
    public void addIntHeader(String name, int value) {
        if (bypassBufferForContentLength(name, value)) {
            httpResponse().addIntHeader(name, value);
        } else {
            super.addIntHeader(name, value);
        }
    }

    /** Abandons buffering if the announced body is too big for it, and tells whether buffering is off. */
    protected boolean bypassBuffer(long contentLength) {
        if (!bufferAbandoned && contentLength > bufferSize) {
            try {
                abandonBuffer();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return bufferAbandoned;
    }

    /**
     * Same as {@link #bypassBuffer(long)} when the header is the {@code Content-Length} one, {@code false} for any
     * other header: the buffer only cares about how big the body is announced to be.
     */
    protected boolean bypassBufferForContentLength(String name, long contentLength) {
        return CONTENT_LENGTH.equalsIgnoreCase(name) && bypassBuffer(contentLength);
    }

    /** Parses a {@code Content-Length} header value, {@code -1} if it is absent or malformed. */
    protected static long parseContentLength(String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1; // let the wrapped response deal with the malformed value
        }
    }

    /**
     * Output stream that caches what it is given until the cache is full or the request goes asynchronous, and
     * writes straight to the wrapped response from then on.
     */
    protected class BufferedOutputStream extends ServletOutputStream {

        protected final ServletOutputStream stream;

        protected BufferedOutputStream(ServletOutputStream stream) {
            this.stream = stream;
        }

        @Override
        @SuppressWarnings("resource") // target() hands back a stream owned by the wrapped response, never ours to close
        public void write(int b) throws IOException {
            target(1).write(b);
        }

        @Override
        @SuppressWarnings("resource") // target() hands back a stream owned by the wrapped response, never ours to close
        public void write(byte[] b, int off, int len) throws IOException {
            target(len).write(b, off, len);
        }

        protected OutputStream target(int len) throws IOException {
            if (!bufferAbandoned && (request.isAsyncStarted() || getContentSize() + len > bufferSize)) {
                abandonBuffer();
            }
            return bufferAbandoned ? getResponse().getOutputStream() : stream;
        }

        @SuppressWarnings("resource") // not ours to close
        @Override
        public void flush() throws IOException {
            if (bufferAbandoned) {
                getResponse().getOutputStream().flush();
            }
            // else no-op, the buffered body is written out once the transaction has committed
        }

        @Override
        public boolean isReady() {
            return stream.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            stream.setWriteListener(writeListener);
        }
    }

}
