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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.impl.blob.StringBlob;
import com.hyland.openarch.ecm.core.blob.BlobStore.OptionalOrUnknown;
import com.hyland.openarch.ecm.core.blob.binary.BinaryGarbageCollector;
import com.hyland.openarch.ecm.core.blob.binary.BinaryManagerStatus;
import com.hyland.openarch.ecm.core.utils.BlobUtils;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(BlobManagerFeature.class)
public abstract class TestAbstractBlobStore {

    protected static final String ID1 = "id1";

    protected static final String ID2 = "id2";

    protected static final String ID4 = "id4";

    protected static final String FOO = "foo";

    protected static final String BAR = "bar";

    protected static final String FOO_SHA256 = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

    protected static final String BAR_SHA256 = "fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9";

    public static final String SHA_256 = "SHA-256";

    @Inject
    protected BlobService blobService;

    protected BlobProvider bp;

    protected BlobStore bs;

    protected Path tmpFile;

    @BeforeEach
    public void setUp() throws IOException {
        bp = blobService.getBlobProvider("test");
        bs = ((BlobStoreBlobProvider) bp).getStore();
        bs.clear();
        tmpFile = Files.createTempFile("tmp_", ".tmp");
    }

    @AfterEach
    public void tearDown() throws IOException {
        bs.clear();
        Files.deleteIfExists(tmpFile);
        AbstractBlobGarbageCollector.DELETE_WITH_RECOVERY = true;
        AbstractBlobGarbageCollector.RECOVER_RECOVERY = false;
    }

    // we can't check the size on files encrypted when stored
    public boolean checkSizeOfGCedFiles() {
        return true;
    }

    public boolean useDeDuplication() {
        return bs.getKeyStrategy().useDeDuplication();
    }

    public boolean hasVersioning() {
        return bs.hasVersioning();
    }

    protected BlobContext blobContext(String id, String value) {
        return new BlobContext(value == null ? null : new StringBlob(value), id, SYSFILE_BLOB);
    }

    protected void assertBlob(String key, String value) throws IOException {
        assertBlob(bs, key, value);
    }

    protected void assertBlob(BlobStore bs, String key, String value) throws IOException {
        // check readBlobTo
        assertTrue(bs.readBlob(key, tmpFile));
        assertEquals(value, new String(Files.readAllBytes(tmpFile), UTF_8));
        // check file
        OptionalOrUnknown<Path> fileOpt = bs.getFile(key);
        if (fileOpt.isKnown()) {
            assertTrue(fileOpt.isPresent());
            assertEquals(value, new String(Files.readAllBytes(fileOpt.get()), UTF_8));
        }
        // check stream
        OptionalOrUnknown<InputStream> streamOpt = bs.getStream(key);
        if (streamOpt.isKnown()) {
            assertTrue(streamOpt.isPresent());
            try (InputStream stream = streamOpt.get()) {
                assertEquals(value, IOUtils.toString(stream, UTF_8));
            }
        }
    }

    protected void assertKey(String expected, String actual) {
        if (!useDeDuplication()) {
            // allow version to be present if bucket has versioning
            int seppos = expected.indexOf(KeyStrategy.VER_SEP);
            if (seppos >= 0) {
                expected = expected.substring(0, seppos);
            }
            if (!actual.startsWith(expected + '@')) {
                assertEquals(expected, actual);
            }
        }
    }

    protected void assertNoBlob(String key) throws IOException {
        assertNoBlob(bs, key);
    }

    protected void assertNoBlob(BlobStore bs, String key) throws IOException {
        // check readBlobTo
        assertFalse(bs.readBlob(key, tmpFile));
        OptionalOrUnknown<Path> fileOpt = bs.getFile(key);
        if (fileOpt.isKnown()) {
            assertFalse(fileOpt.isPresent());
        }
        OptionalOrUnknown<InputStream> streamOpt = bs.getStream(key);
        if (streamOpt.isKnown()) {
            assertFalse(streamOpt.isPresent());
        }
    }

