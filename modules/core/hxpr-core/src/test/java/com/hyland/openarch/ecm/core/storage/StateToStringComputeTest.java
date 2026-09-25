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
package com.hyland.openarch.ecm.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link State#toString()} formatting and {@link State#computeIfAbsent}.
 */
class StateToStringComputeTest {

    @Test
    void emptyStateRendersAsEmptyBraces() {
        assertEquals("{}", new State().toString());
    }

    @Test
    void toStringPinsOrderedKeysFirstThenSortsTheRest() {
        State state = new State();
        state.put("zzz", "last");
        state.put(SYS_ID, "id1"); // a pinned key, must render first
        state.put("aaa", 3L);

        String rendered = state.toString();

        assertTrue(rendered.startsWith("{" + SYS_ID + "=id1"), rendered);
        assertTrue(rendered.indexOf("aaa=3") < rendered.indexOf("zzz=last"), rendered); // remaining keys sorted
    }

    @Test
    void toStringRendersArraysAndNestedStates() {
        State child = new State();
        child.put("n", 1L);
        State state = new State();
        state.put("arr", new Object[] { "x", "y" });
        state.put("child", child);

        String rendered = state.toString();

        assertTrue(rendered.contains("arr=[x,y]"), rendered);
        assertTrue(rendered.contains("child="), rendered);
    }

    @Test
    void toStringTruncatesLongStringsAndLargeArrays() {
        State state = new State();
        state.put("big", "a".repeat(150)); // > DEBUG_MAX_STRING (100)
        Object[] many = new Object[15]; // > DEBUG_MAX_ARRAY (10)
        for (int i = 0; i < many.length; i++) {
            many[i] = Long.valueOf(i);
        }
        state.put("arr", many);

        String rendered = state.toString();

        assertTrue(rendered.contains("chars)..."), rendered);
        assertTrue(rendered.contains("items)..."), rendered);
    }

    @Test
    void computeIfAbsentPutsOnlyWhenMissingAndNonNull() {
        State state = new State();

        // missing -> mapping applied and stored
        assertEquals("y", state.computeIfAbsent("b", k -> "y"));
        assertEquals("y", state.get("b"));

        // present -> existing value kept, mapping not applied
        state.put("a", "x");
        assertEquals("x", state.computeIfAbsent("a", k -> "z"));

        // mapping returns null -> nothing stored
        assertNull(state.computeIfAbsent("c", k -> null));
        assertFalse(state.containsKey("c"));
    }
}
