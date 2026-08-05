/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.opensearch;

import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static org.nuxeo.opensearch.OpenSearchConstants.REINDEX_ON_STARTUP_PROPERTY;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.client.OpenSearchRestClient;
import org.nuxeo.opensearch.listener.OpenSearchInlineListener;
import org.nuxeo.opensearch.test.RepositoryOpenSearchFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.opensearch.client.opensearch.generic.Request;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.generic.Response;

import tools.jackson.databind.JsonNode;
import com.hyland.hxpr.async.tasks.TaskService;

@ExtendWith(FeaturesExtension.class)
@Features({ RepositoryOpenSearchFeature.class, LogCaptureFeature.class })
public class OpenSearchComponentTest {

    private static final int TIMEOUT_DELETE_SECOND = 300;

    private static final int NB_CREATED_DOCUMENTS = 6;

    private static final int NB_REPOSITORIES = 3;

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService openSearchService;

    @Inject
    protected TaskService asyncTasksService;

    @Inject
    protected LogCaptureFeature.Result logCaptureResult;

    private final boolean syncMode = false;

    private int commandProcessed;

    @Test
    @LogCaptureFeature.FilterOn(logLevel = "WARN", loggerClass = OpenSearchServiceImpl.class)
    @WithFrameworkProperty(name = REINDEX_ON_STARTUP_PROPERTY, value = "true")
    public void shouldReindexRepositoriesOnStartUp() throws Exception {
        buildDocs();
        startTransaction();
        ((OpenSearchServiceImpl) openSearchService).reindexOnStartup();
        waitForCompletion();
        assertNumberOfCommandProcessed(NB_CREATED_DOCUMENTS); // A reindex for the section folder doc and the 5 children
                                                              // created at the beginning
        List<String> events = logCaptureResult.getCaughtEventMessages();
        assertThat(events).hasSize(NB_REPOSITORIES);
        events.forEach(event -> assertThat(event).startsWith("Indexing repository: "));
    }

    @Test
    @WithFrameworkProperty(name = REINDEX_ON_STARTUP_PROPERTY, value = "false")
    public void shouldNotReindexIfFlagIsFalse() throws Exception {
        buildDocs();
        startTransaction();
        ((OpenSearchServiceImpl) openSearchService).reindexOnStartup();
        waitForCompletion();
        assertNumberOfCommandProcessed(0);
    }

    @Test
    @WithFrameworkProperty(name = REINDEX_ON_STARTUP_PROPERTY, value = "true")
    public void shouldNotOverrideMappingWhenReindexingOnStartup() throws Exception {
        buildDocs();

        // Update the default mapping, it requires the index to be removed and recreated with the new mapping
        OpenSearchRestClient client = (OpenSearchRestClient) openSearchService.getClient();
        var indexRepo = openSearchService.getIndexNameForRepository(session.getRepositoryName());
        client.deleteIndex(indexRepo, TIMEOUT_DELETE_SECOND);
        File mappingJsonFile = org.nuxeo.common.utils.FileUtils.getResourceFileFromContext("test-mapping.json");
        var mappingJson = FileUtils.readFileToString(mappingJsonFile, StandardCharsets.UTF_8);
        File settingsJsonFile = org.nuxeo.common.utils.FileUtils.getResourceFileFromContext("test-settings.json");
        var settingsJson = FileUtils.readFileToString(settingsJsonFile, StandardCharsets.UTF_8);
        client.createIndex(indexRepo, settingsJson);
        client.createMapping(indexRepo, "doc", mappingJson);

        startTransaction();
        // When the reindex is triggered
        ((OpenSearchServiceImpl) openSearchService).reindexOnStartup();
        waitForCompletion();
        assertNumberOfCommandProcessed(NB_CREATED_DOCUMENTS);

        // Then, the mapping should not have been overridden
        Request request = Requests.builder() //
                                  .method("GET")
                                  .endpoint("/%s/_mapping".formatted(indexRepo))
                                  .build();
        try (Response response = client.performRequestWithTracing(request)) {
            // unfortunately JacksonJsonProvider.createReader is unimplemented, so use Jackson directly
            JsonNode jsonNode = client.getObjectMapper().readTree(response.getBody().get().body());
            String type = jsonNode.get(indexRepo)
                                  .get("mappings")
                                  .get("properties")
                                  .get(SYS_CREATED)
                                  .get("type")
                                  .asText();
            assertThat(type).isEqualTo("text");
        }
    }

