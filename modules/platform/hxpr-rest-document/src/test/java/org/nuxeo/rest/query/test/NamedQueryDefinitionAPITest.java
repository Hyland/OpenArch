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

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.nuxeo.rest.query.QueryAPI.PATH;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.TITLE_SORT;
import static org.nuxeo.rest.query.spec.QueryAssembler.DEFAULT_SELECT_STATEMENT;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.rest.document.test.ContentTestHelper;
import org.nuxeo.rest.query.OpenSearchQueryManager;
import org.nuxeo.rest.query.QueryAPI;
import org.nuxeo.rest.query.QueryManager;
import org.nuxeo.rest.query.io.DocumentNamedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.spec.NamedQueryDefinition;
import org.nuxeo.rest.query.spec.NamedQueryRegistry;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import jakarta.ws.rs.core.Response.Status;

@Features(RestQueryMultipleDocumentsFeature.class)
@Tag("useOpenSearch")
@Import({ QueryAPITestConfiguration.class, TestNamedQueryConfig.class })
@SpringBootTest(classes = { QueryAPI.class })
public class NamedQueryDefinitionAPITest extends AbstractQueryAPITest {

    protected static final String API_PATH = PATH + "/named";

    protected static final String FOLDER_LISTING_QUERY = DEFAULT_SELECT_STATEMENT //
            + " WHERE sys_parentId = '00000000-0000-0000-0000-000000000000'" //
            + " AND sysver_isVersion = 0 ORDER BY sys_title";

    protected ResultActions checkResponse(Status expectedStatus, Map<String, Object> queryParameters) throws Exception {
        return checkResponse(expectedStatus, API_PATH, POST, queryParameters);
    }

    protected ResultActions checkPostResponse(Status expectedStatus, String referencePostBody, Object... args)
            throws Exception {
        return checkResponseWithReferencePayload(expectedStatus, API_PATH, POST, referencePostBody, args);
    }

    // check query registry

    @Autowired
    NamedQueryRegistry qreg;

    @Test
    public void testDefaultNamedQueries() {
        assertEquals(6 + 2 + 2, qreg.getNamedQueries().size()); // 2 aliases and 2 added by test registry
        assertNotNull(qreg.getNamedQuery("folder_listing_test"));
        assertNotNull(qreg.getNamedQuery("folder_listing_test2"));
        NamedQueryDefinition folderListing = qreg.getNamedQuery("folder_listing");
        assertNotNull(folderListing);
        assertEquals("folder_listing", folderListing.name());
        assertEquals(List.of("document_content"), folderListing.aliases());
        assertEquals(List.of(TITLE_SORT), folderListing.sort());
        assertNotNull(folderListing.whereClauseDefinition());
        assertEquals(List.of(), folderListing.quickFilters());
    }

    // check query managers

    @Autowired
    OpenSearchQueryManager osqm;

