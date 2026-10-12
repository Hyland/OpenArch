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
package com.hyland.openarch.rest.query.test;

import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.api.RenditionsConstants.RENDITIONS_CONTAINER_DOCUMENT_TYPE;
import static com.hyland.openarch.ecm.core.api.RenditionsConstants.RENDITIONS_CONTAINER_NAME;
import static com.hyland.openarch.ecm.core.api.RenditionsConstants.RENDITION_DOCUMENT_TYPE;
import static com.hyland.openarch.ecm.core.api.RenditionsConstants.RENDITION_PROPERTY_ID;
import static com.hyland.openarch.ecm.core.api.RenditionsConstants.RENDITION_PROPERTY_SOURCE_DOC;
import static com.hyland.openarch.ecm.core.api.RenditionsConstants.RENDITION_PROPERTY_STATUS;
import static com.hyland.openarch.ecm.core.api.RenditionsConstants.RENDITIONS_STATUS.PENDING;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.rest.query.QueryAPI.PATH;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.rest.document.DocumentDTO;
import com.hyland.openarch.rest.query.OpenSearchQueryManager;
import com.hyland.openarch.rest.query.QueryAPI;
import com.hyland.openarch.rest.query.QueryManager;
import com.hyland.openarch.rest.query.RepositoryQueryManager;
import com.hyland.openarch.rest.query.io.DocumentNamedQueryDTO;
import com.hyland.openarch.rest.query.io.DocumentQueryDTO;
import com.hyland.openarch.rest.query.io.DocumentQueryResultDTO;
import com.hyland.openarch.rest.query.spec.NamedQueryRegistry;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import com.hyland.openarch.async.tasks.TaskService;

import tools.jackson.databind.JsonNode;

/**
 * HXCS-5382: the named queries (default searches) must not return the documents the rendition service creates under
 * a source document, while plain HXQL queries must still find them.
 */
@Features(RestQueryMultipleDocumentsFeature.class)
@Tag("useOpenSearch")
@Import({ QueryAPITestConfiguration.class, TestNamedQueryConfig.class })
@SpringBootTest(classes = QueryAPI.class)
class RenditionNamedQueryAPITest extends AbstractQueryAPITest {

    private static final String SOURCE_NAME = "HXCS-5382-NDA";

    private static final String RENDITION_NAME = SOURCE_NAME + "-pdfPreview";

    private static final String NAMED_PATH = PATH + "/named";

    private static final Duration WAIT = Duration.ofSeconds(20);

    /** A named query call and what it must return: the source, or nothing. */
    private record NamedCall(String queryName, Map<String, Object> parameters, List<String> quickFilters,
            boolean expectSource) {

        @Override
        public String toString() {
            return queryName + " " + parameters + (quickFilters.isEmpty() ? "" : " " + quickFilters);
        }
    }

    @Autowired
    OpenSearchQueryManager openSearchQueryManager;

    @Autowired
    RepositoryQueryManager repositoryQueryManager;

    @Autowired
    NamedQueryRegistry namedQueryRegistry;

    static String sourceId;

    static String containerId;

    static String renditionId;

    /** Created once for the class, as no test changes it. */
    @BeforeAll
    static void createRenditionHierarchy(ApplicationContext context) throws Exception {
        CoreSession session = coreSession(context);
        TransactionHelper.startTransaction();
        // same shape as the rendition service creates: a fixed container name, the rendition named after the source
        DocumentModel source = session.createDocumentModel("/", SOURCE_NAME, SYS_FILE);
        source.setPropertyValue(SYS_TITLE, SOURCE_NAME);
        source.create();
        sourceId = source.getId();

        DocumentModel container = session.createDocumentModel(source.getPathAsString(), RENDITIONS_CONTAINER_NAME,
                RENDITIONS_CONTAINER_DOCUMENT_TYPE);
        container.create();
        containerId = container.getId();

        DocumentModel rendition = session.createDocumentModel(container.getPathAsString(), RENDITION_NAME,
                RENDITION_DOCUMENT_TYPE);
        rendition.setPropertyValue(RENDITION_PROPERTY_ID, "pdfPreview");
        rendition.setPropertyValue(RENDITION_PROPERTY_STATUS, PENDING.toString());
        rendition.setPropertyValue(RENDITION_PROPERTY_SOURCE_DOC, sourceId);
        rendition.create();
        renditionId = rendition.getId();

        session.save();
        commitAndWaitForIndexing();
    }

