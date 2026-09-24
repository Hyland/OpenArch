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
package org.nuxeo.rest.query.test;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.nuxeo.rest.query.QueryAPI.PATH;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.TITLE_SORT;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.QUERY_STRING_PROP;
import static org.nuxeo.rest.query.spec.QueryAssembler.DEFAULT_SELECT_STATEMENT;
import static org.springframework.http.HttpMethod.POST;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.rest.document.test.ContentTestHelper;
import org.nuxeo.rest.query.OpenSearchQueryManager;
import org.nuxeo.rest.query.QueryAPI;
import org.nuxeo.rest.query.QueryManager;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.io.WhereClauseDTO;
import org.nuxeo.rest.query.io.WhereClauseDTO.RestPredicate;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.ws.rs.core.Response.Status;

@Features(RestQueryMultipleDocumentsFeature.class)
@Tag("useOpenSearch")
@Import({ QueryAPITestConfiguration.class })
@SpringBootTest(classes = { QueryAPI.class })
public class AdvancedQueryAPITest extends AbstractQueryAPITest {

    protected static final String API_PATH = PATH + "/advanced";

    protected static final String BASIC_QUERY = DEFAULT_SELECT_STATEMENT + " ORDER BY sys_title";

    protected static final String TEST_QUERY = DEFAULT_SELECT_STATEMENT + " WHERE sys_title = 'File3' ORDER BY sys_title";

    protected ResultActions checkResponse(Status expectedStatus, Map<String, Object> queryParameters) throws Exception {
        return checkResponse(expectedStatus, API_PATH, POST, queryParameters);
    }

    protected ResultActions checkPostResponse(Status expectedStatus, String referencePostBody, Object... args)
            throws Exception {
        return checkResponseWithReferencePayload(expectedStatus, API_PATH, POST, referencePostBody, args);
    }

    /**
     * Create a DocumentAdvancedQueryDTO returning an accurate totalCount
     */
    protected DocumentAdvancedQueryDTO createAdvanceQueryDTO(String repositoryId, String query, Long limit, long offset,
            List<String> sort) {
        return new DocumentAdvancedQueryDTO(repositoryId, query, limit, offset, true, -1, sort);
    }

    /**
     * Create a DocumentAdvancedQueryDTO returning an accurate totalCount
     */
    protected DocumentAdvancedQueryDTO createAdvanceQueryDTO(String repositoryId, String query, Long limit, long offset,
            List<String> sort, Map<String, Object> parameters, WhereClauseDTO whereClauseDefinition,
            List<String> quickFilterClauses) {
        return new DocumentAdvancedQueryDTO(repositoryId, query, limit, offset, true, -1, sort, parameters,
                whereClauseDefinition, quickFilterClauses);
    }

    // check query managers

    @Autowired
    OpenSearchQueryManager osqm;

