/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.upload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadStatusTest {

    @Test
    void testParseWithNull() {
        assertEquals(UploadStatus.INCOMPLETE, UploadStatus.parse(null));
    }

    @Test
    void testParseWithValidValues() {
        assertEquals(UploadStatus.INCOMPLETE, UploadStatus.parse("INCOMPLETE"));
        assertEquals(UploadStatus.PROCESSING, UploadStatus.parse("PROCESSING"));
        assertEquals(UploadStatus.READY, UploadStatus.parse("READY"));
        assertEquals(UploadStatus.ERROR, UploadStatus.parse("ERROR"));
    }

    @Test
    void testParseWithNonString() {
        assertEquals(UploadStatus.INCOMPLETE, UploadStatus.parse(new Object()));
    }

    @Test
    void testParseWithInvalidValue() {
        assertEquals(UploadStatus.INCOMPLETE, UploadStatus.parse("INVALID"));
    }

    @Test
    void testParseWithEmptyString() {
        assertEquals(UploadStatus.INCOMPLETE, UploadStatus.parse(""));
    }
}
