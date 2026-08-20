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
package org.nuxeo.ecm.core.query.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;

/**
 * @since 12.0
 */
public class TestNXQLSerializer {

    /**
     * Asserts that the query serializes to the expected text, and that the text parses back to the same tree.
     */
    protected static void check(String expected, String query) {
        SQLQuery parsed = SQLQueryParser.parse(query);
        String serialized = NXQLSerializer.serialize(parsed);
        assertEquals(expected, serialized);
        assertEquals(parsed, SQLQueryParser.parse(serialized), "does not round-trip: " + serialized);
    }

    /** Asserts that the query serializes to itself, i.e. it is already in canonical form. */
    protected static void checkIdentity(String query) {
        check(query, query);
    }

    @Test
    public void testSelectFrom() {
        checkIdentity("SELECT * FROM SysContent");
        checkIdentity("SELECT sys_title FROM SysContent");
        checkIdentity("SELECT sys_title, sys_id FROM SysContent");
        checkIdentity("SELECT COUNT(sys_id) FROM SysContent");
        checkIdentity("SELECT * FROM SysContent, Note");
    }

    @Test
    public void testComparisons() {
        checkIdentity("SELECT * FROM SysContent WHERE sys_title = 'a'");
        checkIdentity("SELECT * FROM SysContent WHERE sys_title <> 'a'");
        checkIdentity("SELECT * FROM SysContent WHERE size >= 3");
        checkIdentity("SELECT * FROM SysContent WHERE size <= 3.5");
        checkIdentity("SELECT * FROM SysContent WHERE sys_title LIKE 'a%'");
        checkIdentity("SELECT * FROM SysContent WHERE sys_title NOT ILIKE 'a%'");
        checkIdentity("SELECT * FROM SysContent WHERE sys_path STARTSWITH '/a/b'");
        checkIdentity("SELECT * FROM SysContent WHERE sysver_isVersion = TRUE");
        // != is accepted on input, <> is the canonical rendering
        check("SELECT * FROM SysContent WHERE sys_title <> 'a'", "SELECT * FROM SysContent WHERE sys_title != 'a'");
    }

    @Test
    public void testNull() {
        checkIdentity("SELECT * FROM SysContent WHERE sys_title IS NULL");
        checkIdentity("SELECT * FROM SysContent WHERE sys_title IS NOT NULL");
    }

    @Test
    public void testInAndBetween() {
        checkIdentity("SELECT * FROM SysContent WHERE sys_title IN ('a', 'b')");
        checkIdentity("SELECT * FROM SysContent WHERE sys_title NOT IN ('a', 'b')");
        checkIdentity("SELECT * FROM SysContent WHERE size BETWEEN 1 AND 3");
        checkIdentity("SELECT * FROM SysContent WHERE size NOT BETWEEN 1 AND 3");
        checkIdentity("SELECT * FROM SysContent WHERE sys_created BETWEEN"
                + " TIMESTAMP '2020-01-01T00:00:00.000Z' AND TIMESTAMP '2021-01-01T00:00:00.000Z'");
        checkIdentity("SELECT * FROM SysContent WHERE sys_created BETWEEN DATE '2020-01-01' AND DATE '2021-01-01'");
    }

    @Test
    public void testPrecedence() {
        // redundant parentheses are dropped
        check("SELECT * FROM SysContent WHERE a = 1 AND b = 2", "SELECT * FROM SysContent WHERE (a = 1) AND (b = 2)");
        check("SELECT * FROM SysContent WHERE a = 1 AND b = 2 AND c = 3",
                "SELECT * FROM SysContent WHERE ((a = 1) AND (b = 2)) AND (c = 3)");
        // required parentheses are kept
        checkIdentity("SELECT * FROM SysContent WHERE (a = 1 OR b = 2) AND c = 3");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 OR b = 2 AND c = 3");
        check("SELECT * FROM SysContent WHERE a = 1 OR b = 2 AND c = 3",
                "SELECT * FROM SysContent WHERE a = 1 OR (b = 2 AND c = 3)");
        checkIdentity("SELECT * FROM SysContent WHERE NOT (a = 1 AND b = 2)");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 + 2 * 3");
        checkIdentity("SELECT * FROM SysContent WHERE a = (1 + 2) * 3");
    }

