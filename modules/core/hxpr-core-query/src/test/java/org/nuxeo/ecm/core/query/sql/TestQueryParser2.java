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

package org.nuxeo.ecm.core.query.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH;

import org.junit.jupiter.api.Test;
import org.nuxeo.common.utils.ExceptionUtils;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.query.sql.model.WhereClause;

/**
 * @author DM
 */
public class TestQueryParser2 {

    static final String[] CANONICAL_QUERIES = { "SELECT * FROM SysContent", "SELECT title FROM SysContent",
            "SELECT title, author FROM SysContent", "SELECT title FROM SysContent WHERE created > '20060523'",
            "SELECT * FROM t",
            "SELECT getDate(), p FROM t WHERE substring(title, 2) = 'test'",
            "SELECT sys_title FROM SysContent WHERE sys_description = 'test'",
            "SELECT name, title, description FROM SysFolder WHERE state = 2 AND created > '20060523'",
            "SELECT p FROM t WHERE p * -2 / 3 + 4 - 5 = 2", "SELECT p FROM t WHERE p * 2 = 3",
            "SELECT p FROM t WHERE p + 2 = 2", "SELECT p FROM t WHERE p - 2 = 3", "SELECT p FROM t WHERE p / 2 = 4",
            "SELECT p FROM t WHERE p < 1", "SELECT p FROM t WHERE p <= 1", "SELECT p FROM t WHERE p <> 1",
            "SELECT p FROM t WHERE p = 'test'", "SELECT p FROM t WHERE p > 1", "SELECT p FROM t WHERE p >= 1",
            "SELECT p FROM t WHERE p LIKE '%test%'", "SELECT p FROM t WHERE p NOT LIKE '%test%'",
            "SELECT p FROM t WHERE p = 2 AND q = 1 AND s = 3 OR r = 4 OR w = 10",
            "SELECT p FROM t WHERE p = 2 OR s = 3 AND NOT q = 4", "SELECT p FROM t WHERE title = DATE '2007-01-30'",
            "SELECT p FROM t WHERE title = TIMESTAMP '2007-01-30T01:02:03.000+04:00'",
            "SELECT p FROM t WHERE title = '%test'", "SELECT p FROM t WHERE title IS NULL",
            "SELECT p FROM t WHERE title IS NOT NULL", "SELECT p FROM t WHERE DATE(title) = DATE '2007-01-30'",
            "SELECT p FROM t WHERE active = true", "SELECT p FROM t WHERE active = false", };