    /**
     * Checks that nothing at all remains for the given key, including any backend-specific leftovers like versions or
     * delete markers. Subclasses whose backend keeps such leftovers override this.
     */
    protected void assertNothingLeftForKey(String key) throws IOException {
        assertNoBlob(key);
    }

    /**
     * Checks that the backend did accumulate leftovers for the given key after it was written, deleted and written
     * again, so that {@link #assertNothingLeftForKey} is actually checking something. Subclasses whose backend keeps
     * versions or delete markers override this; it is a no-op for the others, which keep no history at all.
     *
     * @param expectedVersions the number of versions the backend is expected to have kept for the key
     */
    protected void assertLeftoversForKey(String key, int expectedVersions) throws IOException {
        // no leftovers to check by default
    }

    @Test
    public void testMissing() throws IOException {
        assertNoBlob(ID1);
    }

    @Test
    public void testCRUD() throws IOException {
        // no blob initially
        assertNoBlob(ID1);

        // store blob
        BlobContext blobContext = blobContext(ID1, FOO);
        String key1 = bp.writeBlob(blobContext);
        assertKey(ID1, key1);
        // check content
        assertBlob(key1, FOO);

        // replace
        String key2 = bs.writeBlob(blobContext(ID1, BAR));
        assertKey(ID1, key2);
        // check content
        assertBlob(key2, BAR);

        if (!useDeDuplication()) {
            // delete
            bs.deleteBlob(blobContext(key2, null));
            // check deleted
            assertNoBlob(key2);
        }
    }

    @Test
    public void testFixupDigestOnWrite() throws IOException {
        assumeTrue(useDeDuplication(), "digest is not fixed up in record mode");

        // write blob with no digest
        Blob blob = new StringBlob(FOO);
        String key = bp.writeBlob(blob);
        assertEquals(FOO_SHA256, key);
        // digest was fixed up
        assertEquals(SHA_256, blob.getDigestAlgorithm());
        assertEquals(FOO_SHA256, blob.getDigest());

        // write blob with temporary digest
        blob = new StringBlob(FOO);
        blob.setDigest("notadigest-0");
        key = bp.writeBlob(blob);
        assertEquals(FOO_SHA256, key);
        // digest was fixed up
        assertEquals(SHA_256, blob.getDigestAlgorithm());
        assertEquals(FOO_SHA256, blob.getDigest());

        // write blob with custom digest
        String digest = "rL0Y20zC+Fzt72VPzMSk2A==";
        blob = new StringBlob(FOO);
        blob.setDigest(digest);
        key = bp.writeBlob(blob);
        assertEquals(FOO_SHA256, key);
        // digest was not fixed up
        assertNull(blob.getDigestAlgorithm());
        assertEquals(digest, blob.getDigest());
    }

    @Test
    public void testFixupDigestOnRead() throws IOException {
        assumeTrue(useDeDuplication(), "digest is not fixed up in record mode");

        // write blob
        String key = bp.writeBlob(new StringBlob(FOO));
        assertEquals(FOO_SHA256, key);

        // read blob
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey(FOO_SHA256);
        Blob blob = bp.readBlob(blobInfo);
        // digest was fixed up
        assertEquals(SHA_256, blob.getDigestAlgorithm());
        assertEquals(FOO_SHA256, blob.getDigest());

        // read blob with temporary digest in database
        blobInfo.setDigest("notadigest-0");
        blob = bp.readBlob(blobInfo);
        // digest was fixed up
        assertEquals(SHA_256, blob.getDigestAlgorithm());
        assertEquals(FOO_SHA256, blob.getDigest());

        // read blob with custom digest in database
        String digest = "rL0Y20zC+Fzt72VPzMSk2A==";
        blobInfo.setDigest(digest);
        blob = bp.readBlob(blobInfo);
        // digest was not fixed up
        assertNull(blob.getDigestAlgorithm());
        assertEquals(digest, blob.getDigest());
    }

