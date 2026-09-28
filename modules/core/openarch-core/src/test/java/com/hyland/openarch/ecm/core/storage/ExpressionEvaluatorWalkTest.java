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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.sql.model.BooleanLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.DateLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.Function;
import com.hyland.openarch.ecm.core.query.sql.model.IntegerLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.Literal;
import com.hyland.openarch.ecm.core.query.sql.model.LiteralList;
import com.hyland.openarch.ecm.core.query.sql.model.MultiExpression;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.StringLiteral;

/**
 * Direct tests for the in-memory predicate evaluation logic of {@link ExpressionEvaluator}, driven by a simple
 * map-backed subclass. Covers the operator dispatch in {@code walkExpression} and the individual {@code walk*} methods.
 */
class ExpressionEvaluatorWalkTest {

    /** A minimal evaluator resolving references and mixins from in-memory maps. */
    static class MapEvaluator extends ExpressionEvaluator {

        final Map<String, Object> values;

        final List<String> mixins;

        MapEvaluator(Map<String, Object> values, List<String> mixins, PathResolver resolver) {
            super(resolver);
            this.values = values;
            this.mixins = mixins == null ? List.of() : mixins;
        }

        @Override
        public Object walkReference(Reference ref) {
            return values.get(ref.name());
        }

        @Override
        public Boolean walkMixinTypes(List<String> queried, boolean include) {
            boolean matches = queried.stream().anyMatch(mixins::contains);
            return include ? matches : !matches;
        }
    }

    private static ExpressionEvaluator eval(Map<String, Object> values) {
        return new MapEvaluator(values, List.of(), null);
    }

    private static Reference ref(String name) {
        return new Reference(name);
    }

    private static StringLiteral str(String value) {
        return new StringLiteral(value);
    }

    private static IntegerLiteral integer(long value) {
        return new IntegerLiteral(value);
    }

    private static Predicate pred(Operand lvalue, Operator op, Operand rvalue) {
        return new Predicate(lvalue, op, rvalue);
    }

