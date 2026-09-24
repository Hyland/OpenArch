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
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.nuxeo.rest.query.QueryAPI.PATH;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.TITLE_SORT;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.DEFAULT_LIMIT_VALUE;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.LIMIT_PROP;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.OFFSET_PROP;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.QUERY_STRING_PROP;
import static org.nuxeo.rest.query.spec.QueryAssembler.DEFAULT_SELECT_STATEMENT;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import jakarta.ws.rs.core.Response.Status;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.local.WithUser;
import com.hyland.openarch.ecm.core.query.QuerySyntax;
import org.nuxeo.rest.document.test.ContentTestHelper;
import org.nuxeo.rest.query.OpenSearchQueryManager;
import org.nuxeo.rest.query.QueryAPI;
import org.nuxeo.rest.query.QueryManager;
import org.nuxeo.rest.query.RepositoryQueryManager;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryDTO;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

@Features(RestQuerySingleDocumentFeature.class)
@Tag("useOpenSearch")
@Import({ QueryAPITestConfiguration.class })
@SpringBootTest(classes = { QueryAPI.class })
public class QueryAPITest extends AbstractQueryAPITest {

    protected ResultActions checkResponse(Status expectedStatus, HttpMethod method, HttpHeaders headers,
            Map<String, Object> queryParameters) throws Exception {
        return checkResponse(expectedStatus, PATH, method, headers, queryParameters);
    }

    protected ResultActions checkResponse(Status expectedStatus, HttpMethod method, Map<String, Object> queryParameters)
            throws Exception {
        return checkResponse(expectedStatus, PATH, method, queryParameters);
    }

    // check query managers

    @Autowired
    OpenSearchQueryManager osqm;

    protected void checkError(QueryManager manager, Long limit, long offset, String expectedError) {
        DocumentQueryDTO q = new DocumentQueryDTO(REPO_ID, null, limit, offset, List.of(TITLE_SORT));
        try {
            manager.getResults(q);
            fail("Should have thrown error");
        } catch (Exception e) {
            assertEquals(expectedError, e.getMessage());
        }
    }

    protected void checkAll(QueryManager manager, Long limit, long offset, long resultLimit, long expectedCount,
            long expectedTotalCount) {
        DocumentQueryDTO q = new DocumentQueryDTO(REPO_ID, null, limit, offset, List.of(TITLE_SORT));
        DocumentQueryResultDTO r = manager.getResults(q);
        String finalQuery = DEFAULT_SELECT_STATEMENT + " ORDER BY sys_title";
        checkResult(r, REPO_ID, finalQuery, resultLimit, offset, expectedCount, expectedTotalCount);
        if (offset <= 1) {
            checkResultDocument(r, 0, "title");
        }
    }

    protected void checkAll(QueryManager manager) {
        checkAll(manager, null, 0, 10, 1, 1);
        checkAll(manager, 100L, 0, 100, 1, 1);
        // conf limit applies:
        checkAll(manager, 10001L, 0, 10000, 1, 1);
        // conf max offset applies:
        checkError(manager, null, 10001, "Error performing query: limit (10) + offset (10001) should be <= 10000 (10011)");
        // ES error:
        checkError(manager, 101L, 9900,
                "Error performing query: limit (101) + offset (9900) should be <= 10000 (10001)");
    }

    @Test
    public void testOpenSearchQueryManager() {
        assertNotNull(osqm);
        checkAll(osqm);
    }

    // check rest endpoint

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testQueryUnauthorized() throws Exception {
        checkResponse(UNAUTHORIZED, POST, Map.of());
    }

    @Test
    public void testQueryAllPOSTV0() throws Exception {
        checkResponse(NOT_ACCEPTABLE, POST, getV0Headers(), Map.of());
    }

    @Test
    public void testQueryAllPOSTV1() throws Exception {
        HttpHeaders headers = getV1Headers();
        var response = checkResponse(OK, POST, headers, Map.of());
        checkQueryResponseBody("rest/query_res_all.json", response, "");
    }

