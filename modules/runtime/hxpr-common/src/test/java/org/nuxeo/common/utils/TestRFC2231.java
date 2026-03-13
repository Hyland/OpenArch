/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Florent Guillaume
 *
 */

package org.nuxeo.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * @author Florent Guillaume
 */
public class TestRFC2231 {

    @Test
    public void testEncodeWithPercent() {
        StringBuilder sb = new StringBuilder();
        RFC2231.percentEscape(sb, "foo");
        assertEquals("foo", sb.toString());
        sb.setLength(0);
        RFC2231.percentEscape(sb, "foo bar");
        assertEquals("foo%20bar", sb.toString());
        sb.setLength(0);
        RFC2231.percentEscape(sb, "R\u00e9sultat d'Activit\u00e9 (;provisoire/draft).");
        assertEquals("R%C3%A9sultat%20d%27Activit%C3%A9%20%28%3Bprovisoire/draft%29.", sb.toString());
    }

    @Test
    public void testDecodeContentDispositionFilename() {
        assertEquals("foo", RFC2231.decodeContentDispositionFilename("foo"));
        assertEquals("foo bar", RFC2231.decodeContentDispositionFilename("foo%20bar"));
        assertEquals("R\u00e9sultat d'Activit\u00e9 (;provisoire/draft).",
            RFC2231.decodeContentDispositionFilename("R%C3%A9sultat%20d%27Activit%C3%A9%20%28%3Bprovisoire/draft%29."));
    }

    @Test
    public void testEncodeContentDisposition() {
        assertEquals("inline; filename=cafe", RFC2231.encodeContentDisposition("cafe", true));
        assertEquals("inline; filename=mydoc2.txt", RFC2231.encodeContentDisposition("mydoc2.txt", true));
        assertEquals("inline; filename*=UTF-8''caf%C3%A9", RFC2231.encodeContentDisposition("caf\u00e9", true));
        assertEquals("attachment; filename*=UTF-8''caf%C3%A9", RFC2231.encodeContentDisposition("caf\u00e9", false));
        assertEquals("attachment; filename*=UTF-8''R%C3%A9sultat%20d%27Activit%C3%A9%20%28%3Bprovisoire%2Fdraft%29.",
                RFC2231.encodeContentDisposition("R\u00e9sultat d'Activit\u00e9 (;provisoire/draft).", false));
        assertEquals("attachment; filename*=UTF-8''%E5%B9%B3%E4%BB%AE%E5%90%8D%20-%20good.txt",
                RFC2231.encodeContentDisposition("\u5e73\u4eee\u540d - good.txt", false));
        assertEquals(
                "attachment; filename*=UTF-8''%E3%83%8C%E3%82%AF%E3%82%BB%E3%82%AA%E3%83%BB%E3%82%B7%E3%82%99%E3%83%A3%E3%83%8F%E3%82%9A%E3%83%B3.txt",
                RFC2231.encodeContentDisposition(
                        "\u30cc\u30af\u30bb\u30aa\u30fb\u30b7\u3099\u30e3\u30cf\u309a\u30f3.txt", false));
    }

    @Test
    public void testGetFileNameFromContentDisposition() {
        assertEquals("cafe",
            RFC2231.getFileNameFromContentDisposition(RFC2231.encodeContentDisposition("cafe", true)));
        assertEquals("filename.jpg",
            RFC2231.getFileNameFromContentDisposition(
                "form-data; name=\"fieldName\"; filename=\"filename.jpg\""));
        assertEquals("mydoc2.txt",
            RFC2231.getFileNameFromContentDisposition(RFC2231.encodeContentDisposition("mydoc2.txt", true)));
        assertEquals("caf\u00e9",
            RFC2231.getFileNameFromContentDisposition(RFC2231.encodeContentDisposition("caf\u00e9", true)));
        assertEquals("caf\u00e9",
            RFC2231.getFileNameFromContentDisposition(RFC2231.encodeContentDisposition("caf\u00e9", false)));
        assertEquals("caf\u00e9",
            RFC2231.getFileNameFromContentDisposition("inline; filename*=UTF-8''caf%C3%A9"));
        assertEquals("caf\u00e9",
            RFC2231.getFileNameFromContentDisposition("attachment; filename*=UTF-8''caf%C3%A9"));
        assertEquals("This is ***fun***",
            RFC2231.getFileNameFromContentDisposition(
                "Content-Disposition: application/x-stuff;filename*=us-ascii'en-us'This%20is%20%2A%2A%2Afun%2A%2A%2A"));
        assertEquals("R\u00e9sultat d'Activit\u00e9 (;provisoire/draft).",
            RFC2231.getFileNameFromContentDisposition(
                RFC2231.encodeContentDisposition(
                    "R\u00e9sultat d'Activit\u00e9 (;provisoire/draft).", false)));
        assertEquals("\u5e73\u4eee\u540d - good.txt",
            RFC2231.getFileNameFromContentDisposition(RFC2231.encodeContentDisposition("\u5e73\u4eee\u540d - good.txt", false)));
        assertEquals(
            "\u30cc\u30af\u30bb\u30aa\u30fb\u30b7\u3099\u30e3\u30cf\u309a\u30f3.txt",
            RFC2231.getFileNameFromContentDisposition(RFC2231.encodeContentDisposition(
                "\u30cc\u30af\u30bb\u30aa\u30fb\u30b7\u3099\u30e3\u30cf\u309a\u30f3.txt", false)));
        assertNull(RFC2231.getFileNameFromContentDisposition("some random text"));
        assertThrows(IllegalArgumentException.class,
            () -> RFC2231.decodeContentDispositionFilename("%ZA%2Afun%2A%2A%ZAR\u00e9sultat "));
    }

}
