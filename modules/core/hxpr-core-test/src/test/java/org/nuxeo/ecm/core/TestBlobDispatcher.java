/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobService;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.storage.dbs.DBSDocument;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

/**
 * Sample test showing how to use a direct access to the binaries storage.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-blob-dispatcher.xml")
public class TestBlobDispatcher {

    @Inject
    protected CoreSession session;

    @Inject
    protected BlobService blobService;

    @Test
    public void testDirectBlob() throws Exception {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        DocumentModel doc2 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();
        doc2.create();

        Blob blob1 = Blobs.createBlob("foo", "text/plain");
        doc1.setPropertyValue(SYSFILE_BLOB, blob1);

        Blob blob2 = Blobs.createBlob("bar", "video/mp4");
        doc2.setPropertyValue(SYSFILE_BLOB, blob2);

        // re-fetch
        doc1 = session.getDocument(doc1.getRef());
        doc2 = session.getDocument(doc2.getRef());

        blob1 = (Blob) doc1.getPropertyValue(SYSFILE_BLOB);
        try (InputStream in = blob1.getStream()) {
            assertEquals("foo", IOUtils.toString(in, UTF_8));
        }
        assertEquals("test", ((ManagedBlob) blob1).getProviderId());

        blob2 = (Blob) doc2.getPropertyValue(SYSFILE_BLOB);
        try (InputStream in = blob2.getStream()) {
            assertEquals("bar", IOUtils.toString(in, UTF_8));
        }
        assertEquals("test2", ((ManagedBlob) blob2).getProviderId());
    }

    @Test
    public void testAlreadyManagedBlob() throws Exception {
        // register blob in provider by hand
        Blob b = Blobs.createBlob("foo", "video/mp4");
        String key = blobService.getBlobProvider("dummy").writeBlob(b);
        key = "dummy:" + key;

        // create a blob already managed and not corresponding to a dispatch target
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey(key);
        blobInfo.setMimeType("video/mp4");
        Blob blob = new SimpleManagedBlob(blobInfo);
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();

        // check that it wasn't dispatched even though the metadata would suggest it
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertTrue(blob instanceof SimpleManagedBlob, blob.getClass().getName());
        String key2 = ((ManagedBlob) blob).getKey();
        assertEquals(key, key2);
    }

    @Test
    public void testAlreadyManagedBlobButTransient() throws Exception {
        // write Java blob to provider
        Blob b = Blobs.createBlob("foo", "video/mp4");
        BlobProvider blobProvider = blobService.getBlobProvider("transient");
        String key = blobProvider.writeBlob(b);
        // re-read the now managed blob
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey(key);
        Blob blob = blobProvider.readBlob(blobInfo);
        assertEquals("transient", ((ManagedBlob) blob).getProviderId());

        // save the transient blob in a document
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();

        // check that it was dispatched and is now stored in a non-transient blob provider
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals("test", ((ManagedBlob) blob).getProviderId());
    }

    @Test
    public void testSwitchDispatch() throws Exception {
        String foo = "foo";
        String foo_test_key = "test:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";
        String foo2_test_key = "test2:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

        // create a regular binary in the first blob provider
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        Blob blob = Blobs.createBlob(foo, "text/plain");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();

        // check binary key
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        String key = ((ManagedBlob) blob).getKey();
        assertEquals(foo_test_key, key);

        // update the blob MIME type to change the dispatch target
        blob.setMimeType("video/mp4");
        doc.setPropertyValue(SYSFILE_BLOB, blob);

        // check that it was dispatched on save to the second blob provider
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo2_test_key, ((ManagedBlob) blob).getKey());
    }

    @Disabled("TODO DISABLED reimplement redispatch on change later")
    @Test
    public void testSwitchDispatchOnChange() throws Exception {
        String foo = "foo";
        String foo_test_key = "test:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";
        String foo2_test_key = "test2:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

        // create a regular binary in the first blob provider
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        Blob blob = Blobs.createBlob(foo, "text/plain");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();

        // check binary key
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        String key = ((ManagedBlob) blob).getKey();
        assertEquals(foo_test_key, key);

        // update the title to change the dispatch target
        doc.setPropertyValue(SYS_TITLE, "video");

        // check that it was dispatched on save to the second blob provider
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals(foo2_test_key, ((ManagedBlob) blob).getKey());
    }

    @Test
    public void testDispatchXPath() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();

        Blob blob = Blobs.createBlob("foo", "text/plain");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        // check binary key
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        String key = ((ManagedBlob) blob).getKey();
        assertTrue(key.startsWith("test:"), key);

        // files/0/file gets stored in the second blob provider
        blob = Blobs.createBlob("bar", "text/plain");
        doc.setPropertyValue("sysfiles_blobs", List.of(Map.of("blob", blob)));
        // check binary key
        blob = (Blob) doc.getPropertyValue("sysfiles_blobs/0/blob");
        key = ((ManagedBlob) blob).getKey();
        assertTrue(key.startsWith("test2:"), key);
    }

    // same with low-level Document API
    // (has a different code path to compute the xpath)
    @Test
    public void testDispatchXPathOnDocument() throws Exception {
        InternalSession internalSession = ((SessionImpl) session).getSession();
        InternalDocument root = internalSession.getRootDocument();
        DBSDocument doc = DBSDocument.newUnCreatedDocument(root, "doc", SYS_FILE, internalSession);
        doc.create();

        Blob blob = Blobs.createBlob("foo", "text/plain");
        doc.setValue(SYSFILE_BLOB, blob);
        // check binary key
        blob = (Blob) doc.getValue(SYSFILE_BLOB);
        String key = ((ManagedBlob) blob).getKey();
        assertTrue(key.startsWith("test:"), key);

        // files/0/file gets stored in the second blob provider
        blob = Blobs.createBlob("bar", "text/plain");
        doc.setValue("sysfiles_blobs", Collections.singletonList(Collections.singletonMap("blob", blob)));
        // check binary key
        blob = (Blob) doc.getValue("sysfiles_blobs/0/blob");
        key = ((ManagedBlob) blob).getKey();
        assertTrue(key.startsWith("test2:"), key);
    }

    @Test
    public void testDispatchPath() throws Exception {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();

        DocumentModel doc1 = session.createDocumentModel("/folder", "otherdoc", SYS_FILE);
        doc1.create();

        DocumentModel doc2 = session.createDocumentModel("/folder", "mydoc", SYS_FILE);
        doc2.create();

        // create a blob in doc1
        Blob blob1 = Blobs.createBlob("foo", "text/plain");
        doc1.setPropertyValue(SYSFILE_BLOB, blob1);
        // check binary key
        blob1 = (Blob) doc1.getPropertyValue(SYSFILE_BLOB);
        String key1 = ((ManagedBlob) blob1).getKey();
        assertTrue(key1.startsWith("test:"), key1);

        // create a blob in doc2
        Blob blob2 = Blobs.createBlob("bar", "text/plain");
        doc2.setPropertyValue(SYSFILE_BLOB, blob2);
        // check binary key, in test4
        blob2 = (Blob) doc2.getPropertyValue(SYSFILE_BLOB);
        String key2 = ((ManagedBlob) blob2).getKey();
        assertTrue(key2.startsWith("test4:"), key2);
    }
}
