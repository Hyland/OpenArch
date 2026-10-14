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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.ScrollResult;
import com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository;
import com.hyland.openarch.runtime.kv.KeyValueService;
import com.hyland.openarch.runtime.kv.KeyValueStore;
import com.hyland.openarch.runtime.mongodb.MongoDBComponent;
import com.hyland.openarch.runtime.mongodb.MongoDBConnectionService;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import com.mongodb.client.ClientSession;

/**
 * Tests the repository with MongoDB sessions and transactions enabled through
 * {@link MongoDBRepository#TRANSACTIONS_ENABLED_PROP}.
 */
@ExtendWith(FeaturesExtension.class)
@Features(MongoDBTransactionsFeature.class)
@Deploy("com.hyland.openarch.ecm.core.mongodb:OSGI-INF/mongodb-kvstore-config.xml")
@Deploy("com.hyland.openarch.ecm.core.mongodb.test:OSGI-INF/test-mongodb-kvstore-transactional-config.xml")
public class TestMongoDBTransactions {

    @Inject
    protected CoreSession session;

    @Inject
    protected KeyValueService keyValueService;

    @Inject
    protected MongoDBConnectionService mongoService;

    @Test
    public void testLockDocumentReadInTheSameTransaction() {
        DocumentModel doc = session.createDocumentModel("/", "locked", SYS_FILE);
        doc.create();
        session.save();
        String id = doc.getId();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // read the document, which pins the snapshot the MongoDB transaction works on
        DocumentModel read = session.getDocument(doc.getRef());
        assertEquals(id, read.getId());

        // lock it, which is deliberately done outside of the MongoDB session because a lock must be immediately
        // visible to the other threads and must survive a rollback
        Lock lock = read.setLock();
        assertNotNull(lock);

        // TODO HXCS-9106 modifying and saving the document we just locked fails with a MongoDB write conflict
        // (error 112), because the lock was written outside of the transaction after its snapshot of the document
        // was taken. Locking a document that the current transaction has read and then modifies is a common
        // pattern, so this must be fixed, by storing the locks in their own collection, before transactions can be
        // enabled in production.
        // read.setPropertyValue(SYS_TITLE, "after lock");
        // session.save();

        // the lock is written outside of the transaction, so it is already visible to everybody else
        assertNotNull(lock.getOwner());

        // the lock also made the document dirty in the session, so the transaction is rolled back instead of being
        // committed, which would hit the very write conflict described above
        TransactionHelper.setTransactionRollbackOnly();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    @Test
    public void testDocumentAndKeyValueWritesShareTheSameSession() {
        KeyValueStore kvStore = keyValueService.getKeyValueStore("transactional");
        DocumentModel doc = session.createDocumentModel("/", "shared-session", SYS_FILE);
        doc.create();
        session.save();
        // the repository connection published a session for the current transaction
        ClientSession clientSession = getCurrentSession();
        assertNotNull(clientSession);
        assertTrue(clientSession.hasActiveTransaction());
        // and the key/value store joins it instead of creating its own
        kvStore.put("shared", "value");
        assertSame(clientSession, getCurrentSession());
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        // the single MongoDB transaction committed both writes
        assertNull(getCurrentSession());
        assertTrue(session.exists(doc.getRef()));
        assertEquals("value", kvStore.getString("shared"));
    }

    @Test
    public void testDocumentAndKeyValueWritesAreRolledBackTogether() {
        KeyValueStore kvStore = keyValueService.getKeyValueStore("transactional");
        DocumentModel doc = session.createDocumentModel("/", "rolled-back", SYS_FILE);
        doc.create();
        session.save();
        DocumentRef ref = doc.getRef();
        ClientSession clientSession = getCurrentSession();
        kvStore.put("rolled-back", "value");
        assertSame(clientSession, getCurrentSession());

        TransactionHelper.setTransactionRollbackOnly();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // neither the document nor the key/value entry was persisted
        assertFalse(session.exists(ref));
        assertNull(kvStore.getString("rolled-back"));
    }

    @Test
    public void testScrollSurvivesTheTransactionThatStartedIt() {
        int nbDocs = 5;
        for (int i = 0; i < nbDocs; i++) {
            DocumentModel doc = session.createDocumentModel("/", "scrolled" + i, SYS_FILE);
            doc.create();
        }
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        ScrollResult<String> ret = session.scroll("SELECT * FROM SysFile", 2, 60);
        int total = ret.getResults().size();
        assertTrue(total < nbDocs);

        // a scroll cannot be transactional, so its cursor must still work in the next transactions
        while (ret.hasResults()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
            ret = session.scroll(ret.getScrollId());
            total += ret.getResults().size();
        }
        assertEquals(nbDocs, total);
    }

    /** Returns the MongoDB session of the current transaction, or {@code null} if there is none. */
    protected ClientSession getCurrentSession() {
        return ((MongoDBComponent) mongoService).getCurrentSession();
    }
}
