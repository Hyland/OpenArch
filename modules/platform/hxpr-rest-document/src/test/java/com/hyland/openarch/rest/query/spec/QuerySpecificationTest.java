/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.rest.query.conf.NamedQueryHelper.TITLE_SORT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.query.sql.HXQLParser;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.rest.query.io.DocumentAdvancedQueryDTO;
import com.hyland.openarch.ecm.core.query.QuerySyntax;
import com.hyland.openarch.rest.query.io.DocumentQueryDTO;
import com.hyland.openarch.rest.query.spec.QueryAssembler.AssembledQuery;

public class QuerySpecificationTest {

    private static final String REPO_ID = "test";

    private static final String QUERY = "SELECT * FROM SysContent";

    private static final Long LIMIT = -1L;

    private static final Integer OFFSET = 0;

    private static final Long COUNT_UPTO = 1L;

    private static final Integer TRACK_TOTAL_COUNT_UPTO = 10;

    private static final Boolean TRACK_TOTAL_COUNT = true;

    private static final List<String> SORT = List.of(TITLE_SORT);

    private static final String SEARCH_AFTER = "token";

    private static final String BOUND_QUERY = "SELECT * FROM SysContent WHERE sys_title = 'a'";

    private static final Statement BOUND_STATEMENT = HXQLParser.parse(BOUND_QUERY);

    private static final DocumentQueryDTO documentQueryDTO = new DocumentQueryDTO(REPO_ID, QUERY, LIMIT, OFFSET,
            SEARCH_AFTER, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, SORT);

    @Test
    public void createQuerySpecification() {
        QuerySpecification querySpecification = new QuerySpecification(REPO_ID, QUERY, null, QUERY, Map.of("p", "a"),
                BOUND_STATEMENT, LIMIT, OFFSET, SEARCH_AFTER, COUNT_UPTO, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO,
                SORT);

        assertEquals("test", querySpecification.repositoryId());
        assertEquals("SELECT * FROM SysContent", querySpecification.query());
        assertEquals(Map.of("p", "a"), querySpecification.parameters());
        assertEquals(BOUND_STATEMENT, querySpecification.statement());
        assertEquals(BOUND_QUERY, querySpecification.boundQuery());
        assertEquals(-1, querySpecification.limit());
        assertEquals(0, querySpecification.offset());
        assertEquals("token", querySpecification.searchAfter());
        assertEquals(1L, querySpecification.countUpTo());
        assertEquals(10, querySpecification.trackTotalCountUpTo());
        assertEquals(true, querySpecification.trackTotalCount());
        assertEquals(List.of(TITLE_SORT), querySpecification.sort());
    }

    @Test
    public void createQuerySpecificationWithDocumentQueryDTO() {
        QuerySpecification querySpecification = new QuerySpecification(documentQueryDTO, QUERY, LIMIT, COUNT_UPTO,
                TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO);

        assertEquals("test", querySpecification.repositoryId());
        assertEquals("SELECT * FROM SysContent", querySpecification.query());
        // a plain query has no parameters to carry, so it is echoed as it is
        assertEquals(Map.of(), querySpecification.parameters());
        assertEquals(QUERY, querySpecification.boundQuery());
        assertEquals(HXQLParser.parse(QUERY), querySpecification.statement());
        assertEquals(-1, querySpecification.limit());
        assertEquals(0, querySpecification.offset());
        assertEquals("token", querySpecification.searchAfter());
        assertEquals(1L, querySpecification.countUpTo());
        assertEquals(10, querySpecification.trackTotalCountUpTo());
        assertEquals(true, querySpecification.trackTotalCount());
        assertEquals(List.of(TITLE_SORT), querySpecification.sort());
    }

