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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TestPattern {

    @Test
    public void testFileNamePattern() {

        FileNamePattern pattern;

        pattern = new FileNamePattern("");
        assertTrue(pattern.match(""));
        assertFalse(pattern.match("a"));

        pattern = new FileNamePattern("abc.txt");
        assertTrue(pattern.match("abc.txt"));
        assertFalse(pattern.match("ac.txt"));

        pattern = new FileNamePattern("?");
        assertTrue(pattern.match("a"));
        assertFalse(pattern.match("ab"));

        pattern = new FileNamePattern("*");
        assertTrue(pattern.match("abababa"));
        assertTrue(pattern.match(""));

        pattern = new FileNamePattern("*.txt");
        assertTrue(pattern.match("a.txt"));
        assertTrue(pattern.match("a.b.txt"));
        assertFalse(pattern.match("a:txt"));
        assertFalse(pattern.match("a.txtz"));

        pattern = new FileNamePattern("*abc.txt");
        assertTrue(pattern.match("abc.txt"));
        assertTrue(pattern.match("xabc.txt"));
        assertTrue(pattern.match("xyzabc.txt"));
        assertFalse(pattern.match("ac.txt"));

        pattern = new FileNamePattern("a*bc.txt");
        assertTrue(pattern.match("abc.txt"));
        assertTrue(pattern.match("axbc.txt"));
        assertTrue(pattern.match("axyzbc.txt"));
        assertFalse(pattern.match("ac.txt"));

        pattern = new FileNamePattern("abc*txt");
        assertTrue(pattern.match("abc.txt"));
        assertTrue(pattern.match("abctxt"));
        assertTrue(pattern.match("abcxyztxt"));
        assertFalse(pattern.match("ac.txt"));

        pattern = new FileNamePattern("abc.t*t");
        assertTrue(pattern.match("abc.txt"));
        assertTrue(pattern.match("abc.tt"));
        assertTrue(pattern.match("abc.txyzt"));
        assertFalse(pattern.match("abc.t"));

        pattern = new FileNamePattern("abc.*");
        assertTrue(pattern.match("abc.txt"));
        assertTrue(pattern.match("abc."));
        assertTrue(pattern.match("abc.t"));
        assertFalse(pattern.match("ab.txt"));

        pattern = new FileNamePattern("?bc.txt");
        assertTrue(pattern.match("abc.txt"));
        assertFalse(pattern.match("zabc.txt"));
        assertFalse(pattern.match("abc.txy"));
        assertFalse(pattern.match("abc.t"));

        pattern = new FileNamePattern("abc.???");
        assertTrue(pattern.match("abc.txt"));
        assertFalse(pattern.match("zabc.txt"));
        assertTrue(pattern.match("abc.txy"));
        assertFalse(pattern.match("abc.t"));

        pattern = new FileNamePattern("*.jar");
        assertTrue(pattern.match("abc.jar"));
        assertTrue(pattern.match("a.b.c.jar"));
        assertFalse(pattern.match("jar.j.ja."));
        assertFalse(pattern.match("jar.j.jar."));
        assertTrue(pattern.match("jar.j.ja.jar"));
        assertTrue(pattern.match(".jar"));
        assertFalse(pattern.match("abc-jar"));

        pattern = new FileNamePattern("openarch?*common*.jar");
        assertTrue(pattern.match("openarch-common.jar"));
        assertTrue(pattern.match("openarch--common.jar"));
        assertTrue(pattern.match("openarch-common-SNAPSHOT-1.0.jar"));
        assertFalse(pattern.match("openarchcommon.jar"));

        pattern = new FileNamePattern("???*");
        assertTrue(pattern.match("abcdef"));
        assertTrue(pattern.match("xyz"));
        assertFalse(pattern.match("ab"));
        assertFalse(pattern.match("x"));

        pattern = new FileNamePattern("??*?**");
        assertTrue(pattern.match("abcdef"));
        assertTrue(pattern.match("xyz"));
        assertFalse(pattern.match("ab"));
        assertFalse(pattern.match("x"));
    }

    @Test
    public void testFilePathPattern() {
        FilePathPattern pattern = new FilePathPattern("a/b/c/d");
        assertTrue(pattern.match("a/b/c/d"));
        assertTrue(pattern.match("a/b//c/d"));
        assertFalse(pattern.match("a/b/c"));

        pattern = new FilePathPattern("**/b/c");
        assertTrue(pattern.match("b/c"));
        assertTrue(pattern.match("a/b/c"));
        assertTrue(pattern.match("x/a/b/c"));
        assertFalse(pattern.match("b/c/d"));

        pattern = new FilePathPattern("b/c/**");
        assertTrue(pattern.match("b/c"));
        assertTrue(pattern.match("b/c/d"));
        assertTrue(pattern.match("b/c/d/e"));
        assertFalse(pattern.match("a/b/c"));

        pattern = new FilePathPattern("a/**/c");
        assertTrue(pattern.match("a/c"));
        assertTrue(pattern.match("a/b/c"));
        assertTrue(pattern.match("a/b/x/c"));
        assertFalse(pattern.match("b/c/d"));
        assertFalse(pattern.match("a/b/c/d"));
    }

}
