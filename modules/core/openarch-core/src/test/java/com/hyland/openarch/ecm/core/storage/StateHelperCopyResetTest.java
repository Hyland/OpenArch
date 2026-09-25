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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.model.DeltaLong;
import com.hyland.openarch.runtime.api.Framework;

/**
 * Direct tests for the thread-safe {@link StateHelper#deepCopy} variants and {@link StateHelper#resetDeltas}.
 */
class StateHelperCopyResetTest {

    private static final String DISABLED_DELTA_PROP = "com.hyland.openarch.core.delta.disabled";

    @Test
    void deepCopyReturnsScalarsAndNullUnchanged() {
        assertNull(StateHelper.deepCopy((Object) null, true));
        assertEquals("str", StateHelper.deepCopy("str", true));
        assertEquals(3L, StateHelper.deepCopy(3L, true));
    }

    @Test
    void deepCopyRejectsUnknownTypes() {
        Object unknown = new Object();
        assertThrows(UnsupportedOperationException.class, () -> StateHelper.deepCopy(unknown, false));
    }

    @Test
    void threadSafeDeepCopyUsesConcurrentListAndCopiesDeeply() {
        State original = new State();
        original.put("scalar", "str");
        original.put("list", new ArrayList<>(List.of("a", "b")));
        State child = new State();
        child.put("n", 1L);
        original.put("child", child);

        State copy = StateHelper.deepCopy(original, true);

        assertEquals("str", copy.get("scalar"));
        assertInstanceOf(CopyOnWriteArrayList.class, copy.get("list"));
        assertEquals(List.of("a", "b"), copy.get("list"));
        assertInstanceOf(State.class, copy.get("child"));
        assertNotSame(child, copy.get("child")); // deep, not shared
        assertEquals(1L, ((State) copy.get("child")).get("n"));
    }

    @Test
    void threadSafeDeepCopyOfListIsConcurrent() {
        List<Object> copy = StateHelper.deepCopy(new ArrayList<>(List.of("a", "b")), true);

        assertInstanceOf(CopyOnWriteArrayList.class, copy);
        assertEquals(List.of("a", "b"), copy);
    }

    @Test
    void resetDeltasReplacesDeltasWithFullValues() {
        State state = new State();
        state.put("counter", DeltaLong.valueOf(10L, 5));
        state.put("plain", "unchanged");
        State child = new State();
        child.put("nested", DeltaLong.valueOf(1L, 2));
        state.put("child", child);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getProperty(DISABLED_DELTA_PROP, "false")).thenReturn("false");

            StateHelper.resetDeltas(state);
        }

        assertEquals(15L, state.get("counter"));
        assertEquals("unchanged", state.get("plain"));
        assertEquals(3L, ((State) state.get("child")).get("nested"));
    }

    @Test
    void resetDeltasIsNoOpWhenDisabled() {
        State state = new State();
        DeltaLong delta = DeltaLong.valueOf(10L, 5);
        state.put("counter", delta);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getProperty(DISABLED_DELTA_PROP, "false")).thenReturn("true");

            StateHelper.resetDeltas(state);
        }

        assertTrue(state.get("counter") instanceof DeltaLong); // left untouched
    }
}
