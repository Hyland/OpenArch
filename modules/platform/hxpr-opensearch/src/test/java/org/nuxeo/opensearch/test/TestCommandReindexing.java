/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.opensearch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.blob.BlobProvider;
import com.hyland.openarch.ecm.core.blob.BlobService;
import com.hyland.openarch.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.opensearch.OpenSearchConstants;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.opensearch.commands.IndexingCommand.Type;
import org.nuxeo.opensearch.io.DocumentDTO;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SearchType;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

import com.hyland.openarch.async.tasks.TaskService;

import jakarta.inject.Inject;

/**
 * Test servcie declaration as well as basic indexing API
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */
@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
@Deploy("org.nuxeo.opensearch.core:disable-listener-contrib.xml")
public class TestCommandReindexing {

    private static final String IDX_NAME = "nxutest";

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService oss;

    @Inject
    protected TaskService asyncTasksService;

    @Inject
    protected BlobService manager;

    private int commandProcessed;

    // Number of processed command since the startTransaction
    public void assertNumberOfCommandProcessed(int processed) throws Exception {
        assertEquals(processed, oss.getTotalCommandProcessed() - commandProcessed);
    }

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() throws Exception {
        asyncTasksService.awaitCompletion(Duration.ofSeconds(20L));
        oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        oss.refresh();
    }

    protected void startTransaction() {
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
        commandProcessed = oss.getTotalCommandProcessed();
    }

    @BeforeEach
    public void setupIndex() throws Exception {
        oss.initIndexes(true);
    }

    @Test
    public void checkIndexing() throws Exception {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "testDoc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "Testme");
        // disable automatic indexing to control manually the indexing command
        doc.putContextData(OpenSearchConstants.DISABLE_AUTO_INDEXING, Boolean.TRUE);
        doc.create();
        session.save();

        // sync non recursive
        IndexingCommand cmd = new IndexingCommand(doc, Type.INSERT, true, false);
        oss.indexNonRecursive(cmd, false);
        assertNumberOfCommandProcessed(1);

        oss.refresh();
        SearchRequest request = new SearchRequest.Builder().index(IDX_NAME) //
                                                           .searchType(SearchType.DfsQueryThenFetch)
                                                           .from(0)
                                                           .size(60)
                                                           .build();
        SearchResponse<DocumentDTO> searchResponse = oss.getClient().search(request);
        // System.out.println(searchResponse.getHits().getAt(0).sourceAsString());
        assertEquals(1, searchResponse.hits().total().value());

        Query query = QueryBuilders.match().field(SYS_TITLE).query(FieldValue.of("Testme")).build().toQuery();
        request = request.toBuilder().query(query).build();
        searchResponse = oss.getClient().search(request);
        assertEquals(1, searchResponse.hits().total().value());
    }

    @Test
    public void shouldHandleMissingBlob() throws Exception {

        // Create the document with a string blob
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "myBlobFile", SYS_FILE);
        doc.putContextData(OpenSearchConstants.DISABLE_AUTO_INDEXING, Boolean.TRUE);
        doc.setPropertyValue(SYS_TITLE, "myBlob");
        Blob fb = Blobs.createBlob("Not worth it", "image/jpeg");
        doc.setPropertyValue(SYSFILE_BLOB, fb);
        doc.create();
        session.save();

        // Remove the binary
        Blob blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        String blobDigest = blob.getDigest();
        BlobProvider bp = manager.getBlobProvider(session.getRepositoryName());
        ((BlobStoreBlobProvider) bp).getStore().deleteBlob(blobDigest);

        IndexingCommand cmd = new IndexingCommand(doc, Type.INSERT, true, false);
        oss.indexNonRecursive(cmd, false);
        assertNumberOfCommandProcessed(1);

        oss.indexNonRecursive(Arrays.asList(cmd, cmd), false);
        assertNumberOfCommandProcessed(3);
    }

    @Test
    public void checkManualAsyncIndexing() throws Exception {
        DocumentModel doc0 = session.createDocumentModel("/", "testNote", "Note");
        doc0.setPropertyValue(SYS_TITLE, "TestNote");
        doc0.create();
        session.save();

        // init index
        IndexingCommand cmd0 = new IndexingCommand(doc0, Type.INSERT, true, false);
        oss.indexNonRecursive(cmd0, false);

        DocumentModel doc = session.createDocumentModel("/", "testDoc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "TestMe");
        doc.putContextData(OpenSearchConstants.DISABLE_AUTO_INDEXING, Boolean.TRUE);
        doc.create();
        session.save();

        // only one doc should be indexed for now
        SearchRequest request = new SearchRequest.Builder().index(IDX_NAME) //
                                                           .searchType(SearchType.DfsQueryThenFetch)
                                                           .from(0)
                                                           .size(60)
                                                           .build();
        SearchResponse<DocumentDTO> searchResponse = oss.getClient().search(request);
        assertEquals(1, searchResponse.hits().total().value());

        Query query = QueryBuilders.match().field(SYS_TITLE).query(FieldValue.of("TestMe")).build().toQuery();
        request = request.toBuilder().query(query).build();
        searchResponse = oss.getClient().search(request);
        assertEquals(0, searchResponse.hits().total().value());

        // now commit and wait for post commit indexing
        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
        startTransaction();
        doc.refresh();

        IndexingCommand cmd = new IndexingCommand(doc, Type.INSERT, false, false);
        oss.runIndexingWorker(Arrays.asList(cmd));
        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
        assertNumberOfCommandProcessed(1);

        // both docs are here
        startTransaction();
        query = QueryBuilders.matchAll().build().toQuery();
        request = request.toBuilder().query(query).build();
        searchResponse = oss.getClient().search(request);
        // System.out.println(searchResponse.getHits().getAt(0).sourceAsString());
        assertEquals(2, searchResponse.hits().total().value());
        query = QueryBuilders.match().field(SYS_TITLE).query(FieldValue.of("TestMe")).build().toQuery();
        request = request.toBuilder().query(query).build();
        searchResponse = oss.getClient().search(request);
        assertEquals(1, searchResponse.hits().total().value());
    }

}
