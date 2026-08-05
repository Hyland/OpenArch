/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.security.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.security.expr.ast.Expr;

public class TestExpressionParser {

    private static void testParse(String string, String expected) {
        Expr expr = new ExpressionVisitor().parse(string);
        assertEquals(expected, expr.toStringPretty());
    }

    private static void testParseDebug(String string, String expected) {
        Expr expr = new ExpressionVisitor().parse(string);
        assertEquals(expected, expr.toString());
    }

    private static void testParseFail(String string) {
        try {
            Expr expr = new ExpressionVisitor().parse(string);
            fail("Should have failed: " + string + " but got: " + expr);
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    @Test
    public void testParseString() {
        testParse("\"\"", "\"\"");
        testParse("\"foo\"", "\"foo\"");
        testParse("\"foo\n\tbar\"", "\"foo\\n\\tbar\""); // embedded newline/tab ok
        testParse("\"foo \\\\ \\\" \\t \\n \\r bar\"", "\"foo \\\\ \\\" \\t \\n \\r bar\"");

        testParseFail("");
        testParseFail("'");
        testParseFail("''"); // bad quotes
        testParseFail("'foo'"); // bad quotes
        testParseFail("foo\""); // unclosed start
        testParseFail("\"foo"); // unclosed end
        testParseFail("\"foo\\\""); // backslash at end
        testParseFail("\"\\'\""); // invalid escape
        testParseFail("\"\\X\""); // invalid escape
        testParseFail("\"\\T\""); // invalid escape (even though \t is accepted)
    }

    @Test
    public void testParseNumber() {
        testParseDebug("0", "LitNum[num=0]");
        testParseDebug("0000", "LitNum[num=0]");
        testParseDebug("0", "LitNum[num=0]");
        testParseDebug("+0", "LitNum[num=0]");
        testParseDebug("-0", "LitNum[num=0]");
        testParseDebug("000000", "LitNum[num=0]");
        testParseDebug("+000000", "LitNum[num=0]");
        testParseDebug("-000000", "LitNum[num=0]");
        testParseDebug("12345678", "LitNum[num=12345678]");
        testParseDebug("+12345678", "LitNum[num=12345678]");
        testParseDebug("-12345678", "LitNum[num=-12345678]");
        testParseDebug("123.123", "LitNum[num=123.123]");
        testParseDebug("+123.123", "LitNum[num=123.123]");
        testParseDebug("-123.123", "LitNum[num=-123.123]");
        testParseDebug("1e10", "LitNum[num=1.0E10]");
        testParseDebug("1E10", "LitNum[num=1.0E10]");
        testParseDebug("123e45", "LitNum[num=1.23E47]");
        testParseDebug("+123e45", "LitNum[num=1.23E47]");
        testParseDebug("-123e45", "LitNum[num=-1.23E47]");
        testParseDebug("123e+45", "LitNum[num=1.23E47]");
        testParseDebug("123e-45", "LitNum[num=1.23E-43]");
        testParseDebug("123.456e45", "LitNum[num=1.23456E47]");
        testParseDebug("+123.456e45", "LitNum[num=1.23456E47]");
        testParseDebug("-123.456e45", "LitNum[num=-1.23456E47]");

        testParseFail("++0");
        testParseFail("+-0");
        testParseFail("--0");
        testParseFail("-+0");
        testParseFail("1..2");
        testParseFail("1ee10");
        testParseFail("1e.10");
        testParseFail("1e10.5");
        testParseFail("12345678901234567890"); // too large
        testParseFail("1e999"); // too large
    }

    @Test
    public void testParseBoolean() {
        testParseDebug("true", "LitBool[bool=true]");
        testParseDebug("TRUE", "LitBool[bool=true]");
        testParseDebug("false", "LitBool[bool=false]");
        testParseDebug("FALSE", "LitBool[bool=false]");

        // failures
        testParseDebug("True", "ValId[name=True]");
        testParseDebug("False", "ValId[name=False]");
    }

    @Test
    public void testPrecedence() {
        // associativity: left-to-right for arithmetic
        testParse("1 * 2 * 3", "((1 * 2) * 3)");
        testParse("1 / 2 * 3", "((1 / 2) * 3)");
        testParse("1 * 2 / 3", "((1 * 2) / 3)");

        testParse("1 + 2 + 3", "((1 + 2) + 3)");
        testParse("1 - 2 + 3", "((1 - 2) + 3)");
        testParse("1 + 2 - 3", "((1 + 2) - 3)");

        // precedence: multiplication and division before addition and subtraction
        testParse("1 + 2 * 3", "(1 + (2 * 3))");
        testParse("1 * 2 + 3", "((1 * 2) + 3)");

        // precedence: and before or
        testParse("a || b && c", "(a || (b && c))");
        testParse("a && b || c", "((a && b) || c)");

        // precedence: comparisons before and/or
        testParse("a && b == c", "(a && (b == c))");
        testParse("a == b && c", "((a == b) && c)");
        testParse("a || b == c && d", "(a || ((b == c) && d))");
        testParse("a && b == c || d", "((a && (b == c)) || d)");

        // associativity: left-to-right for comparisons
        testParse("a == b == c", "((a == b) == c)");
        testParse("a == b == c == d", "(((a == b) == c) == d)");
        testParse("a < b > c <= d >= e", "((((a < b) > c) <= d) >= e)");
        testParse("a < b != c", "((a < b) != c)");

        // unary not binds stronger than and/or or comparisons
        testParse("!a && b", "((! a) && b)");
        testParse("!a || b", "((! a) || b)");
        testParse("!a == b", "((! a) == b)");
        testParse("a && !b", "(a && (! b))");
        testParse("a == !b", "(a == (! b))");

        // ternary binds last
        testParse("a || b ? c : d", "((a || b) ? c : d)");
        testParse("a ? b && c : d", "(a ? (b && c) : d)");
        testParse("a ? b : c || d", "(a ? b : (c || d))");

        testParse("a == b ? c : d", "((a == b) ? c : d)");
        testParse("a ? b == c : d", "(a ? (b == c) : d)");
        testParse("a ? b : c == d", "(a ? b : (c == d))");

    }

    @Test
    public void testIdentifiers() {
        // identifiers
        testParseDebug("foo", "ValId[name=foo]");
        testParseDebug("fooBar", "ValId[name=fooBar]");
        testParseDebug("FooBar", "ValId[name=FooBar]");
        testParseDebug("_foo_BAR_123_baz", "ValId[name=_foo_BAR_123_baz]");

    }

    @Test
    public void testDotAndIdsAndCalls() {
        testParseFail("true()");
        testParseFail("true.foo");
        testParseFail("true.foo()");
        testParseFail("true().foo");
        testParseFail("true().foo()");
        testParseFail("foo.true");
        testParseFail("foo().true");
        testParseFail("foo.true()");
        testParseFail("foo().true()");
        testParseFail("foo.true.bar");
        testParseFail("foo.true().bar");
        testParseFail("foo.true.bar()");
        testParseFail("foo.true().bar()");
        testParseFail("foo.bar.true");
        testParseFail("foo.bar.true()");

        // dotted expressions and method calls
        testParseDebug("foo()", "ValCall[name=foo, args=[]]");
        testParseDebug("foo(123, false)", "ValCall[name=foo, args=[LitNum[num=123], LitBool[bool=false]]]");
        testParseDebug("foo.bar()", "ValDot[left=ValId[name=foo], right=ValCall[name=bar, args=[]]]");
        testParseDebug("foo().bar", "ValDot[left=ValCall[name=foo, args=[]], right=ValId[name=bar]]");
        testParseDebug("foo(bar)", "ValCall[name=foo, args=[ValId[name=bar]]]");
        testParseDebug("foo(bar())", "ValCall[name=foo, args=[ValCall[name=bar, args=[]]]]");
        testParseDebug("foo.bar.baz", // associativity is left to right
                "ValDot[left=ValDot[left=ValId[name=foo], right=ValId[name=bar]], right=ValId[name=baz]]");

        testParse("foo.bar.baz", "foo.bar.baz");
        testParse("foo(     )", "foo()");
        testParse("foo(bar,     \t     baz)", "foo(bar, baz)");
        testParse("foo.bar().baz().gee.heh()", "foo.bar().baz().gee.heh()");
        testParse("foo(bar.baz().gee, bar(baz.gee, gee.baz))", "foo(bar.baz().gee, bar(baz.gee, gee.baz))");

        // misc
        testParse("123 * 456 - (4 + 5) == true || (foo && bar) == FALSE",
                "((((123 * 456) - (4 + 5)) == true) || ((foo && bar) == false))");
        testParse("!true || foo ? false : bar()", "(((! true) || foo) ? false : bar())");
    }

}
