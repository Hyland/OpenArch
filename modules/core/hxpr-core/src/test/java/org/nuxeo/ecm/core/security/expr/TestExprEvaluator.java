/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.security.expr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.security.expr.ExpressionSecurityPolicy.CONTEXT_CONTENT;
import static org.nuxeo.ecm.core.security.expr.ExpressionSecurityPolicy.CONTEXT_USER;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.security.expr.ast.Expr;

public class TestExprEvaluator {

    private static final String NOVAL_PREFIX = "NOVAL: ";

    private static Operand evaluateOperand(String string, NuxeoPrincipal principal) {
        Map<String, Object> context = Map.of( //
                CONTEXT_CONTENT, ExprEvaluator.CONTENT, //
                CONTEXT_USER, principal);
        Expr expr = new ExpressionVisitor().parse(string);
        return new ExprEvaluator(context).evaluateOperand(expr);
    }

    private static Object evaluateValue(String string, NuxeoPrincipal principal, Map<String, Object> content) {
        InternalDocument idoc = mock(InternalDocument.class);
        content.forEach((key, value) -> {
            when(idoc.getValue(key)).thenReturn(value);
        });
        Map<String, Object> context = Map.of( //
                CONTEXT_CONTENT, idoc, //
                CONTEXT_USER, principal);
        Expr expr = new ExpressionVisitor().parse(string);
        return new ExprEvaluator(context).evaluateValue(expr);
    }

    private static void check(String expression, String expectedOperand, Object expectedValue) {
        UserPrincipal user = new UserPrincipal("", null, false);
        check(user, expression, expectedOperand, expectedValue);
    }

    private static void check(NuxeoPrincipal user, String expression, String expectedOperand, Object expectedValue) {
        Map<String, Object> content = Map.of();
        check(user, content, expression, expectedOperand, expectedValue);
    }

    private static void check(NuxeoPrincipal user, Map<String, Object> content, String expression,
            String expectedOperand, Object expectedValue) {
        // operand
        Operand operand = evaluateOperand(expression, user);
        assertEquals(expectedOperand, String.valueOf(operand));
        // value
        if (expectedValue instanceof String str && str.startsWith(NOVAL_PREFIX)) {
            try {
                Object res = evaluateValue(expression, user, content);
                fail("should have failed: " + expression + " but got: " + res);
            } catch (IllegalArgumentException e) {
                assertEquals(str.substring(NOVAL_PREFIX.length()), e.getMessage());
            }
        } else {
            assertEquals(expectedValue, evaluateValue(expression, user, content));
        }
    }

    private static void check(Map<String, Object> content, String expression, String expectedOperand,
            Object expectedValue) {
        UserPrincipal user = new UserPrincipal("", null, false);
        check(user, content, expression, expectedOperand, expectedValue);
    }

