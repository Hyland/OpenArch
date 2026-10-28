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
package com.hyland.openarch.ecm.core.blob.binary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class TestBlobKeySlot {

    private static final List<String> KEYS = List.of("0", "0a", "5f3b", "a", "abc", "f00", "ffff", "", "A1",
            "g12", "%2fx", "tmp-123", "a/b", "zz");

    @Test
    public void testPartition() {
        assertEquals(List.of(BlobKeySlot.ALL), BlobKeySlot.partition(0));

        List<BlobKeySlot> slots = BlobKeySlot.partition(1);
        assertEquals(17, slots.size());
        assertEquals("0", slots.get(0).id());
        assertEquals("f", slots.get(15).id());
        assertEquals(BlobKeySlot.other(1), slots.get(16));
        assertEquals("Z", slots.get(16).id());

        slots = BlobKeySlot.partition(2);
        assertEquals(257, slots.size());
        assertEquals("00", slots.get(0).id());
        assertEquals("01", slots.get(1).id());
        assertEquals("ff", slots.get(255).id());
        assertEquals("ZZ", slots.get(256).id());

        assertThrows(IllegalArgumentException.class, () -> BlobKeySlot.partition(-1));
        assertThrows(IllegalArgumentException.class, () -> BlobKeySlot.partition(3));
    }

    @Test
    public void testEachKeyInExactlyOneSlot() {
        for (int prefixLength = 0; prefixLength <= 2; prefixLength++) {
            List<BlobKeySlot> slots = BlobKeySlot.partition(prefixLength);
            for (String key : KEYS) {
                long count = slots.stream().filter(slot -> slot.contains(key)).count();
                assertEquals(1, count, "key '" + key + "' with prefix length " + prefixLength);
            }
        }
    }

    @Test
    public void testContains() {
        BlobKeySlot a = new BlobKeySlot("a");
        assertTrue(a.contains("a"));
        assertTrue(a.contains("abc"));
        assertFalse(a.contains("ba"));
        assertFalse(a.contains("A1"));

        BlobKeySlot other = BlobKeySlot.other(2);
        assertTrue(other.contains("a")); // too short for a 2-digit prefix
        assertTrue(other.contains("ag"));
        assertTrue(other.contains("g0"));
        assertTrue(other.contains(""));
        assertFalse(other.contains("a0"));
        assertFalse(other.contains("ff00"));
        assertTrue(other.contains("A1")); // uppercase is not a key hexadecimal digit
        assertTrue(BlobKeySlot.other(1).contains("F0"));

        assertTrue(BlobKeySlot.ALL.contains(""));
        assertTrue(BlobKeySlot.ALL.contains("anything"));
    }

    @Test
    public void testAccessors() {
        assertTrue(BlobKeySlot.ALL.isAll());
        assertFalse(BlobKeySlot.ALL.isOther());
        assertEquals("", BlobKeySlot.ALL.getKeyPrefix());
        assertEquals("ALL", BlobKeySlot.ALL.toString());

        BlobKeySlot slot = new BlobKeySlot("ab");
        assertFalse(slot.isAll());
        assertFalse(slot.isOther());
        assertEquals("ab", slot.getKeyPrefix());
        assertEquals("ab", slot.toString());

        BlobKeySlot other = BlobKeySlot.other(1);
        assertFalse(other.isAll());
        assertTrue(other.isOther());
        assertNull(other.getKeyPrefix());
    }

    @Test
    public void testParse() {
        assertEquals(BlobKeySlot.ALL, BlobKeySlot.parse(null));
        assertEquals(BlobKeySlot.ALL, BlobKeySlot.parse(""));
        assertEquals(new BlobKeySlot("3f"), BlobKeySlot.parse("3f"));
        assertEquals(BlobKeySlot.other(2), BlobKeySlot.parse("ZZ"));
        assertThrows(IllegalArgumentException.class, () -> BlobKeySlot.parse("g"));
        assertThrows(IllegalArgumentException.class, () -> BlobKeySlot.parse("A"));
        assertThrows(IllegalArgumentException.class, () -> BlobKeySlot.parse("aZ"));
        assertThrows(IllegalArgumentException.class, () -> BlobKeySlot.parse("abcd"));
    }

}
