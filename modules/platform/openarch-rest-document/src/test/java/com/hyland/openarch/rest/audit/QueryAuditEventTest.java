/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.rest.audit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.hyland.openarch.ecm.core.api.event.DocumentEventTypes;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.test.CapturingEventListener;
import com.hyland.openarch.rest.document.DocumentMapper;
import com.hyland.openarch.rest.query.OpenSearchQueryManager;
import com.hyland.openarch.rest.query.QueryAPI;
import com.hyland.openarch.rest.query.RepositoryQueryManager;
import com.hyland.openarch.rest.query.io.DocumentQueryDTO;
import com.hyland.openarch.rest.query.io.DocumentQueryResultDTO;
import com.hyland.openarch.rest.query.test.AbstractQueryAPITest;
import com.hyland.openarch.rest.query.test.QueryAPITestConfiguration;
import com.hyland.openarch.rest.query.test.RestQueryMultipleDocumentsFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.WithApplicationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.Response.Status.OK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.RELATED_OBJECT;
import static com.hyland.openarch.rest.document.test.ContentTestHelper.getJsonPath;
import static com.hyland.openarch.rest.query.QueryAPI.PATH;
import static com.hyland.openarch.rest.query.io.DocumentQueryDTO.LIMIT_PROP;
import static com.hyland.openarch.rest.query.io.DocumentQueryDTO.OFFSET_PROP;
import static com.hyland.openarch.rest.query.io.DocumentQueryDTO.QUERY_STRING_PROP;
import static org.springframework.http.HttpMethod.POST;

@Tag("useOpenSearch")
@Import({ QueryAPITestConfiguration.class })
@SpringBootTest(classes = { QueryAPI.class, OpenSearchQueryManager.class, DocumentMapper.class })
@Features(RestQueryMultipleDocumentsFeature.class)
@WithApplicationProperties("audit-application.properties")
public class QueryAuditEventTest extends AbstractQueryAPITest {

    @ParameterizedTest
    @CsvSource({ "-1, 10",  // limit -1 means no limit, expect all 10 results
            "6, 6"    // limit 6, expect 6 results / events
    })
    void testQueryAuditEvent(int limit, int expectedResults) throws Exception {

        try (CapturingEventListener listener = new CapturingEventListener(DocumentEventTypes.DOCUMENT_QUERIED)) {

            String sort = "sys_title";
            String query = "SELECT * FROM SysFile";
            var result = checkResponse(OK, PATH, POST, getV1Headers(),
                    Map.of(QUERY_STRING_PROP, query, "sort", List.of(sort), LIMIT_PROP, limit, OFFSET_PROP, 0));

            List<Event> capturedEvents = listener.getCapturedEvents();
            // verify the query result
            assertThat(result).isNotNull();
            // Count documents in the array to make sure the query was executed successfully
            assertThat((int) getJsonPath(result, "documents.length()")).isEqualTo(expectedResults);

            // verify that the event was captured
            assertEquals(expectedResults, capturedEvents.size());
            assertThat(capturedEvents).allMatch(event -> event.getName().equals(DocumentEventTypes.DOCUMENT_QUERIED));
            //check that related object for all events is the same ID
            String relatedObjectId = (String) capturedEvents.getFirst().getContext().getProperty(RELATED_OBJECT);
            assertThat(capturedEvents).allMatch(
                    event -> relatedObjectId.equals(event.getContext().getProperty(RELATED_OBJECT)));
        }
    }

    @ParameterizedTest
    @CsvSource({ "0, 10",  // limit 0 means no limit, expect all 10 results
            "6, 6"    // limit 6, expect 6 results / events
    })
    void testQueryAuditEventRepo(Long limit, int expectedResults, @Autowired RepositoryQueryManager queryManager) {

        try (CapturingEventListener listener = new CapturingEventListener(DocumentEventTypes.DOCUMENT_QUERIED)) {

            List<String> sort = List.of("sys_title");
            String query = "SELECT * FROM SysFile";

            DocumentQueryDTO documentQueryDTO = new DocumentQueryDTO(REPO_ID, query, limit, 0, sort);
            DocumentQueryResultDTO results = queryManager.getResults(documentQueryDTO);

            List<Event> capturedEvents = listener.getCapturedEvents();
            // verify the query result
            assertThat(results).isNotNull();
            // Count documents in the results to make sure the query was executed successfully
            assertThat(results.count()).isEqualTo(expectedResults);

            // verify that the event was captured
            assertEquals(expectedResults, capturedEvents.size());
            assertThat(capturedEvents).allMatch(event -> event.getName().equals(DocumentEventTypes.DOCUMENT_QUERIED));
            //check that related object for all events is the same ID
            String relatedObjectId = (String) capturedEvents.getFirst().getContext().getProperty(RELATED_OBJECT);
            assertThat(capturedEvents).allMatch(
                    event -> relatedObjectId.equals(event.getContext().getProperty(RELATED_OBJECT)));
        }
    }
}