    @AfterAll
    static void deleteRenditionHierarchy(ApplicationContext context) {
        CoreSession session = coreSession(context);
        TransactionHelper.startTransaction();
        if (sourceId != null && session.exists(new IdRef(sourceId))) {
            session.removeDocument(new IdRef(sourceId));
            session.save();
        }
        TransactionHelper.commitOrRollbackTransaction();
    }

    /**
     * The repository is set up for the whole class (Granularity.CLASS), but the {@code @RuntimeInject} fields are only
     * injected per test, so the class-level setup gets the session from the test state.
     */
    private static CoreSession coreSession(ApplicationContext context) {
        return getState(context).getInjector().getInstance(CoreSession.class);
    }

    /** Every named query and alias, with parameters that would reach the rendition documents without the filter. */
    private List<NamedCall> allNamedCalls() {
        String fulltext = SOURCE_NAME;
        return List.of( //
                new NamedCall("advanced_folder_listing", Map.of("title", fulltext), List.of(), true),
                new NamedCall("advanced_document_content", Map.of("title", fulltext), List.of(), true),
                new NamedCall("advanced_search", Map.of("fulltext", fulltext), List.of(), true),
                new NamedCall("advanced_search", Map.of("fulltext", fulltext), List.of("noFolder"), true),
                new NamedCall("advanced_search", Map.of("fulltext", fulltext), List.of("mostRecent"), true),
                new NamedCall("simple_search", Map.of("fulltext", fulltext), List.of(), true),
                new NamedCall("default_document_suggestion", Map.of("fulltext", fulltext + "*"), List.of(), true),
                // the container is the only child of the source, the rendition the only child of the container
                new NamedCall("folder_listing", Map.of("parentId", sourceId), List.of(), false),
                new NamedCall("folder_listing", Map.of("parentId", containerId), List.of(), false),
                new NamedCall("document_content", Map.of("parentId", sourceId), List.of(), false),
                new NamedCall("document_content", Map.of("parentId", containerId), List.of(), false),
                new NamedCall("advanced_folder_listing", Map.of("parentId", containerId), List.of(), false),
                new NamedCall("tree_children", Map.of("parentId", sourceId), List.of(), false));
    }

    /**
     * The named queries the repository (Mongo) backend can run: it answers full-text predicates, the title one
     * included, with "Fulltext search not available".
     */
    private List<NamedCall> repositoryNamedCalls() {
        return allNamedCalls().stream()
                              .filter(call -> !call.parameters().containsKey("fulltext")
                                      && !call.parameters().containsKey("title"))
                              .toList();
    }

    @Test
    void shouldCoverEveryRegisteredNamedQuery() {
        List<String> covered = allNamedCalls().stream().map(NamedCall::queryName).distinct().toList();
        // the test registry adds its own queries, which do not use the default filter
        List<String> defaults = namedQueryRegistry.getNamedQueries()
                                                  .keySet()
                                                  .stream()
                                                  .filter(name -> !name.startsWith("folder_listing_test"))
                                                  .toList();
        assertThat(covered).containsExactlyInAnyOrderElementsOf(defaults);
    }

    @Test
    void shouldExcludeRenditionTypesFromNamedQueriesOnOpenSearch() {
        assertNamedCalls(openSearchQueryManager, allNamedCalls());
    }

    @Test
    void shouldExcludeRenditionTypesFromNamedQueriesOnRepository() {
        assertNamedCalls(repositoryQueryManager, repositoryNamedCalls());
    }

