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
package com.hyland.openarch.ecm.core.mongodb.kv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.hyland.openarch.runtime.kv.AbstractKeyValueStoreTest;
import com.hyland.openarch.runtime.kv.KeyValueStore;
import com.hyland.openarch.runtime.kv.KeyValueStoreProvider;
import com.hyland.openarch.runtime.mongodb.MongoDBComponent;
import com.hyland.openarch.runtime.mongodb.MongoDBConnectionService;
import com.hyland.openarch.runtime.mongodb.MongoDBFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import com.hyland.openarch.runtime.transaction.TransactionRuntimeException;

import jakarta.transaction.Synchronization;

import com.mongodb.client.ClientSession;

@Features(MongoDBFeature.class)
@Deploy("com.hyland.openarch.ecm.core.mongodb:OSGI-INF/mongodb-kvstore-config.xml")
@Deploy("com.hyland.openarch.ecm.core.mongodb.test:OSGI-INF/test-mongodb-kvstore-transactional-config.xml")
public class TestMongoDBKeyValueStore extends AbstractKeyValueStoreTest {

    @Inject
    protected MongoDBConnectionService mongoService;

    @Test
    public void testClass() {
        assertTrue(store instanceof MongoDBKeyValueStore);
    }

    @Test
    public void testTransactionalStoreDeclaration() {
        KeyValueStore transactionalStore = keyValueService.getKeyValueStore("transactional");
        // a store declared transactional is a store of its own, not a view of the default one
        assertNotSame(store, transactionalStore);
        assertSame(transactionalStore, keyValueService.getKeyValueStore("transactional"));
        // outside any transaction the regular store still works, but the transactional one cannot
        TransactionHelper.runWithoutTransaction(() -> {
            assertNull(store.getString("key"));
            assertThrows(IllegalStateException.class, () -> transactionalStore.getString("key"));
        });
        assertThrows(UnsupportedOperationException.class,
                () -> transactionalStore.compareAndSet("key", (String) null, "value"));
        assertThrows(UnsupportedOperationException.class, () -> transactionalStore.addAndGet("key", 1));
    }

    @Test
    public void testTransactionalCommit() {
        KeyValueStore transactionalStore = keyValueService.getKeyValueStore("transactional");
        runInMongoTransaction(true, () -> transactionalStore.put("old", "old"));
        runInMongoTransaction(true, () -> {
            KeyValueStoreProvider transactionalProvider = (KeyValueStoreProvider) transactionalStore;
            transactionalProvider.clear();
            transactionalStore.put("foo", "bar");
            transactionalStore.put("remove", "value");
            assertTrue(transactionalStore.setTTL("foo", 60));
            assertFalse(transactionalStore.setTTL("missing", 60));
            assertTrue(transactionalStore.remove("remove"));
            assertFalse(transactionalStore.remove("missing"));
            assertEquals("bar", transactionalStore.getString("foo"));
            assertEquals(Set.of("foo"), transactionalProvider.keyStream().collect(Collectors.toSet()));
            assertEquals(Set.of(), transactionalProvider.keyStreamWithoutTTL().collect(Collectors.toSet()));
            assertEquals(Set.of("foo"), transactionalStore.getStrings(List.of("foo", "missing")).keySet());
        });
        // the committed state is visible to the next transaction
        runInMongoTransaction(true, () -> {
            assertNull(transactionalStore.getString("old"));
            assertEquals("bar", transactionalStore.getString("foo"));
            assertNull(transactionalStore.getString("remove"));
        });
    }

    @Test
    public void testTransactionalRollback() {
        KeyValueStore transactionalStore = keyValueService.getKeyValueStore("transactional");
        runInMongoTransaction(true, () -> transactionalStore.put("existing", "before"));
        runInMongoTransaction(false, () -> {
            transactionalStore.put("existing", "after");
            transactionalStore.put("created", "value");
            assertEquals("after", transactionalStore.getString("existing"));
            assertEquals("value", transactionalStore.getString("created"));
        });
        // nothing done in the rolled back transaction is visible to the next one
        runInMongoTransaction(true, () -> {
            assertEquals("before", transactionalStore.getString("existing"));
            assertNull(transactionalStore.getString("created"));
        });
    }

