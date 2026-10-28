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
package com.hyland.openarch.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.hyland.openarch.ecm.core.blob.binary.BinaryGarbageCollector;
import com.hyland.openarch.ecm.core.blob.binary.BlobKeySlot;
import com.hyland.openarch.runtime.test.runner.Deploy;

@Deploy("com.hyland.openarch.ecm.core.api.tests:OSGI-INF/test-blob-provider-local-digest.xml")
public class TestLocalBlobStoreDigest extends TestAbstractBlobStore {

    @Test
    public void testFlags() {
        assertFalse(bp.isTransactional());
        assertFalse(bp.isRecordMode());
        assertTrue(bs.getKeyStrategy().useDeDuplication());
    }

    @ParameterizedTest
    @CsvSource({ "0,0", "0,1", "0,2", "1,0", "1,1", "1,2", "2,0", "2,1", "2,2" })
    public void testGCEscapedKeys(int depth, int prefixLength) throws IOException {
        Path dir = ((LocalBlobStore) bs).pathStrategy.dir;
        PathStrategy strategy = depth == 0 ? new PathStrategyFlat(dir) : new PathStrategySubDirs(dir, depth);
        bs = new LocalBlobStore("test", "test", KeyStrategyDocId.instance(), strategy);
        String keptKey = bs.writeBlob(blobContext("bad/key", FOO));
        String deletedKey = bs.writeBlob(blobContext("bad/unused", BAR));
        assertEquals("bad/key", keptKey);
        assertEquals("bad/unused", deletedKey);
        Path invalidFile = dir.resolve("%%zz");
        Files.writeString(invalidFile, "not a blob");
        BinaryGarbageCollector gc = bs.getBinaryGarbageCollector();

        if (prefixLength > 0) {
            gc.start(false, BlobKeySlot.other(prefixLength));
            gc.stop(false);
            assertEquals(0, gc.getStatus().getNumBinaries());
            assertEquals(0, gc.getStatus().getNumBinariesGC());
        }

        gc.start(true, new BlobKeySlot("ba".substring(0, prefixLength)));
        gc.mark(keptKey);
        gc.stop(true);

        assertEquals(1, gc.getStatus().getNumBinaries());
        assertEquals(1, gc.getStatus().getNumBinariesGC());
        assertBlob(keptKey, FOO);
        assertNoBlob(deletedKey);
        assertTrue(Files.exists(strategy.getPathForKey(keptKey)));
        assertFalse(Files.exists(strategy.getPathForKey(deletedKey)));
        assertEquals("not a blob", Files.readString(invalidFile));
    }

    @ParameterizedTest
    @CsvSource({ "0,0", "0,1", "0,2", "1,0", "1,1", "1,2", "2,0", "2,1", "2,2" })
    public void testGCIgnoresInvalidDigests(int depth, int prefixLength) throws IOException {
        Path dir = ((LocalBlobStore) bs).pathStrategy.dir;
        PathStrategy strategy = depth == 0 ? new PathStrategyFlat(dir) : new PathStrategySubDirs(dir, depth);
        bs = new LocalBlobStore("test", "test", bs.getKeyStrategy(), strategy);
        String key = bs.writeBlob(blobContext(ID1, FOO));
        List<Path> unrelatedFiles = new ArrayList<>();
        for (String invalidKey : List.of("notes.txt", key.substring(0, key.length() - 1) + "g", key + "00",
                key.substring(0, key.length() - 2), key.substring(0, 2) + "not-a-digest")) {
            Path file = strategy.getPathForKey(invalidKey);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "not a blob");
            unrelatedFiles.add(file);
        }
        BinaryGarbageCollector gc = bs.getBinaryGarbageCollector();
        if (prefixLength > 0) {
            gc.start(true, BlobKeySlot.other(prefixLength));
            gc.stop(true);
            assertEquals(0, gc.getStatus().getNumBinaries());
            assertEquals(0, gc.getStatus().getNumBinariesGC());
        }
        BlobKeySlot slot = new BlobKeySlot(key.substring(0, prefixLength));
        gc.start(true, slot);
        gc.mark(key);
        gc.stop(true);
        assertEquals(1, gc.getStatus().getNumBinaries());
        assertEquals(0, gc.getStatus().getNumBinariesGC());
        assertBlob(key, FOO);

        gc.start(true, slot);
        gc.stop(true);
        assertEquals(0, gc.getStatus().getNumBinaries());
        assertEquals(1, gc.getStatus().getNumBinariesGC());
        assertNoBlob(key);
        for (Path file : unrelatedFiles) {
            assertEquals("not a blob", Files.readString(file));
        }
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

    @Test
    public void testGCRecoveryCollision() throws IOException {
        LocalBlobStore localBlobStore = (LocalBlobStore) bs;
        String keptKey = bs.writeBlob(blobContext(ID1, FOO));
        String recoveredKey = bs.writeBlob(blobContext(ID2, BAR));
        Path blobPath = localBlobStore.pathStrategy.getPathForKey(recoveredKey);
        Path relativePath = localBlobStore.pathStrategy.dir.relativize(blobPath);
        Path recoveryPath = localBlobStore.pathStrategy.dir.resolve(AbstractBlobGarbageCollector.GC_RECOVERY_DIRECTORY)
                                                      .resolve(relativePath);
        assertFalse(Files.exists(recoveryPath));

        BinaryGarbageCollector gc = bs.getBinaryGarbageCollector();
        gc.start(true);
        gc.mark(keptKey);
        gc.stop(true);

        assertFalse(Files.exists(blobPath));
        assertTrue(Files.exists(recoveryPath));

        Files.createDirectories(blobPath.getParent());
        Files.writeString(blobPath, "new");

        ((LocalBlobStore.LocalBlobGarbageCollector) gc).recoverRecoveryDirectory();
        assertEquals("new", Files.readString(blobPath));
        assertEquals(BAR, Files.readString(recoveryPath));
    }

}
