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
package com.hyland.openarch.opensearch.test;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.opensearch.test.DisableAutoIndexingTestListener.SKIP_INDEXING_TITLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A listener that runs ahead of the OpenSearch listener can disable automatic indexing by setting
 * {@code OpenSearchConstants.DISABLE_AUTO_INDEXING} on the event context: the property it sets is the one the
 * OpenSearch listener reads, on the same context, through the real event service and a real save.
 */
@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
@Deploy("com.hyland.openarch.opensearch.core:disable-auto-indexing-listener-contrib.xml")
public class TestDisableAutoIndexingEventPropagation {

    private static final String DOC_PATH = "/testDoc";

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService oss;

    @Inject
    protected TaskService asyncTasksService;

    private int commandsProcessedAtStart;

    @BeforeEach
    public void setupIndex() throws Exception {
        oss.initIndexes(true);
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "testDoc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "TestMe");
        doc.create();
        saveCommitAndAssertCommandsProcessed(1);
    }

    @Test
    public void indexingIsSkippedWhenAnEarlierListenerDisablesItOnTheEventContext() throws Exception {
        DocumentModel doc = session.getDocument(new PathRef(DOC_PATH));
        doc.setPropertyValue(SYS_TITLE, SKIP_INDEXING_TITLE);

        saveCommitAndAssertCommandsProcessed(0);
    }

    @Test
    public void indexingRunsWhenNoListenerDisablesIt() throws Exception {
        DocumentModel doc = session.getDocument(new PathRef(DOC_PATH));
        doc.setPropertyValue(SYS_TITLE, "indexed-title");

        saveCommitAndAssertCommandsProcessed(1);
    }

    private void saveCommitAndAssertCommandsProcessed(int expected) throws Exception {
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        asyncTasksService.awaitCompletion(Duration.ofSeconds(20));
        oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        oss.refresh();
        assertEquals(expected, oss.getTotalCommandProcessed() - commandsProcessedAtStart);
        startTransaction();
    }

    private void startTransaction() {
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
        commandsProcessedAtStart = oss.getTotalCommandProcessed();
    }
}
