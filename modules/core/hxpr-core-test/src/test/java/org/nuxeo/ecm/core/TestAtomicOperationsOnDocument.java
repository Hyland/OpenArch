/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.LockHelper;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

/**
 * @since 9.3
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.runtime.kv")
public class TestAtomicOperationsOnDocument {

    protected static final int NB_THREADS = 5;

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    protected class GetOrCreateDocumentThread implements Runnable {

        protected CountDownLatch latch;

        protected DocumentRef docRef; // ref for existence test

        protected String docName; // name to create

        protected String docId; // returned id

        public GetOrCreateDocumentThread(DocumentRef docRef, String docName) {
            this(docRef, docName, null);
        }

        public GetOrCreateDocumentThread(DocumentRef docRef, String docName, CountDownLatch latch) {
            this.latch = latch;
            this.docRef = docRef;
            this.docName = docName;
        }

        @Override
        public void run() {

            TransactionHelper.runInTransaction(() -> {
                try {
                    CoreSession s = CoreInstance.getCoreSession(coreFeature.getRepositoryName());
                    LockHelper.doAtomically(computeKey(docName), () -> {
                        DocumentModel doc;
                        if (s.exists(docRef)) {
                            doc = s.getDocument(docRef);
                        } else {
                            if (latch != null) {
                                // Simulate a thread that keeps a document locked
                                try {
                                    latch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt(); // restore interrupt
                                    throw new NuxeoException(e);
                                }
                            }
                            doc = s.createDocumentModel("/", docName, SYS_FILE);
                            doc.create();
                            s.save();
                        }
                        docId = doc.getId();
                    });
                } catch (ConcurrentUpdateException e) {
                    if (latch != null) {
                        latch.countDown();
                    }
                    throw e;
                }
            });
        }
    }

    // simple one for our use case, they have the same parent, just use the name
    protected String computeKey(String name) {
        return name;
    }

    @Test
    public void testCreateDocument() throws Exception {

        String docName = "file";
        DocumentRef docRef = new PathRef("/" + docName);

        GetOrCreateDocumentThread t = new GetOrCreateDocumentThread(docRef, docName);
        Thread thread = new Thread(t);
        thread.start();
        thread.join();

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        assertTrue(session.exists(docRef));

    }

    @Test
    public void testGetDocument() throws Exception {

        String docName = "file";
        DocumentModel doc = session.createDocumentModel("/", docName, SYS_FILE);
        doc.create();
        session.save();
        DocumentRef docRef = doc.getRef();

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        assertTrue(session.exists(doc.getRef()));

        GetOrCreateDocumentThread t = new GetOrCreateDocumentThread(docRef, docName);
        Thread thread = new Thread(t);
        thread.start();
        thread.join();

        assertEquals(doc.getId(), t.docId);
        assertTrue(session.exists(docRef));

    }

    @Test
    public void testConcurrentUpdateException() throws Exception {

        String docName = "file";
        DocumentRef docRef = new PathRef("/" + docName);

        CountDownLatch latch = new CountDownLatch(NB_THREADS - 1);
        MutableObject<String> me = new MutableObject<>();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < NB_THREADS; i++) {
            threads.add(new Thread(new GetOrCreateDocumentThread(docRef, docName, latch)));
        }

        // One of the threads will fail with a concurrent update exception thanks to the latch,
        // so check every one of them to keep error message if this exception occurs.
        threads.forEach(t -> {
            t.setUncaughtExceptionHandler((thread, throwable) -> me.setValue(throwable.getMessage()));
            t.start();
        });

        for (Thread t : threads) {
            t.join();
        }

        assertEquals("Failed to acquire the lock on key " + computeKey(docName), me.getValue());

    }

}
