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
package com.hyland.openarch.ecm.core;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.PropertyException;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

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