    @Test
    public void testTransactionalStoreLazilyCreatesSession() {
        // the key/value store is the first (and only) MongoDB participant in this transaction: it must be able to
        // create and publish its own session, instead of requiring one to already have been published by a
        // repository connection
        KeyValueStore transactionalStore = keyValueService.getKeyValueStore("transactional");
        assertNull(getCurrentSession());
        TransactionHelper.runInNewTransaction(() -> {
            transactionalStore.put("foo", "bar");
            assertEquals("bar", transactionalStore.getString("foo"));
            ClientSession session = getCurrentSession();
            assertNotNull(session);
            assertTrue(session.hasActiveTransaction());
        });
        // the session is committed and cleaned up when the enclosing transaction completes
        assertNull(getCurrentSession());
        runInMongoTransaction(true, () -> assertEquals("bar", transactionalStore.getString("foo")));
    }

    @Test
    public void testTransactionalStoresShareTheSameSession() {
        KeyValueStore firstStore = keyValueService.getKeyValueStore("transactional");
        KeyValueStore secondStore = keyValueService.getKeyValueStore("transactional-other");
        TransactionHelper.runInNewTransaction(() -> {
            firstStore.put("foo", "bar");
            ClientSession session = getCurrentSession();
            assertNotNull(session);
            // the second store joins the session created by the first one
            secondStore.put("foo", "bar");
            assertSame(session, getCurrentSession());
        });
        runInMongoTransaction(true, () -> {
            assertEquals("bar", firstStore.getString("foo"));
            assertEquals("bar", secondStore.getString("foo"));
        });
    }

    @Test
    public void testTransactionalWritesAreNotCommittedWhenALaterCallbackVetoesTheTransaction() {
        // the key/value store writes first, so its participation in the transaction is registered before the
        // synchronization that vetoes the commit; the writes must still not be persisted
        KeyValueStore transactionalStore = keyValueService.getKeyValueStore("transactional");
        assertThrows(TransactionRuntimeException.class, () -> TransactionHelper.runInNewTransaction(() -> {
            transactionalStore.put("vetoed", "value");
            TransactionHelper.registerSynchronization(new Synchronization() {

                @Override
                public void beforeCompletion() {
                    throw new IllegalStateException("veto");
                }

                @Override
                public void afterCompletion(int status) {
                    // nothing to do
                }
            });
        }));
        assertNull(getCurrentSession());
        runInMongoTransaction(true, () -> assertNull(transactionalStore.getString("vetoed")));
    }

    @Test
    public void testClose() {
        MongoDBKeyValueStore otherStore = (MongoDBKeyValueStore) keyValueService.getKeyValueStore("close-test");
        assertNotNull(otherStore.coll);

        otherStore.close();
        assertNull(otherStore.coll);
    }

    /** Returns the MongoDB session of the current transaction, or {@code null} if there is none. */
    protected ClientSession getCurrentSession() {
        return ((MongoDBComponent) mongoService).getCurrentSession();
    }

    /** Runs the given code in its own transaction, which is committed or rolled back as requested. */
    protected void runInMongoTransaction(boolean commit, Runnable runnable) {
        TransactionHelper.runInNewTransaction(() -> {
            runnable.run();
            if (!commit) {
                TransactionHelper.setTransactionRollbackOnly();
            }
        });
    }

    @Override
    protected boolean hasSlowTTLExpiration() {
        return true;
    }

    @Override
    protected void sleepForTTLExpiration(long sleepMS) {
        try {
            // the MongoDB TTLMonitor thread runs every 60 seconds
            Thread.sleep((60 + 10) * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
