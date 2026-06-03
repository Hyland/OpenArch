/*
 * (C) Copyright 2015-2017 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.blob;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.test.runner.LogFeature;

@ExtendWith(FeaturesExtension.class)
@Features({ BlobManagerFeature.class, LogFeature.class, LogCaptureFeature.class })
@Deploy("org.nuxeo.ecm.core.api.tests:OSGI-INF/test-fs-blobprovider.xml")
public class TestFilesystemBlobProvider {

    private static final String CONTENT = "hello";

    private static final String CONTENT_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    private static final String PROVIDER_ID = "testfs";

    @Inject
    protected BlobService blobService;

    protected Path tmpFile;

    protected String tmpFilePath;

    @BeforeEach
    public void setUp() throws IOException {
        tmpFile = Framework.createTempFilePath("tmp", ".txt");
        try (OutputStream out = Files.newOutputStream(tmpFile)) {
            InputStream in = new ByteArrayInputStream(CONTENT.getBytes());
            in.transferTo(out);
        }
        tmpFilePath = tmpFile.toString();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tmpFile != null && Files.exists(tmpFile)) {
            Files.delete(tmpFile);
        }
    }

    @Test
    public void testSupportsUserUpdate() throws Exception {
        BlobProvider blobProvider = blobService.getBlobProvider(PROVIDER_ID);
        assertFalse(blobProvider.supportsUserUpdate());
        assertFalse(blobProvider.supportsSync());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.api.tests:OSGI-INF/test-fs-blobprovider-override.xml")
    public void testSupportsUserUpdateOverride() throws Exception {
        BlobProvider blobProvider = blobService.getBlobProvider(PROVIDER_ID);
        assertTrue(blobProvider.supportsUserUpdate());
        assertTrue(blobProvider.supportsSync());
    }

    @Test
    public void testRead() throws IOException {
        String key = PROVIDER_ID + ":" + tmpFilePath;

        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey(key);
        BlobProvider blobProvider = blobService.getBlobProvider(PROVIDER_ID);
        ManagedBlob blob = (ManagedBlob) blobProvider.readBlob(blobInfo);
        assertNotNull(blob);
        assertEquals(key, blob.getKey());
        try (InputStream in = blob.getStream()) {
            assertEquals(CONTENT, IOUtils.toString(in, UTF_8));
        }

        // same with explicit blob
        blobInfo.setKey(tmpFilePath);
        blobInfo.setMimeType("text/plain");
        blob = ((FilesystemBlobProvider) blobProvider).createBlob(blobInfo);
        assertEquals(key, blob.getKey());
        assertEquals(tmpFile.getFileName().toString(), blob.getFilename());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals(CONTENT.length(), blob.getLength());
        assertEquals(CONTENT_SHA256, blob.getDigest());
        try (InputStream in = blob.getStream()) {
            assertEquals(CONTENT, IOUtils.toString(in, UTF_8));
        }
    }

    @Test
    public void testReadNotFound() throws IOException {
        String path = "/NO_SUCH_FILE_EXISTS";
        assertFalse(Files.exists(Paths.get(path)));
        String key = PROVIDER_ID + ":" + path;

        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey(key);
        BlobProvider blobProvider = blobService.getBlobProvider(PROVIDER_ID);
        ManagedBlob blob = (ManagedBlob) blobProvider.readBlob(blobInfo);
        try (InputStream in = blob.getStream()) {
            fail("Should have raised NoSuchFileException");
        } catch (NoSuchFileException e) {
            assertEquals(path, e.getMessage());
        }
    }

    @Test
    public void testRoot() throws IOException {
        String PROVIDER_ID2 = "testfs2";
        BlobProviderDescriptor descr = new BlobProviderDescriptor();
        descr.klass = FilesystemBlobProvider.class;
        descr.name = PROVIDER_ID2;
        descr.properties = Collections.singletonMap(FilesystemBlobProvider.ROOT_PROP, tmpFile.getParent().toString());
        ((BlobServiceImpl) blobService).registerBlobProvider(descr);
        try {
            // use relative path under root
            String key = PROVIDER_ID2 + ":" + tmpFile.getFileName().toString();
            BlobInfo blobInfo = new BlobInfo();
            blobInfo.setKey(key);
            BlobProvider blobProvider = blobService.getBlobProvider(PROVIDER_ID);
            ManagedBlob blob = (ManagedBlob) blobProvider.readBlob(blobInfo);
            assertNotNull(blob);
            assertEquals(key, blob.getKey());
            try (InputStream in = blob.getStream()) {
                assertEquals(CONTENT, IOUtils.toString(in, UTF_8));
            }
        } finally {
            ((BlobServiceImpl) blobService).unregisterBlobProvider(descr);
        }
    }

    @Test
    public void testIllegalPath() throws IOException {
        String illegalPath = "../foo";

        String PROVIDER_ID2 = "testfs2";
        BlobProviderDescriptor descr = new BlobProviderDescriptor();
        descr.klass = FilesystemBlobProvider.class;
        descr.name = PROVIDER_ID2;
        descr.properties = Collections.singletonMap(FilesystemBlobProvider.ROOT_PROP, tmpFile.getParent().toString());
        ((BlobServiceImpl) blobService).registerBlobProvider(descr);
        try {
            String key = PROVIDER_ID2 + ":" + illegalPath;
            BlobInfo blobInfo = new BlobInfo();
            blobInfo.setKey(key);
            BlobProvider blobProvider = blobService.getBlobProvider(PROVIDER_ID);
            ManagedBlob blob = (ManagedBlob) blobProvider.readBlob(blobInfo);
            assertNotNull(blob);
            assertEquals(key, blob.getKey());
            try (InputStream in = blob.getStream()) {
                fail("Should have raised FileNotFoundException");
            } catch (FileNotFoundException e) {
                // ok
                assertEquals("Illegal path: ../foo", e.getMessage());
            }
        } finally {
            ((BlobServiceImpl) blobService).unregisterBlobProvider(descr);
        }
    }

}
