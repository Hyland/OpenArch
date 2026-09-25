/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

import static com.hyland.openarch.ecm.core.storage.State.NOP;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import com.hyland.openarch.ecm.core.api.PropertyException;
import com.hyland.openarch.ecm.core.api.model.Delta;
import com.hyland.openarch.ecm.core.storage.State.ListDiff;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.runtime.api.Framework;

/**
 * Helpers for deep copy and deep diff of {@link State} objects.
 */
public class StateHelper {

    private static final String DISABLED_DELTA_PROP = "org.nuxeo.core.delta.disabled";

    /** Utility class. */
    private StateHelper() {
    }

    /**
     * Checks if we have a base type compatible with {@link State} helper processing.
     */
    public static boolean isScalar(Object value) {
        return value instanceof String //
                || value instanceof Boolean //
                || value instanceof Long //
                || value instanceof Double //
                || value instanceof BigDecimal //
                || value instanceof Instant //
                || value instanceof Delta;
    }

    /**
     * Compares two values.
     */
    public static boolean equalsStrict(Object a, Object b) {
        if (a == b) {
            return true;
        } else if (a == null || b == null) {
            return false;
        } else if (a instanceof State aState && b instanceof State bState) {
            return equalsStrict(aState, bState);
        } else if (a instanceof List la && b instanceof List lb) {
            return equalsStrict(la, lb);
        } else if (a instanceof ListDiff lda && b instanceof ListDiff ldb) {
            return equalsStrict(lda.diff, ldb.diff) //
                    && equalsStrict(lda.rpush, ldb.rpush) //
                    && equalsStrict(lda.pull, ldb.pull);
        } else if (isScalar(a) && isScalar(b)) {
            return a.equals(b);
        } else {
            return false;
        }
    }