    @Test
    public void testQueryAllWithParametersPOSTV1() throws Exception {
        String query = "SELECT * FROM SysContent";

        var result = checkResponse(OK, POST, getV1Headers(),
                Map.of(QUERY_STRING_PROP, query, "sort", List.of(TITLE_SORT), OFFSET_PROP, 0));
        checkQueryResponseBody("rest/query_res_all.json", result, " ORDER BY sys_title");
    }

    @Test
    public void testQueryAllWithMultipleSortParametersPOSTV1() throws Exception {
        String SORT = "sys_title,sys_lastContributor,sys_title";
        String query = "SELECT * FROM SysContent";

        // List.of(SORT) --> results in a single list item with value "sys_title,sys_lastContributor,sys_title"
        var result = checkResponse(OK, POST, getV1Headers(),
                Map.of(QUERY_STRING_PROP, query, "sort", List.of(SORT), OFFSET_PROP, 0));
        checkQueryResponseBody("rest/query_res_all.json", result, " ORDER BY sys_title, sys_lastContributor, sys_title");
    }

    @Test
    public void testQueryAllWithInvalidSortParametersPOSTV1() throws Exception {
        String SORT = "sort by sys_title ascending: sys_title asc";
        String query = "SELECT * FROM SysContent";

        checkResponse(BAD_REQUEST, POST, getV1Headers(),
                Map.of(QUERY_STRING_PROP, query, "sort", List.of(SORT), LIMIT_PROP, 0, OFFSET_PROP, 0));
    }

    @Test
    public void testQueryAllPOST() throws Exception {
        var result = checkResponse(OK, POST, Map.of());
        checkQueryResponseBody("rest/query_res_all.json", result, "");
    }

    @Test
    public void testQueryAllSortedPOST() throws Exception {
        var result = checkResponse(OK, POST, Map.of("sort", List.of(TITLE_SORT)));
        checkQueryResponseBody("rest/query_res_all.json", result, " ORDER BY sys_title");
    }

    @Test
    @WithUser("myUserName") // user exists
    public void testQueryNoPermissionsPOST() throws Exception {
        var result = checkResponse(OK, POST, Map.of());
        checkResponseBody("rest/query_res_empty.json", result);
    }