    private static void checkFailOperand(NuxeoPrincipal user, String expression, String expectedException) {
        try {
            Operand operand = evaluateOperand(expression, user);
            fail("Should have failed to evaluate: " + expression + " but got: " + operand);
        } catch (IllegalArgumentException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }

    private static void checkFail(String expression, String expectedException) {
        UserPrincipal user = new UserPrincipal("", null, false);
        checkFailOperand(user, expression, expectedException);
    }

    @Test
    public void testConstantFolding() {
        // negation
        check("!false", "true", true);
        check("!true", "false", false);
        check("!null", "null", null);

        // boolean constant folding
        check("true || true", "true", true);
        check("true || false", "true", true);
        check("true || null", "true", true);
        check("false || true", "true", true);
        check("false || false", "false", false);
        check("false || null", "null", null);
        check("null || true", "true", true);
        check("null || false", "null", null);
        check("null || null", "null", null);
        check("true && true", "true", true);
        check("true && false", "false", false);
        check("true && null", "null", null);
        check("false && true", "false", false);
        check("false && false", "false", false);
        check("false && null", "false", false);
        check("null && true", "null", null);
        check("null && false", "false", false);
        check("null && null", "null", null);
        check("true == true", "true", true);
        check("true == false", "false", false);
        check("true == null", "false", false);
        check("false == true", "false", false);
        check("false == false", "true", true);
        check("false == null", "false", false);
        check("true != true", "false", false);
        check("true != false", "true", true);
        check("true != null", "true", true);
        check("false != true", "true", true);
        check("false != false", "false", false);
        check("false != null", "true", true);
        check("null != true", "true", true);
        check("null != false", "true", true);
        check("null != null", "false", false);

        // long expressions
        check("100 / 2 * 1 + 5 - 13", "42", 42L);
        check("1 == 1 && 1 != 2", "true", true);
        check("1 != 1 || 1 == 2", "false", false);
        check("1 < 2 && 3 > -5 && 1 <= 2 && 3 >= -5", "true", true);
        check("1 > 2 || 3 < -5 || 1 >= 2 || 3 <= -5", "false", false);
        check("1 == null", "false", false);
        check("1 != null", "true", true);
        check("null == 1", "false", false);
        check("null != 1", "true", true);
        check("1 + null", "null", null);
        check("1 - null", "null", null);
        check("1 * null", "null", null);
        check("1 / null", "null", null);

        // double expressions
        check("100.0 / 2.0 * 1.0 + 5.0 - 13.0", "42.0", 42.0);
        check("1.0 == 1.0 && 1.0 != 2.0", "true", true);
        check("1.0 != 1.0 || 1.0 == 2.0", "false", false);
        check("1.0 < 2.0 && 3.0 > -5.0 && 1.0 <= 2.0 && 3.0 >= -5.0", "true", true);
        check("1.0 > 2.0 || 3.0 < -5.0 || 1.0 >= 2.0 || 3.0 <= -5.0", "false", false);
        check("1.0 == null", "false", false);
        check("1.0 != null", "true", true);
        check("null == 1.0", "false", false);
        check("null != 1.0", "true", true);
        check("1.0 + null", "null", null);
        check("1.0 - null", "null", null);
        check("1.0 * null", "null", null);
        check("1.0 / null", "null", null);

        // promotion of long to double
        check("1 + 2.0 == 1.0 + 2", "true", true);

        // string comparisons
        check("\"foo\" == \"foo\"", "true", true);
        check("\"foo\" == \"bar\"", "false", false);
        check("\"foo\" == null", "false", false);
        check("null == \"foo\"", "false", false);
        check("\"foo\" != \"foo\"", "false", false);
        check("\"foo\" != \"bar\"", "true", true);
        check("\"foo\" != null", "true", true);
        check("null != \"foo\"", "true", true);

        // string concatenation
        check("\"foo\" + \"bar\"", "'foobar'", "foobar");
        check("\"foo\" + null", "'foonull'", "foonull");
        check("null + \"foo\"", "'nullfoo'", "nullfoo");

        // promotion of string to long for comparison
        check("1 == \"1\"", "true", true);
        check("1 != \"1\"", "false", false);
        check("5 > \"1\"", "true", true);
        check("5 >= \"1\"", "true", true);
        check("10 < \"1\"", "false", false);
        check("10 <= \"1\"", "false", false);
        check("\"1\" == 1", "true", true);
        check("\"1\" != 1", "false", false);
        check("\"1\" < 5", "true", true);
        check("\"1\" <= 5", "true", true);
        check("\"1\" > 10", "false", false);
        check("\"1\" >= 10", "false", false);

        // promotion of string to double for comparison
        check("1 == \"1.0\"", "true", true);
        check("1 != \"1.0\"", "false", false);
        check("5 > \"1.0\"", "true", true);
        check("5 >= \"1.0\"", "true", true);
        check("10 < \"1.0\"", "false", false);
        check("10 <= \"1.0\"", "false", false);
        check("\"1.0\" == 1", "true", true);
        check("\"1.0\" != 1", "false", false);
        check("\"1.0\" < 5", "true", true);
        check("\"1.0\" <= 5", "true", true);
        check("\"1.0\" > 10", "false", false);
        check("\"1.0\" >= 10", "false", false);

        // promotion of numeric to string for concatenation
        check("1 + \"foo\"", "'1foo'", "1foo");
        check("1.0 + \"foo\"", "'1.0foo'", "1.0foo");
        check("\"foo\" + 1", "'foo1'", "foo1");
        check("\"foo\" + 1.0", "'foo1.0'", "foo1.0");

        // ternary
        check("true ? 123 : 456", "123", 123L);
        check("false ? 123 : 456", "456", 456L);
        check("null ? 123 : 456", "null", null);
    }

    @Test
    public void testEvaluationFailures() {
        checkFail("!1", "Invalid expression: (! 1)");
        checkFail("true + true", "Invalid expression: (true + true)");
        checkFail("true + 1", "Invalid expression: (true + 1)");
        checkFail("\"foo\" + true", "Invalid expression: (\"foo\" + true)");
        checkFail("true + \"foo\"", "Invalid expression: (true + \"foo\")");
        checkFail("\"foo\" < true", "Invalid expression: (\"foo\" < true)");
        checkFail("true < \"foo\"", "Invalid expression: (true < \"foo\")");
        checkFail("\"foo\" - \"foo\"", "Invalid expression: (\"foo\" - \"foo\")");
        checkFail("1 == \"NaN\"", "Invalid expression: (1 == \"NaN\")");
        checkFail("1 == \"1e999\"", "Invalid expression: (1 == \"1e999\")");
        checkFail("1 == \"foo\"", "Invalid expression: (1 == \"foo\")");
        checkFail("1 && 1", "Invalid expression: (1 && 1)");
        checkFail("1.0 && 1.0", "Invalid expression: (1.0 && 1.0)");
        checkFail("1 ? 123 : 456", "Invalid expression: (1 ? 123 : 456)");

        checkFail("foo", "No global property: foo");
        checkFail("foo()", "Cannot call: foo()");
        checkFail("content", "Invalid expression: content");
        checkFail("content == 1", "Invalid expression: (content == 1)");
        checkFail("1 == content", "Invalid expression: (1 == content)");
        checkFail("content.foo()", "Cannot call: content.foo()");
        checkFail("user.id.foo", "Cannot dereference: user.id.foo");
        checkFail("user.foo()", "Cannot call: user.foo()");
    }

    @Test
    public void testContent() {
        check("content.sys_title == \"secret\" || content.sys_description == \"\"",
                "((sys_title = 'secret') OR (sys_description = ''))", false);
        check("!content.sys_isFolderish", "NOT sys_isFolderish", null);
        check("content.sys_isFolderish || true", "true", true);
        check("content.sys_isFolderish || false", "sys_isFolderish", null);
        check("true || content.sys_isFolderish", "true", true);
        check("false || content.sys_isFolderish", "sys_isFolderish", null);
        check("content.sys_isFolderish && true", "sys_isFolderish", null);
        check("content.sys_isFolderish && false", "false", false);
        check("true && content.sys_isFolderish", "sys_isFolderish", null);
        check("false && content.sys_isFolderish", "false", false);
        check("content.sys_title == \"foo\"", "(sys_title = 'foo')", false);
        check("content.sys_title + \"foo\"", "(sys_title + 'foo')", "nullfoo");
        check("\"foo\" + content.sys_title", "('foo' + sys_title)", "foonull");
        check("content.sys_title == null", "sys_title IS NULL", true);
        check("content.sys_title != null", "sys_title IS NOT NULL", false);
        // promotion of string to long for comparison
        check("content.sys_title < \"123\"", "(sys_title < 123)", false);
    }

    @Test
    public void testUser() {
        NuxeoPrincipal user = new UserPrincipal("alice", List.of("auditors", "folks"), false);
        user.setProperties(Map.of("clearance", 10L, "int", 10, "float", 1.0F, "str", "10"));

        Map<String, Object> content = Map.of("my_groups", "auditors");

        // static object in context
        check(user, "user.foo", "null", null);
        check(user, "user.id", "'alice'", "alice");
        check(user, "user.clearance > 5", "true", true);
        check(user, "user.clearance < 5", "false", false);
        check(user, "user.int", "10", 10L);
        check(user, "user.float", "1.0", 1.0);
        check(user, "user.str", "'10'", "10");

        // both with constant folding
        check(user, "content.sys_title == \"secret\" || user.clearance < 5", //
                "(sys_title = 'secret')", //
                false);

        // contains
        check(user, "user.groups.contains(\"auditors\")", "true", true);
        check(user, "user.groups.contains(\"audi\" + \"tors\")", "true", true);
        check(user, "user.groups.contains(\"foo\")", "false", false);
        check(user, "user.groups.contains(\"auditors\", \"foo\")", "false", false);
        check(user, "user.groups.contains(\"auditors\", \"folks\")", "true", true);
        check(user, "user.groups.contains(123)", "false", false);

        // contains with property
        check(user, content, "user.groups.contains(content.my_groups)", "(my_groups IN ('auditors', 'folks'))", true);
        // contains with property on empty list
        user = new UserPrincipal("alice", List.of(), false);
        check(user, content, "user.groups.contains(content.my_groups)", "false", false);
        // contains with nonexistent property (evaluated to null)
        check(user, content, "user.groups.contains(content.non_existent)", "false", false);
    }

    @Test
    public void testNullComparisons() {
        UserPrincipal user = new UserPrincipal("", null, false);
        Map<String, Object> content = Map.of();
        // null values compare like if they were a regular value
        check(user, content, "content.sys_title == 10", "(sys_title = 10)", false);
        check(user, content, "content.sys_title != 10", "(sys_title <> 10)", true);
        check(user, content, "content.sys_title > 10", "(sys_title > 10)", false);
        check(user, content, "content.sys_title >= 10", "(sys_title >= 10)", false);
        check(user, content, "content.sys_title < 10", "(sys_title < 10)", false);
        check(user, content, "content.sys_title <= 10", "(sys_title <= 10)", false);
        check(user, content, "content.sys_title == 1.0", "(sys_title = 1.0)", false);
        check(user, content, "content.sys_title != 1.0", "(sys_title <> 1.0)", true);
        check(user, content, "content.sys_title > 1.0", "(sys_title > 1.0)", false);
        check(user, content, "content.sys_title >= 1.0", "(sys_title >= 1.0)", false);
        check(user, content, "content.sys_title < 1.0", "(sys_title < 1.0)", false);
        check(user, content, "content.sys_title <= 1.0", "(sys_title <= 1.0)", false);
        check(user, content, "content.non_existent.sys_title == 10", "(non_existent/sys_title = 10)", false);
    }

    @Test
    public void testFailures() {
        NuxeoPrincipal user = new UserPrincipal("alice", List.of(), false);

        checkFailOperand(user, "user.groups.contains()",
                "Cannot use contains with no arguments: user.groups.contains()");
        checkFailOperand(user, "user.groups.contains(\"foo\", content.sys_title)",
                "Invalid expression: user.groups.contains(\"foo\", content.sys_title)");
        checkFailOperand(user, "user.id.contains(\"foo\")",
                "Cannot use contains on non-list: user.id.contains(\"foo\")");
        checkFailOperand(user, "user.id.contains(foo)", "No global property: foo");
        checkFailOperand(user, "user.id.contains(user)", "Invalid expression: user");
        checkFailOperand(user, "user.id.contains(content)", "Invalid expression: content");
        checkFailOperand(user, "user.id.contains(content.sys_title)",
                "Cannot use contains on non-list: user.id.contains(content.sys_title)");
    }

    @Test
    public void testMapPropertyAccess() {
        // Test with Map containing string values
        Map<String, Object> mapProperties = Map.of("Key1", "val1", "Key2", "val2", "numKey", 123L);
        Map<String, Object> content = Map.of("mapProperty", mapProperties);

        // Basic map property access
        check(content, "content.mapProperty.Key1 == \"val1\"", "(mapProperty/Key1 = 'val1')", true);
        check(content, "content.mapProperty.Key2 == \"val2\"", "(mapProperty/Key2 = 'val2')", true);
        check(content, "content.mapProperty.Key1 == \"wrong\"", "(mapProperty/Key1 = 'wrong')", false);

        // Numeric values in map
        check(content, "content.mapProperty.numKey == 123", "(mapProperty/numKey = 123)", true);
        check(content, "content.mapProperty.numKey > 100", "(mapProperty/numKey > 100)", true);

        // Non-existent key (should return null and fail comparison)
        check(content, "content.mapProperty.nonExistent == \"val\"", "(mapProperty/nonExistent = 'val')", false);

        // Combined expressions
        check(content, "content.mapProperty.Key1 == \"val1\" && content.mapProperty.Key2 == \"val2\"",
                "((mapProperty/Key1 = 'val1') AND (mapProperty/Key2 = 'val2'))", true);
    }

    @Test
    public void testNestedMapPropertyAccess() {
        // Test with nested Maps
        Map<String, Object> nestedMap = Map.of("innerKey", "innerValue");
        Map<String, Object> outerMap = Map.of("nested", nestedMap);
        Map<String, Object> content = Map.of("complexProperty", outerMap);

        // Nested map access
        check(content, "content.complexProperty.nested.innerKey == \"innerValue\"",
                "(complexProperty/nested/innerKey = 'innerValue')", true);
    }

}