    @Test
    public void testByteRange() throws IOException {
        assumeTrue(bp.allowByteRange());

        // store blob
        String key = bs.writeBlob(blobContext(ID1, "abcd1234efgh"));
        // get a byte range
        assertBlob(key + ";4;7", "1234");
    }

    @Test
    public void testWriteFromSameBlobProvier() throws IOException {
        // store blob
        String key1 = bs.writeBlob(blobContext(ID1, FOO));
        assertKey(ID1, key1);

        // construct an actual Blob for it
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey("test:" + key1);
        Blob blob = new SimpleManagedBlob(blobInfo);

        // this write should use an optimized copy path
        String key2 = bs.writeBlob(new BlobContext(blob, ID2, SYSFILE_BLOB));
        assertKey(ID2, key2);
    }

    @Test
    public void testBlobGetFile() throws IOException {
        assumeFalse(bs.unwrap() instanceof InMemoryBlobStore, "InMemoryBlobStore has no File");

        // store blob
        String key1 = bs.writeBlob(blobContext(ID1, FOO));
        assertKey(ID1, key1);

        // construct an actual Blob for it
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey("test:" + key1);
        Blob blob = new SimpleManagedBlob(blobInfo);

        // check that we have an underlying file
        assertNotNull(blob.getFile());
    }

    @Test
    public void testCopy() throws IOException {
        testCopyOrMove(false);
    }

    @Test
    public void testMove() throws IOException {
        testCopyOrMove(true);
    }

    protected void testCopyOrMove(boolean atomicMove) throws IOException {
        // we don't test the unimplemented copyBlob API, as it's only called from commit or during caching
        assumeFalse(bp.isTransactional(), "low-level copy/move not tested in transactional blob store");

        // stream available for source
        BlobStore bs2 = new InMemoryBlobStore("mem", new KeyStrategyDigest(SHA_256));
        testCopyOrMove(bs2, atomicMove);

        // no stream for the source, no local file
        BlobStore bs3 = new InMemoryBlobStore("mem", new KeyStrategyDigest(SHA_256), true, false);
        testCopyOrMove(bs3, atomicMove);

        // no stream for the source, but has local file
        BlobStore bs4 = new InMemoryBlobStore("mem", new KeyStrategyDigest(SHA_256), true, true);
        testCopyOrMove(bs4, atomicMove);
    }

    protected void testCopyOrMove(BlobStore sourceStore, boolean atomicMove) throws IOException {
        assertNull(bs.copyOrMoveBlob(ID2, sourceStore, ID1, atomicMove));
        assertNull(bs.copyOrMoveBlob(ID2, sourceStore, FOO_SHA256, atomicMove));
        String key1 = sourceStore.writeBlob(blobContext(ID1, FOO));
        assertEquals(FOO_SHA256, key1);
        String key2 = bs.copyOrMoveBlob(ID2, sourceStore, key1, atomicMove);
        assertEquals(ID2, key2);
        if (!useDeDuplication()) {
            assertBlob(ID2, FOO);
        }
        if (atomicMove) {
            assertNoBlob(sourceStore, key1);
        } else {
            assertBlob(sourceStore, key1, FOO);
        }
    }

    protected PathStrategy getCachePathStrategy() {
        if (bs instanceof CachingBlobStore) {
            return ((CachingBlobStore) bs).cacheStore.pathStrategy;
        }
        return null;
    }

