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

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.hyland.openarch.runtime.test.runner.Deploy;

@Deploy("com.hyland.openarch.ecm.core.api.tests:OSGI-INF/test-blob-provider-inmemory-caching.xml")
public class TestCachingBlobStore extends TestAbstractBlobStore {

    @Test
    public void testDeduplicatedWriteRestoresMissingBackingBlob() throws IOException {
        CachingBlobStore cachingStore = (CachingBlobStore) bs;
        String key = bs.writeBlob(blobContext(ID1, FOO));
        assertBlob(cachingStore.store, key, FOO);
        assertBlob(cachingStore.cacheStore, key, FOO);

        // Simulate GC on another node: backing storage is cleared but this node's local cache is stale.
        cachingStore.store.deleteBlob(key);
        assertNoBlob(cachingStore.store, key);
        assertBlob(cachingStore.cacheStore, key, FOO);

        assertEquals(key, bs.writeBlob(blobContext(ID2, FOO)));
        assertBlob(cachingStore.store, key, FOO);
        assertBlob(cachingStore.cacheStore, key, FOO);
    }

}