    /** An expr request echoes its expression, and no expression echoes as none rather than as the statement. */
    @Test
    public void createQuerySpecificationWithExprSyntax() {
        String expr = "content.sys_title == \"a\"";
        DocumentAdvancedQueryDTO dto = new DocumentAdvancedQueryDTO(REPO_ID, expr, QuerySyntax.EXPR, LIMIT, OFFSET,
                null, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, SORT, null, null, null);
        QuerySpecification querySpecification = new QuerySpecification(dto,
                new AssembledQuery(BOUND_STATEMENT, BOUND_QUERY), LIMIT, COUNT_UPTO, TRACK_TOTAL_COUNT,
                TRACK_TOTAL_COUNT_UPTO);
        assertEquals(BOUND_QUERY, querySpecification.query());
        assertEquals(QuerySyntax.EXPR, querySpecification.syntax());
        assertEquals(expr, querySpecification.boundQuery());

        dto = new DocumentAdvancedQueryDTO(REPO_ID, null, QuerySyntax.EXPR, LIMIT, OFFSET, null, TRACK_TOTAL_COUNT,
                TRACK_TOTAL_COUNT_UPTO, SORT, null, null, null);
        querySpecification = new QuerySpecification(dto, new AssembledQuery(BOUND_STATEMENT, BOUND_QUERY), LIMIT,
                COUNT_UPTO, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO);
        assertNull(querySpecification.boundQuery());
    }

    @Test
    public void createQuerySpecificationWithDocumentAdvancedQueryDTO() {
        DocumentAdvancedQueryDTO documentAdvancedQueryDTO = new DocumentAdvancedQueryDTO(REPO_ID, QUERY, LIMIT,
                OFFSET, SEARCH_AFTER, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, SORT, Map.of("p", "a"), null, null);
        QuerySpecification querySpecification = new QuerySpecification(documentAdvancedQueryDTO,
                new AssembledQuery(BOUND_STATEMENT, QUERY), LIMIT, COUNT_UPTO, TRACK_TOTAL_COUNT,
                TRACK_TOTAL_COUNT_UPTO);

        assertEquals("test", querySpecification.repositoryId());
        assertEquals("SELECT * FROM SysContent", querySpecification.query());
        // the values for the query's parameters travel with it, to be bound where it runs, and the echoed form of
        // the query has them bound already
        assertEquals(Map.of("p", "a"), querySpecification.parameters());
        assertEquals(BOUND_STATEMENT, querySpecification.statement());
        assertEquals(BOUND_QUERY, querySpecification.boundQuery());
        assertEquals(-1, querySpecification.limit());
        assertEquals(0, querySpecification.offset());
        assertEquals("token", querySpecification.searchAfter());
        assertEquals(1L, querySpecification.countUpTo());
        assertEquals(10, querySpecification.trackTotalCountUpTo());
        assertEquals(true, querySpecification.trackTotalCount());
        assertEquals(List.of(TITLE_SORT), querySpecification.sort());
    }

    /**
     * A parameter of an optional predicate that the request left unfilled is a null value in the map, which
     * PredicateBuilder reads as "not supplied" rather than as an error.
     */
    @Test
    public void createQuerySpecificationWithUnsuppliedParameter() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("byTitle", null);
        QuerySpecification querySpecification = new QuerySpecification(REPO_ID, QUERY, null, QUERY, parameters,
                BOUND_STATEMENT, LIMIT, OFFSET, SEARCH_AFTER, COUNT_UPTO, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO,
                SORT);

        assertTrue(querySpecification.parameters().containsKey("byTitle"));
        assertNull(querySpecification.parameters().get("byTitle"));
    }

    /** Sorts and parameters default to empty rather than being null. */
    @Test
    public void createQuerySpecificationWithoutSort() {
        QuerySpecification querySpecification = new QuerySpecification(REPO_ID, QUERY, null, QUERY, null,
                HXQLParser.parse(QUERY), LIMIT, OFFSET, null, COUNT_UPTO, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO,
                null);
        assertEquals(List.of(), querySpecification.sort());
        assertEquals(Map.of(), querySpecification.parameters());
    }

}
