/*
 * (C) Copyright 2020-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.query.sql.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.query.sql.NXQLSerializer;

/**
 * A node's {@link Object#toString} is its NXQL rendering, produced by {@link NXQLSerializer}: there is one renderer,
 * so debug output cannot drift from what would actually be executed.
 */
public class TestToString {

    /** Asserts the expected NXQL, and that toString and the serializer agree on it. */
    protected static void check(String expected, Operand operand) {
        assertEquals(expected, operand.toString());
        assertEquals(expected, NXQLSerializer.serialize(operand));
    }

    @Test
    public void testAndOr() {
        var f = Predicates.eq("foo", "f1");
        check("foo = 'f1'", f);
        var b = Predicates.or(Predicates.eq("bar", "b1"), Predicates.eq("bar", "b2"));
        check("bar = 'b1' OR bar = 'b2'", b);
        // the disjunction keeps the parentheses it needs inside a conjunction
        check("foo = 'f1' AND (bar = 'b1' OR bar = 'b2')", Predicates.and(f, b));
    }

    @Test
    public void testIn() {
        check("foo IN ()", Predicates.in("foo", List.of()));
        check("foo IN ('f1')", Predicates.in("foo", List.of("f1")));
        check("foo IN ('f1', 'f2')", Predicates.in("foo", List.of("f1", "f2")));
    }

    @Test
    public void testIsNull() {
        var f = Predicates.isnull("foo");
        check("foo IS NULL", f);
        var b = Predicates.isnotnull("bar");
        check("bar IS NOT NULL", b);
        check("foo IS NULL AND bar IS NOT NULL", Predicates.and(f, b));
    }

    @Test
    public void testNot() {
        check("NOT foo = 'f1'", Predicates.not(Predicates.eq("foo", "f1")));
        check("NOT (foo = 'f1' AND bar = 'b1')",
                Predicates.not(Predicates.and(Predicates.eq("foo", "f1"), Predicates.eq("bar", "b1"))));
    }

    @Test
    public void testMultiExpression() {
        var f = Predicates.eq("foo", "f1");
        var b = Predicates.eq("bar", "b1");
        var g = Predicates.eq("gee", "g1");
        // an empty conjunction is vacuously true
        check("1 = 1", new MultiExpression(Operator.AND, List.of()));
        check("foo = 'f1'", new MultiExpression(Operator.AND, List.of(f)));
        check("foo = 'f1' AND bar = 'b1'", new MultiExpression(Operator.AND, List.of(f, b)));
        check("foo = 'f1' AND bar = 'b1' AND gee = 'g1'", new MultiExpression(Operator.AND, List.of(f, b, g)));
    }

    @Test
    public void testLeaves() {
        check("sys_title", new Reference("sys_title"));
        check("DATE(sys_created)", new Reference("sys_created", "DATE"));
        check("'a\\'b'", new StringLiteral("a'b"));
        check("TRUE", BooleanLiteral.LIT_TRUE);
        check("42", new IntegerLiteral(42));
        check(":param", new Parameter("param"));
        check("COUNT(sys_id)", new Function("COUNT", new OperandList(List.of(new Reference("sys_id")))));
    }

}
