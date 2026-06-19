/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.query.test;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.nuxeo.ecm.core.query.nxql.NXQLQueryBuilder.DEFAULT_SELECT_STATEMENT;
import static org.nuxeo.rest.query.QueryAPI.PATH;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.TITLE_SORT;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.DEFAULT_LIMIT_VALUE;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.LIMIT_PROP;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.OFFSET_PROP;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.QUERY_STRING_PROP;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import jakarta.ws.rs.core.Response.Status;

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
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryDTO;
import org.nuxeo.runtime.test.runner.Features;
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
                0L,
                false,
                null
        );

        assertEquals(DEFAULT_LIMIT_VALUE, dto.limit());
        assertEquals(DEFAULT_SELECT_STATEMENT, dto.query());
    }
}
