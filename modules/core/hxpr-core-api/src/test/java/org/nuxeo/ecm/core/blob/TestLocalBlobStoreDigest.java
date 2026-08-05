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
package org.nuxeo.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.nuxeo.runtime.test.runner.Deploy;

@Deploy("org.nuxeo.ecm.core.api.tests:OSGI-INF/test-blob-provider-local-digest.xml")
public class TestLocalBlobStoreDigest extends TestAbstractBlobStore {

    @Test
    public void testFlags() {
        assertFalse(bp.isTransactional());
        assertFalse(bp.isRecordMode());
        assertTrue(bs.getKeyStrategy().useDeDuplication());
    }

    @Test
    public void testConcurrentCopy() throws IOException {
        BlobProvider bp2 = blobService.getBlobProvider("other");
        LocalBlobStore bs2 = (LocalBlobStore) ((BlobStoreBlobProvider) bp2).getStore();
        bs2.clear();

        String key1 = bs2.writeBlob(blobContext(ID1, FOO));
        assertEquals(FOO_SHA256, key1);

        List<Exception> exc = new CopyOnWriteArrayList<>();
        AtomicInteger done = new AtomicInteger();

        // this is the code that we want to be thread-safe
        Runnable runnable = () -> {
            try {
                String key2 = bs.copyOrMoveBlob(ID2, bs2, key1, false);
                assertEquals(ID2, key2);
                done.incrementAndGet();
            } catch (Exception e) {
                exc.add(e);
            }
        };

        int n = 10;

        // check without concurrency first

        for (int i = 0; i < n; i++) {
            runnable.run();
        }
        if (!exc.isEmpty()) {
            throw new RuntimeException(exc.get(0)); // NOSONAR
        }
        assertEquals(n, done.get());

        // now run concurrently

        done.set(0);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            threads.add(new Thread(runnable));
        }
        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e); // NOSONAR
            }
        });
        if (!exc.isEmpty()) {
            throw new RuntimeException(exc.get(0)); // NOSONAR
        }
        assertEquals(n, done.get());
    }



}
