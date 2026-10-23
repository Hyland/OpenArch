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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

class BufferedResponseWrapperTest {

    protected static final String BODY = "blob content";

    /**
     * A blob download: the request thread hands the response over to the asynchronous dispatch that streams the body.
     * The dispatch must not write, and so let the container commit the response, while the request thread is still
     * setting the {@code Content-Length} it held back: the container's headers are not thread-safe.
     */
    @Test
    void testAsynchronousWriteWaitsForTheHandOver() throws Exception {
        var request = new MockHttpServletRequest("GET", "/download");
        request.setAsyncSupported(true);
        var inHandOver = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var contentLengthAtFirstWrite = new AtomicReference<String>();
        var response = new MockHttpServletResponse() {

            @Override
            public void setContentLengthLong(long len) {
                inHandOver.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.setContentLengthLong(len);
            }

            @Override
            public ServletOutputStream getOutputStream() {
                ServletOutputStream stream = super.getOutputStream();
                return new ServletOutputStream() {

                    @Override
                    public void write(int b) throws IOException {
                        contentLengthAtFirstWrite.compareAndSet(null, String.valueOf(getHeader(CONTENT_LENGTH)));
                        stream.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
        var wrapper = new BufferedResponseWrapper(request, response, 64 * 1024);
        wrapper.setContentLengthLong(BODY.length());
        request.startAsync();
        ServletOutputStream out = wrapper.getOutputStream();

        var handOver = new Thread(() -> {
            try {
                wrapper.copyBodyToResponse();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        handOver.start();
        assertTrue(inHandOver.await(10, TimeUnit.SECONDS));

        var asyncWriter = new Thread(() -> {
            try {
                out.write(BODY.getBytes(UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        asyncWriter.start();
        // either the writer waits for the hand-over, or it raced ahead and is done
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (asyncWriter.isAlive() && asyncWriter.getState() != Thread.State.BLOCKED
                && System.nanoTime() < deadline) {
            Thread.sleep(10); // NOSONAR
        }
        release.countDown();
        handOver.join(10_000);
        asyncWriter.join(10_000);

        assertEquals(String.valueOf(BODY.length()), contentLengthAtFirstWrite.get());
        assertEquals(BODY, response.getContentAsString());
    }

}
