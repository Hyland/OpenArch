/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.blob.DocumentBlobService;
import com.hyland.openarch.ecm.core.blob.binary.BinaryManagerStatus;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

/**
 * Base record-mode document-level test for a cloud blob provider, shared by concrete provider implementations
 * (S3, Azure, ...).
 * <p>
 * The shared {@code MyFile} document type is provided by this bundle's {@code test-cloud-record-doctype.xml}
 * contribution. Concrete subclasses provide, through their {@code @Features} and {@code @Deploy}, a feature setting up
 * the backend and a record-mode configuration.
 * <p>
 * This test requires the backend to keep old versions of objects (bucket versioning / immutable storage), so a concrete
 * subclass whose backend does not support it must skip it.
 */
@ExtendWith(FeaturesExtension.class)
@Deploy("com.hyland.openarch.ecm.core.storage.binarymanager.common.tests:OSGI-INF/test-cloud-record-doctype.xml")
public abstract class TestCloudBlobStoreRecordWithDocument {

    protected static final String MY_FILE = "MyFile";

    @Inject
    protected CoreSession session;

    @Test
    public void testDoNotDeleteVersionedObject() {
        // Create a document with blob
        var blobContent = "A simple blob";
        var doc = session.createDocumentModel("/", "document", MY_FILE);
        doc.create();
        session.save(); // gives the doc an id, needed for blob storage

        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob(blobContent));

        // Update original doc's blob and create version
        blobContent = "Another simple blob";
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob(blobContent));
        doc.checkIn();

        // Trigger GC and assert no binaries was garbage collected.
        BinaryManagerStatus gcSt = Framework.getService(DocumentBlobService.class).garbageCollectBinaries(true);
        assertEquals(0, gcSt.getNumBinariesGC());
    }

}
