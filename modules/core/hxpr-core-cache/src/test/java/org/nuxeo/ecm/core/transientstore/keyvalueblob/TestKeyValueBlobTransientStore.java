/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.transientstore.keyvalueblob;

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
import static org.nuxeo.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.DOT_COMPLETED;
import static org.nuxeo.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.DOT_PARAMINFO;
import static org.nuxeo.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.DOT_PARAMS;
import static org.nuxeo.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.DOT_PARAM_DOT;
import static org.nuxeo.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.FORMAT;
import static org.nuxeo.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore.FORMAT_JAVA;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.LocalBlobProvider;
import org.nuxeo.ecm.core.blob.LocalBlobStore.LocalBlobGarbageCollector;
import org.nuxeo.ecm.core.blob.binary.BinaryGarbageCollector;
import org.nuxeo.ecm.core.transientstore.api.MaximumTransientSpaceExceeded;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreProvider;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

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
        ts.putBlobs(key, Collections.singletonList(blob));
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
        List<Blob> blobs = ts.getBlobs(key);
        assertEquals(1, blobs.size());
        Blob blob = blobs.get(0);
        assertEquals("fake.txt", blob.getFilename());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals(contentMD5, blob.getDigest());
        try (InputStream stream = blob.getStream()) {
            assertEquals(content, IOUtils.toString(stream, UTF_8));
        }

        size = tsp.getStorageSize();
        assertEquals(contentByteLength, size);

        // update the entry
        Blob otherBlob = new StringBlob(content2);
        otherBlob.setFilename("fake2.txt");
        otherBlob.setMimeType("text/plain");
        blobs.add(otherBlob);
        ts.putBlobs(key, blobs);

        // check update
        assertTrue(ts.exists(key));
        assertEquals(new HashSet<>(Arrays.asList(key)), tsp.keySet());
        assertEquals(contentByteLength + content2ByteLength, ts.getSize(key));
        blobs = ts.getBlobs(key);
        assertEquals(2, blobs.size());
        assertEquals("fake.txt", blobs.get(0).getFilename());
        assertEquals("fake2.txt", blobs.get(1).getFilename());
        size = tsp.getStorageSize();
        assertEquals(contentByteLength + content2ByteLength, size);

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
        assertNull(ts.getBlobs("fakeEntry"));
        assertEquals(-1, ts.getSize("fakeEntry"));
        assertFalse(ts.isCompleted("fakeEntry"));

        // Entry with parameters only
        ts.putParameter("testEntry", "param1", "value");
        assertTrue(ts.exists("testEntry"));
        Map<String, Object> params = ts.getParameters("testEntry");
        assertNotNull(params);
        assertEquals(1, params.size());
        assertNotNull(ts.getParameter("testEntry", "param1"));
        assertNull(ts.getParameter("testEntry", "param2"));
        List<Blob> blobs = ts.getBlobs("testEntry");
        assertNotNull(blobs);
        assertTrue(blobs.isEmpty());

        // Entry with blobs only
        ts.putBlobs("otherEntry", Collections.singletonList(new StringBlob("joe")));
        assertTrue(ts.exists("otherEntry"));
        params = ts.getParameters("otherEntry");
        assertNotNull(params);
        assertTrue(params.isEmpty());
        blobs = ts.getBlobs("otherEntry");
        assertNotNull(blobs);
        assertEquals(1, blobs.size());
    }

    @Test
    public void verifyMaxSizeException() throws Exception {
        // store is configured for 1MB max
        byte[] bytes = new byte[1024 * 1024 + 1];
        Blob blob = Blobs.createBlob(bytes);
        ts.putBlobs("foo", Collections.singletonList(blob));
        // store another one but we've exceeded the size allowed
        try {
            ts.putBlobs("bar", Collections.singletonList(Blobs.createBlob("x")));
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
        ts.getBlobs(key);
    }

    protected void removeAllBlobsFromBlobProvider() throws Exception {
        // sleep needed because the GC doesn't remove very young files
        Thread.sleep(LocalBlobGarbageCollector.TIME_RESOLUTION + 100);
        BinaryGarbageCollector gc = ((KeyValueBlobTransientStore) ts).getBlobProvider()
                                                                     .getBinaryGarbageCollector();
        gc.start();
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
            ts.putBlobs(key, Collections.singletonList(new StringBlob("foo")));
        };
        Runnable get = () -> {
            List<Blob> blobs = ts.getBlobs(key);
            assertNotNull(blobs);
            assertEquals(1, blobs.size());
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
     * Reading a String parameter from a legacy-layout entry (paraminfo + per-key
     * value, no {@code DOT_PARAMS}) must fall back to the legacy keys via both
     * {@link TransientStore#getParameter} and {@link TransientStore#getParameters}.
     * The fallback exists for entries created on pre-Phase-19 code that are
     * still within their TTL window at deploy time; it can be removed after one
     * {@code firstLevelTTL} window has elapsed since the deploy.
     */
    @Test
    public void verifyLegacyStringParamFallback() {
        // given a legacy-layout entry seeded directly into the KV store
        KeyValueStore kvs = getRawKeyValueStore();
        String key = "legacyEntry";
        kvs.put(key + DOT_COMPLETED, "false");
        kvs.put(key + DOT_PARAMINFO, "[\"name\"]");
        kvs.put(key + DOT_PARAM_DOT + "name", "value-on-old-code");

        // when the consolidated-layout readers run against it
        Object singleParam = ts.getParameter(key, "name");
        Map<String, Object> allParams = ts.getParameters(key);

        // then both fall through to the legacy keys
        assertThat(singleParam).isEqualTo("value-on-old-code");
        assertThat(allParams).containsEntry("name", "value-on-old-code");
    }

    /**
     * Reading a Java-serialised parameter from a legacy-layout entry must fall
     * back to the legacy keys, deserialise the bytes, and return the original
     * object.
     */
    @Test
    public void verifyLegacyJavaSerializedParamFallback() {
        // given a legacy-layout entry whose parameter is Java-serialised
        KeyValueStore kvs = getRawKeyValueStore();
        String key = "legacyJavaEntry";
        Long original = 42L;
        kvs.put(key + DOT_COMPLETED, "false");
        kvs.put(key + DOT_PARAMINFO, "[\"obj\"]");
        kvs.put(key + DOT_PARAM_DOT + "obj", SerializationUtils.serialize(original));
        kvs.put(key + DOT_PARAM_DOT + "obj" + FORMAT, FORMAT_JAVA);

        // when reading the parameter
        Object value = ts.getParameter(key, "obj");

        // then the bytes are deserialised back to the original object
        assertThat(value).isEqualTo(original);
    }

    /**
     * {@link TransientStore#release} and {@link TransientStore#remove} on a
     * legacy-layout entry must touch the legacy paraminfo + per-key + format
     * keys (TTL refresh on release, delete on remove), via the legacy branches
     * in {@link KeyValueBlobTransientStore#setReleaseTTL} and
     * {@link KeyValueBlobTransientStore#removeParameters}.
     */
    @Test
    public void verifyLegacyRemoveAndRelease() {
        // given a legacy-layout entry with two params plus a blob
        KeyValueStore kvs = getRawKeyValueStore();
        String key = "legacyMixed";
        kvs.put(key + DOT_COMPLETED, "false");
        kvs.put(key + DOT_PARAMINFO, "[\"x\",\"y\"]");
        kvs.put(key + DOT_PARAM_DOT + "x", "1");
        kvs.put(key + DOT_PARAM_DOT + "y", "2");
        createBlob(key, "legacy-content");

        // when the entry is released, then removed
        ts.release(key);
        assertThat(ts.exists(key)).as("entry survives release").isTrue();
        ts.remove(key);

        // then the entry is gone and so are all legacy keys
        assertThat(ts.exists(key)).as("entry removed").isFalse();
        assertThat(kvs.getString(key + DOT_PARAMINFO)).isNull();
        assertThat(kvs.getString(key + DOT_PARAM_DOT + "x")).isNull();
        assertThat(kvs.getString(key + DOT_PARAM_DOT + "y")).isNull();
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
}