    @Test
    public void testGC() throws IOException {
        // doesn't bring anything over the LocalBlobStore GC test;  avoid additional setup for this
        assumeFalse(bp.isTransactional(), "GC not tested in transactional blob store");

        // store blob
        String key1 = bs.writeBlob(blobContext(ID1, FOO));
        assertKey(ID1, key1);

        // other blob we'll GC
        String key2 = bs.writeBlob(blobContext(ID2, "barbaz"));
        assertKey(ID2, key2);

        // GC in non-delete mode
        BinaryGarbageCollector gc = bs.getBinaryGarbageCollector();
        assertFalse(gc.isInProgress());
        gc.start(false);
        assertTrue(gc.isInProgress());
        gc.mark(key1);
        assertTrue(gc.isInProgress());
        gc.stop(false);
        assertFalse(gc.isInProgress());
        BinaryManagerStatus status = gc.getStatus();
        assertEquals(1, status.getNumBinaries());
        assertEquals(1, status.getNumBinariesGC());
        if (checkSizeOfGCedFiles()) {
            assertEquals(3, status.getSizeBinaries());
            assertEquals(6, status.getSizeBinariesGC());
        }

        // check content
        assertBlob(key1, FOO);
        assertBlob(key2, "barbaz");

        // real GC
        assertFalse(gc.isInProgress());
        gc.start(true);
        assertTrue(gc.isInProgress());
        // when caching, create a tmp/other file in the cache that shouldn't disappear during GC
        Path cacheTmp = null;
        Path cacheOther = null;
        PathStrategy cachePathStrategy = getCachePathStrategy();
        if (cachePathStrategy != null) {
            cacheTmp = cachePathStrategy.createTempFile();
            cacheOther = cachePathStrategy.getPathForKey(BlobUtils.randomString());
            Files.createFile(cacheOther);
        }
        gc.mark(key1);
        assertTrue(gc.isInProgress());
        gc.stop(true); // delete=true
        assertFalse(gc.isInProgress());
        status = gc.getStatus();
        assertEquals(1, status.getNumBinaries());
        assertEquals(1, status.getNumBinariesGC());
        if (checkSizeOfGCedFiles()) {
            assertEquals(3, status.getSizeBinaries());
            assertEquals(6, status.getSizeBinariesGC());
        }
        // check content, one blob gone
        assertBlob(key1, FOO);
        assertNoBlob(key2);

        // cache tmp/other is still here
        if (cacheTmp != null) {
            assertTrue(Files.exists(cacheTmp));
        }
        if (cacheOther != null) {
            assertTrue(Files.exists(cacheOther));
        }
    }

    @Test
    public void testGCWithConcurrentCreation() throws IOException {
        // doesn't bring anything over the LocalBlobStore GC test;  avoid additional setup for this
        assumeFalse(bp.isTransactional(), "GC not tested in transactional blob store");

        // store blob
        String key1 = bs.writeBlob(blobContext(ID1, FOO));
        assertKey(ID1, key1);

        // other blob we'll GC
        String key2 = bs.writeBlob(blobContext(ID2, "barbaz"));
        assertKey(ID2, key2);

        // GC start

        BinaryGarbageCollector gc = bs.getBinaryGarbageCollector();
        assertFalse(gc.isInProgress());
        gc.start(true);
        assertTrue(gc.isInProgress());
        // when caching, create a tmp/other file in the cache that shouldn't disappear during GC
        Path cacheTmp = null;
        Path cacheOther = null;
        PathStrategy cachePathStrategy = getCachePathStrategy();
        if (cachePathStrategy != null) {
            cacheTmp = cachePathStrategy.createTempFile();
            cacheOther = cachePathStrategy.getPathForKey(BlobUtils.randomString());
            Files.createFile(cacheOther);
        }
        gc.mark(key1);
        assertTrue(gc.isInProgress());

        // add a blob while GC is in progress; the write marks it, so it isn't collected
        String key4 = bs.writeBlob(blobContext(ID4, "xyzzy"));
        assertKey(ID4, key4);

        gc.stop(true);

        assertFalse(gc.isInProgress());
        BinaryManagerStatus status = gc.getStatus();
        // the blob added during GC isn't part of the candidate set computed at start, so it isn't counted
        assertEquals(1, status.getNumBinaries());
        assertEquals(1, status.getNumBinariesGC());
        if (checkSizeOfGCedFiles()) {
            assertEquals(3, status.getSizeBinaries());
            assertEquals(6, status.getSizeBinariesGC());
        }
        // check content, one blob gone
        assertBlob(key1, FOO);
        assertNoBlob(key2);
        // blob added during GC is still here
        assertBlob(key4, "xyzzy");

        // cache tmp/other is still here
        if (cacheTmp != null) {
            assertTrue(Files.exists(cacheTmp));
        }
        if (cacheOther != null) {
            assertTrue(Files.exists(cacheOther));
        }
    }

