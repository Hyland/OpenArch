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
package org.nuxeo.rest.event.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.TITLE_SORT;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.test.CapturingEventListener;
import org.nuxeo.rest.acl.ACEMapper;
import org.nuxeo.rest.acl.ACLMapper;
import org.nuxeo.rest.document.DocumentMapper;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.query.OpenSearchQueryManager;
import org.nuxeo.rest.query.conf.DefaultNamedQueries;
import org.nuxeo.rest.query.conf.DefaultNamedQueryRegistry;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentNamedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.test.RestQueryMultipleDocumentsFeature;
import org.nuxeo.rest.upload.handler.UploadHandlerConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.WithApplicationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

import org.nuxeo.ecm.core.event.impl.query.QueryEvent;

@Features(RestQueryMultipleDocumentsFeature.class)
@WithApplicationProperties("usage-application.properties")
@Tag("useOpenSearch")
@SpringBootTest(classes = { OpenSearchQueryManager.class, DefaultNamedQueryRegistry.class, DefaultNamedQueries.class,
        DocumentMapper.class, UploadHandlerConfig.class, ACLMapper.class, ACEMapper.class })
@TestPropertySource("classpath:usage-application.properties")
class QueryEventTest extends AbstractAPITest {

    private static final String EVENT_NAME = QueryEvent.GENERIC_TYPE_KEY_PATTERN.formatted(QueryEvent.EVENT_NAME_PREFIX, QueryEvent.GENERIC_TYPE_KEY_SUFFIX);
    private static final String QUERY_NAME = "advanced_search";
    private static final CustomComparator EVENT_COMPARATOR = new CustomComparator(JSONCompareMode.STRICT,
            new Customization("elapsedTime", (o1, o2) -> o2 instanceof Number));

    @Autowired
    OpenSearchQueryManager osqm;

    @Test
    void testQueryEvent_basic() throws Exception {
        try (CapturingEventListener listener = new CapturingEventListener(EVENT_NAME)) {
            osqm.getResults(new DocumentQueryDTO(REPO_ID));
            assertQueryEventMetadata(listener, false);
        }
    }

    @Test
    void testQueryEvent_advanced() throws Exception {
        try (CapturingEventListener listener = new CapturingEventListener(EVENT_NAME)) {
            osqm.getResults(new DocumentAdvancedQueryDTO(REPO_ID, null, null, 0, true, -1, List.of(TITLE_SORT)));
            assertQueryEventMetadata(listener, false);
        }
    }

    @Test
    void testQueryEvent_named() throws Exception {
        DocumentNamedQueryDTO namedQuery = new DocumentNamedQueryDTO(
                QUERY_NAME, REPO_ID, null, 0, true, -1, Map.of("fulltext", "File*"), List.of(TITLE_SORT), null);
        try (CapturingEventListener listener = new CapturingEventListener(EVENT_NAME)) {
            osqm.getResults(namedQuery);
            assertQueryEventMetadata(listener, true);
        }
    }

    private void assertQueryEventMetadata(CapturingEventListener listener, boolean expectQueryName) throws Exception {
        assertEquals(1, listener.getCapturedEvents().size(), "Should capture exactly one event");
        Event event = listener.getCapturedEvents().getFirst();
        assertEquals(EVENT_NAME, event.getName(), "Event name should match");
        String actualJson = (String) event.getContext().getProperty(EVENT_DATA_PROPERTY);
        assertNotNull(actualJson, "Event data should not be null");
        String expectedJson;
        if (expectQueryName) {
            expectedJson = """
                {
                  "count": 10,
                  "queryName": "%s",
                  "elapsedTime": 123
                }
                """.formatted(QUERY_NAME);
        } else {
            expectedJson = """
                {
                  "count": 10,
                  "elapsedTime": 123
                }
                """;
        }
        JSONAssert.assertEquals(expectedJson, actualJson, EVENT_COMPARATOR);
    }
}