    protected void checkFolderListing(QueryManager manager) {
        DocumentNamedQueryDTO q = new DocumentNamedQueryDTO("folder_listing", REPO_ID, -1L, 0, true, -1,
                Map.of("parentId", ROOT_ID), null, null);
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID, FOLDER_LISTING_QUERY, -1L, 0, 10, 10L);
        checkResultDocument(r, 0, "File0");
    }

    protected void checkDocumentSuggestion(QueryManager manager) {
        DocumentNamedQueryDTO q = new DocumentNamedQueryDTO("default_document_suggestion", REPO_ID, -1L, 0, true, -1,
                Map.of("fulltext", "File*"), List.of(TITLE_SORT), null);
        DocumentQueryResultDTO r = manager.getResults(q);
        checkResult(r, REPO_ID,
                DEFAULT_SELECT_STATEMENT + " WHERE sys_fulltext = 'File*' AND sysver_isVersion = 0 ORDER BY sys_title",
                -1L, 0, 10, 10L);
        checkResultDocument(r, 0, "File0");
    }

    protected void checkAdvancedSearch(QueryManager manager) {
        DocumentNamedQueryDTO q = new DocumentNamedQueryDTO("advanced_search", REPO_ID, -1L, 0, true, -1,
                Map.of("fulltext", "File*"), List.of(TITLE_SORT), null);
        DocumentQueryResultDTO r = manager.getResults(q);;
        checkResult(r, REPO_ID, //
                DEFAULT_SELECT_STATEMENT + " WHERE sys_fulltext = 'File*'" //
                        + " AND sys_parentId IS NOT NULL AND sys_title IS NOT NULL AND sysver_isVersion = 0" //
                        + " ORDER BY sys_title", //
                -1L, 0, 10, 10L);
        checkResultDocument(r, 0, "File0");
    }

    @Test
    public void tesOpenSearchQueryManager() {
        assertNotNull(osqm);
        checkFolderListing(osqm);
        checkDocumentSuggestion(osqm);
        checkAdvancedSearch(osqm);
    }

    // check rest endpoint

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testNamedQueryNamesNoAuth() throws Exception {
        mvc.perform(MockMvcRequestBuilders.request(GET, API_PATH))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @Test
    public void testNamedQueryNames() throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/named_query_names.json", result);
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testNamedQueryUnauthorized() throws Exception {
        mvc.perform(MockMvcRequestBuilders.request(GET, API_PATH + "/folder_listing"))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @Test
    @WithUser(ALT_USER)
    public void testNamedQueryNoPermissions() throws Exception {
        checkResponse(OK, GET, REPO_ID, API_PATH + "/folder_listing");
    }

    @Test
    public void testNamedQueryFolderListing() throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, API_PATH + "/folder_listing");
        checkResponseBody("rest/named_query_folder_listing.json", result);
    }

    @Test
    public void testNamedQueryAdvancedSearch() throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, API_PATH + "/advanced_search");
        checkResponseBody("rest/named_query_advanced_search.json", result);
    }

    @Test
    public void testNamedQueryNotFound() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, API_PATH + "/foo")
                .andExpectAll(jsonPath("entity-type").value("exception"),
                        jsonPath("message").value("404 NOT_FOUND \"Named query not found.\""),
                        jsonPath("status").value(404));
    }

    /**
     * The handler passed its optional body to the query manager, which read queryName off it -- so the JSON
     * literal null answered 500 rather than 400 (HXCS-8835). A named query cannot run without a name.
     */
    @Test
    public void testNamedQueryWithoutBodyIsABadRequest() throws Exception {
        checkResponseWithPayload(Status.BAD_REQUEST, POST, REPO_ID, "null", API_PATH).andExpectAll(
                jsonPath("entity-type").value("exception"),
                jsonPath("status").value(Status.BAD_REQUEST.getStatusCode()),
                jsonPath("message").value("Required request body is missing"));
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testQueryUnauthorized() throws Exception {
        checkResponse(UNAUTHORIZED, Map.of());
    }

    @Test
    @WithUser(ALT_USER)
    public void testQueryNoPermissions() throws Exception {
        var result = checkPostResponse(OK, "rest/query_req_named.json");
        checkResponseBody("rest/query_res_named_empty.json", result);
    }

    @Test
    public void testQueryFolderListing() throws Exception {
        checkPostResponse(OK, "rest/query_req_named.json").andExpect(jsonPath("count").value(10))
                                                          .andExpect(jsonPath("query").value(FOLDER_LISTING_QUERY));
    }

    @Test
    public void testQueryAdvancedSearch() throws Exception {
        var result = checkPostResponse(OK, "rest/query_req_named_advanced_search.json");
        ContentTestHelper.checkJson("rest/query_res_named_advanced_search.json", result, RESULT_COMPATOR);
    }

    @Test
    public void testQueryAdvancedSearchQuickFilters() throws Exception {
        var result = checkPostResponse(OK, "rest/query_req_named_advanced_search_quick.json");
        ContentTestHelper.checkJson("rest/query_res_named_advanced_search_quick.json", result, RESULT_COMPATOR);
    }

}
