/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map.Entry;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class TestState {

    @Test
    public void testState() {
        State s = new State();
        check(s, 0);

        // putting a null for a key doesn't add it (base State semantics, StateDiff would be different)
        s.put("555", null);
        check(s, 0);

        s.put("1", "1");
        check(s, 1);

        s.remove("1");
        check(s, 0);

        s.put("1", "1");
        check(s, 1);
        // put different value
        s.put("1", "111");
        assertEquals("111", s.get("1"));
        // put again different value to keep testing easily
        s.put("1", "1");
        check(s, 1);
        // putting a null for a key doesn't add it
        s.put("555", null);
        check(s, 1);

        // remove by putting null
        s.put("1", null);
        check(s, 0);

        s.put("1", "1");
        check(s, 1);
        s.put("2", "2");
        check(s, 2);
        s.put("3", "3");
        check(s, 3);
        s.put("4", "4");
        check(s, 4);
        s.put("5", "5");
        check(s, 5);
        // this will switch to a map internally (ARRAY_MAX = 5)
        s.put("6", "6");
        check(s, 6);
        s.put("7", "7");
        check(s, 7);

        // remove by putting a null
        s.put("7", null);
        check(s, 6);
    }

    protected static void check(State s, int n) {
        Set<String> set = s.keySet();
        Set<Entry<String, Object>> es = s.entrySet();
        assertEquals(n, s.size());
        if (n == 0) {
            assertTrue(s.isEmpty());
            assertTrue(set.isEmpty());
            assertTrue(es.isEmpty());
        } else {
            assertFalse(s.isEmpty());
            assertFalse(set.isEmpty());
            assertFalse(es.isEmpty());
        }
        assertNull(s.get("nosuchkey"));
        assertNull(s.remove("nosuchkey"));
        assertFalse(s.containsKey("nosuchkey"));
        assertEquals(n, set.size());
        for (int i = 1; i <= n; i++) {
            String ii = String.valueOf(i);
            assertEquals(ii, s.get(ii));
            assertTrue(set.contains(ii));
        }
        assertFalse(set.contains("nosuchkey"));
        assertEquals(n, es.size());
        String[] ar = s.keyArray();
        assertEquals(n, ar.length);
        int i = 1;
        for (Entry<String, Object> e : es) {
            String ii = String.valueOf(i++);
            assertEquals(ii, e.getKey());
            assertEquals(ii, e.getValue());
        }
    }

    @Test
    public void testHashCodeArrayRepresentation() {
        State s1 = new State();
        s1.put("a", "1");
        s1.put("b", "2");
        State s2 = new State();
        s2.put("a", "1");
        s2.put("b", "2");
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    public void testHashCodeMapRepresentation() {
        // a thread-safe State uses an internal map rather than the key/value arrays
        State s1 = new State(true);
        s1.put("a", "1");
        State s2 = new State(true);
        s2.put("a", "1");
        assertEquals(s1.hashCode(), s2.hashCode());
    }

}
