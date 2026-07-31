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
package org.nuxeo.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TestBlobKey {

    @Test
    public void testOf() {
        BlobKey bk = BlobKey.of("abc", "v1");
        assertEquals("abc", bk.key());
        assertEquals("v1", bk.versionId());

        BlobKey noVersion = BlobKey.of("abc", null);
        assertEquals("abc", noVersion.key());
        assertNull(noVersion.versionId());
    }

    @Test
    public void testParseWithoutVersion() {
        BlobKey bk = BlobKey.parse("abc");
        assertEquals("abc", bk.key());
        assertNull(bk.versionId());
    }

    @Test
    public void testParseWithVersion() {
        BlobKey bk = BlobKey.parse("abc@v1");
        assertEquals("abc", bk.key());
        assertEquals("v1", bk.versionId());
    }

    @Test
    public void testParseKeepsSeparatorsInVersion() {
        // only the first separator splits, the rest belongs to the version id
        BlobKey bk = BlobKey.parse("abc@v1@v2");
        assertEquals("abc", bk.key());
        assertEquals("v1@v2", bk.versionId());
    }

    @Test
    public void testParseEmptyVersion() {
        BlobKey bk = BlobKey.parse("abc@");
        assertEquals("abc", bk.key());
        assertEquals("", bk.versionId());
    }

    @Test
    public void testParseSeparatorAtStart() {
        BlobKey bk = BlobKey.parse("@v1");
        assertEquals("", bk.key());
        assertEquals("v1", bk.versionId());
    }

    @Test
    public void testParseUseVersionFalse() {
        // the whole blob key is the key, even if it contains a separator
        BlobKey bk = BlobKey.parse("abc@v1", false);
        assertEquals("abc@v1", bk.key());
        assertNull(bk.versionId());
    }

    @Test
    public void testParseUseVersionTrue() {
        BlobKey bk = BlobKey.parse("abc@v1", true);
        assertEquals("abc", bk.key());
        assertEquals("v1", bk.versionId());
    }

    @Test
    public void testToStringWithoutVersion() {
        assertEquals("abc", BlobKey.of("abc", null).toString());
    }

    @Test
    public void testToStringWithVersion() {
        assertEquals("abc@v1", BlobKey.of("abc", "v1").toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc", "abc@v1", "abc@", "abc@v1@v2", "@v1" })
    public void testRoundTrip(String blobKey) {
        assertEquals(blobKey, BlobKey.parse(blobKey).toString());
    }
}
