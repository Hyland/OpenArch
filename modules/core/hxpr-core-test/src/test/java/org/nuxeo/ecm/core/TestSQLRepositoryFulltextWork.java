/*
 * (C) Copyright 2013-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Tests fulltext extractor work and updater work.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestSQLRepositoryFulltextWork {

    @Inject
    protected CoreSession session;

    protected void nextTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    protected void waitForAsyncCompletion() {
        nextTransaction();
    }

    private void createFolder() throws PropertyException {
        DocumentModel folder = session.createDocumentModel("/", "testfolder", SYS_FOLDER);
        folder.setPropertyValue(SYS_TITLE, "folder Title");
        folder.create();
    }

    @Test
    public void testFulltext() throws Exception { // NOSONAR (no need to assert anything)
        createFolder();
        createAndDeleteFile("testfile");
    }

    private void createAndDeleteFile(String name) throws PropertyException {
        DocumentModel file = session.createDocumentModel("/testfolder", name, SYS_FILE);
        file.setPropertyValue(SYS_TITLE, "testfile Title");
        file.create();
        session.save();
        nextTransaction();

        // at this point fulltext update is triggered async

        session.removeDocument(new IdRef(file.getId()));
        session.save();

        waitForAsyncCompletion();
    }

    @Test
    public void testFulltextWithConcurrentDelete() throws Exception { // NOSONAR (no need to assert anything)
        createFolder();
        for (int i = 0; i < 50; i++) {
            createAndDeleteFile("testfile" + i);
        }
    }

}
