/*
 * (C) Copyright 2011-2026 Hyland (https://www.hyland.com/).
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 *
 */
public class FileMatcherTest {

    @Test
    public void testMatchFilename() {
        FileMatcher fm = FileMatcher.getMatcher("test-automation-1.2.jar");
        assertTrue(fm.match("test-automation-1.2.jar"));
        assertFalse(fm.match("test-automation-3.4.zip"));
        assertNull(fm.getKey());
        assertNull(fm.getValue());
    }

    @Test
    public void testMatchFilenameWithPatternOnVersion() {
        FileMatcher fm = FileMatcher.getMatcher("test-automation-{v:.+}.jar");
        assertTrue(fm.match("test-automation-1.2.jar"));
        assertEquals("v", fm.getKey(), "Key not extracted");
        assertEquals("1.2", fm.getValue(), "Value not extracted");
    }

    @Test
    public void testMatchFilenameWithPatternOnVersionAndExtension() {
        FileMatcher fm = FileMatcher.getMatcher("test-automation-{v:.+}");
        assertTrue(fm.match("test-automation-1.2.jar"));
        assertEquals("1.2.jar", fm.getValue(), "Value not extracted");
    }

    @Test
    public void testMatchFilenameWithPatternOnNameAndFixedExtension() {
        FileMatcher fm = FileMatcher.getMatcher("{v:.+}.jar");
        assertTrue(fm.match("test-automation-1.2.jar"));
        assertEquals("test-automation-1.2", fm.getValue(), "Value not extracted");
    }

    @Test
    public void testMatchFilenameWithWholePattern() {
        FileMatcher fm = FileMatcher.getMatcher("{v:.+}");
        assertTrue(fm.match("test-automation-1.2.jar"));
        assertEquals( "test-automation-1.2.jar", fm.getValue(), "Value not extracted");
    }

    @Test
    public void testMatchFilenameWithNameAndVersion() {
        FileMatcher fm = FileMatcher.getMatcher("{n:.*-}[0-9]+.*\\.jar");
        assertTrue(fm.match("test-automation-5.5-SNAPSHOT.jar"));
        assertEquals("test-automation-", fm.getValue(), "Value not extracted");
        assertTrue(fm.match("test-automation-5.5.jar"));
        assertEquals("test-automation-", fm.getValue(), "Value not extracted");
    }

    @Test
    public void testMatchWithFilenameAndVersionPattern() {
        FileMatcher fm = FileMatcher.getMatcher("test-automation-" + "{v:[0-9]+.*}\\.jar");
        assertTrue(fm.match("test-automation-5.5-SNAPSHOT.jar"));
        assertEquals("5.5-SNAPSHOT", fm.getValue(), "Value not extracted");
        assertTrue(fm.match("test-automation-5.5.jar"));
        assertEquals("5.5", fm.getValue(), "Value not extracted");
    }

    @Test
    public void testWithPrecompiledPattern() {
        Pattern pattern = Pattern.compile("");
        FileMatcher fm = new FileMatcher(pattern, "key");
        assertFalse(fm.match("test-automation-1.2.jar"));
        assertEquals(pattern, fm.getPattern());
    }

    @Test
    public void testMatchWithFileReference() {
        File testFile = new File("./openarch-common/src/test/resources/test-resource-1.xml");
        FileMatcher fm = FileMatcher.getMatcher(testFile);
        assertTrue(fm.match("test-resource-1.xml"));
        assertFalse(fm.match("test-resource-2.zip"));
        assertNull(fm.getKey());
        assertNull(fm.getValue());
    }
}
