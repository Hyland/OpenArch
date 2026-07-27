/*
 * (C) Copyright 2019-2026 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.ecm.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.BlobStore;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.ecm.core.blob.InMemoryBlobStore;
import org.nuxeo.ecm.core.blob.KeyStrategyDigest;
import org.nuxeo.ecm.core.blob.TestAbstractBlobStore;

/**
 * Base blob-store tests for a cloud blob provider (S3, Azure, ...), covering the copy/move code path optimized for two
 * stores of the same kind, and the fallback to a generic copy.
 * <p>
 * Concrete subclasses provide, through their {@code @Features}, a feature that sets up the backend and deploys two blob
 * providers named {@code test} and {@code other}.
 */
public abstract class TestCloudBlobStoreAbstract extends TestAbstractBlobStore {

    @AfterEach
    @Override
    public void tearDown() throws IOException {
        super.tearDown();

        BlobProvider otherbp = blobService.getBlobProvider("other");
        BlobStore otherbs = ((BlobStoreBlobProvider) otherbp).getStore();
        otherbs.clear();
    }

    // copy/move from another store of the same kind has a different, optimized code path

    @Test
    public void testCopyIsOptimized() {
        BlobProvider otherbp = blobService.getBlobProvider("other");
        BlobStore otherCloudStore = ((BlobStoreBlobProvider) otherbp).getStore(); // no need for unwrap
        assertTrue(bs.copyBlobIsOptimized(otherCloudStore));
        InMemoryBlobStore otherStore = new InMemoryBlobStore("mem", new KeyStrategyDigest("MD5"));
        assertFalse(bs.copyBlobIsOptimized(otherStore));
    }

    @Test
    public void testCopyFromCloudBlobStore() throws IOException {
        testCopyOrMoveFromCloudBlobStore(false);
    }

    @Test
    public void testMoveFromCloudBlobStore() throws IOException {
        testCopyOrMoveFromCloudBlobStore(true);
    }

    protected void testCopyOrMoveFromCloudBlobStore(boolean atomicMove) throws IOException {
        // we don't test the unimplemented copyBlob API, as it's only called from commit or during caching
        assumeFalse(bp.isTransactional(), "low-level copy/move not tested in transactional blob store");

        BlobProvider otherbp = blobService.getBlobProvider("other");
        BlobStore sourceStore = ((BlobStoreBlobProvider) otherbp).getStore();
        String key1 = useDeDuplication() ? FOO_SHA256 : ID1;
        String key2 = useDeDuplication() ? key1 : ID2;
        assertNull(bs.copyOrMoveBlob(key2, sourceStore, key1, atomicMove));
        assertEquals(key1, sourceStore.writeBlob(blobContext(ID1, FOO)));
        String key3 = bs.copyOrMoveBlob(key2, sourceStore, key1, atomicMove);
        assertEquals(key2, key3);
        assertBlob(bs, key2, FOO);
        if (atomicMove) {
            assertNoBlob(sourceStore, key1);
        } else {
            assertBlob(sourceStore, key1, FOO);
        }
    }

    // regression: a copy with a null key (async-digest case, where the digest is not yet known) must generate a
    // temporary key instead of throwing an NPE. A non-cloud source forces the generic (non-optimized) copy path.
    @Test
    public void testCopyWithNullKey() throws IOException {
        assumeFalse(bp.isTransactional(), "low-level copy/move not tested in transactional blob store");

        InMemoryBlobStore sourceStore = new InMemoryBlobStore("mem", new KeyStrategyDigest("MD5"));
        String sourceKey = sourceStore.writeBlob(blobContext(ID1, FOO));
        String key = bs.copyOrMoveBlob(null, sourceStore, sourceKey, false);
        assertNotNull(key);
        assertBlob(bs, key, FOO);
    }
}
