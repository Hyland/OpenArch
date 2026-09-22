/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class TestFileUtils {

    @Test
    public void testReadFromStreamWithPredefinedData() throws IOException {
        final byte[] data = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 123, 3, 3, 4, 5, 2, 13, 34, 5, 56, 31, 34, 5, 65, 3, 4, 34,
                3, 4, 34, 34, 24, 3, 1, 65, 67, 68, 7, 58, 7, 8, 75, 98, 7, 9, 5, 7, 45, 7, 43, 6, };

        final InputStream is = new ByteArrayInputStream(data);
        final byte[] readData = IOUtils.toByteArray(is);

        assertEquals(data.length, readData.length);
        assertTrue(Arrays.equals(data, readData));
    }

    @Test
    public void testReadFromStreamWithGeneratedData() throws IOException {
        int n = 10000;
        final byte[] data = new byte[n];
        for (int i = 0; i < n; i++) {
            data[i] = (byte) (i % 256);
        }
        final InputStream is = new ByteArrayInputStream(data);
        final byte[] readData = IOUtils.toByteArray(is);

        assertEquals(data.length, readData.length);
        assertTrue(Arrays.equals(data, readData));
    }

    @Test
    public void testReadFromStreamWithLongGeneratedData() throws IOException {
        int n = 1000000; // 1M
        final byte[] data = new byte[n];
        for (int i = 0; i < n; i++) {
            data[i] = (byte) (i % 256);
        }
        final InputStream is = new ByteArrayInputStream(data);
        final byte[] readData = IOUtils.toByteArray(is);

        assertEquals(data.length, readData.length);
        assertTrue(Arrays.equals(data, readData));
    }

    @Test
    public void testReadFromStreamWithVLongGeneratedData() throws IOException {
        int n = 0xFFFFFF; // 16M
        final byte[] data = new byte[n];
        for (int i = 0; i < n; i++) {
            data[i] = (byte) (i % 256);
        }
        final InputStream is = new ByteArrayInputStream(data);
        final byte[] readData = IOUtils.toByteArray(is);

        assertEquals(data.length, readData.length);
        assertTrue(Arrays.equals(data, readData));
    }

    @Test
    public void testGetRessourceFromUrl() {
        // testing resources contained in paths with space
        String testFilename = FileUtils.getResourcePathFromContext("test-xmap.xml");
        assertNotEquals("", testFilename);

        // TODO: create a temp file with blank
        // the main problem is to access it with a getResource
        // so that the blanks are encoded
        File file = FileUtils.getResourceFileFromContext("test-xmap.xml");
        assertNotNull(file);
    }

}
