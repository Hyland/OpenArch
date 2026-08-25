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
package com.hyland.hxpr.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_CHECKED_IN;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PREFIX;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.schema.SchemaManager;
import com.hyland.hxpr.expr.ExpressionVisitor;
import com.hyland.hxpr.expr.ast.Expr;

public class TestExprEvaluator {

    private static final String NOVAL_PREFIX = "NOVAL: ";

    /** No runtime here, so the schema manager is mocked with the true-or-null booleans it knows about. */
    private static final SchemaManager SCHEMA_MANAGER = mockSchemaManager();

    private static SchemaManager mockSchemaManager() {
        SchemaManager schemaManager = mock(SchemaManager.class);
        Set.of(SYSVER_IS_VERSION, SYSVER_IS_CHECKED_IN, SYS_IS_FOLDERISH, SYS_IS_TRASHED, SYSGOV_IS_RECORD,
                SYSGOV_HAS_LEGAL_HOLD)
           .forEach(key -> when(schemaManager.isTrueOrNullBoolean(key)).thenReturn(true));
        // the actual forbidden properties are the schema manager's business, here only sys_acl is forbidden
        when(schemaManager.isForbiddenInExpr(SYS_ACL)).thenReturn(true);
        return schemaManager;
    }

    private static Operand evaluateOperand(String string, NuxeoPrincipal principal) {
        Map<String, Object> context = ExprEvaluator.queryContext(principal);
        Expr expr = new ExpressionVisitor().parse(string);
        return new ExprEvaluator(context, SCHEMA_MANAGER).evaluateOperand(expr);
    }