    protected void checkAll(QueryManager manager) {
        DocumentAdvancedQueryDTO q = createAdvanceQueryDTO(REPO_ID, null, null, 0, List.of(TITLE_SORT));
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, BASIC_QUERY, 10L, 0, 10, 10L);
        checkResultDocument(r, 0, "File0");
    }

    protected void checkLimit(QueryManager manager) {
        DocumentAdvancedQueryDTO q = createAdvanceQueryDTO(REPO_ID, null, 1L, 0, List.of(TITLE_SORT));
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, BASIC_QUERY, 1L, 0, 1, 10L);
        checkResultDocument(r, 0, "File0");
    }

    protected void checkOffset(QueryManager manager) {
        DocumentAdvancedQueryDTO q = createAdvanceQueryDTO(REPO_ID, null, null,1, List.of(TITLE_SORT));
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, BASIC_QUERY, 10L, 1, 9, 10L);
        checkResultDocument(r, 0, "File1");
    }

    protected void checkTrackTotalCountFalse(QueryManager manager) {
        DocumentAdvancedQueryDTO q = new DocumentAdvancedQueryDTO(REPO_ID, null, 11L, 0, false, -1, List.of(TITLE_SORT));
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, BASIC_QUERY, 11L, 0, 10, null);
    }

    protected void checkSort(QueryManager manager) {
        DocumentAdvancedQueryDTO q = createAdvanceQueryDTO(REPO_ID, null, 1L, 0, List.of("sys_title DESC"));
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, BASIC_QUERY + " DESC", 1L, 0, 1, 10L);
        checkResultDocument(r, 0, "File9");
    }

    protected void checkParameters(QueryManager manager) {
        String query = "SELECT * FROM SysContent WHERE sys_title = :byTitle";
        DocumentAdvancedQueryDTO q = createAdvanceQueryDTO(REPO_ID, query, null, 0, List.of(TITLE_SORT),
                Map.of("byTitle", "File3"), null, null);
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, TEST_QUERY, 10L, 0, 1, 1L);
        checkResultDocument(r, 0, "File3");
    }

    protected void checkPredicates(QueryManager manager) {
        WhereClauseDTO def = WhereClauseDTO.of(null, List.of(RestPredicate.ofStatic(SYS_TITLE, "=", "File3")));
        DocumentAdvancedQueryDTO q = createAdvanceQueryDTO(REPO_ID, null, null, 0, List.of(TITLE_SORT), null, def, null);
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, TEST_QUERY, 10L, 0, 1, 1L);
        checkResultDocument(r, 0, "File3");
    }

    protected void checkPredicatesWithParameters(QueryManager manager) {
        WhereClauseDTO def = WhereClauseDTO.of(null, List.of(RestPredicate.of(SYS_TITLE, "=", "byTitle")));
        DocumentAdvancedQueryDTO q = createAdvanceQueryDTO(REPO_ID, null, null, 0, List.of(TITLE_SORT),
                Map.of("byTitle", "File3"), def, null);
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, TEST_QUERY, 10L, 0, 1, 1L);
        checkResultDocument(r, 0, "File3");
    }

    protected void checkQuickFilters(QueryManager manager) {
        DocumentAdvancedQueryDTO q = createAdvanceQueryDTO(REPO_ID, null, null, 0, List.of(TITLE_SORT),
                Map.of("byTitle", "File3"), null, List.of("sys_title = :byTitle"));
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, TEST_QUERY, 10L, 0, 1, 1L);
        checkResultDocument(r, 0, "File3");
    }

    /**
     * The counterpart to the seven handlers whose body this branch made required: these two tolerate a null
     * body on purpose -- they substitute a default DTO and search everything -- so a caller that sends none
     * keeps getting results rather than the 400 the other endpoints now answer. Asserted because the claim
     * that they are deliberate is what justified leaving them alone.
     */
    @Test
    public void testAdvancedQueryWithANullBodyStillSearches() throws Exception {
        checkResponseWithPayload(OK, POST, REPO_ID, "null", API_PATH);
    }

    @Test
    public void testOpenSearchQueryManager() {
        assertNotNull(osqm);
        checkAll(osqm);
        checkLimit(osqm);
        checkOffset(osqm);
        checkTrackTotalCountFalse(osqm);
        checkSort(osqm);
        checkParameters(osqm);
        checkPredicates(osqm);
        checkPredicatesWithParameters(osqm);
        checkQuickFilters(osqm);
    }

    // check rest endpoint

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testQueryUnauthorized() throws Exception {
        checkResponse(UNAUTHORIZED, Map.of());
    }

    @Test
    @WithUser(ALT_USER)
    public void testQueryNoPermissions() throws Exception {
        var result = checkResponse(OK, Map.of());
        checkResponseBody("rest/query_res_empty.json", result);
    }

    @Test
    public void testInvalidQuery() throws Exception {
        // ES actually fine with "from Foo"...
        String esInvalidQuery = "SELECT * from Foo WHERE";
        var result = checkResponse(BAD_REQUEST, Map.of(QUERY_STRING_PROP, esInvalidQuery));
        ContentTestHelper.checkErrorResponse(BAD_REQUEST, Pattern.compile(
                "Query: SELECT \\* from Foo WHERE, At position 1:23 mismatched input '<EOF>' expecting .*"), result);
    }

    @Test
    public void testQuery() throws Exception {
        var result = checkPostResponse(OK, "rest/query_req_advanced.json");
        checkQueryResponseBody("rest/query_res_advanced.json", result);
    }

    /** A full request and response, with the query expressed as an expression next to a quick filter clause. */
    @Test
    public void testQueryWithExprSyntax() throws Exception {
        var result = checkPostResponse(OK, "rest/query_req_advanced_expr.json");
        checkQueryResponseBody("rest/query_res_advanced_expr.json", result);
    }

    /** A where clause definition can filter with an expression, on top of the statement the request selects from. */
    @Test
    public void testQueryWithExprWhereClause() throws Exception {
        var result = checkPostResponse(OK, "rest/query_req_advanced_expr_clause.json");
        checkQueryResponseBody("rest/query_res_advanced_expr_clause.json", result);
    }

    @Test
    public void testQueryNull() throws Exception {
        checkPostResponse(OK, "rest/query_req_advanced_null.json", (Object[]) null);
    }

}
