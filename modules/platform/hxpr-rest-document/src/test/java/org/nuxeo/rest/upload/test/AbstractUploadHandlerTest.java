/*
 * (C) Copyright 2015-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thierry Delprat <tdelprat@nuxeo.com>
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.upload.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.UTF_8;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.transientstore.TransientStorageComponent;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreProvider;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.rest.upload.Upload;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.login.LoginService;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;

@Features(UploadFeature.class)
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-store-contrib.xml")
public abstract class AbstractUploadHandlerTest extends RuntimeSpringBootTest {

    protected static final String USER = TEST_ADMIN_USER;

    protected static final String ALT_USER = "myOtherUser";

    protected static final NuxeoPrincipal ALT_PRINCIPAL = DummyLoginFeature.getTestPrincipal(ALT_USER, false);

    @Autowired
    protected UploadHandler handler;

    @RuntimeInject
    LoginService loginService;

    @BeforeEach
    public void checkCurrentUser() {
        NuxeoPrincipal pal = NuxeoPrincipal.getCurrent();
        assertNotNull(pal);
        assertEquals(USER, pal.getId());
    }

    @AfterEach
    public void cleanupTransientStore() {
        ((TransientStorageComponent) Framework.getService(TransientStoreService.class)).cleanUpStores();
    }

    @Test
    public void testUploadInit() {
        Upload upload = handler.newUpload();
        assertNotNull(upload);
        String uploadId = upload.getId();
        assertNotNull(uploadId);
        upload = handler.getUpload(uploadId);
        assertNotNull(upload);
        assertEquals(uploadId, upload.getId());
        assertFalse(upload.hasContent());

        // Check TransientStore storage size
        TransientStore ts = handler.getTransientStore();
        TransientStoreProvider tsm = (TransientStoreProvider) ts;
        assertEquals(0, tsm.getStorageSize());
    }

    @Test
    public void testUploadGet() {
        Upload upload = handler.newUpload();
        assertNotNull(upload);
        String uploadId = upload.getId();
        assertNotNull(uploadId);
        upload = handler.getUpload(uploadId);
        assertNotNull(upload);
        assertEquals(uploadId, upload.getId());

        // check upload security
        try (var __ = loginService.loginPrincipal(ALT_PRINCIPAL)) {
            upload = handler.getUpload(uploadId);
            fail("Should have thrown DocumentSecurityException");
        } catch (DocumentSecurityException e) {
            assertEquals(String.format("User '%s' does not match upload '%s'", ALT_PRINCIPAL.getId(), uploadId),
                    e.getMessage());
        }
    }

    @Test
    public void testAddFileStream() throws IOException {
        Upload upload = handler.newUpload();
        String uploadId = upload.getId();
        Blob blob = Blobs.createBlob("Contenu accentué");
        blob.setFilename("Mon doc 1.txt");
        blob.setMimeType(TEXT_PLAIN);
        upload.addBlob(blob);
        assertTrue(upload.hasContent());

        // Check blob
        Blob blob1 = upload.getBlob();
        assertNotNull(blob1);
        assertEquals("Mon doc 1.txt", blob1.getFilename());
        assertEquals(TEXT_PLAIN, blob1.getMimeType());
        assertEquals("Contenu accentué", blob1.getString());

        // retrieve upload again to check store
        upload = handler.getUpload(uploadId);
        assertNotNull(upload);
        assertEquals(uploadId, upload.getId());
        assertTrue(upload.hasContent());
        assertEquals(blob1, upload.getBlob());
        assertEquals("Mon doc 1.txt", upload.getFileName());
        assertEquals(TEXT_PLAIN, upload.getMimeType());
        assertEquals(UTF_8, upload.getEncoding());
        assertEquals(17, upload.getFileSize());
        assertFalse(upload.isChunked());
        assertTrue(upload.getChunks().isEmpty());
        assertNull(upload.getChunkIndexes());
        assertNull(upload.getChunkCount());

        // Check TransientStore storage size
        TransientStore ts = handler.getTransientStore();
        TransientStoreProvider tsm = (TransientStoreProvider) ts;
        assertEquals(17, tsm.getStorageSize());

        // Clean upload
        upload.clean();
        assertEquals(0, tsm.getStorageSize());
    }

    @Test
    public void testAddChunkStream() throws IOException {
        // Add 3 chunk streams in disorder
        Upload upload = handler.newUpload();
        String uploadId = upload.getId();

        String fileContent = "Contenu accentué composé de 3 chunks";
        String chunk1 = "Contenu accentu";
        String chunk2 = "é composé de ";
        String chunk3 = "3 chunks";
        long fileSize = fileContent.getBytes().length;
        Blob blob1 = Blobs.createBlob(chunk1);
        Blob blob2 = Blobs.createBlob(chunk2);
        Blob blob3 = Blobs.createBlob(chunk3);
        assertEquals(0, upload.getPartialFileSize());
        upload.addChunk(blob1, "Mon doc.txt", TEXT_PLAIN, UTF_8, 3, 0);
        assertEquals(blob1.getLength(), upload.getPartialFileSize());
        upload.addChunk(blob3, "Mon doc.txt", TEXT_PLAIN, UTF_8, 3, 2);
        assertEquals(blob1.getLength() + blob3.getLength(), upload.getPartialFileSize());
        upload.addChunk(blob2, "Mon doc.txt", TEXT_PLAIN, UTF_8, 3, 1);
        assertEquals(blob1.getLength() + blob2.getLength() + blob3.getLength(), upload.getPartialFileSize());
        assertEquals(upload.getPartialFileSize(), upload.getFileSize());

        assertTrue(upload.isChunked());
        assertTrue(upload.hasContent());

        // Check blob
        Blob blob = upload.getBlob();
        assertNotNull(blob);
        assertEquals("Mon doc.txt", blob.getFilename());
        assertEquals(TEXT_PLAIN, blob.getMimeType());
        assertEquals(fileContent, blob.getString());

        // retrieve upload again to check store
        upload = handler.getUpload(uploadId);
        assertNotNull(upload);
        assertEquals(uploadId, upload.getId());
        assertTrue(upload.hasContent());
        assertEquals(blob, upload.getBlob());
        assertTrue(upload.isChunked());
        assertEquals("Mon doc.txt", upload.getFileName());
        assertEquals(TEXT_PLAIN, upload.getMimeType());
        assertEquals(UTF_8, upload.getEncoding());
        assertEquals(fileSize, upload.getFileSize());
        assertEquals((Integer) 3, upload.getChunkCount());
        assertEquals(Arrays.asList(0, 1, 2), upload.getChunkIndexes());
        assertEquals(blob, upload.getBlob());

        // check chunks
        assertNotNull(upload.getChunks());
        assertEquals((Integer) 3, upload.getChunkCount());
        assertEquals(List.of(0, 1, 2), upload.getChunkIndexes());

        // Check transient store
        TransientStore ts = handler.getTransientStore();
        TransientStoreProvider tsm = (TransientStoreProvider) ts;
        assertEquals(76, tsm.getStorageSize());

        String chunkEntryKey1 = uploadId + "_0";
        List<Blob> chunkEntryBlobs = ts.getBlobs(chunkEntryKey1);
        assertEquals(1, chunkEntryBlobs.size());
        blob1 = chunkEntryBlobs.get(0);
        assertEquals(chunk1, blob1.getString());
        assertEquals(15, blob1.getLength());

        String chunkEntryKey2 = uploadId + "_1";
        chunkEntryBlobs = ts.getBlobs(chunkEntryKey2);
        assertEquals(1, chunkEntryBlobs.size());
        blob2 = chunkEntryBlobs.get(0);
        assertEquals(chunk2, blob2.getString());
        assertEquals(15, blob2.getLength());

        String chunkEntryKey3 = uploadId + "_2";
        chunkEntryBlobs = ts.getBlobs(chunkEntryKey3);
        assertEquals(1, chunkEntryBlobs.size());
        blob3 = chunkEntryBlobs.get(0);
        assertEquals(chunk3, blob3.getString());
        assertEquals(8, blob3.getLength());

        // Clean upload
        upload.clean();
        assertEquals(0, tsm.getStorageSize());
    }

    @Test
    public void testUploadCleanupNoChunk() throws IOException {
        Upload upload = handler.newUpload();
        String uploadId = upload.getId();
        assertNotNull(uploadId);

        // Add non chunked file
        upload.addBlob(Blobs.createBlob("SomeContent", TEXT_PLAIN, UTF_8, "file.txt"));

        Blob blob = upload.getBlob();
        assertNotNull(blob);
        assertEquals("file.txt", blob.getFilename());

        // Upload data
        TransientStore ts = handler.getTransientStore();
        TransientStoreProvider tsm = (TransientStoreProvider) ts;
        assertTrue(ts.exists(uploadId));

        // Upload non chunked file
        assertTrue(blob instanceof FileBlob);
        FileBlob fileBlob = (FileBlob) blob;
        File tmpFile = fileBlob.getFile();
        assertNotNull(tmpFile);
        assertTrue(tmpFile.exists());

        upload.clean();
        // Upload data has been removed from cache as well as temporary file
        assertFalse(ts.exists(uploadId));
        assertFalse(new File(tmpFile.getPath()).exists());
        assertEquals(0, tsm.getStorageSize());
    }

    @Test
    public void testUploadCleanupChunk() throws IOException {
        Upload upload = handler.newUpload();
        String uploadId = upload.getId();
        assertNotNull(uploadId);

        // Add chunked file
        upload.addChunk(Blobs.createBlob("Chunk 1 "), "chunkedFile.txt", TEXT_PLAIN, UTF_8, 2, 0);
        upload.addChunk(Blobs.createBlob("Chunk 2 "), "chunkedFile.txt", TEXT_PLAIN, UTF_8, 2, 1);

        Blob blob = upload.getBlob();
        assertNotNull(blob);
        assertEquals("Chunk 1 Chunk 2 ", blob.getString());

        // Upload data
        TransientStore ts = handler.getTransientStore();
        TransientStoreProvider tsm = (TransientStoreProvider) ts;
        assertTrue(ts.exists(uploadId));
        assertTrue(ts.exists(uploadId + "_0"));
        assertTrue(ts.exists(uploadId + "_1"));

        // Upload chunked file
        assertTrue(blob instanceof FileBlob);
        FileBlob chunkedFileBlob = (FileBlob) blob;
        File tmpChunkedFile = chunkedFileBlob.getFile();
        assertNotNull(tmpChunkedFile);
        assertTrue(tmpChunkedFile.exists());

        upload.clean();

        // Upload data has been removed from cache as well as temporary chunked file
        assertFalse(ts.exists(uploadId));
        assertFalse(ts.exists(uploadId + "_0"));
        assertFalse(ts.exists(uploadId + "_1"));
        assertFalse(tmpChunkedFile.exists());
        assertEquals(0, tsm.getStorageSize());
    }

    @Test
    public void testReleaseBlobsNoChunk() throws IOException {
        Upload upload = handler.newUpload();
        String uploadId = upload.getId();
        upload.addBlob(Blobs.createBlob("SomeContent", TEXT_PLAIN, UTF_8, "file.txt"));

        assertNotNull(upload.getBlob());
        TransientStore ts = handler.getTransientStore();
        TransientStoreProvider tsm = (TransientStoreProvider) ts;
        assertTrue(ts.exists(uploadId));
        assertFalse(upload.isReleased());

        upload.releaseBlobs();

        // Upload entry is preserved so its attach status can still be polled
        assertTrue(ts.exists(uploadId));
        assertTrue(upload.isReleased());
        // ...but the blob has been released (size accounting goes back to 0; the actual on-disk
        // file is reclaimed by the blob provider's GC in production, or by the next remove(id))
        assertEquals(0, tsm.getStorageSize());
        // getBlob() must not try to re-create or re-store anything
        assertNull(upload.getBlob());
        assertEquals(0, tsm.getStorageSize());
        // re-reading the upload from the store sees the released state
        Upload reloaded = handler.getUpload(uploadId);
        assertNotNull(reloaded);
        assertTrue(reloaded.isReleased());
        assertNull(reloaded.getBlob());
    }

    @Test
    public void testReleaseBlobsChunk() throws IOException {
        Upload upload = handler.newUpload();
        String uploadId = upload.getId();
        upload.addChunk(Blobs.createBlob("Chunk 1 "), "chunkedFile.txt", TEXT_PLAIN, UTF_8, 2, 0);
        upload.addChunk(Blobs.createBlob("Chunk 2 "), "chunkedFile.txt", TEXT_PLAIN, UTF_8, 2, 1);

        assertNotNull(upload.getBlob());
        TransientStore ts = handler.getTransientStore();
        TransientStoreProvider tsm = (TransientStoreProvider) ts;
        assertTrue(ts.exists(uploadId));
        assertTrue(ts.exists(uploadId + "_0"));
        assertTrue(ts.exists(uploadId + "_1"));
        assertFalse(upload.isReleased());

        upload.releaseBlobs();

        // Upload entry is preserved so its attach status can still be polled
        assertTrue(ts.exists(uploadId));
        assertTrue(upload.isReleased());
        // ...but the chunk entries are gone (including their on-disk files) and the size
        // accounting goes back to 0 (the assembled blob's file is reclaimed by the blob
        // provider's GC in production, or by the next remove(id))
        assertFalse(ts.exists(uploadId + "_0"));
        assertFalse(ts.exists(uploadId + "_1"));
        assertEquals(0, tsm.getStorageSize());
        // getBlob() must short-circuit; in particular it must NOT rebuild and re-store an empty
        // assembled blob from the (now missing) chunks
        assertNull(upload.getBlob());
        assertEquals(0, tsm.getStorageSize());
        // Chunked metadata (filename/mime/encoding/chunkIndexes) is preserved so polling clients
        // still see the original upload info alongside the attach status
        Upload reloaded = handler.getUpload(uploadId);
        assertNotNull(reloaded);
        assertTrue(reloaded.isReleased());
        assertTrue(reloaded.isChunked());
        assertEquals("chunkedFile.txt", reloaded.getFileName());
        assertEquals(TEXT_PLAIN, reloaded.getMimeType());
        assertEquals(UTF_8, reloaded.getEncoding());
        assertEquals((Integer) 2, reloaded.getChunkCount());
        assertEquals(List.of(0, 1), reloaded.getChunkIndexes());
        assertNull(reloaded.getBlob());
        assertEquals(0, tsm.getStorageSize());
    }

    @Test
    public void testUploadConcurrency() throws Exception {
        // Initialize uploads with one file concurrently
        int nbUploads = 100;
        String[] uploadIds = new String[nbUploads];
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(5, 5, 500L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(nbUploads + 1));

        NuxeoPrincipal testUser = NuxeoPrincipal.getCurrent();
        for (int i = 0; i < nbUploads; i++) {
            final int uploadIndex = i;
            tpe.submit(() -> {
                try (var __ = loginService.loginPrincipal(testUser)) {
                    Upload upload = handler.newUpload();
                    String uploadId = upload.getId();
                    upload.addBlob(Blobs.createBlob("SomeContent_" + uploadId, TEXT_PLAIN, UTF_8, "MyUploadFile.txt"));
                    uploadIds[uploadIndex] = uploadId;
                }
            });
        }
        tpe.shutdown();
        boolean finish = tpe.awaitTermination(20, TimeUnit.SECONDS);
        assertTrue(finish, "timeout");

        // Check uploads
        for (String upload : uploadIds) {
            assertNotNull(upload);
        }
        // Test indexes 0, 9, 99, ..., nbFiles - 1
        int nbDigits = (int) (Math.log10(nbUploads) + 1);
        int divisor = nbUploads;
        for (int i = 0; i < nbDigits; i++) {
            int index = nbUploads / divisor - 1;
            String uploadId = uploadIds[index];
            Blob blob = handler.getUpload(uploadId).getBlob();
            assertNotNull(blob);
            assertEquals("MyUploadFile.txt", blob.getFilename());
            assertEquals("SomeContent_" + uploadId, blob.getString());
            divisor = divisor / 10;
        }

        // Check storage size
        TransientStore ts = handler.getTransientStore();
        TransientStoreProvider tsm = (TransientStoreProvider) ts;
        assertTrue(tsm.getStorageSize() > 12 * nbUploads);

        // Clean uploads
        for (String uploadId : uploadIds) {
            handler.getUpload(uploadId).clean();
        }
        assertEquals(0, tsm.getStorageSize());
    }

    @Test
    public void testChunkConcurrency() throws Exception {
        // Initialize an upload
        Upload upload = handler.newUpload();
        String uploadId = upload.getId();
        assertNotNull(uploadId);

        // Add chunks concurrently
        int nbChunks = 5;
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(5, 5, 500L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(nbChunks + 1));

        for (int i = 0; i < nbChunks; i++) {
            final int chunkIndex = i;
            tpe.submit(() -> {
                upload.addChunk(Blobs.createBlob("SomeChunkContent_" + chunkIndex + " "), "MyChunkedFile.txt",
                        TEXT_PLAIN, UTF_8, nbChunks, chunkIndex);
            });
        }
        tpe.shutdown();
        boolean finish = tpe.awaitTermination(20, TimeUnit.SECONDS);
        assertTrue(finish, "timeout");

        // Check chunked file
        assertTrue(upload.isChunked());
        Blob blob = upload.getBlob();
        assertNotNull(blob);
        int nbOccurrences = 0;
        Pattern p = Pattern.compile("SomeChunkContent_");
        Matcher m = p.matcher(blob.getString());
        while (m.find()) {
            nbOccurrences++;
        }
        assertEquals(nbChunks, nbOccurrences);

        // Check storage size
        TransientStore ts = handler.getTransientStore();
        TransientStoreProvider tsm = (TransientStoreProvider) ts;
        assertTrue(tsm.getStorageSize() > 17 * nbChunks);

        // Clean upload
        upload.clean();
        assertEquals(0, tsm.getStorageSize());
    }

}
