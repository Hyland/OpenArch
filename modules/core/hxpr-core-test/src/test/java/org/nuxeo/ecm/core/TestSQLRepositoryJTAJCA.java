/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */

package org.nuxeo.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import jakarta.inject.Inject;
import jakarta.transaction.RollbackException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.runtime.transaction.TransactionRuntimeException;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/disable-schedulers.xml")
public class TestSQLRepositoryJTAJCA {

    private static final Logger log = LogManager.getLogger(TestSQLRepositoryJTAJCA.class);

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected RepositoryService repositoryService;

    @Inject
    protected CoreSession session;

    protected void waitForAsyncCompletion() {
        nextTransaction();
    }

    protected void nextTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    protected CoreSession getCoreSession() {
        return CoreInstance.getCoreSession(null, DummyLoginFeature.getTestPrincipal("toto", false));
    }

    /**
     * Test that connection sharing allows use of several sessions at the same time.
     */
    @Test
    public void testSessionSharing() {
        String repositoryName = session.getRepositoryName();
        session.getRootDocument(); // use the session at least once
        assertEquals(1, repositoryService.getActiveSessionsCount(repositoryName));

        CoreSession session2 = CoreInstance.getCoreSession(repositoryName);
        assertEquals(1, repositoryService.getActiveSessionsCount(repositoryName));
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();
        // check that this is immediately seen from other connection
        // (underlying ManagedConnection is the same)
        assertTrue(session2.exists(new PathRef("/doc")));

        assertEquals(1, repositoryService.getActiveSessionsCount(repositoryName));
    }

    /**
     * Test that a commit throws if a save should have been done.
     */
    @Test
    public void testNoSaveOnCommitAfterCreation() throws Exception {
        // create a doc
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        // no session.save()!
        // let commit do the checks
        try {
            TransactionHelper.commitOrRollbackTransaction();
            fail("commit should have been prevented");
        } catch (TransactionRuntimeException e) {
            if (!(e.getCause() instanceof RollbackException e2)) {
                throw e;
            }
            if (!(e2.getCause() instanceof NuxeoException e3)) {
                throw e;
            }
            String msg = e3.getMessage();
            if (!msg.startsWith("Cannot commit transaction with unsaved session data: created: ")) {
                throw e;
            }
            // ok we got the expected message
        }
    }

    /**
     * Test that a commit throws if a save should have been done.
     */
    @Test
    public void testNoSaveOnCommitAfterModification() throws Exception {
        // create a doc
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FOLDER);
        doc.create();
        session.save();
        nextTransaction();