    /**
     * Compares two {@link State}s.
     */
    public static boolean equalsStrict(State a, State b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (Entry<String, Object> en : a.entrySet()) {
            String key = en.getKey();
            Object va = en.getValue();
            Object vb = b.get(key);
            if (!equalsStrict(va, vb)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two {@link List}s.
     */
    public static boolean equalsStrict(List<Object> a, List<Object> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (Iterator<Object> ita = a.iterator(), itb = b.iterator(); ita.hasNext();) {
            if (!equalsStrict(ita.next(), itb.next())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two values.
     * <p>
     * A {@code null} value or an empty {@code List} is equivalent to an absent value. A {@code null} {@link State} is
     * equivalent to an empty {@link State} (or a {@link State} containing only absent values).
     */
    public static boolean equalsLoose(Object a, Object b) {
        if (a == b) {
            return true;
        } else if (a instanceof State && b instanceof State //
                || a instanceof State && b == null //
                || a == null && b instanceof State) {
            return equalsLoose((State) a, (State) b);
        } else if (a instanceof List && b instanceof List //
                || a instanceof List && b == null //
                || a == null && b instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> la = (List<Object>) a;
            @SuppressWarnings("unchecked")
            List<Object> lb = (List<Object>) b;
            return equalsLoose(la, lb);
        } else if (a instanceof ListDiff lda && b instanceof ListDiff ldb) {
            return equalsLoose(lda.diff, ldb.diff) && equalsLoose(lda.rpush, ldb.rpush);
        } else if (isScalar(a) && isScalar(b)) {
            return a.equals(b); // NOSONAR
        } else {
            return false;
        }
    }

    /**
     * Compares two {@link State}s.
     * <p>
     * A {@code null} value or an empty {@code List} is equivalent to an absent value. A {@code null} {@link State} is
     * equivalent to an empty {@link State} (or a {@link State} containing only absent values).
     */
    public static boolean equalsLoose(State a, State b) {
        if (a == null) {
            a = State.EMPTY;
        }
        if (b == null) {
            b = State.EMPTY;
        }
        for (Entry<String, Object> en : a.entrySet()) {
            Object va = en.getValue();
            if (va == null) {
                // checked by loop on b
                continue;
            }
            String key = en.getKey();
            Object vb = b.get(key);
            if (!equalsLoose(va, vb)) {
                return false;
            }
        }
        for (Entry<String, Object> en : b.entrySet()) {
            String key = en.getKey();
            Object va = a.get(key);
            if (va != null) {
                // already checked by loop on a
                continue;
            }
            Object vb = en.getValue();
            if (!equalsLoose(null, vb)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two {@link List}s.
     * <p>
     * {@code null} values are equivalent to empty lists.
     */
    public static boolean equalsLoose(List<Object> a, List<Object> b) {
        if (a != null && a.isEmpty()) {
            a = null;
        }
        if (b != null && b.isEmpty()) {
            b = null;
        }
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (Iterator<Object> ita = a.iterator(), itb = b.iterator(); ita.hasNext();) {
            if (!equalsLoose(ita.next(), itb.next())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Makes a deep copy of a value.
     */
    public static Object deepCopy(Object value) {
        return deepCopy(value, false);
    }

    /**
     * Makes a deep copy of a value, optionally thread-safe.
     *
     * @param threadSafe if {@code true}, then thread-safe datastructures are used
     */
    public static Object deepCopy(Object value, boolean threadSafe) {
        if (value == null) {
            return value;
        } else if (value instanceof State state) {
            return deepCopy(state, threadSafe);
        } else if (value instanceof List list) {
            return deepCopy(list, threadSafe);
        }
        // else scalar value -- check anyway (debug)
        else if (!isScalar(value)) {
            throw new UnsupportedOperationException("Cannot deep copy: " + value.getClass().getName());
        }
        return value;
    }

    /**
     * Makes a deep copy of a {@link State} map.
     */
    public static State deepCopy(State state) {
        return deepCopy(state, false);
    }

    /**
     * Makes a deep copy of a {@link State} map, optionally thread-safe.
     *
     * @param threadSafe if {@code true}, then thread-safe datastructures are used
     */
    public static State deepCopy(State state, boolean threadSafe) {
        State copy = new State(state.size(), threadSafe);
        for (Entry<String, Object> en : state.entrySet()) {
            copy.put(en.getKey(), deepCopy(en.getValue(), threadSafe));
        }
        return copy;
    }

    /**
     * Makes a deep copy of a {@link List}.
     */
    public static List<Object> deepCopy(List<Object> list) {
        return deepCopy(list, false);
    }

    /**
     * Makes a deep copy of a {@link List}, optionally thread-safe.
     *
     * @param threadSafe if {@code true}, then thread-safe datastructures are used
     */
    public static List<Object> deepCopy(List<Object> list, boolean threadSafe) {
        List<Object> copy = threadSafe ? new CopyOnWriteArrayList<>() : new ArrayList<>(list.size());
        for (Object v : list) {
            copy.add(deepCopy(v, threadSafe));
        }
        return copy;
    }

    public static Object toState(Object value) {
        return toState(value, false);
    }

    public static Object toState(Object value, boolean threadSafe) {
        if (value == null) {
            return null;
        } else if (value instanceof Map map) {
            return toState(map, threadSafe);
        } else if (value instanceof List list) {
            return toState(list, threadSafe);
        } else if (value instanceof Object[] objects) {
            return toState(Arrays.asList(objects), threadSafe);
        } else if (isScalar(value)) {
            return value;
        } else if (value instanceof Integer i) {
            return Long.valueOf(i.longValue());
        } else if (value instanceof Float f) {
            return Double.valueOf(String.valueOf(f)); // f.doubleValue() would lose precision
        } else {
            throw new PropertyException("Invalid value: " + value);
        }
    }

    public static State toState(Map<?, ?> map, boolean threadSafe) {
        State state = new State(map.size(), threadSafe);
        for (Entry<?, ?> en : map.entrySet()) {
            Object k = en.getKey();
            Object value = en.getValue();
            if (!(k instanceof String key)) {
                throw new PropertyException("Invalid key: " + k);
            }
            state.put(key, toState(value, threadSafe));
        }
        return state;
    }

    public static List<Object> toState(List<?> list, boolean threadSafe) {
        List<Object> copy = threadSafe ? new CopyOnWriteArrayList<>() : new ArrayList<>(list.size());
        for (Object value : list) {
            copy.add(toState(value, threadSafe));
        }
        return copy;
    }

    public static Object toMap(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof State state) {
            return toMap(state);
        } else if (value instanceof List list) {
            return toMap(list);
        } else if (isScalar(value)) {
            return value;
        } else {
            throw new PropertyException("Invalid value: " + value);
        }
    }

    public static Map<String, Object> toMap(State state) {
        Map<String, Object> map = new HashMap<>(state.size());
        for (Entry<String, Object> en : state.entrySet()) {
            map.put(en.getKey(), toMap(en.getValue()));
        }
        return map;
    }

    public static List<Object> toMap(List<?> list) {
        List<Object> newList = new ArrayList<>(list.size());
        for (Object value : list) {
            newList.add(toMap(value));
        }
        return newList;
    }

    /**
     * Does a diff of two values.
     *
     * @return a {@link StateDiff}, a {@link ListDiff}, {@link State#NOP}, or an actual value (including {@code null})
     */
    public static Object diff(Object a, Object b) {
        if (equalsLoose(a, b)) {
            return NOP;
        }
        if ((a == null || a instanceof List) && (b == null || b instanceof List)) {
            @SuppressWarnings("unchecked")
            List<Object> la = (List<Object>) a;
            @SuppressWarnings("unchecked")
            List<Object> lb = (List<Object>) b;
            return diff(la, lb);
        }
        if (a instanceof State aState && b instanceof State bState) {
            StateDiff diff = diff(aState, bState);
            return diff.isEmpty() ? NOP : diff;
        }
        return b;
    }

    public static Object diff(List<Object> a, List<Object> b) {
        ListDiff listDiff = new ListDiff();
        if (a == null) {
            a = Collections.emptyList();
        }
        if (b == null) {
            b = Collections.emptyList();
        }
        List<Object> pull = findPull(a, b);
        if (pull != null) {
            listDiff.pull = pull;
            return listDiff;
        }
        int aSize = a.size();
        int bSize = b.size();
        boolean doRPush = aSize < bSize;
        // we can use a list diff if lists are the same size,
        // or we have a rpush
        boolean doDiff = aSize == bSize || doRPush;
        if (!doDiff) {
            return b;
        }
        int len = Math.min(aSize, bSize);
        List<Object> diff = new ArrayList<>(len);
        int nops = 0;
        int diffs = 0;
        for (int i = 0; i < len; i++) {
            Object elemDiff = diff(a.get(i), b.get(i));
            if (elemDiff == NOP) {
                nops++;
            } else if (elemDiff instanceof StateDiff) {
                diffs++;
            }
            // TODO if the individual element diffs are big StateDiffs,
            // do a full State replacement instead
            diff.add(elemDiff);
        }
        if (nops == len) {
            // only nops
            diff = null;
        } else if (diffs == 0) {
            // only setting elements or nops
            // TODO use a higher ratio than 0% of diffs
            return b;
        }
        listDiff.diff = diff;
        if (doRPush) {
            List<Object> rpush = new ArrayList<>(bSize - aSize);
            for (int i = aSize; i < bSize; i++) {
                rpush.add(b.get(i));
            }
            listDiff.rpush = rpush;
        }
        return listDiff;
    }

    /** @since 11.5 */
    public static List<Object> findPull(List<Object> a, List<Object> b) {
        // check that list size decreased
        if (a.size() <= b.size()) {
            return null;
        }
        // check that we have scalar element types (complex not supported)
        Object value = a.get(0);
        if (!isScalar(value)) {
            return null;
        }
        // get the set difference
        Set<Object> set = new HashSet<>(a);
        set.removeAll(b);
        if (set.isEmpty()) {
            return null;
        }
        // check that it's really a pull operation (all candidates are removed)
        List<Object> list = new ArrayList<>(a);
        List<Object> pull = new ArrayList<>(set);
        list.removeAll(pull);
        if (!equalsLoose(list, b)) {
            return null;
        }
        return pull;
    }

    /**
     * Makes a diff copy of two {@link State} maps.
     * <p>
     * The returned diff state contains only the key/values that changed. {@code null} values are equivalent to absent
     * values.
     * <p>
     * For values set to null or removed, the value is null.
     * <p>
     * When setting a delta, the old value is checked to know if the delta should be kept or if a full value should be
     * set instead.
     * <p>
     * For sub-documents, a recursive diff is returned.
     *
     * @return a {@link StateDiff} which, when applied to a, gives b.
     */
    public static StateDiff diff(State a, State b) {
        StateDiff diff = new StateDiff();
        for (Entry<String, Object> en : a.entrySet()) {
            Object va = en.getValue();
            if (va == null) {
                // checked by loop on b
                continue;
            }
            String key = en.getKey();
            Object vb = b.get(key);
            if (vb == null) {
                // value must be cleared
                diff.put(key, null);
            } else {
                // compare values
                Object elemDiff = diff(va, vb);
                if (elemDiff != NOP) {
                    if (elemDiff instanceof Delta delta) {
                        Object deltaBase = delta.getBase();
                        if (!Objects.equals(va, deltaBase)) {
                            // delta's base is not the old value
                            // -> set a new value, don't use a delta update
                            elemDiff = delta.getFullValue();
                        }
                        // else delta's base is the in-database value
                        // because base is consistent with old value, assume the delta is already properly computed
                    }
                    diff.put(key, elemDiff);
                }
            }
        }
        for (Entry<String, Object> en : b.entrySet()) {
            String key = en.getKey();
            Object va = a.get(key);
            if (va != null) {
                // already checked by loop on a
                continue;
            }
            Object vb = en.getValue();
            if (!equalsLoose(null, vb)) {
                // value must be set or rpushed
                diff.put(key, rpushOrValue(vb));
            }
        }
        return diff;
    }

    /**
     * Returns a pure rpush ListDiff for the value if it's a list.
     *
     * @param value the value
     * @return a pure rpush ListDiff for the value, if possible
     * @since 11.4
     */
    public static Object rpushOrValue(Object value) {
        if (value instanceof List) {
            ListDiff listDiff = new ListDiff();
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            listDiff.rpush = list;
            return listDiff;
        } else {
            return value;
        }
    }

    /**
     * Changes the deltas stored into actual full values.
     *
     * @since 6.0
     */
    public static void resetDeltas(State state) {
        if (Boolean.parseBoolean(Framework.getProperty(DISABLED_DELTA_PROP, "false"))) {
            return;
        }
        for (Entry<String, Object> en : state.entrySet()) {
            Object value = en.getValue();
            if (value instanceof State valueState) {
                resetDeltas(valueState);
            } else if (value instanceof Delta delta) {
                state.put(en.getKey(), delta.getFullValue());
            }
        }
    }

}
