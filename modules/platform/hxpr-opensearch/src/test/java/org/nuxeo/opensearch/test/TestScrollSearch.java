/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.opensearch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import com.hyland.hxpr.async.tasks.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.api.OpenSearchScrollResult;
import org.nuxeo.opensearch.query.NxQueryBuilder;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Tests the scroll search API exposed by {@link OpenSearchService}.
 *
 * @since 8.3
 */
@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
public class TestScrollSearch {

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService oss;

    @Inject
    protected TaskService asyncTasksService;

    @BeforeEach
    public void setupIndex() throws Exception {
        oss.initIndexes(true);
    }

    @Test
    public void testScroll() throws Exception {
        int nbDocs = 40;
        int pageSize = 20;

        buildAndIndexTree(nbDocs);

        // Initial search request, includes the first batch of results
        String query = "select * FROM SysContent order by sys_path";
        OpenSearchScrollResult res = oss.scroll(new NxQueryBuilder(session).nxql(query).limit(pageSize), 10000);
        assertNotNull(res);
        assertNotNull(res.getQueryBuilder());
        assertEquals(10000, res.getKeepAlive());
        assertNotNull(res.getScrollId());

        // Next result batches
        int totalDocCount = 0;
        List<String> docPaths = new ArrayList<>();
        DocumentModelList docs = res.getDocuments();
        while (!docs.isEmpty()) {
            int hitCount = docs.size();
            assertEquals(pageSize, hitCount);
            totalDocCount += hitCount;
            docPaths.addAll(docs.stream().map(DocumentModel::getPathAsString).toList());
            res = oss.scroll(res);
            docs = res.getDocuments();
        }
        assertEquals(nbDocs, totalDocCount);

        // Check order
        assertEquals(session.query(query).stream().map(DocumentModel::getPathAsString).toList(), docPaths);
    }

    protected void buildAndIndexTree(int docCount) throws Exception {
        startTransaction();
        buildTree(docCount);
        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
        startTransaction();
    }

    protected void buildTree(int docCount) {
        String root = "/";
        for (int i = 0; i < docCount; i++) {
            String name = "folder" + i;
            DocumentModel doc = session.createDocumentModel(root, name, SYS_FOLDER);
            doc.setPropertyValue(SYS_TITLE, "Folder" + i);
            doc.create();
        }
        session.save();
    }

    protected void startTransaction() {
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
    }

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() throws Exception {
        asyncTasksService.awaitCompletion(Duration.ofSeconds(20L));
        oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        oss.refresh();
    }

}
