/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hyland.openarch.ecm.core.blob.binary.BlobKeySlot;

public class TestPathStrategies {

    private String stringify(Path file) {
        return file.toString().replace(File.separatorChar, '/');
    }

    @Test
    public void testSafeRegex() throws IOException {
        String rejected = "";
        for (char c = ' '; c <= '~'; c++) {
            if (!PathStrategy.SAFE.matcher(String.valueOf(c)).matches()) {
                rejected += c;
            }
        }
        assertEquals("%/:\\", rejected);
    }

    @Test
    public void testSafePath() throws IOException {
        Path dir = Files.createTempDirectory("tmp_");
        PathStrategy ps = new PathStrategyFlat(dir);

        // unchanged
        for (String key : Arrays.asList( //
                "foo.doc", //
                "hello world.bin" //
                )) {
            assertEquals(key, ps.safePath(key));
            assertEquals(key, ps.safePathInverse(key));
        }

        // encoded
        String[] table = new String[] {
                ".", "%.", //
                "..", "%..", //
                "a/b", "%a%2fb", //
                "a\\b", "%a%5cb", //
                "100%", "%100%25", //
                "50:50", "%50%3a50", //
                "caf\u00e9", "%caf%c3%a9" //
        };
        for (int i = 0; i < table.length; i += 2) {
            String key = table[i];
            String expected = table[i+1];
            assertEquals(expected, ps.safePath(key));
            assertEquals(key, ps.safePathInverse(expected));
        }
    }

    @Test
    public void testSafePathInverseInvalid() throws IOException {
        Path dir = Files.createTempDirectory("tmp_");
        PathStrategy ps = new PathStrategyFlat(dir);
        assertNull(ps.safePathInverse("%foo%"));
        assertNull(ps.safePathInverse("%foo%0"));
        assertNull(ps.safePathInverse("%foo%xy"));
    }

    @Test
    public void testPathStrategyFlat() throws IOException {
        Path dir = Files.createTempDirectory("tmp_");
        PathStrategy ps = new PathStrategyFlat(dir);

        // valid key
        String path = stringify(ps.getPathForKey("123456789"));
        assertTrue(path.endsWith("/123456789"), path);

        // encoded key
        path = stringify(ps.getPathForKey("../foo"));
        assertTrue(path.endsWith("/%..%2ffoo"), path);
    }

    @Test
    public void testPathStrategySubDirs() throws IOException {
        Path dir = Files.createTempDirectory("tmp_");
        PathStrategy ps = new PathStrategySubDirs(dir, 2);

        // valid key
        String path = stringify(ps.getPathForKey("123456789"));
        assertTrue(path.endsWith("/12/34/123456789"), path);

        // short key
        path = stringify(ps.getPathForKey("abc"));
        assertTrue(path.endsWith("/000/abc"), path);

        // encoded key
        path = stringify(ps.getPathForKey("bad/key"));
        assertTrue(path.endsWith("/%b/ad/%bad%2fkey"), path);
    }

    @Test
    public void testPathPrefixesForSlot() throws IOException {
        Path dir = Files.createTempDirectory("tmp_");
        PathStrategy flat = new PathStrategyFlat(dir);
        assertEquals(List.of(""), flat.getPathPrefixesForSlot(BlobKeySlot.ALL));
        assertEquals(List.of(""), flat.getPathPrefixesForSlot(BlobKeySlot.other(1)));
        assertEquals(List.of("a", "%a"), flat.getPathPrefixesForSlot(new BlobKeySlot("a")));
        assertEquals("abc", flat.getPathPrefixForKeyPrefix("abc"));

        PathStrategy subDirs1 = new PathStrategySubDirs(dir, 1);
        assertEquals(List.of(""), subDirs1.getPathPrefixesForSlot(BlobKeySlot.ALL));
        assertEquals(List.of(""), subDirs1.getPathPrefixesForSlot(BlobKeySlot.other(2)));
        assertEquals(List.of("a", "%a/%a", "000/"), subDirs1.getPathPrefixesForSlot(new BlobKeySlot("a")));
        assertEquals(List.of("ab/ab", "%a/%ab", "000/"), subDirs1.getPathPrefixesForSlot(new BlobKeySlot("ab")));
        assertEquals("ab/abc", subDirs1.getPathPrefixForKeyPrefix("abc"));
        // the short keys directory is already covered
        assertEquals(List.of("0", "%0/%0"), subDirs1.getPathPrefixesForSlot(new BlobKeySlot("0")));
        assertEquals(List.of("00/00", "%0/%00", "000/"), subDirs1.getPathPrefixesForSlot(new BlobKeySlot("00")));

        PathStrategy subDirs2 = new PathStrategySubDirs(dir, 2);
        assertEquals(List.of("a", "%a/", "000/"), subDirs2.getPathPrefixesForSlot(new BlobKeySlot("a")));
        assertEquals(List.of("ab/", "%a/b", "000/"), subDirs2.getPathPrefixesForSlot(new BlobKeySlot("ab")));
        assertEquals("ab/c", subDirs2.getPathPrefixForKeyPrefix("abc"));

        // the prefixes match the actual paths
        String key = "abcdef0123456789"; // pragma: allowlist secret
        for (PathStrategy ps : List.of(flat, subDirs1, subDirs2)) {
            String path = dir.relativize(ps.getPathForKey(key)).toString().replace(File.separatorChar, '/');
            for (int len = 1; len <= BlobKeySlot.MAX_PREFIX_LENGTH; len++) {
                String pathPrefix = ps.getPathPrefixesForSlot(new BlobKeySlot(key.substring(0, len))).get(0);
                assertTrue(path.startsWith(pathPrefix), path + " / " + pathPrefix);
            }
        }
    }

    @Test
    public void testSlotPrefixesCoverEscapedKeys() throws IOException {
        Path dir = Files.createTempDirectory("tmp_");
        for (PathStrategy ps : List.of(new PathStrategyFlat(dir), new PathStrategySubDirs(dir, 1),
                new PathStrategySubDirs(dir, 2))) {
            for (String key : List.of("bad/key", "ab:c", "a\\b", "ab%", "00/short", "a", "ab", "a\u00e9")) {
                String path = dir.relativize(ps.getPathForKey(key)).toString().replace(File.separatorChar, '/');
                for (int length = 1; length <= BlobKeySlot.MAX_PREFIX_LENGTH; length++) {
                    for (BlobKeySlot slot : BlobKeySlot.partition(length)) {
                        if (slot.contains(key)) {
                            long matches = ps.getPathPrefixesForSlot(slot).stream().filter(path::startsWith).count();
                            assertEquals(1, matches, key + " / " + path + " / " + slot);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testTempFile() throws IOException {
        Path dir = Files.createTempDirectory("tmp_");
        PathStrategy ps = new PathStrategyFlat(dir);
        Path path = ps.createTempFile();
        assertTrue(ps.isTempFile(path));
        // non-temp file
        Path path2 = path.resolveSibling("1234");
        assertFalse(ps.isTempFile(path2));
    }

}
