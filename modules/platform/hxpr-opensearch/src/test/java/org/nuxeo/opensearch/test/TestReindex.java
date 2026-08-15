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

import static java.lang.Boolean.TRUE;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static org.nuxeo.opensearch.OpenSearchConstants.INDEX_BULK_MAX_SIZE_PROPERTY;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import com.hyland.hxpr.async.tasks.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.api.trash.TrashService;
import org.nuxeo.opensearch.OpenSearchServiceImpl;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.listener.OpenSearchInlineListener;
import org.nuxeo.opensearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.ConsoleLogLevelThreshold;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Test "on the fly" indexing via the listener system
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */

@ExtendWith(FeaturesExtension.class)
@Features({ RepositoryOpenSearchFeature.class, LogCaptureFeature.class })
public class TestReindex {

    private static final String MY_FILE = "MyFile";

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService oss;

    @Inject
    protected TrashService trashService;

    @Inject
    protected TaskService asyncTasksService;

    @Inject
    LogCaptureFeature.Result logCaptureResult;

    private boolean syncMode = false;

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() throws Exception {
        asyncTasksService.awaitCompletion(Duration.ofSeconds(20L));
        oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        oss.refresh();
    }

    protected void startTransaction() {
        if (syncMode) {
            OpenSearchInlineListener.useSyncIndexing.set(TRUE);
        }
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
    }

    @BeforeEach
    public void setupIndex() {
        oss.initIndexes(true);
    }

    @AfterEach
    public void disableSynchronousMode() {
        OpenSearchInlineListener.useSyncIndexing.remove();
        syncMode = false;
    }

    @Test
    public void shouldReindexDocument() throws Exception {
        buildDocs();
        startTransaction();

        String nxql = "SELECT * FROM SysContent, SysRelation order by sys_id";
        OpenSearchService ess = Framework.getService(OpenSearchService.class);
        DocumentModelList coreDocs = session.query(nxql);
        DocumentModelList docs = ess.query(new NxQueryBuilder(session).nxql(nxql).limit(100));

        assertEquals(coreDocs.totalSize(), docs.totalSize());
        assertEquals(getDigest(coreDocs), getDigest(docs));
        // cannot do that because of NXP-16154
        // Assert.assertEquals(getDigest(coreDocs), 42, docs.totalSize());
        oss.initIndexes(true);
        oss.refresh();
        DocumentModelList docs2 = ess.query(new NxQueryBuilder(session).nxql("SELECT * FROM SysContent"));
        assertEquals(0, docs2.totalSize());
        oss.runReindexingWorker(session.getRepositoryName(), "SELECT * FROM SysContent");
        oss.runReindexingWorker(session.getRepositoryName(), "SELECT * FROM SysRelation");
        waitForCompletion();
        docs2 = ess.query(new NxQueryBuilder(session).nxql(nxql).limit(100));

        assertEquals(getDigest(coreDocs), getDigest(docs2));
    }

    private void buildDocs() throws Exception {
        startTransaction();

        DocumentModel folder = session.createDocumentModel("/", "section", SYS_FOLDER);
        folder.create();
        for (int i = 0; i < 10; i++) {
            DocumentModel doc = session.createDocumentModel("/", "testDoc" + i, MY_FILE);
            doc.setPropertyValue(SYS_TITLE, "TestMe" + i);
            doc.setPropertyValue(SYSFILE_BLOB, new StringBlob("You know for search" + i));
            doc.create();
        }
        session.save();

        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
        startTransaction();

        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.getDocument(new PathRef("/testDoc" + i));
            doc.setPropertyValue(SYS_DESCRIPTION, "Description TestMe" + i);
            session.checkIn(doc.getRef());
            if (i % 2 == 0) {
                trashService.trashDocuments(singletonList(doc));
            }
        }
        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
    }

    protected String getDigest(DocumentModelList docs) {
        StringBuilder sb = new StringBuilder();
        for (DocumentModel doc : docs) {
            String nameOrTitle = doc.getName();
            if (nameOrTitle == null || nameOrTitle.isEmpty()) {
                nameOrTitle = doc.getTitle();
            }
            sb.append(doc.getType()).append(" ").append(" ").append(doc.getId()).append(" ");
            sb.append(nameOrTitle);
            sb.append("\n");
        }
        return sb.toString();
    }

    @Test
    @LogCaptureFeature.FilterOn(logLevel = "WARN", loggerClass = OpenSearchServiceImpl.class)
    @ConsoleLogLevelThreshold("ERROR")
    public void shouldReindexDocumentWithSmallBulkSize() throws Exception {
        try {
            System.setProperty(INDEX_BULK_MAX_SIZE_PROPERTY, "4096");
            shouldReindexDocument();
            List<String> events = logCaptureResult.getCaughtEventMessages();
            assertFalse(events.isEmpty(), "Expecting warn message");
            assertTrue(events.get(events.size() - 1).contains("Max bulk size reached"));
        } finally {
            System.clearProperty(INDEX_BULK_MAX_SIZE_PROPERTY);
        }
    }

}
