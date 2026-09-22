/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.concurrent.CountDownLatch;

import jakarta.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

/**
 * Tests locking behavior under transaction. Subclass tests with no transaction.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestSQLRepositoryLocking {

    protected static final Logger log = LogManager.getLogger(TestSQLRepositoryJTAJCA.class);

    @Inject
    protected CoreSession session;

    protected void nextTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    @Test
    public void testLocking() throws Exception {
        DocumentModel root = session.getRootDocument();

        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        assertNull(doc.getLockInfo());
        assertFalse(doc.isLocked());
        session.save();

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");
        doc.setLock();

        assertEquals(TEST_ADMIN_USER, doc.getLockInfo().getOwner());
        assertNotNull(doc.getLockInfo().getCreated());
        assertTrue(doc.isLocked());

        session.save();
        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");

        assertEquals(TEST_ADMIN_USER, doc.getLockInfo().getOwner());
        assertNotNull(doc.getLockInfo().getCreated());
        assertTrue(doc.isLocked());

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");
        doc.removeLock();

        assertNull(doc.getLockInfo());
        assertFalse(doc.isLocked());

        session.save();
        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");

        assertFalse(doc.isLocked());
    }

    @Test
    public void testLockingBeforeSave() throws Exception {
        DocumentModel root = session.getRootDocument();
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        doc.setLock();
        session.save();

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");
        assertTrue(doc.isLocked());
    }

    // check we don't have a SQL-level locking error due to the lock manager
    // connection reading a row that was written but not yet committed by the
    // main connection
    @Test
    public void testGetLockAfterCreate() throws Exception {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();
        session.save();
        // read lock after save (SQL INSERT)
        assertNull(doc1.getLockInfo());

        DocumentModel doc2 = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc2.create();
        session.save();
        // set lock after save (SQL INSERT)
        doc2.setLock();
    }

    protected CountDownLatch threadStartLatch;

    protected CountDownLatch lockingLatch;

    protected volatile boolean locked;

    @Disabled
    @Test
    public void testLockingWithMultipleThreads() throws Exception {
        final String repositoryName = session.getRepositoryName();
        DocumentModel root = session.getRootDocument();
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");
        assertFalse(doc.isLocked());

        // start other thread
        threadStartLatch = new CountDownLatch(1);
        lockingLatch = new CountDownLatch(1);
        Thread t = new Thread() {
            @Override
            public void run() {
                TransactionHelper.startTransaction();
                try {
                    CoreSession session2 = CoreInstance.getCoreSession(repositoryName);
                    DocumentModel root2 = session2.getRootDocument();
                    DocumentModel doc2 = session2.getChild(root2.getRef(), "doc");
                    // let main thread continue
                    threadStartLatch.countDown();
                    // wait main thread trigger
                    lockingLatch.await();
                    locked = doc2.isLocked();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    threadStartLatch.countDown(); // error recovery
                    TransactionHelper.commitOrRollbackTransaction();
                }
            }
        };
        t.start();
        threadStartLatch.await();

        doc.setLock();
        assertTrue(doc.isLocked());

        // trigger other thread check
        lockingLatch.countDown();
        t.join();

        assertTrue(locked);
    }

}
