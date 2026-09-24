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
package com.hyland.openarch.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.GENERIC_DOCUMENT_SECURITY_ERROR;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.blob.BlobService;
import com.hyland.openarch.ecm.core.blob.ManagedBlob;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

/**
 * Tests of the record blob dispatcher.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-blob-dispatcher-record.xml")
public class TestBlobDispatcherRecord {

    private static final String GOVERNABLE_FILE = "GovernableFile";
    @Inject
    protected CoreSession session;

    @Inject
    protected BlobService blobService;

    @Test
    public void testDispatchRecord() throws Exception {
        String foo = "foo";
        String foo_test_key = "test:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

        // create a regular binary in the first blob provider
        DocumentModel doc = session.createDocumentModel("/", "doc", GOVERNABLE_FILE);
        Blob blob = Blobs.createBlob(foo, "text/plain");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();

        // check binary key
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        String key = ((ManagedBlob) blob).getKey();
        assertEquals(foo_test_key, key);

        // turn the blob into a record
        session.setRecord(doc.getRef(), true);

        // check that it was dispatched to the record blob provider
        doc.refresh();
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals("records1:" + doc.getId(), ((ManagedBlob) blob).getKey());

        // the blob cannot be changed if there is a legal hold
        session.setLegalHold(doc.getRef(), true);
        try {
            doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("bar"));
            fail();
        } catch (DocumentSecurityException e) {
            assertEquals(String.format(
                            GENERIC_DOCUMENT_SECURITY_ERROR,
                            SecurityConstants.WRITE, session.getPrincipal().getId(), doc.getId()),
                    e.getMessage());
        }

        // the blob cannot be deleted if there is a legal hold
        try {
            doc.setPropertyValue(SYSFILE_BLOB, null);
            fail();
        } catch (DocumentSecurityException e) {
            assertEquals(String.format(
                    GENERIC_DOCUMENT_SECURITY_ERROR,
                    SecurityConstants.WRITE, session.getPrincipal().getId(), doc.getId()),
                    e.getMessage());
        }
    }

    @Test
    public void testDispatchRecordCopy() throws Exception {
        String foo = "foo";
        String foo_test_key = "test:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

        // create a regular binary in the first blob provider
        DocumentModel doc = session.createDocumentModel("/", "doc", GOVERNABLE_FILE);
        Blob blob = Blobs.createBlob(foo, "text/plain");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();

        // check binary key
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo_test_key, ((ManagedBlob) blob).getKey());

        // turn the blob into a record
        session.setRecord(doc.getRef(), true);

        // check that it was dispatched to the record blob provider
        doc.refresh();
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals("records1:" + doc.getId(), ((ManagedBlob) blob).getKey());

        // copy the doc
        session.copy(doc.getRef(), new PathRef("/"), "doccopy");

        // check that the copied document is not a record anymore
        DocumentModel docCopy = session.getDocument(new PathRef("/doccopy"));
        assertFalse(docCopy.isRecord());
        // and its blob is in the regular blob provider
        Blob blobCopy = (Blob) docCopy.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo_test_key, ((ManagedBlob) blobCopy).getKey());
    }

    @Test
    public void testDispatchRecordCopyDeep() throws Exception {
        String foo = "foo";
        String foo_test_key = "test:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

        // create a folder to be copied
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();

        // create a regular binary in the first blob provider
        DocumentModel doc = session.createDocumentModel("/folder", "doc", GOVERNABLE_FILE);
        Blob blob = Blobs.createBlob(foo, "text/plain");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();

        // check binary key
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo_test_key, ((ManagedBlob) blob).getKey());

        // turn the blob into a record
        session.setRecord(doc.getRef(), true);

        // check that it was dispatched to the record blob provider
        doc.refresh();
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals("records1:" + doc.getId(), ((ManagedBlob) blob).getKey());

        // copy the folder
        session.copy(folder.getRef(), new PathRef("/"), "foldercopy");

        // check that the copied document is not a record anymore
        DocumentModel docCopy = session.getDocument(new PathRef("/foldercopy/doc"));
        assertFalse(docCopy.isRecord());
        // and its blob is in the regular blob provider
        Blob blobCopy = (Blob) docCopy.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo_test_key, ((ManagedBlob) blobCopy).getKey());
    }

    /**
     * Make the version a record, and check that when we restore it the live doc isn't a record.
     */
    @Test
    @Disabled("NXP-28645 but keep test for when versions can be made records")
    public void testDispatchRestoreVersionWhichIsRecord() throws Exception {
        String foo = "foo";
        String foo_test_key = "test:acbd18db4cc2f85cedef654fccc4a4d8";

        // create a regular binary in the first blob provider
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        Blob blob = Blobs.createBlob(foo, "text/plain");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();

        // check binary key
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo_test_key, ((ManagedBlob) blob).getKey());

        // check in the version
        DocumentRef verRef = session.checkIn(doc.getRef());

        // turn the version into a record
        session.setRecord(verRef, true);

        // check that it was dispatched to the record blob provider
        DocumentModel ver = session.getDocument(verRef);
        blob = (Blob) ver.getPropertyValue(SYSFILE_BLOB);
        assertEquals("records1:" + ver.getId(), ((ManagedBlob) blob).getKey());

        // restore the version
        session.restoreVersion(verRef);

        // check that the copied version is not a record anymore
        doc.refresh();
        assertFalse(doc.isRecord());
        // and its blob is in the regular blob provider
        Blob blobCopy = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo_test_key, ((ManagedBlob) blobCopy).getKey());
    }

    /**
     * Slightly different from above: here the live doc is a record, and we restore a non-record version on top of it.
     */
    @Test
    @Disabled("NXP-28645 but keep test for when versions can be made records")
    public void testDispatchRestoreVersionOnTopOfRecord() throws Exception {
        String foo = "foo";
        String foo_test_key = "test:acbd18db4cc2f85cedef654fccc4a4d8";

        // create a regular binary in the first blob provider
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        Blob blob = Blobs.createBlob(foo, "text/plain");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();

        // check binary key
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo_test_key, ((ManagedBlob) blob).getKey());

        // check in the version
        DocumentRef verRef = session.checkIn(doc.getRef());

        // turn the live doc into a record
        session.setRecord(doc.getRef(), true);

        // check that it was dispatched to the record blob provider
        doc.refresh();
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals("records1:" + doc.getId(), ((ManagedBlob) blob).getKey());

        // restore the version
        session.restoreVersion(verRef);

        // check that the live doc (now a copy of the version) is not a record anymore
        doc.refresh();
        assertFalse(doc.isRecord());
        // and its blob is in the regular blob provider
        Blob blobCopy = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo_test_key, ((ManagedBlob) blobCopy).getKey());
    }

}
