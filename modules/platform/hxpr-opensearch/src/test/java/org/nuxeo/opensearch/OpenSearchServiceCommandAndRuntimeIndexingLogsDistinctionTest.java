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
package org.nuxeo.opensearch;

import jakarta.inject.Inject;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nuxeo.ecm.core.api.BulkIndexingException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.opensearch.api.OpenSearchClient;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.opensearch.test.RepositoryOpenSearchFeature;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;

import java.io.Writer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ExtendWith(FeaturesExtension.class)
@Features({ RepositoryOpenSearchFeature.class, LogCaptureFeature.class })
class OpenSearchServiceCommandAndRuntimeIndexingLogsDistinctionTest {

    @RuntimeInject
    protected OpenSearchService oss;

    @RuntimeInject
    CoreSession coreSession;

    @Inject
    protected LogCaptureFeature.Result logCaptureResult;

    @Mock
    protected OpenSearchClient client;

    @Mock
    protected BulkResponse bulkResponse;

    @Mock
    protected BulkResponseItem bulkResponseItem;

    @Mock
    protected ErrorCause errorCause;

    @Mock
    protected JsonProvider jsonProvider;

    @Mock
    protected JsonGenerator jsonGenerator;

    @Mock
    protected IndexingCommand badCommand;

    @Test
    @LogCaptureFeature.FilterOn(logLevel = "ERROR", loggerClass = OpenSearchServiceImpl.class)
    void shouldLogcommandReindexingFailed() {
        DocumentModel doc = coreSession.createDocumentModel("/", "File1", "SysFile");
        doc.create();
        coreSession.save();
        ((OpenSearchServiceImpl)oss).setOpenSearchClient(client);
        when(client.index(any())).thenThrow(new NuxeoException("test"));
        IndexingCommand indexingCommand = new IndexingCommand(doc, IndexingCommand.Type.INSERT, false, false);
        assertThrows(NuxeoException.class, () -> oss.indexNonRecursive(indexingCommand, true));
        List<String> events = logCaptureResult.getCaughtEventMessages();
        assertThat(events.stream().filter(s -> s.startsWith("OpenSearchService indexing task failed. Re-Indexing Command")).count()).isEqualTo(1);
    }

    @Test
    @LogCaptureFeature.FilterOn(logLevel = "ERROR", loggerClass = OpenSearchServiceImpl.class)
    void shouldLogRuntimeIndexingFailed() {
        DocumentModel doc = coreSession.createDocumentModel("/", "File1", "SysFile");
        doc.create();
        coreSession.save();
        ((OpenSearchServiceImpl)oss).setOpenSearchClient(client);
        when(client.index(any())).thenThrow(new NuxeoException("test"));
        IndexingCommand indexingCommand = new IndexingCommand(doc, IndexingCommand.Type.INSERT, false, false);
        assertThrows(NuxeoException.class, () -> oss.indexNonRecursive(indexingCommand, false));
        List<String> events = logCaptureResult.getCaughtEventMessages();
        assertThat(events.stream().filter(s -> s.startsWith("OpenSearchService indexing task failed. Runtime Indexing")).count()).isEqualTo(1);
    }

    @Test
    @LogCaptureFeature.FilterOn(logLevel = "ERROR", loggerClass = OpenSearchServiceImpl.class)
    void shouldLogcommandReindexingFailedOnSendBulkCommands() {
        DocumentModel doc = coreSession.createDocumentModel("/", "File1", "SysFile");
        doc.create();
        coreSession.save();
        ((OpenSearchServiceImpl)oss).setOpenSearchClient(client);

        when(badCommand.getRepositoryName()).thenReturn(doc.getRepositoryName());
        when(badCommand.getTargetDocumentId()).thenReturn(doc.getId());
        when(client.getJsonProvider()).thenReturn(jsonProvider);
        when(jsonProvider.createGenerator(any(Writer.class))).thenReturn(jsonGenerator);
        when(client.bulk(any())).thenReturn(bulkResponse);
        when(bulkResponse.errors()).thenReturn(true);
        when(bulkResponse.items()).thenReturn(List.of(bulkResponseItem));
        when(bulkResponseItem.error()).thenReturn(errorCause);
        when(bulkResponseItem.status()).thenReturn(HttpStatus.SC_BAD_REQUEST);

        IndexingCommand indexingCommand = new IndexingCommand(doc, IndexingCommand.Type.INSERT, false, false);
        List<IndexingCommand> commandsList = List.of(badCommand, indexingCommand);
        assertThrows(BulkIndexingException.class, () -> oss.indexNonRecursive(commandsList, true));
        List<String> events = logCaptureResult.getCaughtEventMessages();
        assertThat(events.stream().filter(s -> s.contains("OpenSearchService indexing task failed. Re-Indexing Command")).count()).isEqualTo(1);
    }

    @Test
    @LogCaptureFeature.FilterOn(logLevel = "ERROR", loggerClass = OpenSearchServiceImpl.class)
    void shouldLogRuntimeIndexingFailedOnSendBulkCommands() {
        DocumentModel doc = coreSession.createDocumentModel("/", "File1", "SysFile");
        doc.create();
        coreSession.save();
        ((OpenSearchServiceImpl)oss).setOpenSearchClient(client);

        when(badCommand.getRepositoryName()).thenReturn(doc.getRepositoryName());
        when(badCommand.getTargetDocumentId()).thenReturn(doc.getId());
        when(client.getJsonProvider()).thenReturn(jsonProvider);
        when(jsonProvider.createGenerator(any(Writer.class))).thenReturn(jsonGenerator);
        when(client.bulk(any())).thenReturn(bulkResponse);
        when(bulkResponse.errors()).thenReturn(true);
        when(bulkResponse.items()).thenReturn(List.of(bulkResponseItem));
        when(bulkResponseItem.error()).thenReturn(errorCause);
        when(bulkResponseItem.status()).thenReturn(HttpStatus.SC_BAD_REQUEST);

        IndexingCommand indexingCommand = new IndexingCommand(doc, IndexingCommand.Type.INSERT, false, false);
        List<IndexingCommand> commandsList = List.of(badCommand, indexingCommand);
        assertThrows(BulkIndexingException.class, () -> oss.indexNonRecursive(commandsList, false));
        List<String> events = logCaptureResult.getCaughtEventMessages();
        assertThat(events.stream().filter(s -> s.contains("OpenSearchService indexing task failed. Runtime Indexing")).count()).isEqualTo(1);
    }
}
