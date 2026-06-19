/*
 * (C) Copyright 2025 Hyland (http://www.hyland.com/) and others.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.nuxeo.rest.query.QueryAPI.PATH;
import static org.nuxeo.rest.query.test.RestQueryAggregationsFeature.BOOLEAN_PROP_PATH;
import static org.nuxeo.rest.query.test.RestQueryAggregationsFeature.STRING_LIST_PROP_PATH;
import static org.springframework.http.HttpMethod.POST;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.json.JSONException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.rest.document.test.ContentTestHelper;
import org.nuxeo.rest.query.OpenSearchQueryManager;
import org.nuxeo.rest.query.QueryAPI;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.TermsAggregationQueryResultDTO;
import org.nuxeo.runtime.test.runner.Features;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.Response;

@Features(RestQueryAggregationsFeature.class)
@Tag("useOpenSearch")
@Import({ QueryAPITestConfiguration.class })
@SpringBootTest(classes = { QueryAPI.class })
@Disabled("Temporarily disabled due to limit behavior change")
public class TermsAggregationQueryAPITest extends AbstractQueryAPITest {

    protected static final String API_PATH = PATH + "/termsAggregation";

    protected static final int DEFAULT_LIMIT = 10;

    static final String DEFAULT_HXQL_QUERY = "SELECT * FROM SysContent";

    static final String JSON_PAYLOAD = """
             {
               "termsAggregationProperty": "%s",
               "limit": "%s",
               "searchTerm": %s,
               "query": {
                 "limit": 0,
                 "offset": 0,
                 "query": "%s",
                 "repositoryId": "%s",
                 "trackTotalCount": true,
                 "trackTotalCountUpTo": -1
               }
             }
            """;

    @Autowired
    OpenSearchQueryManager openSearchQueryManager;

    static Stream<Arguments> searchExpressionTestCases() {
        return Stream.of(Arguments.of("", "rest/terms_aggregation_query_res_empty_search.json"),
                Arguments.of(null, "rest/terms_aggregation_query_res_empty_search.json"),
                Arguments.of("*", "rest/terms_aggregation_query_res_empty_search.json"),
                Arguments.of("**", "rest/terms_aggregation_query_res_empty_search.json"),
                Arguments.of("objectId", "rest/terms_aggregation_query_res_search_objectId.json"),
                Arguments.of("ID", "rest/terms_aggregation_query_res_search_ID.json"),
                Arguments.of("*ow", "rest/terms_aggregation_query_res_search_wildcard_ow.json"),
                Arguments.of(":*", "rest/terms_aggregation_query_res_search_colon_wildcard.json"),
                Arguments.of("mo*ed", "rest/terms_aggregation_query_res_search_mo_wildcard_ed.json"));
    }

    static Stream<Arguments> hxqlQueries() {
        return Stream.of(
                Arguments.of("SELECT * FROM SysContent WHERE sys_description='source-1'",
                        "rest/terms_aggregation_query_res_source_1.json"),
                Arguments.of("SELECT * FROM SysContent WHERE sys_description='source-2'",
                        "rest/terms_aggregation_query_res_source_2.json"),
                Arguments.of("SELECT * FROM SysContent WHERE sys_description='source-unknown'",
                        "rest/terms_aggregation_query_res_source_unknown.json"));
    }

    protected ResultActions checkTermsAggregationResponse(Response.StatusType expectedStatus, String propertyField,
            String searchTerm, int aggregationLimit, String hxqlQuery) throws Exception {
        String searchString = searchTerm == null ? null : '"' + searchTerm + '"';
        String payload = JSON_PAYLOAD.formatted(propertyField, aggregationLimit, searchString, hxqlQuery, REPO_ID);
        return checkResponseWithPayload(expectedStatus, POST, REPO_ID, payload, API_PATH);
    }

    protected ResultActions checkTermsAggregationResponse(Response.StatusType expectedStatus, String propertyField,
            String searchTerm, int aggregationLimit) throws Exception {
        return checkTermsAggregationResponse(expectedStatus, propertyField, searchTerm, aggregationLimit,
                DEFAULT_HXQL_QUERY);
    }

    protected ResultActions checkTermsAggregationResponse(String propertyField, String searchTerm, int aggregationLimit,
            String hxqlQuery) throws Exception {
        return checkTermsAggregationResponse(OK, propertyField, searchTerm, aggregationLimit, hxqlQuery);

    }

    protected ResultActions checkTermsAggregationResponse(String propertyField, String searchTerm, int aggregationLimit)
            throws Exception {
        return checkTermsAggregationResponse(propertyField, searchTerm, aggregationLimit, DEFAULT_HXQL_QUERY);
    }

    protected void checkResultAgainstJson(TermsAggregationQueryResultDTO result, String expectedFilePath)
            throws IOException, JSONException {
        String expected = ContentTestHelper.getReferenceContent(expectedFilePath);
        String actual = new ObjectMapper().writeValueAsString(result);
        JSONAssert.assertEquals(expected, actual, true);
    }

    @Nested
    class RestAPITests {

        @Test
        @WithUser(TEST_ANONYMOUS_USER)
        void testQueryUnauthorized() throws Exception {
            checkResponse(UNAUTHORIZED, API_PATH, POST, Map.of());
        }

        @Test
        @WithUser(ALT_USER)
        void testQueryWithAltUser() throws Exception {
            ResultActions result = checkTermsAggregationResponse(STRING_LIST_PROP_PATH, "so", DEFAULT_LIMIT);
            checkResponseBody("rest/terms_aggregation_query_res_alt_user.json", result);
        }

        @Test
        void testBasicTermsAggregationQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse(STRING_LIST_PROP_PATH, "so", DEFAULT_LIMIT);
            checkResponseBody("rest/terms_aggregation_query_res_basic.json", result);
        }

        @Test
        void testEmptySearchExpressionQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse(STRING_LIST_PROP_PATH, "", DEFAULT_LIMIT);
            checkResponseBody("rest/terms_aggregation_query_res_empty_search.json", result);
        }

        @Test
        void testNullSearchExpressionQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse(STRING_LIST_PROP_PATH, null, DEFAULT_LIMIT);
            checkResponseBody("rest/terms_aggregation_query_res_empty_search.json", result);
        }

        @Test
        void testZeroLimitQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse(STRING_LIST_PROP_PATH, null, 0);
            checkResponseBody("rest/terms_aggregation_query_res_empty_search.json", result);
        }

        @Test
        void testNegativeLimitQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse(STRING_LIST_PROP_PATH, null, -1);
            checkResponseBody("rest/terms_aggregation_query_res_empty_search.json", result);
        }

        @Test
        void testInvalidPropertyFieldQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse("invalid_field", null, DEFAULT_LIMIT);
            assertNotNull(result);
            checkResponseBody("rest/terms_aggregation_query_res_empty.json", result);
        }

        @Test
        void testInvalidHxlSyntaxQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse(BAD_REQUEST, "anything", null, DEFAULT_LIMIT,
                    "SELECT * FROM some bad syntax");
            assertNotNull(result);
            ContentTestHelper.checkErrorResponse(BAD_REQUEST, Pattern.compile(
                    "Query: SELECT \\* FROM some bad syntax, At position 1:19 mismatched input 'bad' expecting .*"),
                    result);
        }

        @Test
        void testNonStringPropertyFieldQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse(BAD_REQUEST, BOOLEAN_PROP_PATH, null, DEFAULT_LIMIT);
            assertNotNull(result);
            ContentTestHelper.checkErrorResponse(BAD_REQUEST, Pattern.compile(
                    "Error performing OpenSearch query: Can only use wildcard queries on keyword and text fields - not on \\[any_obj.myBoolean] which is of type \\[boolean]"),
                    result);
        }

        @Test
        void testNonStringPropertyWithSearchTermQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse(BAD_REQUEST, BOOLEAN_PROP_PATH, "thatshouldnotwork",
                    DEFAULT_LIMIT);
            assertNotNull(result);
            ContentTestHelper.checkErrorResponse(BAD_REQUEST, Pattern.compile(
                    "Error performing OpenSearch query: Can only use wildcard queries on keyword and text fields - not on \\[any_obj.myBoolean] which is of type \\[boolean]"),
                    result);
        }

        @ParameterizedTest
        @MethodSource("org.nuxeo.rest.query.test.TermsAggregationQueryAPITest#searchExpressionTestCases")
        void testSearchExpressionVariants(String searchTerm, String expectedJsonPath) throws Exception {
            ResultActions result = checkTermsAggregationResponse(STRING_LIST_PROP_PATH, searchTerm, DEFAULT_LIMIT);
            checkResponseBody(expectedJsonPath, result);
        }

        @ParameterizedTest
        @MethodSource("org.nuxeo.rest.query.test.TermsAggregationQueryAPITest#hxqlQueries")
        void testHxqlFilteredQueries(String query, String expectedJsonPath) throws Exception {
            ResultActions results = checkTermsAggregationResponse(STRING_LIST_PROP_PATH, "so", DEFAULT_LIMIT, query);
            checkResponseBody(expectedJsonPath, results);
        }

        @Test
        void testHighLimitQuery() throws Exception {
            ResultActions result = checkTermsAggregationResponse(STRING_LIST_PROP_PATH, "so", 100);
            checkResponseBody("rest/terms_aggregation_query_res_high_limit.json", result);
        }
    }

    @Nested
    class QueryManagerTests {

        @Test
        void testBasicQuery() throws Exception {
            assertNotNull(openSearchQueryManager);
            DocumentQueryDTO query = new DocumentQueryDTO(REPO_ID, null, 1L, 0, true, -1, null);
            TermsAggregationQueryResultDTO result = openSearchQueryManager.getResults(query, STRING_LIST_PROP_PATH,
                    "so", DEFAULT_LIMIT);

            assertNotNull(result);
            assertEquals(REPO_ID, result.repositoryId());
            checkResultAgainstJson(result, "rest/terms_aggregation_query_res_basic.json");
        }

        @ParameterizedTest
        @MethodSource("org.nuxeo.rest.query.test.TermsAggregationQueryAPITest#searchExpressionTestCases")
        void testSearchExpressionVariants(String searchTerm, String expectedJsonPath) throws Exception {
            DocumentQueryDTO query = new DocumentQueryDTO(REPO_ID, null, 1L, 0, true, -1, null);
            TermsAggregationQueryResultDTO result = openSearchQueryManager.getResults(query, STRING_LIST_PROP_PATH,
                    searchTerm, DEFAULT_LIMIT);

            assertNotNull(result);
            assertEquals(REPO_ID, result.repositoryId());
            checkResultAgainstJson(result, expectedJsonPath);
        }

        @ParameterizedTest
        @MethodSource("org.nuxeo.rest.query.test.TermsAggregationQueryAPITest#hxqlQueries")
        void testHxqlFilteredQueries(String query, String expectedJsonPath) throws Exception {
            DocumentQueryDTO documentQuery = new DocumentQueryDTO(REPO_ID, query, 1L, 0, true, -1, null);
            TermsAggregationQueryResultDTO result = openSearchQueryManager.getResults(documentQuery,
                    STRING_LIST_PROP_PATH, "so", DEFAULT_LIMIT);

            assertNotNull(result);
            assertEquals(REPO_ID, result.repositoryId());
            checkResultAgainstJson(result, expectedJsonPath);
        }

        @Test
        void testHighLimit() throws Exception {
            DocumentQueryDTO query = new DocumentQueryDTO(REPO_ID, null, 1L, 0, true, -1, null);
            TermsAggregationQueryResultDTO result = openSearchQueryManager.getResults(query, STRING_LIST_PROP_PATH,
                    "so", 100);

            assertNotNull(result);
            assertEquals(REPO_ID, result.repositoryId());
            checkResultAgainstJson(result, "rest/terms_aggregation_query_res_high_limit.json");
        }
    }
}
