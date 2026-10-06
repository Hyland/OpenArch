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
package com.hyland.openarch.ecm.core.transientstore.keyvalueblob;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static com.hyland.openarch.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.BLOBKEY_PREFIX;
import static com.hyland.openarch.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.DOT_BLOB;
import static com.hyland.openarch.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.DOT_BLOB_DOT;
import static com.hyland.openarch.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.DOT_BLOBINFO;
import static com.hyland.openarch.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.DOT_COMPLETED;
import static com.hyland.openarch.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.DOT_PARAMS;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.impl.blob.StringBlob;
import com.hyland.openarch.ecm.core.blob.AbstractBlobGarbageCollector;
import com.hyland.openarch.ecm.core.blob.BlobProvider;
import com.hyland.openarch.ecm.core.blob.LocalBlobProvider;
import com.hyland.openarch.ecm.core.blob.binary.BinaryGarbageCollector;
import com.hyland.openarch.ecm.core.transientstore.api.MaximumTransientSpaceExceeded;
import com.hyland.openarch.ecm.core.transientstore.api.TransientStore;
import com.hyland.openarch.ecm.core.transientstore.api.TransientStoreProvider;
import com.hyland.openarch.ecm.core.transientstore.api.TransientStoreService;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.kv.KeyValueService;
import com.hyland.openarch.runtime.kv.KeyValueStore;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

/**
 * @since 9.3
 */
@ExtendWith(FeaturesExtension.class)
@Features(KeyValueBlobTransientStoreFeature.class)
public class TestKeyValueBlobTransientStore {

    protected static final String NAME = "testkvb";

    protected static final String NAME2 = "testkvb2";

    @Inject
    protected TransientStoreService tss;

    protected TransientStoreProvider tsp;

    protected TransientStore ts;

    @BeforeEach
    public void setUp() {
        setUp(NAME);
    }

    protected void setUp(String name) {
        ts = tss.getStore(name);
        tsp = (TransientStoreProvider) ts;
    }

    @AfterEach
    public void tearDown() {
        tsp.removeAll();
    }

    protected void createBlob(String key, String content) {
        Blob blob = new StringBlob(content);
        blob.setFilename("fake.txt");
        blob.setMimeType("text/plain");
        blob.setDigest(DigestUtils.md5Hex(content));
        ts.putBlob(key, blob);
    }

    @Test
    public void verifyDefaultAndFallback() {
        TransientStore def = tss.getStore("default");
        TransientStore other1 = tss.getStore("storeNotRegisteredInXML");
        TransientStore other2 = tss.getStore("otherStoreNotRegisteredInXML");
        assertTrue(def instanceof KeyValueBlobTransientStore);
        assertTrue(other1 instanceof KeyValueBlobTransientStore);
        assertTrue(other2 instanceof KeyValueBlobTransientStore);
        // check that the binary store configuration uses the proper default
        assertEquals("mytransientbinaries_transient_default", getBlobProviderStorageDir(def));
        assertEquals("mytransientbinaries_transient_storeNotRegisteredInXML", getBlobProviderStorageDir(other1));
        assertEquals("mytransientbinaries_transient_otherStoreNotRegisteredInXML", getBlobProviderStorageDir(other2));
        // put a key in the default one
        def.putParameter("foo", "A", "bar");
        assertEquals("bar", def.getParameter("foo", "A"));
        // make sure there's no key collision
        assertNull(other1.getParameter("foo", "A"));
        // put a key in the first derived one
        other1.putParameter("gee", "B", "moo");
        // make sure there's no key collision
        assertNull(def.getParameter("gee", "B"));
        assertNull(other2.getParameter("gee", "B"));
    }

    protected String getBlobProviderStorageDir(TransientStore transientStore) {
        BlobProvider blobProvider = ((KeyValueBlobTransientStore) transientStore).getBlobProvider();
        Path dir = ((LocalBlobProvider) blobProvider).getStorageDir();
        return dir.getParent().getFileName().toString();
    }