    @Test
    public void testGCRecovery() throws IOException {
        assumeFalse(bp.isTransactional(), "GC not tested in transactional blob store");

        String keptKey = bs.writeBlob(blobContext(ID1, FOO));
        String recoveredKey = bs.writeBlob(blobContext(ID2, BAR));
        BinaryGarbageCollector gc = bs.getBinaryGarbageCollector();

        gc.start(true);
        gc.mark(keptKey);
        gc.stop(true);
        assertBlob(keptKey, FOO);
        assertNoBlob(recoveredKey);

        // Recovery storage is excluded from a normal dry run.
        gc.start(false);
        gc.mark(keptKey);
        gc.stop(false);
        assertEquals(0, gc.getStatus().getNumBinariesGC());
        assertNoBlob(recoveredKey);

        AbstractBlobGarbageCollector.RECOVER_RECOVERY = true;
        gc.start(false);
        gc.mark(keptKey);
        gc.mark(recoveredKey);
        gc.stop(false);
        assertBlob(recoveredKey, BAR);
    }

    @Test
    public void testGCPermanentDeletionAndRecoveryCleanup() throws IOException {
        assumeFalse(bp.isTransactional(), "GC not tested in transactional blob store");

        String keptKey = bs.writeBlob(blobContext(ID1, FOO));
        String recoveredKey = bs.writeBlob(blobContext(ID2, BAR));
        BinaryGarbageCollector gc = bs.getBinaryGarbageCollector();

        gc.start(true);
        gc.mark(keptKey);
        gc.stop(true);
        assertNoBlob(recoveredKey);

        String deletedKey = bs.writeBlob(blobContext(ID4, "deleted"));
        AbstractBlobGarbageCollector.DELETE_WITH_RECOVERY = false;
        gc.start(true);
        gc.mark(keptKey);
        gc.stop(true);
        assertNoBlob(deletedKey);

        // Permanent deletion also removed the previous recovery storage.
        AbstractBlobGarbageCollector.RECOVER_RECOVERY = true;
        gc.start(false);
        gc.mark(keptKey);
        gc.stop(false);
        assertNoBlob(recoveredKey);
        assertNoBlob(deletedKey);
    }

    @Test
    public void testGCLeavesNothingBehind() throws IOException {
        assumeFalse(bp.isTransactional(), "GC not tested in transactional blob store");

        String keptKey = bs.writeBlob(blobContext(ID1, FOO));
        String deletedKey = bs.writeBlob(blobContext(ID2, BAR));
        // delete and write the same content again, so that a versioned backend ends up with two versions and a
        // delete marker for that key, all of which the garbage collection must remove too
        bs.deleteBlob(deletedKey);
        assertEquals(deletedKey, bs.writeBlob(blobContext(ID2, BAR)));
        assertLeftoversForKey(deletedKey, 2);
        BinaryGarbageCollector gc = bs.getBinaryGarbageCollector();

        AbstractBlobGarbageCollector.DELETE_WITH_RECOVERY = false;
        gc.start(true);
        gc.mark(keptKey);
        gc.stop(true);

        assertBlob(keptKey, FOO);
        assertNothingLeftForKey(deletedKey);
    }

}
