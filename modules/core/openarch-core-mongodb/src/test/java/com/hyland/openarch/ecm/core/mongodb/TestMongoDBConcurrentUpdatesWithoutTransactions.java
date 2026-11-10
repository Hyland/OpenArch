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
package com.hyland.openarch.ecm.core.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.runtime.mongodb.MongoDBComponent;
import com.hyland.openarch.runtime.mongodb.MongoDBConnectionService;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

/**
 * Confirms that separate field updates can both succeed when MongoDB transactions are disabled.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
public class TestMongoDBConcurrentUpdatesWithoutTransactions {

    @Inject
    protected CoreSession session;

    @Inject
    protected MongoDBConnectionService mongoService;

    @Test
    public void testConcurrentUpdatesToDifferentFieldsDoNotConflict() throws Exception {
        assertNull(((MongoDBComponent) mongoService).getCurrentSession());

        DocumentModel doc = session.createDocumentModel("/", "concurrent-fields-no-tx", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "initial title");
        doc.setPropertyValue(SYS_DESCRIPTION, "initial description");
        doc.create();
        session.save();
        DocumentRef ref = doc.getRef();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        DocumentModel stale = session.getDocument(ref);
        String repositoryName = session.getRepositoryName();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                TransactionHelper.runInTransaction(() -> {
                    CoreSession otherSession = CoreInstance.getCoreSessionSystem(repositoryName);
                    DocumentModel fresh = otherSession.getDocument(ref);
                    fresh.setPropertyValue(SYS_DESCRIPTION, "updated description");
                    otherSession.save();
                });
            } catch (Throwable t) {
                workerFailure.set(t);
            }
        });
        writer.start();
        writer.join();

        assertNull(workerFailure.get());
        stale.setPropertyValue(SYS_TITLE, "updated title");
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        DocumentModel persisted = session.getDocument(ref);
        assertEquals("updated title", persisted.getPropertyValue(SYS_TITLE));
        assertEquals("updated description", persisted.getPropertyValue(SYS_DESCRIPTION));
    }
}
