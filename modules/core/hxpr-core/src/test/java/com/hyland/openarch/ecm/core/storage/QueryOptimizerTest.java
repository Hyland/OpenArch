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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.sql.model.MultiExpression;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.StringLiteral;

/**
 * Unit tests for the prefix-grouping helpers of {@link QueryOptimizer} (which accepts a {@code null} schema manager).
 */
class QueryOptimizerTest {

    /** Minimal concrete optimizer with no schema manager, sufficient for the prefix helpers. */
    private static class TestOptimizer extends QueryOptimizer {

        TestOptimizer() {
            super(null);
        }

        @Override
        public String getCorrelatedWildcardPrefix(String name) {
            return "";
        }
    }

    private static Predicate pred(String reference) {
        return new Predicate(new Reference(reference), Operator.EQ, new StringLiteral("v"));
    }

    @Test
    void findPrefixFindsCommonPrefixAndPartitions() {
        List<String> strings = new ArrayList<>(List.of("a", "a/b", "a/c", "x"));
        List<String> withPrefix = new ArrayList<>();

        String prefix = QueryOptimizer.findPrefix(strings, withPrefix);

        assertEquals("a", prefix);
        assertEquals(List.of("a/b", "a/c"), withPrefix);
        assertEquals(List.of("x"), strings); // prefix and its children removed
    }

    @Test
    void findPrefixReturnsNullWhenNoCommonPrefix() {
        List<String> strings = new ArrayList<>(List.of("a", "b", "c"));
        List<String> withPrefix = new ArrayList<>();

        assertNull(QueryOptimizer.findPrefix(strings, withPrefix));
        assertEquals(List.of("a", "b", "c"), strings);
        assertEquals(List.of(), withPrefix);
    }

    @Test
    void makeSingleAndPredicateReturnsSoleElement() {
        TestOptimizer optimizer = new TestOptimizer();
        Predicate only = pred("a/x");
        assertSame(only, optimizer.makeSingleAndPredicate("a", List.of(only)));
    }

    @Test
    void makeSingleAndPredicateBuildsAndForMultiple() {
        TestOptimizer optimizer = new TestOptimizer();
        Predicate p1 = pred("x");
        Predicate p2 = pred("y");

        Predicate result = optimizer.makeSingleAndPredicate("", List.of(p1, p2));

        MultiExpression multi = assertInstanceOf(MultiExpression.class, result);
        assertEquals(Operator.AND, multi.operator);
        assertEquals(2, multi.predicates.size());
    }

    @Test
    void reorganizeNestsPrefixGroup() {
        TestOptimizer optimizer = new TestOptimizer();
        Map<String, Predicate> grouped = new LinkedHashMap<>();
        grouped.put("a", pred("a/x"));
        grouped.put("a/b", pred("a/b/y"));

        optimizer.reorganizeGroupedExpressions(grouped);

        assertEquals(List.of("a"), new ArrayList<>(grouped.keySet()));
        assertInstanceOf(MultiExpression.class, grouped.get("a"));
    }

    @Test
    void reorganizeThrowsOnTooComplexCorrelatedWildcards() {
        TestOptimizer optimizer = new TestOptimizer();
        Map<String, Predicate> grouped = new LinkedHashMap<>();
        grouped.put("a", pred("a/x"));
        grouped.put("a/b", pred("a/b/y"));
        grouped.put("a/c", pred("a/c/z"));

        assertThrows(QueryParseException.class, () -> optimizer.reorganizeGroupedExpressions(grouped));
    }

    @Test
    void reorganizeLeavesUnrelatedGroupsUnchanged() {
        TestOptimizer optimizer = new TestOptimizer();
        Map<String, Predicate> grouped = new LinkedHashMap<>();
        Predicate a = pred("a/x");
        Predicate b = pred("b/y");
        grouped.put("a", a);
        grouped.put("b", b);

        optimizer.reorganizeGroupedExpressions(grouped);

        assertEquals(List.of("a", "b"), new ArrayList<>(grouped.keySet()));
        assertSame(a, grouped.get("a"));
        assertSame(b, grouped.get("b"));
    }
}