        // modify the doc
        doc = session.getDocument(doc.getRef());
        doc.setPropertyValue(SYS_TITLE, "title1");
        // no session.save()!
        // let commit do the checks
        try {
            TransactionHelper.commitOrRollbackTransaction();
            fail("commit should have been prevented");
        } catch (TransactionRuntimeException e) {
            if (!(e.getCause() instanceof RollbackException e2)) {
                throw e;
            }
            if (!(e2.getCause() instanceof NuxeoException e3)) {
                throw e;
            }
            String msg = e3.getMessage();
            if (!msg.startsWith("Cannot commit transaction with unsaved session data: modified: id=" + doc.getId())) {
                throw e;
            }
            // ok we got the expected message
        }
    }

    /**
     * Cannot use session after close if no tx.
     */
    @Test
    public void testAccessWithoutTx() {
        TransactionHelper.commitOrRollbackTransaction();
        try {
            session.getRootDocument();
            fail("should throw");
        } catch (NuxeoException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Cannot use a session outside a transaction"), msg);
        }
        TransactionHelper.startTransaction();
    }

    /**
     * Testing that if 2 modifications are done at the same time on the same document on 2 separate transactions, one is
     * rejected (TransactionRuntimeException)
     */
    // not working as is
    @Disabled
    @Test
    public void testConcurrentModification() throws Exception {
        // first transaction
        DocumentModel doc = session.createDocumentModel("/", "doc", "Note");
        doc.setPropertyValue(SYS_TITLE, "initial");
        doc.create();
        // let commit do an implicit save
        nextTransaction();
        // release cx
        coreFeature.releaseCoreSession();

        final DocumentRef ref = new PathRef("/doc");
        TransactionHelper.startTransaction();
        // openSession();
        doc = session.getDocument(ref);
        doc.setPropertyValue(SYS_TITLE, "first");
        Thread t = new Thread(() -> {
            try {
                TransactionHelper.startTransaction();
                try {
                    CoreSession session2 = getCoreSession();
                    DocumentModel doc1 = session2.getDocument(ref);
                    doc1.setPropertyValue(SYS_TITLE, "second update");
                } catch (Exception e) {
                    log.error("Catched error while setting title", e);
                } finally {
                    TransactionHelper.commitOrRollbackTransaction();
                }
            } catch (Exception e) {
                fail(e.toString());
            }
        });
        t.start();
        t.join();
        try {
            TransactionHelper.commitOrRollbackTransaction(); // release cx
            fail("expected TransactionRuntimeException");
        } catch (TransactionRuntimeException e) {
            // expected
        }
    }

    @Test
    public void testAcquireThroughSessionId() {
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        session.save();

        assertNotNull(file.getCoreSession());
    }

    @Test
    public void testReconnectAfterClose() {
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        session.save();
        CoreSession closedSession = session;

        waitForAsyncCompletion();
        coreFeature.releaseCoreSession();

        // use a closed session. because of tx, we can reconnect it
        assertNotNull(closedSession.getRootDocument());

        // reopen session for rest of the code
        session = coreFeature.createCoreSession();
    }

    @Test
    public void testReconnectAfterCommit() {
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        session.save();

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        // keep existing CoreSession whose Session was implicitly closed
        // by commit
        // reconnect possible through tx
        assertNotNull(session.getRootDocument());
    }

    @Test
    public void testReconnectAfterCloseNoTx() {
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        session.save();
        CoreSession closedSession = session;

        waitForAsyncCompletion();
        coreFeature.releaseCoreSession();
        TransactionHelper.commitOrRollbackTransaction();

        // no startTransaction
        // use a closed session -> exception
        try {
            closedSession.getRootDocument();
            fail("should throw");
        } catch (NuxeoException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Cannot use a session outside a transaction"), msg);
        } finally {
            TransactionHelper.startTransaction();
        }
    }

    @Test
    public void testReconnectAfterCloseThroughSessionId() {
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        session.save();

        session = coreFeature.reopenCoreSession();

        assertNotNull(file.getCoreSession()); // document is still attached
    }

    @Test
    public void testMultiThreaded() throws Exception {
        assertNotNull(session.getRootDocument());

        final CoreSession finalSession = session;
        Thread t = new Thread(() -> {
            try {
                TransactionHelper.startTransaction();
                try {
                    assertNotNull(finalSession.getRootDocument());
                } finally {
                    TransactionHelper.commitOrRollbackTransaction();
                }
            } catch (Exception e) {
                fail(e.toString());
            }
        });
        t.start();
        t.join();

        assertNotNull(session.getRootDocument());
    }

    @Test
    public void testMultiThreadedNeedsTx() throws Exception {
        assertNotNull(session.getRootDocument());

        final CoreSession finalSession = session;
        final Exception[] threadException = new Exception[1];
        Thread t = new Thread(() -> {
            try {
                // no tx
                finalSession.getRootDocument();
            } catch (Exception e) {
                threadException[0] = e;
            }
        });
        t.start();
        t.join();
        Exception e = threadException[0];
        assertNotNull(e);
        assertTrue(e instanceof NuxeoException, e.getMessage());
    }

    @Test
    public void testCloseFromOtherTx() throws Exception {
        assertNotNull(session.getRootDocument());

        Thread t = new Thread(() -> {
            try {
                coreFeature.releaseCoreSession();
            } catch (Exception e) {
                fail(e.toString());
            }
        });
        t.start();
        t.join();
        session = coreFeature.createCoreSession();
    }

    @Test
    public void testPreemptiveTransactionTimeout() throws Exception {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction(1); // 1s transaction
        Thread.sleep(2000); // 2s sleep
        try {
            // any use of the session after the timeout is enough to trigger the exception
            session.getRootDocument();
            fail("should have preemptively timed out");
        } catch (TransactionRuntimeException e) {
            assertEquals("Transaction has timed out", e.getMessage());
        }
        try {
            TransactionHelper.commitOrRollbackTransaction();
            fail("commit after timeout should raise an exception");
        } catch (TransactionRuntimeException e) {
            assertEquals("Unable to commit: Transaction timeout", e.getMessage());
        }
        TransactionHelper.startTransaction();
    }

}
