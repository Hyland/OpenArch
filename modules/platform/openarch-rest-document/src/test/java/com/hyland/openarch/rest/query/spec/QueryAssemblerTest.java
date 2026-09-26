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
package com.hyland.openarch.rest.query.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;

import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.QuerySyntax;
import com.hyland.openarch.ecm.core.query.nxql.SortInfo;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.rest.query.io.DocumentAdvancedQueryDTO;
import com.hyland.openarch.rest.query.io.DocumentQueryDTO;
import com.hyland.openarch.rest.query.io.WhereClauseDTO;
import com.hyland.openarch.rest.query.spec.QueryAssembler.AssembledQuery;
import com.hyland.openarch.rest.query.io.WhereClauseDTO.RestPredicate;

import com.hyland.openarch.expr.ExprPredicates;

public class QueryAssemblerTest {

    protected static final String REPO_ID = "test";

    protected static AssembledQuery assembleQuery(String query, List<String> sort, Map<String, Object> parameters,
            WhereClauseDTO whereClause, List<String> quickFilterClauses) {
        DocumentAdvancedQueryDTO dto = new DocumentAdvancedQueryDTO(REPO_ID, query, null, 0, true, -1, sort,
                parameters, whereClause, quickFilterClauses);
        return QueryAssembler.assemble(dto, DocumentQueryDTO.getSortInfos(sort), QueryAssemblerTest::toPredicate);
    }

    /** The query to execute, which keeps its parameters. */
    protected static String assemble(String query, List<String> sort, Map<String, Object> parameters,
            WhereClauseDTO whereClause, List<String> quickFilterClauses) {
        return assembleQuery(query, sort, parameters, whereClause, quickFilterClauses).query();
    }

    protected static String assemble(String query) {
        return assemble(query, null, null, null, null);
    }

