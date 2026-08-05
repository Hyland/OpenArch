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
package org.nuxeo.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.io.IOException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.DocumentBlobService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.binary.BinaryManagerStatus;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/disable-schedulers.xml")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-blob-gc-same-storage.xml")
public class TestBlobGCSharedStorage {

    protected static final String CONTENT1 = "hello world";

    protected static final String CONTENT2 = "bonjour le monde";

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    @Inject
    protected DocumentBlobService documentBlobService;

    @Test
    public void testGCSharedStorage() throws IOException {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.setPropertyValue("mydc_source", "first");
        doc1.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob(CONTENT1));
        doc1.create();
        DocumentModel doc2 = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc2.setPropertyValue("mydc_source", "second");
        doc2.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob(CONTENT2));
        doc2.create();
        session.save();

        // check that the two blobs were dispatched to different blob providers
        ManagedBlob blob1 = (ManagedBlob) doc1.getPropertyValue(SYSFILE_BLOB);
        ManagedBlob blob2 = (ManagedBlob) doc2.getPropertyValue(SYSFILE_BLOB);
        assertTrue(blob1.getKey().startsWith("first:"), blob1.getKey());
        assertTrue(blob2.getKey().startsWith("second:"), blob2.getKey());

        // check that when the GC runs nothing gets deleted
        sleepBeforeGC();
        BinaryManagerStatus status = documentBlobService.garbageCollectBinaries(true);
        assertEquals(4, status.getNumBinaries()); // two binaries seen twice
        assertEquals(0, status.getNumBinariesGC()); // nothing deleted

        // check that the blobs are still here
        blob1 = (ManagedBlob) doc1.getPropertyValue(SYSFILE_BLOB);
        assertEquals(CONTENT1, blob1.getString());
        blob2 = (ManagedBlob) doc2.getPropertyValue(SYSFILE_BLOB);
        assertEquals(CONTENT2, blob2.getString());
    }

    // sleep before GC to pass its time threshold
    protected void sleepBeforeGC() {
        try {
            Thread.sleep(3_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NuxeoException(e);
        }
    }

}
