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
package org.nuxeo.ecm.core.storage.mem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.ListDiff;
import org.nuxeo.ecm.core.storage.State.StateDiff;
import org.nuxeo.ecm.core.storage.StateHelper;

public class TestMemRepositoryApplyDiff {

    private static final List<Object> list(Object... strings) {
        return new ArrayList<>(Arrays.asList(strings));
    }

    private static final StateDiff stateDiff(Object... values) {
        assertEquals(0, values.length % 2);
        StateDiff diff = new StateDiff();
        for (int i = 0; i < values.length; i += 2) {
            diff.put((String) values[i], values[i + 1]);
        }
        return diff;
    }

    private static final ListDiff listDiff(List<Object> diff, List<Object> rpush, List<Object> pull) {
        ListDiff listDiff = new ListDiff();
        listDiff.diff = diff;
        listDiff.rpush = rpush;
        listDiff.pull = pull;
        return listDiff;
    }

    private static final ListDiff rpush(Object... values) {
        return listDiff(null, list(values), null);
    }

    private static final State state(Object... values) {
        return stateDiff(values);
    }

    private static void assertEqualsStrict(String message, Object a, Object b) {
        assertTrue(StateHelper.equalsStrict(a, b), message);
    }

    private static void assertApplyDiff(State expected, State state, StateDiff diff) {
        MemConnection.applyDiff(state, diff);
        assertEqualsStrict(state.toString(), expected, state);
    }

    @Test
    public void testApplyDiff() {
        assertApplyDiff(state("A", "B"), //
                state("A", "B"), //
                stateDiff());
        assertApplyDiff(state("A", "B"), //
                state(), //
                stateDiff("A", "B"));

        assertApplyDiff(state("A", state("B", "D")), //
                state("A", state("B", "C")), //
                stateDiff("A", stateDiff("B", "D")));

        assertApplyDiff(state("A", state("B", "C", "D", "E")), //
                state("A", state("B", "C")), //
                stateDiff("A", stateDiff("D", "E")));

        assertApplyDiff(state("A", list("B", "C")), //
                state("A", list("B")), //
                stateDiff("A", rpush("C")));
    }

    @Test
    public void testCopiesValues() {
        List<Object> list1 = list("L1");
        State state = state("A", list1);
        List<Object> list2 = list("L2");
        MemConnection.applyDiff(state, (StateDiff) state("A", list2));
        // make sure we don't end up with the actual list2 in the new state, but with a copy
        Object list3 = state.get("A");
        assertEqualsStrict("Should be equal", list2, list3);
        assertNotSame(list2, list3);
    }

    @Test
    public void testCopiesListDiffDiff() {
        List<Object> list = list(list("L1"));
        List<Object> list1 = list("L2");
        MemConnection.applyDiff(list, listDiff(list(list1), null, null)); // diff
        // make sure we don't end up with the actual list1 in the new list, but with a copy
        Object list2 = list.get(0);
        assertEqualsStrict("Should be equal", list1, list2);
        assertNotSame(list1, list2);
    }

    @Test
    public void testCopiesListDiffRpush() {
        List<Object> list = list();
        List<Object> list1 = list("L1");
        MemConnection.applyDiff(list, listDiff(null, list(list1), null)); // rpush
        // make sure we don't end up with the actual list1 in the new list, but with a copy
        Object list2 = list.get(0);
        assertEqualsStrict("Should be equal", list1, list2);
        assertNotSame(list1, list2);
    }

    @Test
    public void testCopiesListDiffRpushOnNull() {
        List<Object> list = list("L1");
        ListDiff listDiff = listDiff(null, list, null);
        Object res = MemConnection.applyDiff(null, listDiff); // rpush
        assertTrue(res instanceof List, res.getClass().toString());
        List<Object> list2 = (List<Object>) res;
        assertEqualsStrict("Should be equal", list, list2);
    }

    @Test
    public void testCopiesListDiffPull() {
        List<Object> list = list("A", "B", "C", "B", "A");
        MemConnection.applyDiff(list, listDiff(null, null, list("A", "B"))); // pull
        assertEqualsStrict("Should be equal", Arrays.asList("C"), list);
    }

}