    @Test
    public void testPlainQuery() {
        assertEquals("SELECT * FROM SysContent", assemble(null));
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'a'",
                assemble("SELECT * FROM SysContent WHERE sys_title = 'a'"));
    }

    @Test
    public void testSort() {
        assertEquals("SELECT * FROM SysContent ORDER BY sys_title",
                assemble(null, List.of("sys_title"), null, null, null));
        assertEquals("SELECT * FROM SysContent ORDER BY sys_title DESC",
                assemble(null, List.of("sys_title DESC"), null, null, null));
        assertEquals("SELECT * FROM SysContent ORDER BY sys_created DESC, sys_title",
                assemble(null, List.of("sys_created desc", "sys_title"), null, null, null));
    }

    /** A requested sort replaces one the query text already carries, rather than being appended after it. */
    @Test
    public void testSortReplacesTheQueryOwnOrderBy() {
        String query = "SELECT * FROM SysContent ORDER BY sys_created";
        assertEquals("SELECT * FROM SysContent ORDER BY sys_title", assemble(query, List.of("sys_title"), null, null, null));
        assertEquals("SELECT * FROM SysContent ORDER BY sys_created", assemble(query, List.of(), null, null, null));
    }

    /** A sort column goes into the query as an identifier, which cannot be escaped, so it has to be validated. */
    @Test
    public void testInvalidSortColumn() {
        var e = assertThrows(RepositoryException.class,
                () -> assemble(null, List.of("sys_title IS NULL OR x"), null, null, null));
        assertTrue(e.getMessage().startsWith("Not a valid field name:"), e.getMessage());
        assertEquals(400, e.getStatusCode());
    }

    /**
     * The query to execute keeps its {@code :name} parameters, bound where it runs; the echoed form has them bound
     * here, since that is what a response shows the caller.
     */
    @Test
    public void testParameters() {
        AssembledQuery assembled = assembleQuery("SELECT * FROM SysContent WHERE sys_title = :byTitle", null,
                Map.of("byTitle", "File3"), null, null);
        assertEquals("SELECT * FROM SysContent WHERE sys_title = :byTitle", assembled.query());
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'File3'", assembled.boundQuery());
    }

    @Test
    public void testMissingParameter() {
        var e = assertThrows(QueryParseException.class,
                () -> assemble("SELECT * FROM SysContent WHERE sys_title = :byTitle"));
        assertTrue(e.getMessage().contains("No value for query parameter :byTitle"), e.getMessage());
        assertEquals(400, e.getStatusCode());
    }

    /** Values reach the echoed query as literals, so nothing they contain can be read as syntax. */
    @Test
    public void testValuesCannotInjectSyntax() {
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'x\\' OR sys_title LIKE \\'%'",
                assembleQuery("SELECT * FROM SysContent WHERE sys_title = :byTitle", null,
                        Map.of("byTitle", "x' OR sys_title LIKE '%"), null, null).boundQuery());
    }

    @Test
    public void testPredicates() {
        WhereClauseDTO whereClause = WhereClauseDTO.of(null,
                List.of(RestPredicate.ofStatic(SYS_TITLE, "=", "File3")));
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'File3'",
                assemble(null, null, null, whereClause, null));
    }

    @Test
    public void testPredicatesWithParameters() {
        WhereClauseDTO whereClause = WhereClauseDTO.of(null, List.of(RestPredicate.of(SYS_TITLE, "=", "byTitle")));
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'File3'",
                assemble(null, null, Map.of("byTitle", "File3"), whereClause, null));
        // a predicate whose value was not supplied leaves the query without a where clause
        assertEquals("SELECT * FROM SysContent", assemble(null, null, null, whereClause, null));
    }

    /** Predicates come first, then the query's own where clause, then the quick filters. */
    @Test
    public void testPredicatesAndQueryClauseAndQuickFilters() {
        WhereClauseDTO whereClause = WhereClauseDTO.of("SELECT * FROM SysContent WHERE sysver_isVersion = 0",
                List.of(RestPredicate.of(SYS_TITLE, "=", "byTitle")));
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'File3' AND sysver_isVersion = 0"
                + " AND mydc_nature <> 'article'",
                assemble(null, null, Map.of("byTitle", "File3"), whereClause, List.of("mydc_nature != 'article'")));
    }

    /** A where clause definition's own query wins over the request's. */
    @Test
    public void testWhereClauseQueryWins() {
        WhereClauseDTO whereClause = WhereClauseDTO.of("SELECT * FROM Note WHERE a = 1", List.of());
        assertEquals("SELECT * FROM Note WHERE a = 1",
                assemble("SELECT * FROM SysContent WHERE b = 2", null, null, whereClause, null));
    }

    @Test
    public void testQuickFilters() {
        assertEquals("SELECT * FROM SysContent WHERE sys_title = :byTitle",
                assemble(null, null, Map.of("byTitle", "File3"), null, List.of("sys_title = :byTitle")));
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'File3'",
                assembleQuery(null, null, Map.of("byTitle", "File3"), null, List.of("sys_title = :byTitle"))
                        .boundQuery());
        assertEquals("SELECT * FROM SysContent WHERE a = 1 AND b = 2",
                assemble(null, null, null, null, List.of("a = 1", "b = 2")));
        // blank clauses are skipped, and do not discard the ones already gathered
        assertEquals("SELECT * FROM SysContent WHERE a = 1 AND b = 2",
                assemble(null, null, null, null, List.of("a = 1", "  ", "b = 2")));
    }

    /** An OR clause keeps its parentheses when it becomes part of a conjunction. */
    @Test
    public void testPrecedenceIsPreserved() {
        assertEquals("SELECT * FROM SysContent WHERE (a = 1 OR b = 2) AND c = 3",
                assemble("SELECT * FROM SysContent WHERE a = 1 OR b = 2", null, null, null, List.of("c = 3")));
    }

    @Test
    public void testSortedColumn() {
        String query = "SELECT * FROM SysContent WHERE " + QueryAssembler.SORTED_COLUMN + " IS NOT NULL";
        // it stands for the first sort column
        assertEquals("SELECT * FROM SysContent WHERE sys_title IS NOT NULL ORDER BY sys_title",
                assemble(query, List.of("sys_title"), null, null, null));
        assertEquals("SELECT * FROM SysContent WHERE sys_created IS NOT NULL ORDER BY sys_created DESC, sys_title",
                assemble(query, List.of("sys_created desc", "sys_title"), null, null, null));
        // with no sort it falls back to the id
        assertEquals("SELECT * FROM SysContent WHERE sys_id IS NOT NULL", assemble(query, List.of(), null, null, null));
        // it also resolves alongside predicates, which used to prevent it
        WhereClauseDTO whereClause = WhereClauseDTO.of(query, List.of(RestPredicate.of(SYS_TITLE, "=", "byTitle")));
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'File3' AND sys_title IS NOT NULL ORDER BY sys_title",
                assemble(null, List.of("sys_title"), Map.of("byTitle", "File3"), whereClause, null));
    }

    @Test
    public void testInvalidQuery() {
        var e = assertThrows(QueryParseException.class, () -> assemble("SELECT * from Foo WHERE"));
        assertTrue(e.getMessage().contains("Query: SELECT * from Foo WHERE"), e.getMessage());
        assertEquals(400, e.getStatusCode());
    }

    @Test
    public void testInvalidQuickFilterClause() {
        var e = assertThrows(QueryParseException.class,
                () -> assemble(null, null, null, null, List.of("this is not a clause")));
        assertTrue(e.getMessage().contains("Clause: this is not a clause"), e.getMessage());
    }

    // --- expr syntax ---

    protected static final RepositoryPrincipal PRINCIPAL = principal();

    protected static final SchemaManager SCHEMA_MANAGER = schemaManager();

    protected static RepositoryPrincipal principal() {
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.getId()).thenReturn("bob");
        when(principal.getGroups()).thenReturn(List.of("members", "readers"));
        return principal;
    }

    protected static SchemaManager schemaManager() {
        SchemaManager schemaManager = mock(SchemaManager.class);
        when(schemaManager.isSecurityProperty(SYS_ACL)).thenReturn(true);
        return schemaManager;
    }

    protected static AssembledQuery assembleExprQuery(String expr, List<String> sort, WhereClauseDTO whereClause,
            List<String> quickFilterClauses) {
        DocumentAdvancedQueryDTO dto = new DocumentAdvancedQueryDTO(REPO_ID, expr, QuerySyntax.EXPR, null, 0, null,
                true, -1, sort, null, whereClause, quickFilterClauses);
        List<SortInfo> sortInfos = DocumentQueryDTO.getSortInfos(sort);
        return QueryAssembler.assemble(dto, sortInfos, QueryAssemblerTest::toPredicate);
    }

    protected static Predicate toPredicate(String expr) {
        return ExprPredicates.toPredicate(expr, PRINCIPAL, SCHEMA_MANAGER);
    }

    protected static String assembleExpr(String expr) {
        return assembleExprQuery(expr, null, null, null).query();
    }

    /** An expression is a filter on top of a statement, which it does not itself provide. */
    @Test
    public void testExpr() {
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'a'", assembleExpr("content.sys_title == \"a\""));
        assertEquals("SELECT * FROM SysContent WHERE sys_title <> 'a' AND tp_int > 3",
                assembleExpr("content.sys_title != \"a\" && content.tp_int > 3"));
        // a dotted property is a path into a complex property
        assertEquals("SELECT * FROM SysContent WHERE cpx_complex/foo = 'a'",
                assembleExpr("content.cpx_complex.foo == \"a\""));
        // no expression filters nothing out
        assertEquals("SELECT * FROM SysContent", assembleExpr(null));
        assertEquals("SELECT * FROM SysContent", assembleExpr("  "));
    }

    /**
     * The current user is available to the expression, and an expression that does not depend on the content is
     * reduced to a constant.
     */
    @Test
    public void testExprUser() {
        assertEquals("SELECT * FROM SysContent WHERE sys_lastContributor = 'bob'",
                assembleExpr("content.sys_lastContributor == user.id"));
        // an always true constant filters nothing out
        assertEquals("SELECT * FROM SysContent", assembleExpr("user.id == \"bob\""));
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'a'",
                assembleExpr("user.id == \"bob\" && content.sys_title == \"a\""));
        // an always false one matches no document at all
        assertEquals("SELECT * FROM SysContent WHERE 1 = 0", assembleExpr("user.id == \"alice\""));
        assertEquals("SELECT * FROM SysContent WHERE 1 = 0",
                assembleExpr("user.id == \"alice\" && content.sys_title == \"a\""));
    }

    /** A list the expression knows statically becomes an IN. */
    @Test
    public void testExprContains() {
        assertEquals("SELECT * FROM SysContent WHERE sys_lastContributor IN ('members', 'readers')",
                assembleExpr("user.groups.contains(content.sys_lastContributor)"));
    }

    /** An expression combines with everything else the request carries. */
    @Test
    public void testExprWithSortAndQuickFiltersAndWhereClause() {
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'a' AND b = 2 ORDER BY sys_title",
                assembleExprQuery("content.sys_title == \"a\"", List.of("sys_title"), null, List.of("b = 2")).query());
        // a where clause definition provides the statement the expression filters
        WhereClauseDTO whereClause = WhereClauseDTO.of("SELECT * FROM Note WHERE a = 1", List.of());
        assertEquals("SELECT * FROM Note WHERE a = 1 AND sys_title = 'a'",
                assembleExprQuery("content.sys_title == \"a\"", null, whereClause, null).query());
    }

    /** A where clause definition can filter with an expression instead of providing the statement. */
    @Test
    public void testExprWhereClause() {
        WhereClauseDTO whereClause = WhereClauseDTO.ofExpr("content.sys_title == \"a\"", null);
        // the statement to select from is then the request's own, which the definition only filters
        assertEquals("SELECT * FROM Note WHERE sys_title = 'a' AND b = 2",
                assemble("SELECT * FROM Note WHERE b = 2", null, null, whereClause, null));
        // or the default statement when the request has none
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'a'", assemble(null, null, null, whereClause, null));
        // and the request may be an expression too
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'a' AND tp_int > 3",
                assembleExprQuery("content.tp_int > 3", null, whereClause, null).query());
        // the definition's predicates come first, then its expression
        whereClause = WhereClauseDTO.ofExpr("content.sys_title == \"a\"",
                List.of(RestPredicate.ofStatic(SYS_TITLE, "LIKE", "b%")));
        assertEquals("SELECT * FROM SysContent WHERE sys_title LIKE 'b%' AND sys_title = 'a'",
                assemble(null, null, null, whereClause, null));
    }

    @Test
    public void testInvalidExpr() {
        var e = assertThrows(QueryParseException.class, () -> assembleExpr("this is not an expression"));
        assertTrue(e.getMessage().startsWith("Invalid expression: this is not an expression: "), e.getMessage());
        assertEquals(400, e.getStatusCode());
    }

    /** An expression that is not a predicate cannot filter anything. */
    @Test
    public void testExprIsNotAPredicate() {
        var e = assertThrows(QueryParseException.class, () -> assembleExpr("content.sys_title"));
        assertEquals("Expression is not a predicate: content.sys_title", e.getMessage());
        assertEquals(400, e.getStatusCode());
    }

    /** Disallowed system properties are as out of reach in a query as they are anywhere else. */
    @Test
    public void testExprDisallowedProperty() {
        var e = assertThrows(QueryParseException.class, () -> assembleExpr("content.sys_acl == \"x\""));
        assertTrue(e.getMessage().contains("Disallowed system property: sys_acl"), e.getMessage());
    }

    /** A full-text hint written in the query text survives assembly. */
    @Test
    public void testFulltextHint() {
        assertEquals("SELECT * FROM SysContent WHERE /*+FT: ANALYZER(fr_analyzer) */ sys_fulltext = 'a'",
                assemble("SELECT * FROM SysContent WHERE /*+FT: ANALYZER(fr_analyzer) */ sys_fulltext = 'a'"));
    }

}
