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
package com.hyland.openarch.ecm.core.io.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.blob.ByteRange;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for {@link DownloadHelper}.
 */
public class TestDownloadHelper {

    /** IOException whose simple name matches the one {@link DownloadHelper} looks for. */
    private static class EofException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    @Test
    public void testParseRangeFull() {
        ByteRange range = DownloadHelper.parseRange("bytes=0-99", 1000);
        assertEquals(0, range.getStart());
        assertEquals(99, range.getEnd());
    }

    @Test
    public void testParseRangeOpenEnd() {
        ByteRange range = DownloadHelper.parseRange("bytes=100-", 1000);
        assertEquals(100, range.getStart());
        assertEquals(999, range.getEnd());
    }

    @Test
    public void testParseRangeSuffix() {
        ByteRange range = DownloadHelper.parseRange("bytes=-100", 1000);
        assertEquals(900, range.getStart());
        assertEquals(999, range.getEnd());
    }

    @Test
    public void testParseRangeSuffixLargerThanLength() {
        ByteRange range = DownloadHelper.parseRange("bytes=-5000", 1000);
        assertEquals(0, range.getStart());
        assertEquals(999, range.getEnd());
    }

    @Test
    public void testParseRangeWrongPrefix() {
        assertNull(DownloadHelper.parseRange("0-99", 1000));
    }

    @Test
    public void testParseRangeMultipleRangesUnsupported() {
        assertNull(DownloadHelper.parseRange("bytes=0-99,200-299", 1000));
    }

    @Test
    public void testParseRangeMissingDash() {
        assertNull(DownloadHelper.parseRange("bytes=100", 1000));
    }

    @Test
    public void testParseRangeEmptyStartAndEnd() {
        assertNull(DownloadHelper.parseRange("bytes=-", 1000));
    }

    @Test
    public void testParseRangeStartAfterEnd() {
        assertNull(DownloadHelper.parseRange("bytes=500-100", 1000));
    }

    @Test
    public void testParseRangeNotANumber() {
        assertNull(DownloadHelper.parseRange("bytes=abc-def", 1000));
    }

    @Test
    public void testGetRFC2231ContentDispositionNoInline() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertEquals("attachment; filename=file.txt",
                DownloadHelper.getRFC2231ContentDisposition(request, "file.txt"));
    }

    @Test
    public void testGetRFC2231ContentDispositionInlineParam() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(DownloadHelper.INLINE)).thenReturn("true");
        assertEquals("inline; filename=file.txt",
                DownloadHelper.getRFC2231ContentDisposition(request, "file.txt"));
    }

    @Test
    public void testGetRFC2231ContentDispositionInlineParamFalse() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(DownloadHelper.INLINE)).thenReturn("false");
        assertEquals("attachment; filename=file.txt",
                DownloadHelper.getRFC2231ContentDisposition(request, "file.txt"));
    }

    @Test
    public void testGetRFC2231ContentDispositionInlineAttribute() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(DownloadHelper.INLINE)).thenReturn(null);
        when(request.getAttribute(DownloadHelper.INLINE)).thenReturn("true");
        assertEquals("inline; filename=file.txt",
                DownloadHelper.getRFC2231ContentDisposition(request, "file.txt"));
    }

    @Test
    public void testGetRFC2231ContentDispositionExplicitInline() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertEquals("inline; filename=file.txt",
                DownloadHelper.getRFC2231ContentDisposition(request, "file.txt", Boolean.TRUE));
    }

    @Test
    public void testGetRFC2231ContentDispositionExplicitAttachment() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertEquals("attachment; filename=file.txt",
                DownloadHelper.getRFC2231ContentDisposition(request, "file.txt", Boolean.FALSE));
    }

    @Test
    public void testGetContentTypeHeaderNoContentType() {
        Blob blob = mock(Blob.class);
        when(blob.getMimeType()).thenReturn(null);
        assertNull(DownloadHelper.getContentTypeHeader(blob));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "application/pdf, null, application/pdf",
            "text/plain, ISO-8859-1, text/plain; charset=ISO-8859-1",
            "'text/plain; charset=US-ASCII', UTF-8, 'text/plain; charset=UTF-8'" })
    public void testGetContentTypeHeader(String mimeType, String encoding, String expected) {
        Blob blob = mock(Blob.class);
        when(blob.getMimeType()).thenReturn(mimeType);
        when(blob.getEncoding()).thenReturn(encoding);
        assertEquals(expected, DownloadHelper.getContentTypeHeader(blob));
    }

    @Test
    public void testIsClientAbortErrorNull() {
        assertFalse(DownloadHelper.isClientAbortError(null));
    }

    @Test
    public void testIsClientAbortErrorEof() {
        assertTrue(DownloadHelper.isClientAbortError(new EofException()));
    }

    @Test
    public void testIsClientAbortErrorPlainIOException() {
        assertFalse(DownloadHelper.isClientAbortError(new IOException("boom")));
    }

    @Test
    public void testIsClientAbortErrorNested() {
        Throwable t = new RuntimeException("wrapper", new EofException());
        assertTrue(DownloadHelper.isClientAbortError(t));
    }

    @Test
    public void testIsClientAbortErrorNonIOException() {
        assertFalse(DownloadHelper.isClientAbortError(new RuntimeException("boom")));
    }

    @Test
    public void testIsClientAbortErrorDeeperThanLoopGuard() {
        // a cause chain longer than the internal 20-iteration guard, without any EofException
        IOException t = new IOException("root");
        for (int i = 0; i < 25; i++) {
            t = new IOException("wrap-" + i, t);
        }
        assertFalse(DownloadHelper.isClientAbortError(t));
    }

    @Test
    public void testHandleClientDisconnectClientAbort() {
        // must not throw for a client disconnect
        assertDoesNotThrow(() -> DownloadHelper.handleClientDisconnect(new EofException()));
    }

    @Test
    public void testHandleClientDisconnectRethrows() {
        IOException e = new IOException("unexpected");
        assertThrows(IOException.class, () -> DownloadHelper.handleClientDisconnect(e));
    }

    @Test
    public void testLogClientAbort() {
        // exercises the logging path and unwrapException over a cause chain
        assertDoesNotThrow(() -> DownloadHelper.logClientAbort(new RuntimeException("wrapper", new EofException())));
    }
}
