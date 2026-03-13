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
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * @author sfermigier
 */
public class TestPath {

    @Test
    public void test() {
        Path path = new Path("/a/b/c/d");

        assertFalse(path.isRoot());
        assertFalse(path.isEmpty());

        assertTrue(path.isAbsolute());

        assertEquals(4, path.segmentCount());
        assertEquals(4, path.segments().length);
        assertEquals("a", path.segment(0));
        assertEquals("b", path.segment(1));
        assertEquals("c", path.segment(2));
        assertEquals("d", path.segment(3));
        assertNull(path.segment(4));
        assertEquals("d", path.lastSegment());

        assertTrue(new Path("").isPrefixOf(path));
        assertTrue(new Path("/").isPrefixOf(path));
        assertTrue(path.isPrefixOf(new Path("/a/b/c/d/e")));
        assertFalse(path.isPrefixOf(new Path("/a/b/c")));
        assertFalse(path.isPrefixOf(new Path("/a/b/c/e")));
        assertFalse(path.isPrefixOf(new Path("/e/b/c/d")));

        assertEquals(new Path("/a/b/c/d"), path.removeFirstSegments(0));
        assertEquals(new Path(""), path.removeFirstSegments(50));
        assertEquals(new Path("b/c/d"), path.removeFirstSegments(1));

        assertEquals(new Path("/a/b/c/d"), path.removeLastSegments(0));
        assertEquals(new Path("/"), path.removeLastSegments(50));
        assertEquals(new Path("/a/b/c"), path.removeLastSegments(1));
    }

    @Test
    public void testToString() {
        Path path = new Path("");
        assertEquals("", path.toString());

        path = new Path("/a/b/c/d");
        assertEquals("/a/b/c/d", path.toString());
    }

    @Test
    public void testEquals() {
        Path path = new Path("/a/b/c/d");
        Path path2 = new Path("/a/b/c/d/");
        Path path3 = new Path("/a/b/c////d/");
        Path path4 = new Path("/a/b/././c/./e/../d/");
        // Path path5 = new Path("../a/b/c/d");

        assertEquals(path, path);
        assertEquals(path, path2);
        assertEquals(path, path3);
        assertEquals(path, path4);
        // assertEquals(path, path5);

        assertEquals(path.hashCode(), path2.hashCode());
        assertEquals(path.hashCode(), path3.hashCode());
        assertEquals(path.hashCode(), path4.hashCode());
        // assertEquals(path.hashCode(), path5.hashCode());

        assertNotNull(path);

        assertNotEquals(new Path("a/b/c/d"), path);
        assertNotEquals(new Path("/a/b/c/e"), path);
        assertNotEquals(new Path("/a/b/c/d/e"), path);
    }

    @Test
    public void testGetFileExtension() {
        assertNull(new Path("/a/b/c/").getFileExtension());
        assertNull(new Path("/a/b/c").getFileExtension());
        assertNull(new Path("").getFileExtension());
        assertEquals("doc", new Path("/a/b/c.doc").getFileExtension());
    }

    @Test
    public void testBasic() {
        final Path path = new Path("/a/b/c");

        assertEquals(3, path.segmentCount());
        assertTrue(path.isAbsolute());
        assertFalse(path.isEmpty());
        assertFalse(path.isRoot());
        assertEquals(3, path.segments().length);
        assertEquals("a", path.segment(0));
        assertEquals("b", path.segment(1));
        assertEquals("c", path.segment(2));
    }

    @Test
    public void testFileExtension() {
        Path path = new Path("/a/b/c");
        assertNull(path.getFileExtension());

        path = new Path("/a/b/c.txt");
        assertEquals("txt", path.getFileExtension());
        assertEquals("/a/b/c", path.removeFileExtension().toString());
        path = new Path("a/b/c");
        assertEquals("a/b/c", path.removeFileExtension().toString());

        path = new Path("/a/b/c/");
        assertNull(path.getFileExtension());
    }

    @Test
    public void testPathNormalisation() {
        Path path = new Path("////a/./b/../c");
        assertEquals("/a/c", path.toString());
    }

    @Test
    public void testEquality() {
        assertEquals(new Path("/a/b/c"), new Path("/a/b/c"));
        assert !new Path("/a/b/c").equals(new Path("/a/b"));
    }

    @Test
    public void testAppend() {
        Path path1 = new Path("/a/b/c");
        Path path2 = new Path("/d/e/f");
        Path path3 = new Path("/a/b/c/d/e/f");
        // XXX: I don't think this is natural, see my comment in Path.java
        assertEquals(path1.append(path2), path3);
        assertEquals(path1, path1.append(new Path("")));

        assertEquals(path1, path1.append(""));
        assertEquals(path1, path1.append("."));
        assertEquals(new Path("a/b/c"), new Path("").append(path1));
        assertEquals(path1, new Path("/").append(path1));
        assertEquals(new Path("/a/b/"), path1.append(".."));
        assertEquals(path3, path1.append("/d/e/f"));
    }

    @Test
    public void testMakeRelative() {
        Path path = new Path("./b/c");
        assertEquals(new Path("./b/c"), path.makeRelative());
        path = new Path("a/b/c");
        path.makeRelative();
    }

    @Test
    public void testMakeAbsolute() {
        Path path = new Path("/a/b/c");
        assertEquals(new Path("/a/b/c"), path.makeAbsolute());
        path = new Path("./b/c");
        assertEquals(new Path("/b/c"), path.makeAbsolute());
    }

    @Test
    public void testRemoveFirstSegments() {
        Path path = new Path("/a/b/c");
        assertThrows(IllegalArgumentException.class, () -> path.removeFirstSegments(-5));
    }

    @Test
    public void testRemoveLastSegments() {
        Path path = new Path("/a/b/c");
        assertThrows(IllegalArgumentException.class, () -> path.removeLastSegments(-5));
    }

}
