/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.transaction.Synchronization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import com.hyland.openarch.runtime.transaction.TransactionRuntimeException;

import java.time.Instant;

@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, TransactionalFeature.class })
public class TestTransactionHelper {

    @Test
    public void testTransactionTtl() throws InterruptedException {
        TransactionHelper.commitOrRollbackTransaction();
        int ttl = TransactionHelper.getTransactionTimeToLive();
        // outside of a transaction
        assertEquals(-1, ttl);
        int timeout = 2;
        TransactionHelper.startTransaction(timeout);
        ttl = TransactionHelper.getTransactionTimeToLive();
        // alive
        assertTrue(ttl > 0, "Got " + ttl);
        // timeout
        Thread.sleep((timeout + 2) * 1000);
        assertTrue(TransactionHelper.isTransactionTimedOut());
        ttl = TransactionHelper.getTransactionTimeToLive();
        assertEquals(0, ttl);
        try {
            TransactionHelper.commitOrRollbackTransaction();
        } catch (TransactionRuntimeException e) {
            // tx timeout is expected
            TransactionHelper.startTransaction();
        }
    }

    @Test
    public void testTransactionThreadNaming() {
        String threadName = TransactionHelper.runWithoutTransaction(() -> Thread.currentThread().getName());
        assertTrue(threadName.startsWith("Repository-"), threadName);
        assertTrue(threadName.endsWith("-runInTransaction"), threadName);
    }

    @Test
    public void testTransactionTimestamp() {
        // Check that the timestamp is defined when a transaction is started
        var timestamp = TransactionHelper.getTransactionStartTime();
        assertNotNull(timestamp);

        // Check that the timestamp is null when a transaction is committed
        TransactionHelper.commitOrRollbackTransaction();
        assertNull(TransactionHelper.getTransactionStartTime());
    }

    @Test
    public void testTransactionSynchronization() {
        var timestamp = TransactionHelper.getTransactionStartTime();
        assertNotNull(timestamp);
        TransactionHelper.registerSynchronization(new DummySynchronization(timestamp));
        TransactionHelper.commitOrRollbackTransaction();
    }

    class DummySynchronization implements Synchronization {

        Instant timestamp;

        public DummySynchronization(Instant timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public void beforeCompletion() {
            assertEquals(timestamp, TransactionHelper.getTransactionStartTime());
        }

        @Override
        public void afterCompletion(int status) {
            assertEquals(timestamp, TransactionHelper.getTransactionStartTime());
        }
    }
}
