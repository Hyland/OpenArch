/*
 * (C) Copyright 2018-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.common.utils;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public class TestBatchUtils {

    @Test
    public void testGroupByDerived() {
        Function<String, String> deriver = s -> s.substring(0, 1); // first letter
        BiPredicate<String, String> comparator = (a, b) -> a.equals(b);
        List<String> values;
        List<Pair<String, List<String>>> result;

        values = asList();
        result = BatchUtils.groupByDerived(values, deriver, comparator);
        assertEquals("[]", result.toString());

        values = asList("a1");
        result = BatchUtils.groupByDerived(values, deriver, comparator);
        assertEquals("[(a,[a1])]", result.toString());

        values = asList("a1", "a2", "a3");
        result = BatchUtils.groupByDerived(values, deriver, comparator);
        assertEquals("[(a,[a1, a2, a3])]", result.toString());

        values = asList("a1", "a2", "b3");
        result = BatchUtils.groupByDerived(values, deriver, comparator);
        assertEquals("[(a,[a1, a2]), (b,[b3])]", result.toString());

        values = asList("a1", "b2", "a3", "b4");
        result = BatchUtils.groupByDerived(values, deriver, comparator);
        assertEquals("[(a,[a1]), (b,[b2]), (a,[a3]), (b,[b4])]", result.toString());

        values = asList("a1", "a2", "b3", "c4", "a5", "a6", "a7", "c8");
        result = BatchUtils.groupByDerived(values, deriver, comparator);
        assertEquals("[(a,[a1, a2]), (b,[b3]), (c,[c4]), (a,[a5, a6, a7]), (c,[c8])]", result.toString());
    }

    private static void assertTryWithSplit(String expected, String... strings) {
        assertEquals(expected, tryWithSplit(strings));
    }

    private static StringBuilder SB = new StringBuilder();

    private static String tryWithSplit(String... strings) {
        SB.setLength(0);
        BatchUtils.tryWithSplit(Arrays.asList(strings), list -> process(list));
        return SB.toString();
    }

    // represents one attempted batch of processing
    private static void process(List<String> list) {
        // for this test, anything with more than 3 characters total is too large
        if (list.stream().mapToInt(String::length).sum() > 3) {
            throw new TooLargeException();
        }
        // otherwise collect what's processed, with delimiters to show boundaries
        SB.append("(");
        list.forEach(SB::append);
        SB.append(")");
    }

    @Test
    void testTryWithSplit() {
        assertTryWithSplit("");

        assertTryWithSplit("(a)", "a");
        assertTryWithSplit("(ab)", "a", "b");
        assertTryWithSplit("(abc)", "a", "b", "c");
        assertTryWithSplit("(ab)(cd)", "a", "b", "c", "d");
        assertTryWithSplit("(ab)(cde)", "a", "b", "c", "d", "e"); // rounding up would give different result (abc)(de)
        assertTryWithSplit("(abc)(def)", "a", "b", "c", "d", "e", "f");
        assertTryWithSplit("(abc)(de)(fg)", "a", "b", "c", "d", "e", "f", "g");
        assertTryWithSplit("(ab)(cd)(ef)(gh)", "a", "b", "c", "d", "e", "f", "g", "h");
        assertTryWithSplit("(ab)(cd)(ef)(ghi)", "a", "b", "c", "d", "e", "f", "g", "h", "i");
        assertTryWithSplit("(ab)(cde)(fg)(hij)", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        assertTryWithSplit("(ab)(cde)(fgh)(ijk)", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
        assertTryWithSplit("(abc)(def)(ghi)(jkl)", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l");
        assertTryWithSplit("(abc)(def)(ghi)(jk)(lm)", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m");

        assertTryWithSplit("(ab)", "ab");
        assertTryWithSplit("(ab)(cde)", "ab", "cde");
        assertTryWithSplit("(ab)(cde)(f)", "ab", "cde", "f");
        assertTryWithSplit("(ab)(cde)(fg)", "ab", "cde", "f", "g");
        assertTryWithSplit("(ab)(cde)(f)(ghi)", "ab", "cde", "f", "g", "hi");
        assertTryWithSplit("(ab)(cde)(f)(g)(hij)", "ab", "cde", "f", "g", "hi", "j");
    }

    @Test
    void testTryWithSplitExceptions() {
        TooLargeException e = assertThrows(TooLargeException.class, () -> tryWithSplit("a", "bcde", "fg", "hijk"));
        assertEquals("Task too large: bcde", e.getMessage());
        assertEquals("Task too large: hijk", e.getSuppressed()[0].getMessage());
        // but some batches have been processed nevertheless
        assertEquals("(a)(fg)", SB.toString());
    }

}