    private static Object evaluateValue(String string, NuxeoPrincipal principal, Map<String, Object> content) {
        InternalDocument idoc = mock(InternalDocument.class);
        content.forEach((key, value) -> {
            // Mock sys properties via getPropertyValue, schema properties via getValue
            if (key.startsWith(SYS_PREFIX)) {
                when(idoc.getPropertyValue(key)).thenReturn(value);
            } else {
                when(idoc.getValue(key)).thenReturn(value);
            }
        });
        Map<String, Object> context = ExprEvaluator.documentContext(idoc, principal);
        Expr expr = new ExpressionVisitor().parse(string);
        return new ExprEvaluator(context, SCHEMA_MANAGER).evaluateValue(expr);
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

    /**
     * Checks an expression that cannot be turned into a query filter but still evaluates against a document.
     */
    private static void checkNoOperand(String expression, String expectedException, Object expectedValue) {
        UserPrincipal user = new UserPrincipal("", null, false);
        checkFailOperand(user, expression, expectedException);
        assertEquals(expectedValue, evaluateValue(expression, user, Map.of()));
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
        check("!false", "TRUE", true);
        check("!true", "FALSE", false);
        check("!null", "null", null);

        // boolean constant folding
        check("true || true", "TRUE", true);
        check("true || false", "TRUE", true);
        check("true || null", "TRUE", true);
        check("false || true", "TRUE", true);
        check("false || false", "FALSE", false);
        check("false || null", "null", null);
        check("null || true", "TRUE", true);
        check("null || false", "null", null);
        check("null || null", "null", null);
        check("true && true", "TRUE", true);
        check("true && false", "FALSE", false);
        check("true && null", "null", null);
        check("false && true", "FALSE", false);
        check("false && false", "FALSE", false);
        check("false && null", "FALSE", false);
        check("null && true", "null", null);
        check("null && false", "FALSE", false);
        check("null && null", "null", null);
        check("true == true", "TRUE", true);
        check("true == false", "FALSE", false);
        check("true == null", "FALSE", false);
        check("false == true", "FALSE", false);
        check("false == false", "TRUE", true);
        check("false == null", "FALSE", false);
        check("true != true", "FALSE", false);
        check("true != false", "TRUE", true);
        check("true != null", "TRUE", true);
        check("false != true", "TRUE", true);
        check("false != false", "FALSE", false);
        check("false != null", "TRUE", true);
        check("null != true", "TRUE", true);
        check("null != false", "TRUE", true);
        check("null != null", "FALSE", false);

        // long expressions
        check("100 / 2 * 1 + 5 - 13", "42", 42L);
        check("1 == 1 && 1 != 2", "TRUE", true);
        check("1 != 1 || 1 == 2", "FALSE", false);
        check("1 < 2 && 3 > -5 && 1 <= 2 && 3 >= -5", "TRUE", true);
        check("1 > 2 || 3 < -5 || 1 >= 2 || 3 <= -5", "FALSE", false);
        check("1 == null", "FALSE", false);
        check("1 != null", "TRUE", true);
        check("null == 1", "FALSE", false);
        check("null != 1", "TRUE", true);
        check("1 + null", "null", null);
        check("1 - null", "null", null);
        check("1 * null", "null", null);
        check("1 / null", "null", null);

        // double expressions
        check("100.0 / 2.0 * 1.0 + 5.0 - 13.0", "42.0", 42.0);
        check("1.0 == 1.0 && 1.0 != 2.0", "TRUE", true);
        check("1.0 != 1.0 || 1.0 == 2.0", "FALSE", false);
        check("1.0 < 2.0 && 3.0 > -5.0 && 1.0 <= 2.0 && 3.0 >= -5.0", "TRUE", true);
        check("1.0 > 2.0 || 3.0 < -5.0 || 1.0 >= 2.0 || 3.0 <= -5.0", "FALSE", false);
        check("1.0 == null", "FALSE", false);
        check("1.0 != null", "TRUE", true);
        check("null == 1.0", "FALSE", false);
        check("null != 1.0", "TRUE", true);
        check("1.0 + null", "null", null);
        check("1.0 - null", "null", null);
        check("1.0 * null", "null", null);
        check("1.0 / null", "null", null);

        // promotion of long to double
        check("1 + 2.0 == 1.0 + 2", "TRUE", true);

        // string comparisons
        check("\"foo\" == \"foo\"", "TRUE", true);
        check("\"foo\" == \"bar\"", "FALSE", false);
        check("\"foo\" == null", "FALSE", false);
        check("null == \"foo\"", "FALSE", false);
        check("\"foo\" != \"foo\"", "FALSE", false);
        check("\"foo\" != \"bar\"", "TRUE", true);
        check("\"foo\" != null", "TRUE", true);
        check("null != \"foo\"", "TRUE", true);

        // string concatenation
        check("\"foo\" + \"bar\"", "'foobar'", "foobar");
        check("\"foo\" + null", "'foonull'", "foonull");
        check("null + \"foo\"", "'nullfoo'", "nullfoo");

        // promotion of string to long for comparison
        check("1 == \"1\"", "TRUE", true);
        check("1 != \"1\"", "FALSE", false);
        check("5 > \"1\"", "TRUE", true);
        check("5 >= \"1\"", "TRUE", true);
        check("10 < \"1\"", "FALSE", false);
        check("10 <= \"1\"", "FALSE", false);
        check("\"1\" == 1", "TRUE", true);
        check("\"1\" != 1", "FALSE", false);
        check("\"1\" < 5", "TRUE", true);
        check("\"1\" <= 5", "TRUE", true);
        check("\"1\" > 10", "FALSE", false);
        check("\"1\" >= 10", "FALSE", false);

        // promotion of string to double for comparison
        check("1 == \"1.0\"", "TRUE", true);
        check("1 != \"1.0\"", "FALSE", false);
        check("5 > \"1.0\"", "TRUE", true);
        check("5 >= \"1.0\"", "TRUE", true);
        check("10 < \"1.0\"", "FALSE", false);
        check("10 <= \"1.0\"", "FALSE", false);
        check("\"1.0\" == 1", "TRUE", true);
        check("\"1.0\" != 1", "FALSE", false);
        check("\"1.0\" < 5", "TRUE", true);
        check("\"1.0\" <= 5", "TRUE", true);
        check("\"1.0\" > 10", "FALSE", false);
        check("\"1.0\" >= 10", "FALSE", false);

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
                "sys_title = 'secret' OR sys_description = ''", false);
        check("!content.sys_isFolderish", "NOT sys_isFolderish", true);
        check("content.sys_isFolderish || true", "TRUE", true);
        check("content.sys_isFolderish || false", "sys_isFolderish", false);
        check("true || content.sys_isFolderish", "TRUE", true);
        check("false || content.sys_isFolderish", "sys_isFolderish", false);
        check("content.sys_isFolderish && true", "sys_isFolderish", false);
        check("content.sys_isFolderish && false", "FALSE", false);
        check("true && content.sys_isFolderish", "sys_isFolderish", false);
        check("false && content.sys_isFolderish", "FALSE", false);
        check("content.sys_title == \"foo\"", "sys_title = 'foo'", false);
        // arithmetic on a content property still evaluates against a document, but cannot become a query filter
        checkNoOperand("content.sys_title + \"foo\"",
                "Cannot use arithmetic on a content property in a query: (content.sys_title + \"foo\")", "nullfoo");
        checkNoOperand("\"foo\" + content.sys_title",
                "Cannot use arithmetic on a content property in a query: (\"foo\" + content.sys_title)", "foonull");
        check("content.sys_title == null", "sys_title IS NULL", true);
        check("content.sys_title != null", "sys_title IS NOT NULL", false);
        // promotion of string to long for comparison
        check("content.sys_title < \"123\"", "sys_title < 123", false);
    }