    static final String[] GOOD_QUERIES = { "SELECT * FROM SysContent", "SELECT title FROM SysContent",
            "SELECT title, author FROM SysContent", "SELECT title FROM SysContent WHERE created > \"20060523\"",

            "SELECT * FROM t",
            "SELECT getDate(), p FROM t WHERE substring(title, 2) = 'test'",
            "SELECT sys_title FROM SysContent WHERE sys_description = 'test'",
            "SELECT name, title, description FROM SysFolder WHERE state = 2 AND created > \"20060523\"",
            "SELECT p AS pp, q AS qq, r FROM t AS t1", "SELECT q FROM t WHERE q != 1",
            "SELECT p FROM t WHERE p * -2 / 3 + 4 - 5 = 2", "SELECT p FROM t WHERE p * 2 = 3",
            "SELECT p FROM t WHERE p + 2 = 2", "SELECT p FROM t WHERE p - 2 = 3", "SELECT p FROM t WHERE p / 2 = 4",
            "SELECT p FROM t WHERE p < 1", "SELECT p FROM t WHERE p <= 1", "SELECT p FROM t WHERE p <> 1",
            "SELECT p FROM t WHERE p = 'test'", "SELECT p FROM t WHERE p > 1", "SELECT p FROM t WHERE p >= 1",
            "SELECT p FROM t WHERE p BETWEEN 10 AND 20 AND q='test'",
            "SELECT p FROM t WHERE p IN (12, 13) AND q='test'", "SELECT p FROM t WHERE p LIKE '%test%'",
            "SELECT p FROM t WHERE p NOT BETWEEN 10 AND 20 AND q='test'",
            "SELECT p FROM t WHERE p NOT IN (12, 13) AND q='test'", "SELECT p FROM t WHERE p NOT LIKE '%test%'",
            "SELECT p FROM t WHERE p=2 AND q=1 AND s=3 OR r = 4 OR w = 10",
            "SELECT p FROM t WHERE p=2 OR s=3 AND NOT q=4", "SELECT p FROM t WHERE title = DATE '2007-01-30'",
            "SELECT p FROM t WHERE title = TIMESTAMP '2007-01-30 01:02:03+04:00'",
            "SELECT p FROM t WHERE DATE(title) = DATE '2007-01-30'",
            "SELECT p FROM t WHERE DATE(title) < DATE '2007-01-30'",
            "SELECT p FROM t WHERE DATE(title) >= DATE '2007-01-30'",
            "SELECT p FROM t WHERE DATE(title) BETWEEN DATE '2007-01-30' AND DATE '2007-01-30'",
            "SELECT p FROM t WHERE DATE(title) NOT BETWEEN DATE '2007-01-30' AND DATE '2007-01-30'",
            "SELECT p FROM t WHERE title = '%te\\'s\"t'", "SELECT p FROM t WHERE title0 = 'te\\\\st'",
            "SELECT p FROM t WHERE title = .2", "SELECT p FROM t WHERE title = 1.2",
            "SELECT p FROM t WHERE title = 12", "SELECT p FROM t WHERE title1 = \"%te\\\"s't\"",
            "SELECT p FROM t WHERE title2 = \"%test\"", "SELECT p FROM t WHERE title3 = \"te\\\\st\"",
            "SELECT p, q, r FROM t ORDER BY p, q",
            "SELECT p, q FROM t ORDER BY p, q ASC", "SELECT p, q FROM t ORDER BY p, q DESC",
            "SELECT p, q FROM t ORDER BY p DESC, q DESC", "SELECT p, q FROM t ORDER BY p DESC, q",
            "SELECT p, q, r FROM t1, t2, t3", "SELECT p1, $id, p3 FROM t1, t2 WHERE state=1 AND title = 'test'",
            "SELECT p1, p2 FROM t WHERE (title = \"test\" OR p2 >= 10.2) AND p1 + p2 < 5",
            "SELECT p1, p2 FROM t WHERE title = \"test\" OR p2 >= 10.2 AND p1 + p2 < 5",
            "SELECT p1, p2 FROM table WHERE p1 > 0 OR p2 <= 10.2 AND p1 - p2 = 5",
            "SELECT * FROM SysContent WHERE (sys_creator = 'Pedro' OR sys_creator = 'Piotr' OR sys_creator = 'Pierre')",
            "SELECT * FROM SysContent WHERE (sys_creator = 'default1' OR sys_creator = 'default2')",
            "SELECT * FROM SysContent WHERE sys_contributors = 'Administrator' AND sys_path STARTSWITH 'somelocation'",
            "SELECT * FROM SysContent WHERE sys_created < DATE '2006-12-15' ORDER BY sys_modified DESC",
            "SELECT * FROM SysContent WHERE sys_created < DATE '2006-12-15' ORDER BY sys_modified",
            "SELECT * FROM SysContent WHERE sys_created < DATE '2006-12-15'",
            "SELECT * FROM SysContent WHERE sys_created > DATE '2006-10-12'",
            "SELECT * FROM SysContent WHERE sys_created BETWEEN DATE '2006-10-12' AND DATE '2006-12-15'",
            "SELECT * FROM SysContent WHERE sys_created NOT BETWEEN DATE '2006-10-12' AND DATE '2006-12-15'",
            "SELECT * FROM SysContent WHERE sys_creator = 'Pedro'", "SELECT * FROM SysContent WHERE intparameter < '3'",
            "SELECT * FROM SysContent WHERE textparameter = 'some text' AND intparameter < '3'",
            "SELECT * FROM SysContent WHERE textparameter = 'some text'",

            "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/'",
            "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/a'",
            "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/alpha/beta'",

            // Joins by the where clause
            "SELECT * FROM SysContent WHERE sys_id = sysrelation.subject",
            "SELECT * FROM SysContent WHERE sys_id = sysrelation.subject " + " AND sys_title='foo'",

            // Reported bogus
            "SELECT * FROM SysContent WHERE NOT sys_path STARTSWITH '/some/path'",

            // Boolean literals
            "SELECT p FROM t WHERE active = TRUE", "SELECT p FROM t WHERE active = FALSE",
            "SELECT p FROM t WHERE active IN (TRUE, FALSE)", };

    static final String[] CASE_INDEPENDENCY_GOOD_QUERIES = {
            "Select title from SysContent",
            "select title From SysContent",
            "SELECT q FROM t where q != 1",
            "SELECT q FROM t Where q != 1",
            "SELECT p FROM t WHERE p Between 10 And 20 And q='test'",
            "SELECT p FROM t WHERE p between 10 And 20 and q='test'",
            "SELECT p FROM t WHERE p In (12, 13) AND q='test'",
            "SELECT p FROM t WHERE p in (12, 13) AND q='test'",
            "SELECT p FROM t WHERE p Like '%test%'",
            "SELECT p FROM t WHERE p like'%test%'",
            "SELECT p FROM t WHERE p not BETWEEN 10 AND 20 AND q='test'",
            "SELECT p FROM t WHERE p Not BETWEEN 10 AND 20 AND q='test'",
            "SELECT p FROM t WHERE p=2 or s=3 AND NOT q=4",
            "SELECT p FROM t WHERE p=2 Or s=3 AND NOT q=4",
            "SELECT p FROM t WHERE title = Timestamp '2007-01-30 01:02:03+04:00'",
            "SELECT p FROM t WHERE title = TimeStamp '2007-01-30 01:02:03+04:00'",
            "SELECT p FROM t WHERE title = timestamp '2007-01-30 01:02:03+04:00'",

            // Not parsed ?
            // "SELECT p, q, r FROM t GROUP BY p, q",
            // "SELECT p, q, r FROM t HAVING p = 1",

            "SELECT p, q, r FROM t order by p, q", "SELECT p, q, r FROM t Order By p, q",
            "SELECT p, q FROM t ORDER BY p, q Asc", "SELECT p, q FROM t ORDER BY p, q asc",
            "SELECT p, q FROM t ORDER BY p, q desc", "SELECT p, q FROM t ORDER BY p, q Desc",
            "SELECT * FROM SysContent WHERE sys_created < date '2006-12-15' ORDER BY sys_modified DESC",
            "SELECT * FROM SysContent WHERE sys_created < Date '2006-12-15' ORDER BY sys_modified DESC",
            "SELECT * FROM SysContent WHERE sys_path StartsWith '/'",
            "SELECT * FROM SysContent WHERE sys_path Startswith '/'",
            "SELECT * FROM SysContent WHERE sys_path startswith '/'", };