    @Test
    void shouldExcludeRenditionTypesFromNamedQueriesThroughRest() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (String sync : List.of("false", "true")) {
            List<NamedCall> calls = "true".equals(sync) ? repositoryNamedCalls() : allNamedCalls();
            for (NamedCall call : calls) {
                JsonNode body = postNamed(call, sync);
                softly.assertThat(ids(body)).as("sync=%s %s", sync, call).containsExactlyElementsOf(expectedIds(call));
                softly.assertThat(body.get("totalCount").asLong())
                      .as("totalCount sync=%s %s", sync, call)
                      .isEqualTo(expectedIds(call).size());
            }
        }
        softly.assertAll();
    }

    @Test
    void shouldExposeTheFilterInEveryNamedQueryDefinition() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (String name : List.of("folder_listing", "advanced_folder_listing", "tree_children",
                "default_document_suggestion", "simple_search", "advanced_search")) {
            JsonNode definition = MAPPER.readTree(checkResponse(OK, NAMED_PATH + "/" + name, GET, null).andReturn()
                                                                                              .getResponse()
                                                                                              .getContentAsString());
            softly.assertThat(definition.at("/whereClauseDefinition/query").asString())
                  .as(name)
                  .contains("sys_primaryType NOT IN ('SysRendition', 'SysRenditionsContainer')");
        }
        softly.assertAll();
    }

    @Test
    void shouldKeepRenditionTypesQueryableWithPlainHxql() throws Exception {
        String query = "SELECT * FROM SysContent WHERE sys_ancestorIds = '%s'".formatted(sourceId);
        for (QueryManager manager : List.of(openSearchQueryManager, repositoryQueryManager)) {
            DocumentQueryResultDTO result = manager.getResults(new DocumentQueryDTO(REPO_ID, query, -1L, 0, List.of()));
            assertThat(result.documents()).as(manager.getClass().getSimpleName())
                                          .extracting(DocumentDTO::getId)
                                          .containsExactlyInAnyOrder(containerId, renditionId);
        }
        JsonNode body = MAPPER.readTree(checkResponse(OK, PATH, POST, Map.of("query", query)).andReturn()
                                                                                           .getResponse()
                                                                                           .getContentAsString());
        assertThat(body.findValuesAsString(SYS_PRIMARY_TYPE)).as("REST")
                                                           .containsExactlyInAnyOrder(RENDITION_DOCUMENT_TYPE,
                                                                   RENDITIONS_CONTAINER_DOCUMENT_TYPE);
    }

    private void assertNamedCalls(QueryManager manager, List<NamedCall> calls) {
        SoftAssertions softly = new SoftAssertions();
        for (NamedCall call : calls) {
            DocumentQueryResultDTO result = manager.getResults(new DocumentNamedQueryDTO(call.queryName(), REPO_ID,
                    -1L, 0, true, -1, call.parameters(), null, call.quickFilters()));
            softly.assertThat(result.documents()).as(call.toString())
                  .extracting(DocumentDTO::getId)
                  .containsExactlyElementsOf(expectedIds(call));
            softly.assertThat(result.totalCount()).as("totalCount " + call).isEqualTo(expectedIds(call).size());
        }
        softly.assertAll();
    }

    private List<String> expectedIds(NamedCall call) {
        return call.expectSource() ? List.of(sourceId) : List.of();
    }

    private JsonNode postNamed(NamedCall call, String sync) throws Exception {
        Map<String, Object> body = Map.of("queryName", call.queryName(), "parameters", call.parameters(),
                "selectedQuickFilters", call.quickFilters(), "trackTotalCount", true);
        return MAPPER.readTree(checkResponse(OK, NAMED_PATH + "?sync=" + sync, POST, body).andReturn()
                                                                                         .getResponse()
                                                                                         .getContentAsString());
    }

    private static List<String> ids(JsonNode body) {
        List<String> ids = new ArrayList<>();
        body.get("documents").forEach(doc -> ids.add(doc.get(SYS_ID).asString()));
        return ids;
    }

    private static void commitAndWaitForIndexing() throws Exception {
        TransactionHelper.commitOrRollbackTransaction();
        assertThat(Framework.getService(TaskService.class).awaitCompletion(WAIT)).as("async tasks completed").isTrue();
        OpenSearchService openSearchService = Framework.getService(OpenSearchService.class);
        openSearchService.prepareWaitForIndexing().get(WAIT.toSeconds(), TimeUnit.SECONDS);
        openSearchService.refresh();
    }
}
