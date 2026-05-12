/*
 * (C) Copyright 2023 Hyland (http://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 */
package org.nuxeo.ecm.core.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.UTF_8;

import org.junit.jupiter.api.Test;

public class BlobUtilsTest {

    @Test
    public void testContentTypeHelpers() {
        // text content types
        assertEquals(TEXT_PLAIN, BlobUtils.getMimeType(TEXT_PLAIN)); // NOSONAR
        assertNull(BlobUtils.getCharset(TEXT_PLAIN));

        assertEquals(TEXT_PLAIN, BlobUtils.getMimeType("text/plain; charset=UTF-8"));
        assertEquals(UTF_8, BlobUtils.getCharset("text/plain; charset=UTF-8")); // NOSONAR

        assertEquals(TEXT_PLAIN, BlobUtils.getMimeType("text/plain ; CHARSET = UTF-8 ; foo=bar"));
        assertEquals(UTF_8, BlobUtils.getCharset("text/plain ; CHARSET = UTF-8 ; foo=bar"));

        // PDF content types
        assertEquals("application/pdf", BlobUtils.getMimeType("application/pdf")); // NOSONAR
        assertNull(BlobUtils.getCharset("application/pdf"));

        assertEquals("application/pdf", BlobUtils.getMimeType("application/pdf; charset=UTF-8"));
        assertEquals(UTF_8, BlobUtils.getCharset("application/pdf; charset=UTF-8"));
    }
}