    static final String[] BAD_QUERIES = { "SELECT * FROM SysContent WHERE sys_path STARTSWITH",
            "SELECT * FROM SysContent WHERE sys_path STARTWITH '/'", "SELECT * WHERE sys_path STARTWITH '/'",
            "SELECT * WHERE sys_path STARTSWITH", "SELECT * WHERE sys_path STARTSWITH xxx",
            "SELECT * FROM SysContent foo" };

    static final String[] BAD_QUERIES_OLD = {
            "SELECT * WHERE location STARTWITH '/'",
            "SELECT * WHERE location STARTSWITH", "SELECT * WHERE location STARTSWITH xxx" };

    /**
     * Tests that good queries (queries from GOOD_QUERIES array) are successfully parsed.
     */
    @Test
    public void testGoodQueries() {
        int i = 0;
        try {
            for (; i < GOOD_QUERIES.length; i++) {
                SQLQueryParser.parse(GOOD_QUERIES[i]);
            }
        } catch (QueryParseException e) {
            fail("Failed to parse a good query: \n" + GOOD_QUERIES[i] + "\nCause is:\n"
                    + ExceptionUtils.getRootCause(e).getMessage());
        }
    }

    /**
     * Tests that parsing fail for bad queries (queries fom BAD_QUERIES array).
     */
    @Test
    public void testBadQueries() {
        for (String badQuery : BAD_QUERIES) {
            try {
                SQLQueryParser.parse(badQuery);
                // Not so bad this query: bad query was successfully parsed ->
                // error
                fail("A bad Query has been successfully parsed: " + badQuery);
            } catch (QueryParseException e) {
                // this is really a bad query -> continue
            }
        }
    }

    @Test
    public void testBadQueriesOld() {
        for (String badQuery : BAD_QUERIES_OLD) {
            try {
                SQLQueryParser.parse(badQuery);
                // Not so bad this query: bad query was successfully parsed ->
                // error
                fail("A bad Query has been successfully parsed: " + badQuery);
            } catch (QueryParseException e) {
                // this is really a bad query -> continue
            }
        }
    }

    @Test
    public void testLocationLiterals() {
        // test double quoted strings
        SQLQuery query = SQLQueryParser.parse("SELECT p FROM t WHERE sys_path STARTSWITH '/test'");
        WhereClause whereClause = query.getWhereClause();
        assertEquals(Operator.STARTSWITH, whereClause.predicate.operator);

        Reference sleft = (Reference) whereClause.predicate.lvalue;
        assertEquals(SYS_PATH, sleft.name);
        StringLiteral sright = (StringLiteral) whereClause.predicate.rvalue;
        assertEquals("/test", sright.value());
    }

    /**
     * Tests equals method on SQLQuery.
     */
    @Test
    public void testEquals() {
        for (String s : GOOD_QUERIES) {
            SQLQuery query1 = SQLQueryParser.parse(s);
            SQLQuery query2 = SQLQueryParser.parse(s);
            assertEquals(query1, query2, "Self equality test on " + s + ": ");
            assertEquals(query1.hashCode(), query2.hashCode());
            assertEquals(query1.toString(), query2.toString());
        }
    }

    /**
     * For non-equality, one must make sure that no two queries are equivalent in the GOOD_QUERIES list (ex: "p != 1"
     * and "p &lt;&gt; 1").
     */
    @Test
    public void testNotEquals() {
        for (String s1 : GOOD_QUERIES) {
            for (String s2 : GOOD_QUERIES) {
                if (s1.equals(s2)) {
                    continue;
                }
                SQLQuery query1 = SQLQueryParser.parse(s1);
                SQLQuery query2 = SQLQueryParser.parse(s2);
                // "Non-equality test on queries
                assertNotEquals(query1, query2);
            }
        }
    }

    @Test
    public void testToString() {
        for (String s : CANONICAL_QUERIES) {
            SQLQuery query1 = SQLQueryParser.parse(s);
            assertEquals(s, query1.toString());
        }
    }

}
