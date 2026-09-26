/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.query.test;

import static org.hamcrest.Matchers.is;
import static com.hyland.openarch.rest.query.QueryAPI.PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import jakarta.ws.rs.core.Response.Status;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.rest.query.QueryAPI;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

@Features(RestQueryMultipleDocumentsFeature.class)
@Tag("useOpenSearch")
@Import({ QueryAPITestConfiguration.class })
@SpringBootTest(classes = { QueryAPI.class })
public class QueryAPITotalCountTest extends AbstractQueryAPITest {

    private static final String COUNT = "count";

    private static final String DOCUMENTS = "documents";

    private static final String TOTAL_COUNT = "totalCount";

    private static final String TOTAL_COUNT_UP_TO = "trackTotalCountUpTo";

    private static final String TOTAL_COUNT_IS_TRUNC = "totalCountIsTruncated";

    private static final String TRACK_TOTAL_COUNT = "trackTotalCount";

    private static final int DOC_COUNT = 10;

    protected ResultActions checkResponse(Status expectedStatus, Map<String, Object> parameters) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, APPLICATION_JSON_VALUE);
        return checkResponse(expectedStatus, PATH, HttpMethod.POST, headers, parameters);
    }

    protected void assertTotalCount(int expectedDocumentCount, Boolean expectedTrackTotalCount,
            Boolean expectedTotalCountTruncated, Long expectedTotalCount, ResultActions response) throws Exception {
        response.andExpectAll(jsonPath(COUNT).value(expectedDocumentCount),
                jsonPath(DOCUMENTS + ".length()", is(expectedDocumentCount)));

        if (expectedTotalCount != null) {
            response.andExpect(jsonPath(TOTAL_COUNT).value(expectedTotalCount));
        } else {
            response.andExpect(jsonPath(TOTAL_COUNT).doesNotExist());
        }

        if (expectedTrackTotalCount != null) {
            response.andExpect(jsonPath(TRACK_TOTAL_COUNT).value(expectedTrackTotalCount));
        } else {
            response.andExpect(jsonPath(TRACK_TOTAL_COUNT).doesNotExist());
        }

        if (expectedTotalCountTruncated != null) {
            response.andExpect(jsonPath(TOTAL_COUNT_IS_TRUNC).value(expectedTotalCountTruncated));
        } else {
            response.andExpect(jsonPath(TOTAL_COUNT_IS_TRUNC).doesNotExist());
        }
    }

    @Test
    public void testTotalHitCountFalse() throws Exception {
        var response = checkResponse(Status.OK, Map.of(TRACK_TOTAL_COUNT, false));
        assertTotalCount(DOC_COUNT, false, null, null, response);
    }

    @Test
    public void testTotalHitCountTrueCountUpToSetLargerThanCount() throws Exception {
        var response = checkResponse(Status.OK, Map.of(TRACK_TOTAL_COUNT, true, TOTAL_COUNT_UP_TO, 20));
        assertTotalCount(DOC_COUNT, null, null, 10L, response);
    }

    @Test
    public void testTotalHitCountTrueCountUpToEqualCount() throws Exception {
        var response = checkResponse(Status.OK, Map.of(TRACK_TOTAL_COUNT, true, TOTAL_COUNT_UP_TO, DOC_COUNT));
        assertTotalCount(DOC_COUNT, null, null, 10L, response);
    }

    @Test
    public void testTotalHitCountTrueCountUpToLessThanCount() throws Exception {
        var response = checkResponse(Status.OK, Map.of(TRACK_TOTAL_COUNT, true, TOTAL_COUNT_UP_TO, 5));
        assertTotalCount(DOC_COUNT, null, true, 5L, response);
    }
}