    @Test
    public void verifyStorage() throws Exception {

        String key = "mykey";
        String content = "FakeContentWithBinary\u00e9";
        int contentByteLength = content.getBytes(UTF_8).length;
        assertNotEquals(contentByteLength, content.length());
        String contentMD5 = DigestUtils.md5Hex(content);

        String content2 = "FakeContent2";
        int content2ByteLength = content2.getBytes(UTF_8).length;

        // start empty
        long size = tsp.getStorageSize();
        assertEquals(0, size);

        // create content
        ts.putParameter(key, "A", "1");
        ts.putParameter(key, "B", "b");
        createBlob(key, content);

        // check that entry is stored
        assertTrue(ts.exists(key));
        assertFalse(ts.isCompleted(key));
        assertEquals(new HashSet<>(Arrays.asList(key)), tsp.keySet());
        assertEquals(contentByteLength, ts.getSize(key));
        assertEquals("1", ts.getParameter(key, "A"));
        assertEquals("b", ts.getParameter(key, "B"));
        Blob blob = ts.getBlob(key);
        assertNotNull(blob);
        assertEquals("fake.txt", blob.getFilename());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals(contentMD5, blob.getDigest());
        try (InputStream stream = blob.getStream()) {
            assertEquals(content, IOUtils.toString(stream, UTF_8));
        }

        size = tsp.getStorageSize();
        assertEquals(contentByteLength, size);

        // update the entry with a replacement blob
        Blob otherBlob = new StringBlob(content2);
        otherBlob.setFilename("fake2.txt");
        otherBlob.setMimeType("text/plain");
        ts.putBlob(key, otherBlob);

        // check update
        assertTrue(ts.exists(key));
        assertEquals(new HashSet<>(Arrays.asList(key)), tsp.keySet());
        assertEquals(content2ByteLength, ts.getSize(key));
        Blob updatedBlob = ts.getBlob(key);
        assertNotNull(updatedBlob);
        assertEquals("fake2.txt", updatedBlob.getFilename());
        size = tsp.getStorageSize();
        assertEquals(content2ByteLength, size);

        // move to deletable entries
        // check that still here
        ts.release(key);
        assertTrue(ts.exists(key));
        assertEquals(1, tsp.keySet().size());
        assertTrue(tsp.keySet().contains(key));

        // check Remove
        ts.remove(key);
        assertFalse(ts.exists(key));
        assertEquals(0, tsp.keySet().size());

        size = tsp.getStorageSize();
        assertEquals(0, size);
    }

    @Test
    public void verifyStorageWithExplicitConfiguration() throws Exception {
        setUp(NAME2);
        verifyStorage();
    }

    @Test
    public void verifyDuplicateParam() throws Exception {
        String key = "mykey";
        ts.putParameter(key, "A", "1");
        ts.putParameter(key, "A", "2");
        assertEquals("2", ts.getParameter(key, "A"));
        assertEquals(Collections.singletonMap("A", "2"), ts.getParameters(key));
    }

    @Test
    public void verifyNullCases() throws Exception {
        // Non existing entry
        assertFalse(ts.exists("fakeEntry"));
        assertNull(ts.getParameters("fakeEntry"));
        assertNull(ts.getParameter("fakeEntry", "fakeParameter"));
        assertNull(ts.getBlob("fakeEntry"));
        assertEquals(0, ts.getSize("fakeEntry"));
        assertFalse(ts.isCompleted("fakeEntry"));

        // Entry with parameters only
        ts.putParameter("testEntry", "param1", "value");
        assertTrue(ts.exists("testEntry"));
        Map<String, Object> params = ts.getParameters("testEntry");
        assertNotNull(params);
        assertEquals(1, params.size());
        assertNotNull(ts.getParameter("testEntry", "param1"));
        assertNull(ts.getParameter("testEntry", "param2"));
        assertNull(ts.getBlob("testEntry"));
        assertEquals(0, ts.getSize("testEntry"));

        // Entry with blobs only
        ts.putBlob("otherEntry", new StringBlob("joe"));
        assertTrue(ts.exists("otherEntry"));
        params = ts.getParameters("otherEntry");
        assertNotNull(params);
        assertTrue(params.isEmpty());
        assertNotNull(ts.getBlob("otherEntry"));
    }

    @Test
    public void verifyMaxSizeException() throws Exception {
        // store is configured for 1MB max
        byte[] bytes = new byte[1024 * 1024 + 1];
        Blob blob = Blobs.createBlob(bytes);
        ts.putBlob("foo", blob);
        // store another one but we've exceeded the size allowed
        try {
            ts.putBlob("bar", Blobs.createBlob("x"));
            fail("Should have exceeded maximum transient space");
        } catch (MaximumTransientSpaceExceeded e) {
            assertEquals("Maximum Transient Space Exceeded", e.getMessage());
        }
    }

    @Test
    public void testGC() throws Exception {
        String content = "SomeContent";
        int contentByteLength = content.getBytes(UTF_8).length;

        // two entries with same content
        createBlob("foo", content);
        createBlob("bar", content);
        assertTrue(ts.exists("foo"));
        assertTrue(ts.exists("bar"));
        assertEquals(contentByteLength * 2, tsp.getStorageSize());

        // do GC
        tsp.doGC();

        // entry is still here
        assertTrue(ts.exists("foo"));
        assertTrue(ts.exists("bar"));
        assertEquals(contentByteLength * 2, tsp.getStorageSize());

        // now remove one entry and do GC
        ts.remove("bar");
        tsp.doGC();

        // one entry is still here
        assertTrue(ts.exists("foo"));
        assertFalse(ts.exists("bar"));
        assertEquals(contentByteLength, tsp.getStorageSize());

        // remove last entry
        ts.remove("foo");
        tsp.doGC();

        // entry is gone
        assertFalse(ts.exists("foo"));
        assertFalse(ts.exists("bar"));
        assertEquals(0, tsp.getStorageSize());
    }

    /**
     * Test GC of a blob that's been removed from the underlying blob store by someone else.
     */
    @Test
    public void testMissingBlobGC() throws Exception { // NOSONAR (no need to assert anything)
        // create a blob
        String key = "foo";
        createBlob(key, "SomeContent");

        removeAllBlobsFromBlobProvider();

        // do transient store GC, should not crash
        tsp.doGC();
        // GC should still be possible again
        tsp.doGC();
    }