    /**
     * Every binary operator is left-associative, so a right operand that binds equally tightly keeps its
     * parentheses: without them the text would re-parse left-nested, which is a different tree.
     */
    @Test
    public void testRightNestedOperatorsKeepTheirParentheses() {
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 - (2 - 3)");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 - (2 + 3)");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 + (2 - 3)");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 + (2 + 3)");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 / (2 * 3)");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 * (2 / 3)");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 * (2 * 3)");
        checkIdentity("SELECT * FROM SysContent WHERE p = 1 AND (q = 2 AND r = 3)");
        checkIdentity("SELECT * FROM SysContent WHERE p = 1 OR (q = 2 OR r = 3)");
        // a left-nested chain needs none of them
        check("SELECT * FROM SysContent WHERE a = 1 - 2 - 3", "SELECT * FROM SysContent WHERE a = (1 - 2) - 3");
        check("SELECT * FROM SysContent WHERE a = 1 * 2 / 3", "SELECT * FROM SysContent WHERE a = (1 * 2) / 3");
        check("SELECT * FROM SysContent WHERE p = 1 AND q = 2 AND r = 3",
                "SELECT * FROM SysContent WHERE (p = 1 AND q = 2) AND r = 3");
        // nor does a right operand that binds more tightly
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 - 2 * 3");
        checkIdentity("SELECT * FROM SysContent WHERE p = 1 OR q = 2 AND r = 3");
    }

    @Test
    public void testOrderByLimitOffset() {
        checkIdentity("SELECT * FROM SysContent ORDER BY sys_title");
        checkIdentity("SELECT * FROM SysContent ORDER BY sys_title DESC");
        checkIdentity("SELECT * FROM SysContent ORDER BY sys_created DESC, sys_title");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 ORDER BY sys_title LIMIT 10");
        checkIdentity("SELECT * FROM SysContent WHERE a = 1 ORDER BY sys_title LIMIT 10 OFFSET 5");
    }

    @Test
    public void testFulltextHint() {
        checkIdentity("SELECT * FROM SysContent WHERE /*+FT: INDEX(sys_title.custom) */ sys_title LIKE 'a'");
        checkIdentity("SELECT * FROM SysContent WHERE /*+FT: ANALYZER(fr_analyzer) */ sys_fulltext = 'a'");
        checkIdentity("SELECT * FROM SysContent WHERE"
                + " /*+FT: INDEX(sys_title.fulltext) OPERATOR(fuzzy) */ sys_fulltext = 'a'");
    }

    /**
     * String literals are escaped on the way out; this is what keeps a tree built from untrusted values from producing
     * a query that means something other than the tree says.
     */
    @Test
    public void testStringEscaping() {
        checkIdentity("SELECT * FROM SysContent WHERE sys_title = 'O\\'Brien'");
        checkIdentity("SELECT * FROM SysContent WHERE sys_title = 'back\\\\slash'");
        checkIdentity("SELECT * FROM SysContent WHERE sys_title = 'a\\nb'");
        // a value that would break out of its literal if it were not escaped
        Predicate predicate = Predicates.eq("sys_title", "x' OR sys_title LIKE '%");
        assertEquals("sys_title = 'x\\' OR sys_title LIKE \\'%'", NXQLSerializer.serialize(predicate));
        SQLQuery reparsed = SQLQueryParser.parse("SELECT * FROM SysContent WHERE " + NXQLSerializer.serialize(predicate));
        assertEquals(new org.nuxeo.ecm.core.query.sql.model.WhereClause(predicate), reparsed.getWhereClause());
    }

    @Test
    public void testDoubleQuotedStringsBecomeSingleQuoted() {
        check("SELECT * FROM SysContent WHERE sys_title = 'a'", "SELECT * FROM SysContent WHERE sys_title = \"a\"");
    }

    @Test
    public void testSerializePredicate() {
        assertEquals("foo = 'f1'", NXQLSerializer.serialize(Predicates.eq("foo", "f1")));
        assertEquals("foo IS NULL", NXQLSerializer.serialize(Predicates.isnull("foo")));
        assertEquals("foo IN ('f1', 'f2')", NXQLSerializer.serialize(Predicates.in("foo", List.of("f1", "f2"))));
        assertEquals("foo BETWEEN 1 AND 3", NXQLSerializer.serialize(Predicates.between("foo", 1, 3)));
        assertEquals("foo = 'f1' AND bar = 'b1'", NXQLSerializer.serialize(
                new MultiExpression(Operator.AND, List.of(Predicates.eq("foo", "f1"), Predicates.eq("bar", "b1")))));
        assertEquals("foo = 'f1' AND (bar = 'b1' OR bar = 'b2')",
                NXQLSerializer.serialize(new MultiExpression(Operator.AND,
                        List.of(Predicates.eq("foo", "f1"),
                                new MultiExpression(Operator.OR,
                                        List.of(Predicates.eq("bar", "b1"), Predicates.eq("bar", "b2")))))));
    }

    @Test
    public void testEmptyMultiExpression() {
        // an empty conjunction is vacuously true, an empty disjunction vacuously false
        assertEquals("1 = 1", NXQLSerializer.serialize(new MultiExpression(Operator.AND, List.of())));
        assertEquals("1 = 0", NXQLSerializer.serialize(new MultiExpression(Operator.OR, List.of())));
    }

    /**
     * A where clause holding an empty conjunction can be left out, being vacuously true. One holding an empty
     * disjunction cannot: dropping it would turn a query matching nothing into one matching everything.
     */
    @Test
    public void testEmptyMultiExpressionAsWhereClause() {
        assertEquals("SELECT * FROM SysContent", serializeWithPredicate(new MultiExpression(Operator.AND, List.of())));
        assertEquals("SELECT * FROM SysContent WHERE 1 = 0",
                serializeWithPredicate(new MultiExpression(Operator.OR, List.of())));
    }

    protected static String serializeWithPredicate(Predicate predicate) {
        return NXQLSerializer.serialize(SQLQueryParser.parse("SELECT * FROM SysContent").withPredicate(predicate));
    }

}