    @Test
    public void testValidQueryPOST() throws Exception {
        // ES actually fine with "from Foo"...
        String validQuery = "SELECT * from Foo";
        var result = checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, validQuery));
        checkQueryResponseBody("rest/query_res_default.json", result);
    }

    /**
     * The counterpart to the seven handlers whose body this branch made required: these two tolerate a null
     * body on purpose -- they substitute a default DTO and search everything -- so a caller that sends none
     * keeps getting results rather than the 400 the other endpoints now answer. Asserted because the claim
     * that they are deliberate is what justified leaving them alone.
     */
    @Test
    public void testQueryWithANullBodyStillSearches() throws Exception {
        checkResponseWithPayload(OK, POST, REPO_ID, "null", PATH).andExpect(jsonPath("documents").isArray());
    }

    /**
     * POST /api/query/termsAggregation needs two inputs it cannot work without, and only one of them said so:
     * a body missing termsAggregationProperty was a clean 400 while a body missing query answered 500
     * (HXCS-8835), because the aggregation read repositoryId off the absent object. The published schema
     * advertised the field that crashed as optional.
     * <p>
     * These live here rather than beside the other termsAggregation tests because that class is @Disabled, and
     * a skipped test proves nothing. Neither request reaches OpenSearch: both are rejected while the body is
     * being read.
     */
    @Test
    public void testTermsAggregationWithoutQueryIsABadRequest() throws Exception {
        var result = checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID,
                "{\"termsAggregationProperty\":\"sys_primaryType\"}", PATH + "/termsAggregation");
        ContentTestHelper.checkErrorResponse(BAD_REQUEST, "Invalid input", result);
    }

    /** The mirror case, which answered 400 all along: the property missing, the query present. */
    @Test
    public void testTermsAggregationWithoutPropertyIsABadRequest() throws Exception {
        var result = checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID,
                "{\"query\":{\"query\":\"SELECT * FROM SysContent\"}}", PATH + "/termsAggregation");
        ContentTestHelper.checkErrorResponse(BAD_REQUEST, "Invalid input", result);
    }

    /**
     * Present-and-null is not the same request as absent, and {@code @JsonProperty(required = true)} refuses
     * only the latter, so both of these answered 500 before HXCS-8835 while the absent field answered 400. An
     * empty query object is deliberately still accepted: it filters nothing, which is a legitimate aggregation.
     */
    @Test
    public void testTermsAggregationWithExplicitlyNullFieldsIsABadRequest() throws Exception {
        var noQuery = checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID,
                "{\"query\":null,\"termsAggregationProperty\":\"sys_primaryType\"}", PATH + "/termsAggregation");
        ContentTestHelper.checkErrorResponse(BAD_REQUEST, "Invalid input", noQuery);

        var noProperty = checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID,
                "{\"query\":{\"query\":\"SELECT * FROM SysContent\"},\"termsAggregationProperty\":null}",
                PATH + "/termsAggregation");
        ContentTestHelper.checkErrorResponse(BAD_REQUEST, "Invalid input", noProperty);
    }

    @Test
    public void testInvalidQueryPOST() throws Exception {
        // ES actually fine with "from Foo"...
        String esInvalidQuery = "SELECT * from Foo WHERE";
        var result = checkResponse(BAD_REQUEST, POST, Map.of(QUERY_STRING_PROP, esInvalidQuery));
        ContentTestHelper.checkErrorResponse(BAD_REQUEST, Pattern.compile(
                "Query: SELECT \\* from Foo WHERE, At position 1:23 mismatched input '<EOF>' expecting .*"), result);
    }

    protected void checkQuery(String query, int expectedResultCount) throws Exception {
        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, query)).andExpect(
                jsonPath("count").value(expectedResultCount));

        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, query)).andExpect(
                jsonPath("count").value(expectedResultCount));
    }

    @Test
    public void testComplexQueriesPOST() throws Exception {
        checkQuery("SELECT * from TestDocument", 1);
        checkQuery("SELECT * FROM SysContent WHERE sys_title = \"foo\"", 0);
        checkQuery("SELECT * FROM SysContent WHERE sys_title = \"title\"", 1);
        checkQuery("SELECT * FROM SysContent WHERE tp_stringArray IN (\"foo\")", 1);
        checkQuery("SELECT * FROM SysContent WHERE tp_stringArray IN (\"schtroumpf\")", 0);
    }

    @Test
    public void testFulltextQueryPOST() throws Exception {
        String query = "SELECT * FROM SysContent WHERE sys_fulltext = \"complex\"";
        // ok with ES
        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, query)).andExpect(jsonPath("count").value(1));

        // try ES again with a non-matching query
        query = "SELECT * FROM SysContent WHERE sys_fulltext = \"schtroumpf\"";
        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, query)).andExpect(jsonPath("count").value(0));
    }

    @Test
    public void testIndexingDone() throws Exception {
        checkResponse(OK, PATH + "/waitForFullTextSearchIndexing", GET, null).andExpect(
                jsonPath("indexing").value("done"));
    }

    @Test
    @WithMockUser(ADMIN)
    public void testLimitZeroReturnsNoDocs() throws Exception {
        HttpHeaders headers = getV1Headers();
        var response = checkResponse(OK, POST, headers, Map.of("limit", 0));

        response.andExpect(jsonPath("$.documents").isEmpty());
        response.andExpect(jsonPath("$.limit").value(0));
    }

    @Test
    @WithMockUser(ADMIN)
    public void testLimitMinusOneReturnsDocs() throws Exception {
        HttpHeaders headers = getV1Headers();
        var response = checkResponse(OK, POST, headers, Map.of("limit", -1));

        response.andExpect(jsonPath("$.documents").isNotEmpty());
        response.andExpect(jsonPath("$.limit").value(-1));
    }

    @Test
    @WithMockUser(ADMIN)
    public void testLimitAbsentUsesDefault() throws Exception {
        HttpHeaders headers = getV1Headers();
        var response = checkResponse(OK, POST, headers, Map.of());

        response.andExpect(jsonPath("$.documents").isNotEmpty());
        response.andExpect(jsonPath("$.limit").value(10));
    }

    @Test
    public void testEmbeddingQueryDTOLimitNullDefaults() {
        EmbeddingQueryDTO dto = new EmbeddingQueryDTO(
                "repo",
                "*",
                List.of(1.0, 2.0),
                null,
                null,
                null,
                null,
                0L,
                false,
                null
        );

        assertEquals(DEFAULT_LIMIT_VALUE, dto.limit());
        assertEquals(DEFAULT_SELECT_STATEMENT, dto.query());
    }

    // --- expr syntax ---

    protected static final String SYNTAX_PROP = "syntax";

    protected static final String EXPR = "expr";

    @Autowired
    RepositoryQueryManager rqm;

    protected ResultActions checkPostResponse(Status expectedStatus, String referencePostBody, Object... args)
            throws Exception {
        return checkResponseWithReferencePayload(expectedStatus, PATH, POST, referencePostBody, args);
    }

    /** A full request and response, with the query expressed as an expression. */
    @Test
    public void testExprQueryReferencePOST() throws Exception {
        var result = checkPostResponse(OK, "rest/query_req_expr.json");
        checkQueryResponseBody("rest/query_res_expr.json", result);
    }

    /** The document of this test is titled "title". */
    @Test
    public void testExprQueryPOST() throws Exception {
        String expr = "content.sys_title == \"title\"";
        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, expr, SYNTAX_PROP, EXPR)) //
                .andExpect(jsonPath("count").value(1))
                // the response echoes the query as the caller expressed it
                .andExpect(jsonPath("query").value(expr))
                .andExpect(jsonPath("syntax").value(EXPR));
        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, "content.sys_title == \"foo\"", SYNTAX_PROP, EXPR))
                .andExpect(jsonPath("count").value(0));
        // no expression filters nothing out, and there is then no query to echo back
        checkResponse(OK, POST, Map.of(SYNTAX_PROP, EXPR)).andExpect(jsonPath("count").value(1))
                                                          .andExpect(jsonPath("query").isEmpty())
                                                          .andExpect(jsonPath("syntax").value(EXPR));
    }

    /** An unsupported syntax is rejected rather than silently read as the default. */
    @Test
    public void testUnknownQuerySyntaxPOST() throws Exception {
        var result = checkResponse(BAD_REQUEST, POST,
                Map.of(QUERY_STRING_PROP, DEFAULT_SELECT_STATEMENT, SYNTAX_PROP, "exrp"));
        ContentTestHelper.checkErrorResponse(BAD_REQUEST,
                Pattern.compile(".*not one of the values accepted for Enum class.*"), result);
    }

    /** The default syntax is HXQL, which the response has no need to mention. */
    @Test
    public void testQuerySyntaxDefaultsToHxqlPOST() throws Exception {
        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, DEFAULT_SELECT_STATEMENT))
                .andExpect(jsonPath("query").value(DEFAULT_SELECT_STATEMENT))
                .andExpect(jsonPath("syntax").doesNotExist());
        // asking for it explicitly is the same thing
        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, DEFAULT_SELECT_STATEMENT, SYNTAX_PROP, "hxql"))
                .andExpect(jsonPath("syntax").doesNotExist());
    }

    /** The expression sees the current user, and what does not depend on the content folds into a constant. */
    @Test
    public void testExprQueryWithUserPOST() throws Exception {
        String expr = "content.sys_title == \"title\" && user.id == \"" + ADMIN + "\"";
        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, expr, SYNTAX_PROP, EXPR))
                .andExpect(jsonPath("count").value(1));
        // an always false constant makes the whole query match nothing
        expr = "content.sys_title == \"title\" && user.id == \"nobody\"";
        checkResponse(OK, POST, Map.of(QUERY_STRING_PROP, expr, SYNTAX_PROP, EXPR))
                .andExpect(jsonPath("count").value(0));
    }

    @Test
    public void testInvalidExprQueryPOST() throws Exception {
        String expr = "this is not an expression";
        var result = checkResponse(BAD_REQUEST, POST, Map.of(QUERY_STRING_PROP, expr, SYNTAX_PROP, EXPR));
        ContentTestHelper.checkErrorResponse(BAD_REQUEST, Pattern.compile("Invalid expression: " + expr + ": .*"),
                result);
    }

    /** An expression is a filter, so an HXQL statement is not one, and neither is a lone property. */
    @Test
    public void testExprQueryIsNotAStatementPOST() throws Exception {
        checkResponse(BAD_REQUEST, POST, Map.of(QUERY_STRING_PROP, DEFAULT_SELECT_STATEMENT, SYNTAX_PROP, EXPR));
        var result = checkResponse(BAD_REQUEST, POST, Map.of(QUERY_STRING_PROP, "content.sys_title", SYNTAX_PROP, EXPR));
        ContentTestHelper.checkErrorResponse(BAD_REQUEST, "Expression is not a predicate: content.sys_title", result);
    }

    /** Disallowed system properties are as out of reach in a query as they are anywhere else. */
    @Test
    @WithUser("myUserName") // an ordinary user, not an administrator
    public void testExprQueryDisallowedPropertyPOST() throws Exception {
        var result = checkResponse(BAD_REQUEST, POST,
                Map.of(QUERY_STRING_PROP, "content.sys_acl == \"x\"", SYNTAX_PROP, EXPR));
        ContentTestHelper.checkErrorResponse(BAD_REQUEST,
                Pattern.compile(".*Disallowed system property: sys_acl.*"), result);
    }

    /** An administrator bypasses the security restriction, but the index still rejects an unavailable property. */
    @Test
    public void testExprQueryDisallowedPropertyAsAdministratorPOST() throws Exception {
        // the default test user is an administrator
        String expr = "content.sys_acl == \"x\"";
        checkResponse(BAD_REQUEST, POST, Map.of(QUERY_STRING_PROP, expr, SYNTAX_PROP, EXPR));
    }

    /** Both backends run an expression, and both echo it back. */
    @Test
    public void testExprQueryManagers() {
        String expr = "content.sys_title == \"title\"";
        for (QueryManager manager : List.of(osqm, rqm)) {
            DocumentQueryDTO q = new DocumentQueryDTO(REPO_ID, expr, QuerySyntax.EXPR, null, 0, null, true, null,
                    List.of());
            DocumentQueryResultDTO r = manager.getResults(q);
            assertEquals(1, r.count());
            assertEquals(expr, r.query());
            assertEquals(QuerySyntax.EXPR, r.syntax());
        }
    }

    @Test
    public void testQueryAllPOSTSyncTrue() throws Exception {
        var result = checkResponse(OK, POST, Map.of("sync", true));
        checkQueryResponseBody("rest/query_res_all.json", result, "");
    }

    @Test
    public void testQueryAllSortedPOSTSyncTrue() throws Exception {
        var result = checkResponse(OK, POST, Map.of("sync", true, "sort", List.of(TITLE_SORT)));
        checkQueryResponseBody("rest/query_res_all.json", result, " ORDER BY sys_title");
    }

    @Test
    public void testValidQueryPOSTSyncTrue() throws Exception {
        String validQuery = "SELECT * from Foo";
        var result = checkResponse(OK, POST, Map.of("sync", true, QUERY_STRING_PROP, validQuery));
        checkQueryResponseBody("rest/query_res_default.json", result);
    }

    @Test
    public void testComplexQueriesPOSTSyncTrue() throws Exception {
        checkResponse(OK, POST, Map.of("sync", true, QUERY_STRING_PROP, "SELECT * from TestDocument"))
                .andExpect(jsonPath("count").value(1));
        checkResponse(OK, POST, Map.of("sync", true, QUERY_STRING_PROP, "SELECT * FROM SysContent WHERE sys_title = \"title\""))
                .andExpect(jsonPath("count").value(1));
    }
}