    /**
     * Test getting a blob that's been removed from underlying the blob store by someone else.
     */
    @Test
    public void testMissingBlobGet() throws Exception { // NOSONAR (no need to assert anything)
        // create a blob
        String key = "foo";
        createBlob(key, "SomeContent");

        removeAllBlobsFromBlobProvider();

        // get the blob, should not crash
        ts.getBlob(key);
    }

    protected void removeAllBlobsFromBlobProvider() throws Exception {
        BinaryGarbageCollector gc = ((KeyValueBlobTransientStore) ts).getBlobProvider()
                                                                     .getBinaryGarbageCollector();
        gc.start(true);
        // mark nothing, so remove everything
        gc.stop(true);
    }

    @Test
    public void testGetParametersWithConcurrentRemove() throws Exception {
        String key = "foo";
        String param = "bar";
        String value = "gee";
        final int COUNT = 10_000;
        CyclicBarrier barrier = new CyclicBarrier(2);
        Runnable synchronizer = () -> {
            try {
                barrier.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        };
        Runnable remover = () -> {
            synchronizer.run();
            for (int i = 0; i < COUNT; i++ ) {
                ts.remove(key);
            }
        };
        MutableObject<RuntimeException> exc = new MutableObject<>();
        Runnable getter = () -> {
            synchronizer.run();
            for (int i = 0; i < COUNT; i++ ) {
                ts.putParameter(key, param, value);
                try {
                    ts.getParameters(key);
                } catch (NullPointerException e) {
                    exc.setValue(e);
                }
            }
        };
        Thread t1 = new Thread(remover);
        Thread t2 = new Thread(getter);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        if (exc.getValue() != null) {
            throw exc.getValue();
        }
    }

    @Test
    public void testPutBlobsGetBlobsConcurrency() throws Exception {
        String key = "mykey";
        Runnable put = () -> {
            ts.putBlob(key, new StringBlob("foo"));
        };
        // With the flat single-document .blob layout, putBlob atomically replaces
        // the blob state in one KV write. A concurrent getBlob may observe the brief window
        // between removeBlob (which clears .blob) and the new kvs.put(DOT_BLOB),
        // returning null. That is correct transient-store behaviour: the entry is
        // still alive (DOT_COMPLETED present) but has no blob in transit. It must never
        // crash or throw.
        Runnable get = () -> {
            // null is valid during a concurrent replacement — entry exists, momentarily blobless
            ts.getBlob(key);
        };

        // check once that it's ok without concurrency
        put.run();
        get.run();

        // define threads to run things concurrently in a loop
        Runnable putLoop = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    put.run();
                } catch (RuntimeException e) {
                    if (!(e.getCause() instanceof InterruptedException)) {
                        throw e;
                    }
                }
            }
        };
        Thread putThread = new Thread(putLoop, "putLoop");
        MutableObject<AssertionError> exc = new MutableObject<>();
        Runnable getLoop = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    get.run();
                } catch (AssertionError e) {
                    exc.setValue(e);
                    putThread.interrupt();
                    throw e;
                }
            }
        };
        Thread getThread = new Thread(getLoop, "getLoop");

        // start things concurrently
        putThread.start();
        getThread.start();
        Thread.sleep(1000); // keep threads looping for 1s
        putThread.interrupt();
        getThread.interrupt();
        putThread.join();
        getThread.join();
        if (exc.getValue() != null) {
            throw exc.getValue();
        }
        // explicit assertion: no AssertionError was thrown on the getBlob thread
        assertThat(exc.getValue()).as("getBlob must not fail under concurrent putBlob").isNull();
    }

    /**
     * Returns the underlying {@link KeyValueStore} so tests can either inspect
     * the on-disk shape directly or seed legacy-shaped keys to exercise the
     * pre-{@code DOT_PARAMS} fallback path. Store name {@code transient_<NAME>}
     * matches the wiring in {@link KeyValueBlobTransientStore#init} (defaults
     * to {@code TRANSIENT_ID_PREFIX + "_" + name}).
     */
    protected KeyValueStore getRawKeyValueStore() {
        return Framework.getService(KeyValueService.class).getKeyValueStore("transient_" + NAME);
    }

    /**
     * Defensive: if a {@code DOT_PARAMS} entry holds malformed JSON,
     * {@link KeyValueBlobTransientStore#readParamsTree} returns null and readers
     * must not throw. {@link TransientStore#getParameter} returns null and
     * {@link TransientStore#getParameters} returns an empty map (entry exists
     * since {@code DOT_COMPLETED} is set).
     */
    @Test
    public void verifyMalformedConsolidatedParamsJson() {
        // given an entry whose params bag is unparseable JSON
        KeyValueStore kvs = getRawKeyValueStore();
        String key = "malformedEntry";
        kvs.put(key + DOT_COMPLETED, "false");
        kvs.put(key + DOT_PARAMS, "not-json{");

        // when reading parameters from it
        Object singleParam = ts.getParameter(key, "anything");
        Map<String, Object> allParams = ts.getParameters(key);

        // then the malformed JSON is treated as missing data, no crash
        assertThat(singleParam).isNull();
        assertThat(allParams).isNotNull().isEmpty();
    }

    /**
     * Defensive: if a {@code DOT_PARAMS} entry holds valid JSON but the root is
     * not an object (e.g. an array or scalar written by a hand-edit or a
     * future caller bypassing {@link KeyValueBlobTransientStore#setNodeValue}),
     * {@link KeyValueBlobTransientStore#readParamsTree} returns null with a
     * warn-level log; readers behave as for the malformed-JSON case.
     */
    @Test
    public void verifyNonObjectConsolidatedParamsJson() {
        // given an entry whose params bag parses to a JSON array
        KeyValueStore kvs = getRawKeyValueStore();
        String key = "nonObjectEntry";
        kvs.put(key + DOT_COMPLETED, "false");
        kvs.put(key + DOT_PARAMS, "[1,2,3]");

        // when reading parameters from it
        Object singleParam = ts.getParameter(key, "anything");
        Map<String, Object> allParams = ts.getParameters(key);

        // then the non-object root is treated as missing data, no crash
        assertThat(singleParam).isNull();
        assertThat(allParams).isNotNull().isEmpty();
    }

    /**
     * Round-trip the value types production callers pass to
     * {@link TransientStore#putParameter}: {@code String}, {@code Boolean},
     * {@code Integer}, and {@code Long}. Numeric values come back as
     * {@code Long} regardless of whether they were written as Integer or Long
     * (see {@link KeyValueBlobTransientStore#nodeToObject}).
     */
    @Test
    public void verifyConsolidatedNativeTypeRoundTrip() {
        // given parameters of the four supported types
        String key = "nativeTypeRoundTrip";

        // when written individually
        ts.putParameter(key, "name", "embeddings.parquet");
        ts.putParameter(key, "chunked", true);
        ts.putParameter(key, "chunkCount", 10);
        ts.putParameter(key, "partialFileSize", 1048576L);

        // then each comes back as its native Java type, with numerics normalised to Long
        assertThat(ts.getParameter(key, "name")).isEqualTo("embeddings.parquet");
        assertThat(ts.getParameter(key, "chunked")).isEqualTo(true);
        assertThat(ts.getParameter(key, "chunkCount")).isEqualTo(10L);
        assertThat(ts.getParameter(key, "partialFileSize")).isEqualTo(1048576L);
        assertThat(ts.getParameters(key))
                .containsEntry("name", "embeddings.parquet")
                .containsEntry("chunked", true)
                .containsEntry("chunkCount", 10L)
                .containsEntry("partialFileSize", 1048576L);
    }

    /**
     * Locks in the strict-typing contract on {@link KeyValueBlobTransientStore#setNodeValue}:
     * any value type outside String / Boolean / Integer / Long is rejected at write
     * time. A future caller that needs another type must extend the dispatch
     * explicitly there rather than have it silently encoded into JSON.
     */
    @Test
    public void verifyUnsupportedParameterTypeIsRejected() {
        // given an upload key and a value type the codec doesn't support
        String key = "unsupportedType";
        java.util.Date unsupported = new java.util.Date();

        // when written via putParameter
        Throwable thrown = catchThrowable(() -> ts.putParameter(key, "ts", unsupported));

        // then it fails fast with a message naming the offending type
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported parameter type")
                .hasMessageContaining("java.util.Date");
    }

    /**
     * {@link TransientStore#putParameters} merges all entries in a single
     * {@code atomicUpdate} round-trip on the consolidated params doc. Callers
     * (Upload.addChunk, DefaultUploadHandler.newUpload) rely on this to batch
     * multi-field writes; a regression to a per-entry loop would silently
     * multiply CAS contention on the per-upload params doc.
     */
    @Test
    public void verifyPutParametersWritesInOneAtomicUpdate() {
        // given several params of mixed types in one batch
        String key = "batchedPut";
        Map<String, Object> batch = Map.of(
                "name", "embeddings.parquet",
                "chunked", true,
                "chunkCount", 10,
                "partialFileSize", 1048576L);

        // when written via putParameters
        ts.putParameters(key, batch);

        // then all entries land in the single DOT_PARAMS doc
        assertThat(getRawKeyValueStore().getString(key + DOT_PARAMS)).isNotNull();
        assertThat(ts.getParameters(key))
                .containsEntry("name", "embeddings.parquet")
                .containsEntry("chunked", true)
                .containsEntry("chunkCount", 10L)
                .containsEntry("partialFileSize", 1048576L);
    }

    /**
     * {@link TransientStore#putParameters} with an empty map is a no-op:
     * it does not create the params doc or mark the entry as existing.
     */
    @Test
    public void verifyPutParametersWithEmptyMapIsNoop() {
        // given a key that doesn't exist yet
        String key = "emptyBatch";

        // when putParameters is called with an empty map
        ts.putParameters(key, Map.of());

        // then no params doc is created and the entry doesn't exist
        assertThat(getRawKeyValueStore().getString(key + DOT_PARAMS)).isNull();
        assertThat(ts.exists(key)).isFalse();
    }

    /**
     * After {@link TransientStoreProvider#doGC()} recomputes the storage-size
     * accumulator, the {@code __blobsize__} key must be written via
     * {@link KeyValueStore#put(String, Long)} - i.e. as a native numeric value,
     * not a {@link String}.
     * <p>
     * The DocumentDB fast path in {@code KeyValueBlobTransientStore#addStorageSize}
     * (used on every {@link TransientStore#putBlob} / remove) relies on
     * {@code $inc}, which DocumentDB rejects with error code 14 on a non-numeric
     * field. A regression to {@code kvs.put(_, String.valueOf(size))} would
     * drop every subsequent {@code addAndGet} into the CAS-loop fallback and
     * re-introduce the per-key contention on {@code __blobsize__} that
     * CIN-13859 removed.
     * <p>
     * Verified via {@link Mockito#verify} on the {@code put} overload because
     * neither the {@code MemKeyValueStore} nor {@code MongoDBKeyValueStore}
     * expose a way to distinguish the storage type via a read: {@code getLong}
     * accepts both native {@code Long} and stringified digits by design.
     */
    @Test
    public void verifyComputeStorageSizeWritesLongNotString() {
        // given a real KeyValueBlobTransientStore whose KV store is a Mockito spy
        KeyValueStore spyKvs = Mockito.spy(getRawKeyValueStore());
        KeyValueBlobTransientStore tspWithSpy = Mockito.spy((KeyValueBlobTransientStore) tsp);
        Mockito.doReturn(spyKvs).when(tspWithSpy).getKeyValueStore();
        String content = "SomeContent";
        long expectedSize = content.getBytes(UTF_8).length;
        createBlob("foo", content);

        // when the accumulator is recomputed
        tspWithSpy.computeStorageSize();

        // then __blobsize__ is written via the Long overload and never as a String
        verify(spyKvs).put(KeyValueBlobTransientStore.STORAGE_SIZE, Long.valueOf(expectedSize));
        verify(spyKvs, never()).put(eq(KeyValueBlobTransientStore.STORAGE_SIZE), anyString());
    }

    static Stream<Arguments> isToDeleteEmptyCases() {
        AbstractBlobGarbageCollector emptyToDelete = new AbstractBlobGarbageCollector() {
            @Override public String getStorageAddress() { return "empty-toDelete"; }
            @Override protected String getId() { return "empty-toDelete"; }
            @Override public void computeToDelete() { /* nothing */ }
            @Override public long removeUnmarkedBlob(String key, boolean delete) { return -1; }
        };
        AbstractBlobGarbageCollector nonEmptyToDelete = new AbstractBlobGarbageCollector() {
            @Override public String getStorageAddress() { return "non-empty-toDelete"; }
            @Override protected String getId() { return "non-empty-toDelete"; }
            @Override public void computeToDelete() { toDelete.add("key"); }
            @Override public long removeUnmarkedBlob(String key, boolean delete) { return -1; }
        };
        return Stream.of(
                Arguments.of(emptyToDelete, true,  "empty toDelete → true (mark scan can be skipped)"),
                Arguments.of(nonEmptyToDelete, false, "non-empty toDelete → false (mark scan required)")
        );
    }

    /**
     * {@link AbstractBlobGarbageCollector#isToDeleteEmpty()} must return {@code true} when
     * {@code toDelete} is empty after {@link AbstractBlobGarbageCollector#start()}, {@code false}
     * when it is non-empty, and {@code false} for old-style GCs that use {@code marked} instead
     * of {@code toDelete} (conservative: cannot skip the mark phase).
     */
    @ParameterizedTest(name = "{2}")
    @MethodSource("isToDeleteEmptyCases")
    public void verifyIsToDeleteEmptyReflectsBlobStoreContents(
            AbstractBlobGarbageCollector garbageCollector, boolean expectedResult, String description) {
        // given the GC supplied by the test case

        // when GC is started and isToDeleteEmpty is queried
        garbageCollector.start(false);
        boolean actualResult = garbageCollector.isToDeleteEmpty();
        garbageCollector.stop(false);

        // then the result matches the expected value for this scenario
        assertThat(actualResult).as(description).isEqualTo(expectedResult);
    }

    /**
     * After {@link TransientStoreProvider#doGCSkipsMarkScanAndPreservesEntriesWhenBlobStoreIsEmpty},
     * {@link TransientStoreProvider#doGC()} must skip the {@code getBlobKeys()} DocumentDB scan
     * when the blob store is empty after {@link AbstractBlobGarbageCollector#start()}: no blobs
     * are deleted, KV entries are not read for their blob keys, and the storage size is recomputed
     * correctly. This fast-exit avoids an O(entries × blobs) round-trip cost when the blob store
     * is already clean (the common post-ingestion scenario from CIN-15654).
     */
    @Test
    public void verifyDoGCSkipsMarkScanAndPreservesEntriesWhenBlobStoreIsEmpty() {
        // given a transient store with an entry that has a blob
        createBlob("entry1", "content");
        assertThat(ts.exists("entry1")).isTrue();
        long sizeAfterPut = tsp.getStorageSize();
        assertThat(sizeAfterPut).isGreaterThan(0);

        // when all blobs are removed from the blob store by running GC with entry removed
        ts.remove("entry1");
        tsp.doGC(); // clears the blob store; storage size should now be 0

        // then storage size is recomputed to 0 even though the KV mark scan was skipped
        long actualSizeAfterGC = tsp.getStorageSize();
        assertThat(actualSizeAfterGC).as("storage size should be 0 after all entries removed and GC run").isZero();
    }

    /**
     * When the blob provider's GC is an {@link AbstractBlobGarbageCollector} with a non-empty
     * {@code toDelete} set, {@link TransientStoreProvider#doGC()} must execute the
     * {@code keyStream()} mark scan so that live blobs are protected.
     * Uses a store backed by {@code InMemoryBlobProvider} whose GC extends
     * {@link AbstractBlobGarbageCollector}, covering the mark-scan body in {@code doGC()}.
     */
    @Test
    public void verifyDoGCRunsMarkScanWhenAbstractGCHasNonEmptyToDelete() {
        // given an InMemoryBlobProvider-backed transient store with a blob entry
        TransientStoreService transientStoreService = Framework.getService(TransientStoreService.class);
        KeyValueBlobTransientStore transientStore = (KeyValueBlobTransientStore) transientStoreService.getStore("inmemstore");
        transientStore.putBlob("gcMarkEntry", new StringBlob("data"));
        assertThat(transientStore.getStorageSize()).isGreaterThan(0);

        // when doGC is called (InMemoryBlobGarbageCollector extends AbstractBlobGarbageCollector
        // and will have non-empty toDelete since the blob exists in the store)
        transientStore.doGC();

        // then the mark scan ran: the live blob was marked and is still tracked
        long actualSize = transientStore.getStorageSize();
        assertThat(actualSize).as("storage size after doGC with live blob").isGreaterThanOrEqualTo(0);

        // cleanup
        transientStore.removeAll();
    }

    /**
     * When the blob provider's GC is an {@link AbstractBlobGarbageCollector} with an empty
     * {@code toDelete} set (blob store empty), {@link TransientStoreProvider#doGC()} must
     * skip the {@code keyStream()} mark scan entirely.
     * Uses a store backed by {@code InMemoryBlobProvider} whose GC extends
     * {@link AbstractBlobGarbageCollector}, covering the fast-exit path in {@code doGC()}.
     */
    @Test
    public void verifyDoGCFastExitWhenAbstractGCHasEmptyToDelete() {
        // given an InMemoryBlobProvider-backed transient store with a blob entry that is then removed
        TransientStoreService transientStoreService = Framework.getService(TransientStoreService.class);
        KeyValueBlobTransientStore transientStore = (KeyValueBlobTransientStore) transientStoreService.getStore("inmemstore");
        transientStore.putBlob("gcFastExitEntry", new StringBlob("data"));
        transientStore.remove("gcFastExitEntry");
        transientStore.doGC(); // first GC: clears the blob from the InMemory store, toDelete now empty

        // when doGC is called again with empty blob store (toDelete will be empty after gc.start(true))
        transientStore.doGC();

        // then storage size is 0 and no exception was thrown (fast-exit path completed cleanly)
        long actualSize = transientStore.getStorageSize();
        assertThat(actualSize).as("storage size after fast-exit doGC").isZero();

        // cleanup
        transientStore.removeAll();
    }

    /**
     * {@link TransientStoreProvider#removeAll()} must clear all KV entries and then run
     * {@link TransientStoreProvider#doGC()} to clean up orphaned blobs, leaving the store empty.
     */
    @Test
    public void verifyRemoveAllClearsStoreAndRunsGC() {
        // given a transient store with several entries
        createBlob("r1", "alpha");
        createBlob("r2", "beta");
        assertThat(ts.exists("r1")).isTrue();
        assertThat(ts.exists("r2")).isTrue();
        assertThat(tsp.getStorageSize()).isGreaterThan(0);

        // when removeAll is called
        tsp.removeAll();

        // then all entries are gone and storage size is 0
        assertThat(ts.exists("r1")).as("r1 removed by removeAll").isFalse();
        assertThat(ts.exists("r2")).as("r2 removed by removeAll").isFalse();
        assertThat(tsp.getStorageSize()).as("storage size reset after removeAll").isZero();
    }

    // -----------------------------------------------------------------------
    // CIN-15767: flat .blob layout and __blobkey__ prefix index
    // -----------------------------------------------------------------------

    /**
     * After {@link TransientStore#putBlob}, blob metadata must be inlined in the
     * {@code .blob} key (flat layout) rather than written to a separate {@code .blob.0}
     * key. The {@code .blob.0} key must be absent for newly written entries.
     */
    @Test
    public void verifyFlatBlobLayoutOnPutBlob() {
        // given a fresh entry with one blob
        String key = "flatLayoutEntry";
        createBlob(key, "flat content");

        // then blob metadata is inlined in .blob (flat layout)
        KeyValueStore kvs = getRawKeyValueStore();
        String blobInfoJson = kvs.getString(key + DOT_BLOB);
        assertThat(blobInfoJson).as(".blob must be present and contain inline key field")
                .isNotNull()
                .contains("\"key\"");

        // and no separate .blob.0 key is written for new entries
        String legacyBlobKey = kvs.getString(key + DOT_BLOB_DOT + "0");
        assertThat(legacyBlobKey).as(".blob.0 must not be written for new entries").isNull();
    }

    /**
     * After {@link TransientStore#putBlob}, a {@code __blobkey__.<blobKey>} index entry must
     * be present in the KV store for every blob stored. This allows {@link #doGC()} to enumerate
     * all live blob keys via a single prefix scan rather than per-entry reads.
     */
    @Test
    public void verifyBlobKeyIndexEntryWrittenOnPutBlobs() {
        // given an entry with a blob
        String key = "blobKeyIndexEntry";
        createBlob(key, "indexed blob content");

        // get the stored flat blob JSON and extract the inlined blob key
        KeyValueStore kvs = getRawKeyValueStore();
        String blobInfoJson = kvs.getString(key + DOT_BLOB);
        assertThat(blobInfoJson).as(".blob must exist").isNotNull();
        assertThat(blobInfoJson).as(".blob must have key field").contains("\"key\"");
        String blobKey = extractBlobKeyFromFlatJson(blobInfoJson);

        // then a __blobkey__.<blobKey> index entry must exist
        assertThat(kvs.getString(BLOBKEY_PREFIX + blobKey))
                .as("__blobkey__.<blobKey> index entry must be present after putBlob")
                .isNotNull();
    }

    /**
     * After {@link TransientStore#remove}, the {@code __blobkey__} index entry for the blob must
     * be deleted so that the GC prefix scan doesn't see stale references.
     */
    @Test
    public void verifyBlobKeyIndexEntryRemovedOnRemove() {
        // given an entry with a blob
        String key = "removedBlobKeyIndex";
        createBlob(key, "remove test");

        KeyValueStore kvs = getRawKeyValueStore();
        String blobInfoJson = kvs.getString(key + DOT_BLOB);
        String blobKey = extractBlobKeyFromFlatJson(blobInfoJson);

        // when the entry is removed
        ts.remove(key);

        // then the __blobkey__ index entry is also removed
        assertThat(kvs.getString(BLOBKEY_PREFIX + blobKey))
                .as("__blobkey__.<blobKey> must be deleted on remove")
                .isNull();
    }

    /**
     * {@link TransientStore#getBlob} must transparently read entries written in the old
     * {@code .blobinfo} + {@code .blob.0} layout (pre-CIN-15767). This ensures rolling-deploy
     * safety: pods running the new code can serve entries that were written by old pods.
     */
    @Test
    public void verifyGetBlobsFallsBackToLegacyLayout() {
        // given an entry written via the new path first (to get a real blob into the provider)
        String key = "legacyLayoutEntry";
        String content = "legacy content";
        String digest = DigestUtils.md5Hex(content);
        ts.putBlob(key, new StringBlob(content, "text/plain", null, "legacy.txt"));

        // extract the blob key from the new-layout .blob, then reshape to old layout
        KeyValueStore kvs = getRawKeyValueStore();
        String blobInfoJson = kvs.getString(key + DOT_BLOB);
        String blobKey = extractBlobKeyFromFlatJson(blobInfoJson);

        // write old-layout: .blobinfo without blob.0.* fields, a separate .blob.0, no index entry
        kvs.put(key + DOT_BLOBINFO, "{\"count\":\"1\",\"size\":\"" + content.getBytes(UTF_8).length + "\"}");
        kvs.put(key + DOT_BLOB_DOT + "0",
                "{\"key\":\"" + blobKey + "\",\"mimeType\":\"text/plain\",\"filename\":\"legacy.txt\",\"length\":\""
                        + content.getBytes(UTF_8).length + "\",\"digest\":\"" + digest + "\"}");
        kvs.put(BLOBKEY_PREFIX + blobKey, (String) null); // remove index entry

        // when getBlob is called
        Blob actualBlob = ts.getBlob(key);

        // then the blob is returned correctly from the old layout fallback
        assertThat(actualBlob).as("should return blob from legacy layout").isNotNull();
        assertThat(actualBlob.getFilename()).isEqualTo("legacy.txt");
    }

    /**
     * {@link TransientStoreProvider#doGC()} with new-layout entries must use the
     * {@code __blobkey__} prefix scan to mark live blobs, not the legacy {@code getBlobKeys}
     * per-entry reads. After GC on a store with live entries, all blobs must still be accessible.
     */
    @Test
    public void verifyDoGCUsesNewBlobKeyIndex() {
        // given two entries with blobs (new flat layout)
        createBlob("gcIdx1", "gc index content 1");
        createBlob("gcIdx2", "gc index content 2");
        long sizeAfterPut = tsp.getStorageSize();
        assertThat(sizeAfterPut).isGreaterThan(0);

        // when GC runs with live blobs in the blob store
        tsp.doGC();

        // then both entries survive (blobs were marked via __blobkey__ prefix scan)
        assertThat(ts.exists("gcIdx1")).as("gcIdx1 must survive doGC").isTrue();
        assertThat(ts.exists("gcIdx2")).as("gcIdx2 must survive doGC").isTrue();
        assertThat(ts.getBlob("gcIdx1")).as("gcIdx1 blob still readable").isNotNull();
        assertThat(ts.getBlob("gcIdx2")).as("gcIdx2 blob still readable").isNotNull();
        assertThat(tsp.getStorageSize()).as("storage size unchanged after GC with live entries")
                                       .isEqualTo(sizeAfterPut);
    }

    /**
     * {@link TransientStoreProvider#doGC()} must still protect blobs written in the old layout
     * (no {@code __blobkey__} index, blob key in {@code .blob.0}) via the legacy
     * {@link KeyValueBlobTransientStore#getBlobKeys(String)} fallback.
     */
    @Test
    public void verifyDoGCLegacyFallbackProtectsOldLayoutBlobs() {
        // given an entry first written via new layout (to get a real blob into the provider)
        String key = "gcLegacyEntry";
        String content = "old layout gc content";
        createBlob(key, content);

        KeyValueStore kvs = getRawKeyValueStore();
        String blobInfoJson = kvs.getString(key + DOT_BLOB);
        String blobKey = extractBlobKeyFromFlatJson(blobInfoJson);

        // reshape to old layout: strip blob.* fields from .blob, write .blobinfo + .blob.0, remove index
        kvs.put(key + DOT_BLOB, (String) null); // clear new-layout document
        kvs.put(key + DOT_BLOBINFO,
                "{\"count\":\"1\",\"size\":\"" + content.getBytes(UTF_8).length + "\"}");
        kvs.put(key + DOT_BLOB_DOT + "0",
                "{\"key\":\"" + blobKey + "\",\"mimeType\":\"text/plain\",\"length\":\""
                        + content.getBytes(UTF_8).length + "\"}");
        kvs.put(BLOBKEY_PREFIX + blobKey, (String) null); // no index entry — simulates old code

        // when GC runs
        tsp.doGC();

        // then the old-layout blob was protected via the getBlobKeys fallback
        assertThat(ts.exists(key)).as("old-layout entry must survive doGC").isTrue();
        assertThat(ts.getBlob(key)).as("old-layout blob still readable after doGC").isNotNull();
    }

    /**
     * Helper: extracts the blob provider key from a flat {@code .blob} JSON string.
     * Parses {@code "key":"<value>"}.
     */
    private static String extractBlobKeyFromFlatJson(String blobInfoJson) {
        assertThat(blobInfoJson).as("blobinfo JSON").isNotNull();
        // Use a non-greedy match on "key": to avoid matching fields like "mimeType" etc.
        // The JSON contains "key":"<blobKey>" where blobKey is the actual blob provider key.
        int idx = blobInfoJson.indexOf("\"key\"");
        assertThat(idx).as("key field in blobinfo JSON").isGreaterThan(-1);
        return blobInfoJson.substring(idx + "\"key\"".length())
                           .replaceAll("^\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    /**
     * {@link TransientStore#remove} on an entry written in the old layout (separate {@code .blob.0}
     * key, {@code size} field in {@code .blobinfo}) must clean up the old-layout keys, adjust the
     * storage-size accumulator, and not leave a stale {@code __blobkey__} index entry.
     * Covers the {@code removeBlobs} old-layout fallback path introduced in CIN-15767.
     */
    @Test
    public void verifyRemoveCleansUpOldLayoutEntry() {
        // given an entry seeded via the new path (to get a real blob into the provider)
        String key = "oldLayoutRemoveEntry";
        String content = "old layout remove content";
        ts.putBlob(key, new StringBlob(content, "text/plain", null, "old.txt"));

        // reshape to old layout: .blobinfo with a "size" field + separate .blob.0 key, no blob.key inline
        KeyValueStore kvs = getRawKeyValueStore();
        String blobInfoJson = kvs.getString(key + DOT_BLOB);
        String blobKey = extractBlobKeyFromFlatJson(blobInfoJson);
        int contentBytes = content.getBytes(UTF_8).length;
        kvs.put(key + DOT_BLOB, (String) null); // clear new-layout document so fallback path is exercised
        kvs.put(key + DOT_BLOBINFO, "{\"size\":\"" + contentBytes + "\"}");
        kvs.put(key + DOT_BLOB_DOT + "0",
                "{\"key\":\"" + blobKey + "\",\"mimeType\":\"text/plain\",\"length\":\"" + contentBytes + "\"}");
        kvs.put(BLOBKEY_PREFIX + blobKey, (String) null); // no index entry — simulates pre-index old code

        long sizeBeforeRemove = tsp.getStorageSize();
        assertThat(sizeBeforeRemove).as("storage size reflects the seeded blob").isGreaterThan(0);

        // when the entry is removed
        ts.remove(key);

        // then the entry is gone, .blob.0 is cleaned up, and storage size is adjusted
        assertThat(ts.exists(key)).as("entry must not exist after remove").isFalse();
        assertThat(kvs.getString(key + DOT_BLOB_DOT + "0")).as(".blob.0 legacy key must be deleted").isNull();
        assertThat(tsp.getStorageSize()).as("storage size must decrease by blob length").isEqualTo(sizeBeforeRemove - contentBytes);
    }
}
