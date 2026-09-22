/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.ecm.core.mongodb;

import static org.junit.jupiter.api.Assertions.fail;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

/**
 * Test that the MongoDB unique index on parent id + child name does its job.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
public class TestMongoDBIndices {

    @Inject
    protected CoreSession session;

    @Test
    public void testConcurrentCopyWithSameName() throws Exception {
        doTestConcurrent("copydoc", (s, src, dst) -> {
            s.copy(new PathRef("/" + src), new PathRef("/"), dst);
        });
    }

    @Test
    public void testConcurrentMoveWithSameName() throws Exception {
        doTestConcurrent("movedoc", (s, src, dst) -> {
            s.move(new PathRef("/" + src), new PathRef("/"), dst);
            // a simple rename move does no implicit save, so do it manually
            s.save();
        });
    }


    @FunctionalInterface
    private interface ParallelTask {
        void accept(CoreSession s, String src, String dst);
    }

    public void doTestConcurrent(String prefix, ParallelTask task) throws Exception {
        for (int i = 0; i < 10; i++) {
            if (doTestConcurrentOnce(prefix + i + "-", task)) {
                return; // ok
            }
            // retry if we had no ConcurrentUpdateException, this may happen
            // if one thread is fast enough to do its save before the other
            // explicitly checks for duplicates
        }
        fail("Expected ConcurrentUpdateException");
    }

    public boolean doTestConcurrentOnce(String prefix, ParallelTask task) throws Exception {
        String source1 = prefix + "A";
        String source2 = prefix + "B";
        String dest = prefix + "DEST";
        DocumentModel doc1 = session.createDocumentModel("/", source1, SYS_FILE);
        doc1.create();
        DocumentModel doc2 = session.createDocumentModel("/", source2, SYS_FILE);
        doc2.create();
        session.save();

        List<Exception> errors = new CopyOnWriteArrayList<>();
        CyclicBarrier barrier = new CyclicBarrier(2);
        BiConsumer<String, String> consumer = (src, dst) -> {
            TransactionHelper.runInTransaction(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    CoreSession s = CoreInstance.getCoreSession(session.getRepositoryName());
                    barrier.await(5, TimeUnit.SECONDS);
                    task.accept(s, src, dst);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException();
                } catch (RuntimeException | BrokenBarrierException | TimeoutException e) {
                    errors.add(e);
                    TransactionHelper.setTransactionRollbackOnly();
                }
            });
        };
        Thread thread1 = new Thread(() -> consumer.accept(source1, dest));
        Thread thread2 = new Thread(() -> consumer.accept(source2, dest));
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // we must have exactly one ConcurrentUpdateException
        if (errors.isEmpty()) {
            return false; // expected ConcurrentUpdateException
        }
        Exception e = errors.get(0);
        if (errors.size() == 2) {
            e.addSuppressed(errors.get(1));
            throw e;
        }
        if (!(e instanceof ConcurrentUpdateException)) {
            throw e;
        }
        return true; // ok
    }
}
