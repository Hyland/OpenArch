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
package com.hyland.openarch.ecm.core.query.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT;

import java.util.List;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.sql.model.BooleanLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.DateLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.DoubleLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.FromClause;
import com.hyland.openarch.ecm.core.query.sql.model.FtHint;
import com.hyland.openarch.ecm.core.query.sql.model.Function;
import com.hyland.openarch.ecm.core.query.sql.model.IntegerLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.LiteralList;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.query.sql.model.OperandList;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.OrderByClause;
import com.hyland.openarch.ecm.core.query.sql.model.OrderByExpr;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.SelectClause;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.query.sql.model.StringLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.WhereClause;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class TestQueryParser {

    static final String[] GOOD_QUERIES = {
            "SELECT name, title, description FROM SysFolder WHERE state = 2 AND created > \"20060523\"" };

    static final String[] BAD_QUERIES = {
            "SELECT name WHERE title, description FROM SysFolder WHERE state = 2 AND created > \"20060523\"", "name, ",
            "SELECT FROM WHERE", "SELECT name WHERE state=2", "PLEASE GET ME SOME DOCUMENTS" };

    /**
     * Checks that literals are correctly parsed.
     */
    @Test
    public void testLiterals() {
        // test double quoted strings
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE title=\"%test\"");
        StringLiteral sl = (StringLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals("%test", sl.value());

        query = HXQLParser.parse("SELECT p FROM t WHERE title=\"%te\\\"s't\"");
        sl = (StringLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals("%te\"s't", sl.value());

        query = HXQLParser.parse("SELECT p FROM t WHERE title=\"te\\\\st\"");
        sl = (StringLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals("te\\st", sl.value());

        // test single quoted strings
        query = HXQLParser.parse("SELECT p FROM t WHERE title='%te\\'s\"t'");
        sl = (StringLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals("%te's\"t", sl.value());

        query = HXQLParser.parse("SELECT p FROM t WHERE title='te\\\\st'");
        sl = (StringLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals("te\\st", sl.value());

        // integers
        query = HXQLParser.parse("SELECT p FROM t WHERE title=12");
        IntegerLiteral il = (IntegerLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals(12, il.value());

        // doubles
        query = HXQLParser.parse("SELECT p FROM t WHERE title=1.2");
        DoubleLiteral dl = (DoubleLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals(1.2, dl.value(), 1e-8);

        query = HXQLParser.parse("SELECT p FROM t WHERE title=.2");
        dl = (DoubleLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals(0.2, dl.value(), 1e-8);

        query = HXQLParser.parse("SELECT p FROM t WHERE title=-1.2");
        dl = (DoubleLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals(-1.2, dl.value(), 1e-8);

        // dates
        DateLiteral datel;
        query = HXQLParser.parse("SELECT p FROM t WHERE title = DATE '2007-01-30'");
        datel = (DateLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals("DATE '2007-01-30'", datel.toString());
        query = HXQLParser.parse("SELECT p FROM t WHERE title = TIMESTAMP '2007-01-30 01:02:03+04:00'");
        datel = (DateLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals("TIMESTAMP '2007-01-29T21:02:03.000Z'", datel.toString());

        // booleans
        query = HXQLParser.parse("SELECT p FROM t WHERE active = TRUE");
        BooleanLiteral bl = (BooleanLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals(true, bl.value());
        assertSame(BooleanLiteral.LIT_TRUE, bl);

        query = HXQLParser.parse("SELECT p FROM t WHERE active = FALSE");
        bl = (BooleanLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals(false, bl.value());
        assertSame(BooleanLiteral.LIT_FALSE, bl);

        // case-insensitive
        query = HXQLParser.parse("SELECT p FROM t WHERE active = true");
        bl = (BooleanLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals(true, bl.value());

        query = HXQLParser.parse("SELECT p FROM t WHERE active = False");
        bl = (BooleanLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals(false, bl.value());
    }

    @Test
    public void testDoubleBackslash() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE title='a\\\\b'");
        StringLiteral sl = (StringLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals("a\\b", sl.value());

        query = HXQLParser.parse("SELECT p FROM t WHERE title='a\\\\'");
        sl = (StringLiteral) query.getWhereClause().predicate().rvalue;
        assertEquals("a\\", sl.value());
    }

    @Test
    public void testNamespace() {
        Statement query = HXQLParser.parse("SELECT sys_title FROM SysContent WHERE sys_description = 'test'");
        SelectClause select = query.getSelectClause();
        String v = ((Reference) select.elements().get(0)).name();
        assertEquals("sys_title", v);
    }

    @Test
    public void testComplexProperties() {
        Statement query = HXQLParser.parse("SELECT sys_foo/bar/baz FROM SysContent"
                + " WHERE sys_foo/3/ho = sys_bar/*/bobby" + " OR sys_foo/5/gee = sys_foo/*6/hop");
        SelectClause select = query.getSelectClause();
        String v = ((Reference) select.elements().get(0)).name();
        assertEquals("sys_foo/bar/baz", v);
        Predicate where = query.getWhereClause().predicate();
        Predicate p1 = (Predicate) where.lvalue;
        Predicate p2 = (Predicate) where.rvalue;
        assertEquals("sys_foo/3/ho", ((Reference) p1.lvalue).name());
        assertEquals("sys_bar/*/bobby", ((Reference) p1.rvalue).name());
        assertEquals("sys_foo/5/gee", ((Reference) p2.lvalue).name());
        assertEquals("sys_foo/*6/hop", ((Reference) p2.rvalue).name());

        // check parsing of complex properties in other contexts
        HXQLParser.parse("SELECT x FROM SysContent" + " WHERE sys_foo/bar LIKE 'foo' OR sys_foo/bar IS NULL"
                + " OR sys_foo/bar BETWEEN 1 AND 1000" + " ORDER BY sys_foo/bar");
    }

    @Test
    public void testVariables() {
        Statement query = HXQLParser.parse("SELECT p1, $id, p3 FROM t1, t2 WHERE state=1 AND title = 'test'");

        SelectClause select = query.getSelectClause();
        assertEquals("p1", ((Reference) select.elements().get(0)).name());
        assertEquals("$id", ((Reference) select.elements().get(1)).name());
        assertEquals("p3", ((Reference) select.elements().get(2)).name());

        FromClause from = query.getFromClause();
        assertEquals("t1", from.elements().get(0));
        assertEquals("t2", from.elements().get(1));

        Predicate e1 = (Predicate) query.getWhereClause().predicate().lvalue;
        Predicate e2 = (Predicate) query.getWhereClause().predicate().rvalue;
        assertEquals("state", ((Reference) e1.lvalue).name());
        assertEquals("title", ((Reference) e2.lvalue).name());
    }

    @Test
    public void testOperators() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE p = 'test'");
        Operator op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.EQ, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p > 1");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.GT, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p >= 1");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.GTEQ, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p < 1");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.LT, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p <= 1");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.LTEQ, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p <> 1");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.NOTEQ, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p != 1");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.NOTEQ, op);

    }

    @Test
    public void testLikeOperator() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE p LIKE '%test%'");
        Operator op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.LIKE, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p NOT LIKE '%test%'");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.NOTLIKE, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p ILIKE '%test%'");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.ILIKE, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p NOT ILIKE '%test%'");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.NOTILIKE, op);
    }

    @Test
    public void testInOperator() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE p IN (12, 13) AND q='test'");
        Operator op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.AND, op);

        Predicate e = (Predicate) query.getWhereClause().predicate().lvalue;
        assertEquals(Operator.IN, e.operator);

        LiteralList list = new LiteralList(List.of(
                new IntegerLiteral(12),
                new IntegerLiteral(13)));
        assertEquals(list, e.rvalue);
        assertEquals("p", ((Reference) e.lvalue).name());

        query = HXQLParser.parse("SELECT p FROM t WHERE p NOT IN (12, 13) AND q='test'");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.AND, op);

        e = (Predicate) query.getWhereClause().predicate().lvalue;
        assertEquals(Operator.NOTIN, e.operator);

        list = new LiteralList(List.of(new IntegerLiteral(12), new IntegerLiteral(13)));
        assertEquals(list, e.rvalue);
        assertEquals("p", ((Reference) e.lvalue).name());
    }

    @Test
    public void testBetweenOperator() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE p BETWEEN 10 AND 20 AND q='test'");
        Operator op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.AND, op);

        Predicate e = (Predicate) query.getWhereClause().predicate().lvalue;
        assertEquals(Operator.BETWEEN, e.operator);

        LiteralList list = new LiteralList(List.of(
                new IntegerLiteral(10),
                new IntegerLiteral(20)));
        assertEquals(list, e.rvalue);
        assertEquals("p", ((Reference) e.lvalue).name());

        query = HXQLParser.parse("SELECT p FROM t WHERE p NOT BETWEEN 10 AND 20 AND q='test'");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.AND, op);

        e = (Predicate) query.getWhereClause().predicate().lvalue;
        assertEquals(Operator.NOTBETWEEN, e.operator);

        list = new LiteralList(List.of(new IntegerLiteral(10), new IntegerLiteral(20)));
        assertEquals(list, e.rvalue);
        assertEquals("p", ((Reference) e.lvalue).name());
    }

    @Test
    public void testIsNull() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE p IS NULL");
        Operator op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.ISNULL, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p IS NOT NULL");
        op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.ISNOTNULL, op);

        query = HXQLParser.parse("SELECT p FROM t WHERE p IS NULL OR p = 'abc'");
        Predicate p = (Predicate) query.getWhereClause().predicate().lvalue;
        assertEquals(Operator.ISNULL, p.operator);
    }

    @Test
    public void testFunction() {
        Statement query = HXQLParser.parse("SELECT getDate(), p FROM t WHERE substring(title, 2) = 'test'");
        Operator op = query.getWhereClause().predicate().operator;
        assertEquals(Operator.EQ, op);

        Function fn = (Function) query.getWhereClause().predicate().lvalue;
        assertEquals("substring", fn.name());

        OperandList args = new OperandList(List.of(
                new Reference("title"),
                new IntegerLiteral(2)));
        assertEquals(args, fn.args());
    }

    @Test
    public void testFunctions() {
        Statement query = HXQLParser.parse("SELECT COUNT(p), AVG(p) FROM t");
        SelectClause select = query.getSelectClause();
        assertEquals(2, select.elements().size());

        OperandList ops = new OperandList(List.of(new Reference("p")));
        assertEquals(new Function("COUNT", ops), select.elements().get(0));
        assertEquals(new Function("AVG", ops), select.elements().get(1));
    }

    @Test
    public void testDateCast() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE DATE(sys_modified) = DATE '2010-01-01'");

        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference); // with cast
        assertEquals(new Reference("sys_modified", "DATE"), lvalue);
    }

    @Test
    public void testEsHintIndex() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE /*+FT: INDEX(sys_title.ngram) */ sys_title = 'foo'");
        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(1, ((Reference) lvalue).ftHint().indexFields().size());
        assertEquals(new Reference(new Reference("sys_title"),
                new FtHint("sys_title.ngram", null, null)), lvalue);
    }

    @Test
    public void testEsHintIndexWithDash() {
        Statement query = HXQLParser.parse(
                "SELECT p FROM t WHERE /*+FT: INDEX(my-schema:article.author) */ my-schema:article/author = 'foo'");
        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(1, ((Reference) lvalue).ftHint().indexFields().size());
        assertEquals(new Reference(new Reference("my-schema:article/author"),
                new FtHint("my-schema:article.author", null, null)), lvalue);
    }

    @Test
    public void testEsHintIndexBoost() {
        Statement query = HXQLParser.parse(
                "SELECT p FROM t WHERE /*+ft: INDEX(sys_title.ngram^3) */ sys_title = 'foo'");
        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(1, ((Reference) lvalue).ftHint().indexFields().size());
        assertEquals(new Reference(new Reference("sys_title"),
                new FtHint("sys_title.ngram^3", null, null)), lvalue);
    }

    @Test
    public void testEsHintMultiIndex() {
        Statement query = HXQLParser.parse(
                "SELECT p FROM t WHERE /*+FT: INDEX(sys_title,sys_description) */ sys_fulltext = 'foo'");
        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(2, ((Reference) lvalue).ftHint().indexFields().size());
        assertEquals(new Reference(new Reference(SYS_FULLTEXT),
                new FtHint("sys_title,sys_description", null, null)), lvalue);
    }

    @Test
    public void testEsHintAnalyzer() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE /*+FT: ANALYZER(fulltext) */ sys_title LIKE 'foo'");
        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(new Reference(new Reference("sys_title"), new FtHint((String) null, "fulltext", null)), lvalue);
    }

    @Test
    public void testEsHintOperator() {
        Statement query = HXQLParser.parse(
                "SELECT p FROM t WHERE /*+FT: OPERATOR(regex) */ sys_title = 'foo|bar|ba*'");
        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(new Reference(new Reference("sys_title"), new FtHint((String) null, null, "regex")), lvalue);
    }

    @Test
    public void testEsHintIndexAndAnalyzer() {
        Statement query = HXQLParser.parse(
                "SELECT p FROM t WHERE /*+FT: Index(sys_title.ngram) analyzer(fulltext) */ sys_title = 'foo'");
        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(new Reference(new Reference("sys_title"),
                new FtHint("sys_title.ngram", "fulltext", null)), lvalue);
    }

    @Test
    public void testEsHintIndexAnalyzerAndOperator() {
        Statement query = HXQLParser.parse(
                "SELECT p FROM t WHERE /*+FT: Index(sys_title.ngram) analyzer(fulltext) operator(fuzzy)*/ sys_title = 'foo'");
        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(new Reference(new Reference("sys_title"),
                new FtHint("sys_title.ngram", "fulltext", "fuzzy")), lvalue);
    }

    @Test
    public void testEsHintMultiClauses() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE /*+FT: INDEX(sys_title.ngram) */ sys_title = 'foo' "
                + "AND /*+FT: ANALYZER(fulltext2) */ sys_description LIKE 'bar'");
        Operand pred = query.getWhereClause().predicate().rvalue;
        assertTrue(pred instanceof Predicate);
        Operand lvalue = ((Predicate) pred).lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(new Reference(new Reference("sys_description"), new FtHint((String) null, "fulltext2", null)), lvalue);
    }

    @Test
    public void testEsHintEmpty() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE /*+FT: */ sys_title = 'foo'");
        Operand lvalue = query.getWhereClause().predicate().lvalue;
        assertTrue(lvalue instanceof Reference);
        assertEquals(new Reference(new Reference("sys_title"), (FtHint) null), lvalue);
    }

    @Test
    public void testAliasIsNotSupported() {
        assertThrows(QueryParseException.class, () -> HXQLParser.parse("SELECT p AS pp FROM t"));
        assertThrows(QueryParseException.class, () -> HXQLParser.parse("SELECT p FROM t AS t1"));
    }

    @Test
    public void testArithmeticIsNotSupported() {
        assertThrows(QueryParseException.class, () -> HXQLParser.parse("SELECT p FROM t WHERE p + 2 = 3"));
        assertThrows(QueryParseException.class, () -> HXQLParser.parse("SELECT p FROM t WHERE p - 2 = 3"));
        assertThrows(QueryParseException.class, () -> HXQLParser.parse("SELECT p FROM t WHERE p * 2 = 3"));
        assertThrows(QueryParseException.class, () -> HXQLParser.parse("SELECT p FROM t WHERE p / 2 = 3"));
        // a negative number is still a literal
        assertEquals(-2L, ((IntegerLiteral) HXQLParser.parse("SELECT p FROM t WHERE p = -2")
                                                          .getWhereClause()
                                                          .predicate()
                                                          .rvalue).value());
    }

    /**
     * AND binds tighter than OR, NOT tighter than AND, and both AND and OR are left-associative.
     */
    @Test
    public void testOperatorPrecedence() {
        Statement query = HXQLParser.parse("SELECT p FROM t WHERE p=2 AND q=1 AND s=3 OR r = 4 OR w = 10");
        Predicate pred = query.getWhereClause().predicate();
        assertEquals(Operator.OR, pred.operator);
        assertEquals(Operator.EQ, ((Predicate) pred.rvalue).operator);

        Predicate e = (Predicate) pred.lvalue;
        assertEquals(Operator.OR, e.operator);
        assertEquals(Operator.EQ, ((Predicate) e.rvalue).operator);

        e = (Predicate) e.lvalue;
        assertEquals(Operator.AND, e.operator);
        assertEquals(Operator.EQ, ((Predicate) e.rvalue).operator);

        e = (Predicate) e.lvalue;
        assertEquals(Operator.AND, e.operator);
        assertEquals(Operator.EQ, ((Predicate) e.rvalue).operator);
        assertEquals(Operator.EQ, ((Predicate) e.lvalue).operator);

        query = HXQLParser.parse("SELECT p FROM t WHERE p=2 OR s=3 AND NOT q=4");
        pred = query.getWhereClause().predicate();
        assertEquals(Operator.OR, pred.operator);
        assertEquals(Operator.EQ, ((Predicate) pred.lvalue).operator);

        e = (Predicate) pred.rvalue;
        assertEquals(Operator.AND, e.operator);
        assertEquals(Operator.EQ, ((Predicate) e.lvalue).operator);

        e = (Predicate) e.rvalue;
        assertEquals(Operator.NOT, e.operator);
        assertEquals(Operator.EQ, ((Predicate) e.lvalue).operator);
    }

    @Test
    public void testGoodQueries() {
        int i = 0;
        try {
            for (; i < GOOD_QUERIES.length; i++) {
                HXQLParser.parse(GOOD_QUERIES[i]);
            }
        } catch (QueryParseException e) {
            // parse error
            fail("Failed to parse a good query: " + GOOD_QUERIES[i]);
        }
    }

    /**
     * Tests that parsing fails for bad queries (queries from BAD_QUERIES array).
     */
    @Test
    public void testBadQueries() {
        for (String badQuery : BAD_QUERIES) {
            try {
                HXQLParser.parse(badQuery);
                // Not so bad this query: bad query was successfully parsed -> error
                fail("A bad Query has been successfully parsed: " + badQuery);
            } catch (QueryParseException e) {
                // this is really a bad query -> continue
            }
        }
    }

    /**
     * Author the generated AST.
     *
     * <pre>
     *              OR
     *    p1&gt;0                  AND
     *              p2&lt;=10.2            =
     *                          p1-p2        5
     * </pre>
     *
     * TODO: add tests for DateLiteral, other operators, paranthesys etc
     */
    @Test
    public void testAST() {
        Statement query = HXQLParser.parse("SELECT p1, p2 FROM table WHERE p1 > 0 OR p2 <= 10.2 AND p1 = 5");

        List<Operand> select = query.getSelectClause().elements();
        assertEquals(2, select.size());

        Predicate where = query.getWhereClause().predicate();
        assertEquals(Operator.OR, where.operator);

        Predicate expr = (Predicate) where.lvalue;
        assertEquals(Operator.GT, expr.operator);
        assertEquals("p1", ((Reference) expr.lvalue).name());
        assertEquals(0, ((IntegerLiteral) expr.rvalue).value());

        // root expr
        expr = (Predicate) where.rvalue;
        assertEquals(Operator.AND, expr.operator);

        Predicate expr1 = (Predicate) expr.lvalue;
        assertEquals(Operator.LTEQ, expr1.operator);
        assertEquals("p2", ((Reference) expr1.lvalue).name());
        assertEquals(10.2, ((DoubleLiteral) expr1.rvalue).value(), 1e-8);

        Predicate expr2 = (Predicate) expr.rvalue;
        assertEquals(Operator.EQ, expr2.operator);
        assertEquals("p1", ((Reference) expr2.lvalue).name());
        assertEquals(5, ((IntegerLiteral) expr2.rvalue).value());
    }

    /**
     * Tests the manual query creation and parser by comparing the two queries (the manual one ith the parsed one).
     *
     * <pre>
     *              OR
     *    title="test"         AND
     *              p2&gt;=10.2            &lt;
     *                          p1+p2        5
     * </pre>
     */
    @Test
    public void testWhereClause() {
        Statement query = HXQLParser.parse(
                "SELECT p1, p2 FROM t WHERE title = \"test\" OR p2 >= 10.2 AND p1 < 5");

        Predicate expr1 = new Predicate(new Reference("p1"), Operator.LT, new IntegerLiteral(5));

        Predicate expr2 = new Predicate(new Reference("p2"), Operator.GTEQ, new DoubleLiteral(10.2));
        expr1 = new Predicate(expr2, Operator.AND, expr1);

        expr2 = new Predicate(new Reference("title"), Operator.EQ, new StringLiteral("test"));

        Predicate pred = new Predicate(expr2, Operator.OR, expr1);

        // create the query by hand
        Statement myquery = new Statement(
                new SelectClause(List.of(new Reference("p1"), new Reference("p2"))), new FromClause(List.of("t")),
                new WhereClause(pred));

        assertEquals(myquery, query);
    }

    /**
     * Same query as before but with parenthesis on first OR condition.
     *
     * <pre>
     *                          AND
     *               OR                     &lt;
     *                                p1+p2      5
     *    title="test"   p2&gt;=10.2
     * </pre>
     */
    @Test
    public void testWhereClauseWithParenthesis() {
        Statement query = HXQLParser.parse(
                "SELECT p1, p2 FROM t WHERE (title = \"test\" OR p2 >= 10.2) AND p1 < 5");

        // create the query by hand
        Predicate expr1 = new Predicate(new Reference("title"), Operator.EQ, new StringLiteral("test"));
        Predicate expr2 = new Predicate(new Reference("p2"), Operator.GTEQ, new DoubleLiteral(10.2));
        expr1 = new Predicate(expr1, Operator.OR, expr2);

        expr2 = new Predicate(new Reference("p1"), Operator.LT, new IntegerLiteral(5));

        Predicate pred = new Predicate(expr1, Operator.AND, expr2);

        Statement myquery = new Statement(
                new SelectClause(List.of(new Reference("p1"), new Reference("p2"))), new FromClause(List.of("t")),
                new WhereClause(pred));

        assertEquals(myquery, query);
    }

    @Test
    public void testSelectClause() {
        Statement query = HXQLParser.parse("SELECT * FROM t");
        assertTrue(query.getSelectClause().elements().isEmpty());
    }

    @Test
    public void testOrderByClause() {
        Statement query = HXQLParser.parse("SELECT p, q, r FROM t ORDER BY p, q");
        String expected = "SELECT p, q, r FROM t ORDER BY p, q";
        assertEquals(expected, query.toString());

        OrderByClause clause = query.getOrderByClause();
        List<OrderByExpr> elements = clause.elements();
        assertEquals("p", elements.get(0).reference().name());
        assertFalse(elements.get(0).isDescending());
        assertEquals("q", elements.get(1).reference().name());
        assertFalse(elements.get(1).isDescending());
        assertEquals(2, elements.size());

        query = HXQLParser.parse("SELECT p, q, r FROM t ORDER BY p, q ASC");
        // ASC is the default, so the canonical form leaves it out
        expected = "SELECT p, q, r FROM t ORDER BY p, q";
        assertEquals(expected, query.toString());

        clause = query.getOrderByClause();
        elements = clause.elements();
        assertEquals("p", elements.get(0).reference().name());
        assertFalse(elements.get(0).isDescending());
        assertEquals("q", elements.get(1).reference().name());
        assertFalse(elements.get(1).isDescending());
        assertEquals(2, elements.size());

        query = HXQLParser.parse("SELECT p, q, r FROM t ORDER BY p, q DESC");
        expected = "SELECT p, q, r FROM t ORDER BY p, q DESC";
        assertEquals(expected, query.toString());

        clause = query.getOrderByClause();
        elements = clause.elements();
        assertEquals("p", elements.get(0).reference().name());
        assertFalse(elements.get(0).isDescending());
        assertEquals("q", elements.get(1).reference().name());
        assertTrue(elements.get(1).isDescending());
        assertEquals(2, elements.size());

        query = HXQLParser.parse("SELECT p, q, r FROM t ORDER BY p DESC, q");
        expected = "SELECT p, q, r FROM t ORDER BY p DESC, q";
        assertEquals(expected, query.toString());

        clause = query.getOrderByClause();
        elements = clause.elements();
        assertEquals("p", elements.get(0).reference().name());
        assertTrue(elements.get(0).isDescending());
        assertEquals("q", elements.get(1).reference().name());
        assertFalse(elements.get(1).isDescending());
        assertEquals(2, elements.size());
    }

}