    @Test
    @WithFrameworkProperty(name = REINDEX_ON_STARTUP_PROPERTY, value = "true")
    public void shouldUpdateIndexDynamicSettingWhenReindexingOnStartup() throws Exception {
        buildDocs();

        // create the index with default settings
        OpenSearchRestClient client = (OpenSearchRestClient) openSearchService.getClient();
        var indexRepo = openSearchService.getIndexNameForRepository(session.getRepositoryName());
        client.deleteIndex(indexRepo, TIMEOUT_DELETE_SECOND);
        File settingsJsonFile = org.nuxeo.common.utils.FileUtils.getResourceFileFromContext("test-settings.json");
        var settingsJson = FileUtils.readFileToString(settingsJsonFile, StandardCharsets.UTF_8);
        client.createIndex(indexRepo, settingsJson);
        JsonNode createIndexSettings = getApiResponse(client, indexRepo, "settings");
        String totalFieldsLimit;
        totalFieldsLimit = createIndexSettings.get(indexRepo)
                        .get("settings")
                        .get("index")
                        .get("mapping")
                        .get("total_fields")
                        .get("limit")
                        .asText();

        assertThat(totalFieldsLimit).isEqualTo("1000");

        // update the index with opensearch dynamic settings
        File dynamicSettingsJsonFile = org.nuxeo.common.utils.FileUtils.getResourceFileFromContext("test-dynamic-settings.json");
        var dynamicSettingsJson = FileUtils.readFileToString(dynamicSettingsJsonFile, StandardCharsets.UTF_8);
        client.updateIndexDynamicSettings(indexRepo, dynamicSettingsJson);
        JsonNode updateIndexSettings = getApiResponse(client, indexRepo, "settings");
        totalFieldsLimit = updateIndexSettings.get(indexRepo)
                .get("settings")
                .get("index")
                .get("mapping")
                .get("total_fields")
                .get("limit")
                .asText();

        assertThat(totalFieldsLimit).isEqualTo("1500");

        // re-applying same index dynamic settings should not cause exceptions
        client.updateIndexDynamicSettings(indexRepo, dynamicSettingsJson);
        JsonNode reapplyIndexSettings = getApiResponse(client, indexRepo, "settings");
        totalFieldsLimit = reapplyIndexSettings.get(indexRepo)
                .get("settings")
                .get("index")
                .get("mapping")
                .get("total_fields")
                .get("limit")
                .asText();

        assertThat(totalFieldsLimit).isEqualTo("1500");
    }

    @Test
    @WithFrameworkProperty(name = REINDEX_ON_STARTUP_PROPERTY, value = "true")
    public void shouldThrowExceptionWhenStaticSettingsAreReAppliedOnExistingIndexOnStartup() throws Exception {
        buildDocs();

        // create the index with default settings
        OpenSearchRestClient client = (OpenSearchRestClient) openSearchService.getClient();
        var indexRepo = openSearchService.getIndexNameForRepository(session.getRepositoryName());
        client.deleteIndex(indexRepo, TIMEOUT_DELETE_SECOND);
        File settingsJsonFile = org.nuxeo.common.utils.FileUtils.getResourceFileFromContext("test-settings.json");
        var settingsJson = FileUtils.readFileToString(settingsJsonFile, StandardCharsets.UTF_8);
        client.createIndex(indexRepo, settingsJson);
        JsonNode createIndexSettings = getApiResponse(client, indexRepo, "settings");
        String totalFieldsLimit;
        totalFieldsLimit = createIndexSettings.get(indexRepo)
                        .get("settings")
                        .get("index")
                        .get("number_of_shards")
                        .asText();

        assertThat(totalFieldsLimit).isEqualTo("1");

        // update the index with opensearch static settings
        String staticSettingsJson = "{\"settings\":{\"index\":{\"number_of_shards\":2}}}";
        assertThatExceptionOfType(NuxeoException.class)
                .isThrownBy(() -> {
                    client.updateIndexDynamicSettings(indexRepo, staticSettingsJson);
                }).withMessageContaining("Fail to update index settings");
    }

    private void buildDocs() throws Exception {
        startTransaction();

        DocumentModel folder = session.createDocumentModel("/", "section", SYS_FOLDER);
        folder.create();
        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.createDocumentModel("/", "testDoc" + i, SYS_FILE);
            doc.setPropertyValue(SYS_TITLE, "TestMe" + i);
            doc.setPropertyValue(SYSFILE_BLOB, new StringBlob("You know for search" + i));
            doc.create();
        }
        session.save();

        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
    }

    protected void startTransaction() {
        if (syncMode) {
            OpenSearchInlineListener.useSyncIndexing.set(TRUE);
        }
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
        commandProcessed = openSearchService.getTotalCommandProcessed();
    }

    private JsonNode getApiResponse(OpenSearchRestClient client, String index, String api) throws Exception {
        Request request = Requests.builder() //
                                  .method("GET")
                                  .endpoint("/%s/_%s".formatted(index, api))
                                  .build();
        try (Response response = client.performRequestWithTracing(request)) {
            return client.getObjectMapper().readTree(response.getBody().get().body());
        }
    }

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() throws Exception {
        asyncTasksService.awaitCompletion(Duration.ofSeconds(20L));
        openSearchService.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        openSearchService.refresh();
    }

    // Number of processed command since the startTransaction
    public void assertNumberOfCommandProcessed(int processed) throws Exception {
        assertEquals(processed, openSearchService.getTotalCommandProcessed() - commandProcessed);
    }
}