    @Test
    void equalityOnScalar() {
        ExpressionEvaluator e = eval(Map.of("title", "hello"));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("title"), Operator.EQ, str("hello"))));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref("title"), Operator.EQ, str("other"))));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("title"), Operator.NOTEQ, str("other"))));
    }

    @Test
    void equalityOnListReferenceMatchesAnyElement() {
        ExpressionEvaluator e = eval(Map.of("tags", List.of("a", "b", "c")));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("tags"), Operator.EQ, str("b"))));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref("tags"), Operator.EQ, str("z"))));
    }

    @Test
    void comparisonsOnNumbers() {
        ExpressionEvaluator e = eval(Map.of("num", 5L));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("num"), Operator.LT, integer(10))));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref("num"), Operator.GT, integer(10))));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("num"), Operator.LTEQ, integer(5))));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("num"), Operator.GTEQ, integer(5))));
    }

    @Test
    void comparisonWithMissingValueIsNull() {
        ExpressionEvaluator e = eval(Map.of("num", 5L));
        assertNull(e.walkExpression(pred(ref("missing"), Operator.LT, integer(1))));
    }

    @Test
    void betweenAndNotBetween() {
        ExpressionEvaluator e = eval(Map.of("num", 5L));
        LiteralList range = new LiteralList(List.of(integer(1), integer(10)));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("num"), Operator.BETWEEN, range)));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref("num"), Operator.NOTBETWEEN, range)));

        LiteralList above = new LiteralList(List.of(integer(6), integer(10)));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref("num"), Operator.BETWEEN, above)));
    }

    @Test
    void inAndNotIn() {
        ExpressionEvaluator e = eval(Map.of("num", 5L));
        LiteralList list = new LiteralList(List.of(integer(5), integer(6)));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("num"), Operator.IN, list)));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref("num"), Operator.NOTIN, list)));

        ExpressionEvaluator listEval = eval(Map.of("tags", List.of("a", "b")));
        LiteralList strings = new LiteralList(List.of(str("b"), str("z")));
        assertEquals(Boolean.TRUE, listEval.walkExpression(pred(ref("tags"), Operator.IN, strings)));
    }

    @Test
    void inRejectsNonListRhs() {
        ExpressionEvaluator e = eval(Map.of("num", 5L));
        Predicate predicate = pred(ref("num"), Operator.IN, integer(5));
        assertThrows(QueryParseException.class, () -> e.walkExpression(predicate));
    }

    @Test
    void likeVariants() {
        ExpressionEvaluator e = eval(Map.of("title", "Hello"));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("title"), Operator.LIKE, str("H%"))));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("title"), Operator.ILIKE, str("h%"))));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("title"), Operator.NOTLIKE, str("z%"))));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("title"), Operator.NOTILIKE, str("z%"))));
    }

    @Test
    void likeRejectsNonStringRhs() {
        ExpressionEvaluator e = eval(Map.of("title", "Hello"));
        Predicate predicate = pred(ref("title"), Operator.LIKE, integer(1));
        assertThrows(QueryParseException.class, () -> e.walkExpression(predicate));
    }

    @Test
    void isNullAndIsNotNull() {
        ExpressionEvaluator e = eval(Map.of("title", "hello"));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("missing"), Operator.ISNULL, null)));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref("title"), Operator.ISNOTNULL, null)));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref("title"), Operator.ISNULL, null)));
    }

    @Test
    void andOrNot() {
        ExpressionEvaluator e = eval(Map.of("a", "1", "b", "2"));
        Predicate pa = pred(ref("a"), Operator.EQ, str("1"));
        Predicate pb = pred(ref("b"), Operator.EQ, str("9"));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(pa, Operator.AND, pb)));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(pa, Operator.OR, pb)));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(pa, Operator.NOT, null)));
    }

    @Test
    void multiExpressionAndOr() {
        ExpressionEvaluator e = eval(Map.of("a", "1", "b", "2"));
        Predicate pa = pred(ref("a"), Operator.EQ, str("1"));
        Predicate pb = pred(ref("b"), Operator.EQ, str("2"));
        assertEquals(Boolean.TRUE, e.walkExpression(new MultiExpression(Operator.AND, List.of(pa, pb))));
        Predicate pc = pred(ref("b"), Operator.EQ, str("9"));
        assertEquals(Boolean.TRUE, e.walkExpression(new MultiExpression(Operator.OR, List.of(pa, pc))));
    }

    @Test
    void notRejectsNonBoolean() {
        ExpressionEvaluator e = eval(Map.of("title", "hello"));
        Predicate predicate = pred(ref("title"), Operator.NOT, null);
        assertThrows(QueryParseException.class, () -> e.walkExpression(predicate));
    }

    @Test
    void functionOperandIsUnsupported() {
        ExpressionEvaluator e = eval(Map.of("title", "hello"));
        Predicate predicate = pred(ref("title"), Operator.EQ, new Function("NOW"));
        assertThrows(UnsupportedOperationException.class, () -> e.walkExpression(predicate));
    }

    @Test
    void mixinTypesEqualityAndMembership() {
        ExpressionEvaluator e = new MapEvaluator(Map.of(), List.of("Foo", "Bar"), null);
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref(SYS_MIXIN_TYPES), Operator.EQ, str("Foo"))));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref(SYS_MIXIN_TYPES), Operator.NOTEQ, str("Foo"))));
        LiteralList list = new LiteralList(List.of(str("Bar"), str("Baz")));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref(SYS_MIXIN_TYPES), Operator.IN, list)));
    }

    @Test
    void mixinTypesRejectsNonStringRhs() {
        ExpressionEvaluator e = new MapEvaluator(Map.of(), List.of("Foo"), null);
        Predicate predicate = pred(ref(SYS_MIXIN_TYPES), Operator.EQ, integer(1));
        assertThrows(QueryParseException.class, () -> e.walkExpression(predicate));
    }

    @Test
    void ecmPathResolvesToIdEquality() {
        Map<String, Object> values = new HashMap<>();
        values.put(SYS_ID, "id1");
        ExpressionEvaluator e = new MapEvaluator(values, List.of(), path -> "/a/b".equals(path) ? "id1" : null);
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref(SYS_PATH), Operator.EQ, str("/a/b/"))));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref(SYS_PATH), Operator.EQ, str("/unknown"))));
    }

    @Test
    void ecmPathRejectsBadOperatorAndMissingResolver() {
        ExpressionEvaluator noResolver = new MapEvaluator(new HashMap<>(), List.of(), null);
        Predicate badOperator = pred(ref(SYS_PATH), Operator.LT, str("/a"));
        assertThrows(QueryParseException.class, () -> noResolver.walkExpression(badOperator));
        Predicate missingResolver = pred(ref(SYS_PATH), Operator.EQ, str("/a"));
        assertThrows(QueryParseException.class, () -> noResolver.walkExpression(missingResolver));
    }

    @Test
    void parentPathResolvesToParentIdEquality() {
        Map<String, Object> values = new HashMap<>();
        values.put(SYS_PARENT_ID, "id1");
        ExpressionEvaluator e = new MapEvaluator(values, List.of(), path -> "/a".equals(path) ? "id1" : null);
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref(SYS_PARENT_PATH), Operator.EQ, str("/a/"))));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref(SYS_PARENT_PATH), Operator.EQ, str("/unknown"))));

        Predicate badOperator = pred(ref(SYS_PARENT_PATH), Operator.LIKE, str("/%"));
        assertThrows(QueryParseException.class, () -> e.walkExpression(badOperator));
    }

    @Test
    void ancestorIdMembership() {
        Map<String, Object> values = new HashMap<>();
        values.put(SYS_ANCESTOR_IDS, List.of("anc1", "anc2"));
        ExpressionEvaluator e = new MapEvaluator(values, List.of(), null);
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref(SYS_ANCESTOR_IDS), Operator.EQ, str("anc1"))));
        assertEquals(Boolean.FALSE, e.walkExpression(pred(ref(SYS_ANCESTOR_IDS), Operator.EQ, str("nope"))));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref(SYS_ANCESTOR_IDS), Operator.NOTEQ, str("nope"))));

        ExpressionEvaluator placeless = new MapEvaluator(new HashMap<>(), List.of(), null);
        assertEquals(Boolean.FALSE, placeless.walkExpression(pred(ref(SYS_ANCESTOR_IDS), Operator.EQ, str("anc1"))));
    }

    @Test
    void isTrashed() {
        Map<String, Object> values = new HashMap<>();
        values.put(SYS_IS_TRASHED, Boolean.TRUE);
        ExpressionEvaluator e = new MapEvaluator(values, List.of(), null);
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref(SYS_IS_TRASHED), Operator.EQ, BooleanLiteral.LIT_TRUE)));
        assertEquals(Boolean.TRUE, e.walkExpression(pred(ref(SYS_IS_TRASHED), Operator.EQ, integer(1))));
        Predicate invalidRvalue = pred(ref(SYS_IS_TRASHED), Operator.EQ, str("bad"));
        assertThrows(QueryParseException.class, () -> e.walkExpression(invalidRvalue));
    }

    @Test
    void fulltextEqualityIsUnavailable() {
        ExpressionEvaluator e = eval(Map.of());
        Predicate predicate = pred(ref(SYS_FULLTEXT), Operator.EQ, str("x"));
        assertThrows(QueryParseException.class, () -> e.walkExpression(predicate));
    }

    @Test
    void startsWithOnPathAndNonPath() {
        Map<String, Object> pathValues = new HashMap<>();
        pathValues.put(SYS_ANCESTOR_IDS, List.of("ancX"));
        ExpressionEvaluator pathEval = new MapEvaluator(pathValues, List.of(),
                path -> "/a".equals(path) ? "ancX" : null);
        assertEquals(Boolean.TRUE, pathEval.walkExpression(pred(ref(SYS_PATH), Operator.STARTSWITH, str("/a"))));

        ExpressionEvaluator exact = eval(Map.of("field", "/a/b"));
        assertEquals(Boolean.TRUE, exact.walkExpression(pred(ref("field"), Operator.STARTSWITH, str("/a/b"))));

        ExpressionEvaluator prefix = eval(Map.of("field", "/a/b/c"));
        assertEquals(Boolean.TRUE, prefix.walkExpression(pred(ref("field"), Operator.STARTSWITH, str("/a/b"))));
    }

    @Test
    void dateCastValidation() {
        ExpressionEvaluator e = eval(new HashMap<>());
        Reference dateRef = new Reference("dob", "DATE");
        // a TIMESTAMP literal under a DATE() cast is rejected
        Predicate timestampCast = pred(dateRef, Operator.EQ, new DateLiteral("2020-01-01T10:00:00", false));
        assertThrows(QueryParseException.class, () -> e.walkExpression(timestampCast));
        // a DATE-only literal is accepted
        Literal dateOnly = new DateLiteral("2020-01-01", true);
        Predicate dateOnlyPredicate = pred(dateRef, Operator.EQ, dateOnly);
        assertDoesNotThrow(() -> e.walkExpression(dateOnlyPredicate));
    }
}
