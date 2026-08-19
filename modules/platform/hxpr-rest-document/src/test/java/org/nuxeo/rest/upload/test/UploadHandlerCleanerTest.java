/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.upload.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.UTF_8;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.transientstore.TransientStorageComponent;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.rest.upload.Upload;
import org.nuxeo.rest.upload.UploadHandlerCleaner;
import org.nuxeo.rest.upload.handler.DefaultUploadHandler;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Unit tests for {@link UploadHandlerCleaner}, which frees the blob storage of the uploads a request consumed once
 * that request's transaction has committed.
 */
@Features(UploadFeature.class)
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-store-contrib.xml")
@SpringBootTest(classes = { DefaultUploadHandler.class, UploadHandlerCleaner.class })
public class UploadHandlerCleanerTest extends RuntimeSpringBootTest {

    @Autowired
    protected UploadHandler handler;

    @Autowired
    protected UploadHandlerCleaner cleaner;

    @AfterEach
    public void cleanupTransientStore() {
        ((TransientStorageComponent) Framework.getService(TransientStoreService.class)).cleanUpStores();
    }

    @Test
    public void testBlobsAreReleasedOnceTheTransactionCommits() {
        String uploadId = newUpload();
        assertTrue(TransactionHelper.isTransactionActive());

        cleaner.add(uploadId);
        // still whole while the transaction is running: the document it is attached to isn't committed yet
        assertEquals(Set.of(uploadId), cleaner.get());
        assertFalse(handler.getUpload(uploadId).isReleased());

        endTransaction();

        assertTrue(handler.getUpload(uploadId).isReleased());
        assertEquals(Set.of(), cleaner.get());
    }

    @Test
    public void testBlobsSurviveARolledBackTransaction() {
        String uploadId = newUpload();

        cleaner.add(uploadId);
        TransactionHelper.setTransactionRollbackOnly();
        endTransaction();

        // the document was never created, so the upload must stay whole and remain attachable
        assertFalse(handler.getUpload(uploadId).isReleased());
        assertEquals(Set.of(), cleaner.get());
    }

    @Test
    public void testTrackingIsDroppedFromThePooledThread() {
        cleaner.add(newUpload());
        endTransaction();

        // whatever the outcome, nothing is left for the next request running on this thread to release
        assertEquals(Set.of(), cleaner.get());
    }

    protected String newUpload() {
        Upload upload = handler.newUpload();
        upload.addBlob(Blobs.createBlob("test content", TEXT_PLAIN, UTF_8, "test.txt"));
        return upload.getId();
    }

    /** Ends the transaction the test feature started, and opens a fresh one for the assertions and the teardown. */
    protected void endTransaction() {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

}