    @Test
    public void testUser() {
        NuxeoPrincipal user = new UserPrincipal("alice", List.of("auditors", "folks"), false);
        user.setProperties(Map.of("clearance", 10L, "int", 10, "float", 1.0F, "str", "10"));

        Map<String, Object> content = Map.of("my_groups", "auditors");

        // static object in context
        check(user, "user.foo", "null", null);
        check(user, "user.id", "'alice'", "alice");
        check(user, "user.clearance > 5", "TRUE", true);
        check(user, "user.clearance < 5", "FALSE", false);
        check(user, "user.int", "10", 10L);
        check(user, "user.float", "1.0", 1.0);
        check(user, "user.str", "'10'", "10");

        // both with constant folding
        check(user, "content.sys_title == \"secret\" || user.clearance < 5", //
                "sys_title = 'secret'", //
                false);

        // contains
        check(user, "user.groups.contains(\"auditors\")", "TRUE", true);
        check(user, "user.groups.contains(\"audi\" + \"tors\")", "TRUE", true);
        check(user, "user.groups.contains(\"foo\")", "FALSE", false);
        check(user, "user.groups.contains(\"auditors\", \"foo\")", "FALSE", false);
        check(user, "user.groups.contains(\"auditors\", \"folks\")", "TRUE", true);
        check(user, "user.groups.contains(123)", "FALSE", false);

        // contains with property
        check(user, content, "user.groups.contains(content.my_groups)", "my_groups IN ('auditors', 'folks')", true);
        // contains with property on empty list
        user = new UserPrincipal("alice", List.of(), false);
        check(user, content, "user.groups.contains(content.my_groups)", "FALSE", false);
        // contains with nonexistent property (evaluated to null)
        check(user, content, "user.groups.contains(content.non_existent)", "FALSE", false);
    }

    @Test
    public void testNullComparisons() {
        UserPrincipal user = new UserPrincipal("", null, false);
        Map<String, Object> content = Map.of();
        // null values compare like if they were a regular value
        check(user, content, "content.sys_title == 10", "sys_title = 10", false);
        check(user, content, "content.sys_title != 10", "sys_title <> 10", true);
        check(user, content, "content.sys_title > 10", "sys_title > 10", false);
        check(user, content, "content.sys_title >= 10", "sys_title >= 10", false);
        check(user, content, "content.sys_title < 10", "sys_title < 10", false);
        check(user, content, "content.sys_title <= 10", "sys_title <= 10", false);
        check(user, content, "content.sys_title == 1.0", "sys_title = 1.0", false);
        check(user, content, "content.sys_title != 1.0", "sys_title <> 1.0", true);
        check(user, content, "content.sys_title > 1.0", "sys_title > 1.0", false);
        check(user, content, "content.sys_title >= 1.0", "sys_title >= 1.0", false);
        check(user, content, "content.sys_title < 1.0", "sys_title < 1.0", false);
        check(user, content, "content.sys_title <= 1.0", "sys_title <= 1.0", false);
        check(user, content, "content.non_existent.sys_title == 10", "non_existent/sys_title = 10", false);
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
        check(content, "content.mapProperty.Key1 == \"val1\"", "mapProperty/Key1 = 'val1'", true);
        check(content, "content.mapProperty.Key2 == \"val2\"", "mapProperty/Key2 = 'val2'", true);
        check(content, "content.mapProperty.Key1 == \"wrong\"", "mapProperty/Key1 = 'wrong'", false);

        // Numeric values in map
        check(content, "content.mapProperty.numKey == 123", "mapProperty/numKey = 123", true);
        check(content, "content.mapProperty.numKey > 100", "mapProperty/numKey > 100", true);

        // Non-existent key (should return null and fail comparison)
        check(content, "content.mapProperty.nonExistent == \"val\"", "mapProperty/nonExistent = 'val'", false);

        // Combined expressions
        check(content, "content.mapProperty.Key1 == \"val1\" && content.mapProperty.Key2 == \"val2\"",
                "mapProperty/Key1 = 'val1' AND mapProperty/Key2 = 'val2'", true);
    }

    @Test
    public void testNestedMapPropertyAccess() {
        // Test with nested Maps
        Map<String, Object> nestedMap = Map.of("innerKey", "innerValue");
        Map<String, Object> outerMap = Map.of("nested", nestedMap);
        Map<String, Object> content = Map.of("complexProperty", outerMap);

        // Nested map access
        check(content, "content.complexProperty.nested.innerKey == \"innerValue\"",
                "complexProperty/nested/innerKey = 'innerValue'", true);
    }

    @Test
    public void testSysProperties() {
        UserPrincipal user = new UserPrincipal("", null, false);

        // Test sys_primaryType evaluation
        Map<String, Object> content1 = Map.of("sys_primaryType", "SysFile");
        check(user, content1, "content.sys_primaryType == \"SysFile\"", "sys_primaryType = 'SysFile'", true);

        // Test sys_isFolderish evaluation
        Map<String, Object> content2 = Map.of("sys_isFolderish", true);
        check(user, content2, "content.sys_isFolderish == true", "sys_isFolderish = TRUE", true);

        // Test sys_isFolderish with null (not folderish) (this is a true-or-null boolean property)
        Map<String, Object> content3 = Map.of();
        check(user, content3, "content.sys_isFolderish == false", "sys_isFolderish = FALSE", true);
    }

    @Test
    public void testDisallowedProperty() {
        UserPrincipal user = new UserPrincipal("", null, false);
        // sys_acl is a forbidden property (see mockSchemaManager)
        String expression = "content." + SYS_ACL + " == null";

        // the check is done in the same way whether an operand or a value is computed
        var operandException = assertThrows(IllegalArgumentException.class, () -> evaluateOperand(expression, user));
        var valueException = assertThrows(IllegalArgumentException.class,
                () -> evaluateValue(expression, user, Map.of()));
        assertEquals(operandException.getMessage(), valueException.getMessage());
        assertTrue(operandException.getMessage().startsWith("Disallowed system property: sys_acl"),
                operandException.getMessage());
    }

}
