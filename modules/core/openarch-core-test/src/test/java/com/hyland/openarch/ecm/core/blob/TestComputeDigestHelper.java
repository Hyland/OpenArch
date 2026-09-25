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
package com.hyland.openarch.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.BLOB_DIGEST_UPDATED_NEW_DIGEST;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.BLOB_DIGEST_UPDATED_NEW_KEY;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.BLOB_DIGEST_UPDATED_OLD_DIGEST;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.BLOB_DIGEST_UPDATED_OLD_KEY;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.BLOB_DIGEST_UPDATED;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.api.impl.blob.StringBlob;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.test.CapturingEventListener;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("com.hyland.openarch.ecm.core.test:OSGI-INF/test-storage-blobstore-contrib.xml")
public class TestComputeDigestHelper {

    protected static final String FOO = "foo";

    protected static final String BAR = "bar";

    protected static final String FOO_SHA256 = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

    protected static final String BAR_SHA256 = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

    @Inject
    protected CoreSession session;

    @Test
    public void testComputeDigest() throws IOException {
        InMemoryBlobProvider bp = new InMemoryBlobProvider();
        bp.initialize("mybp", Collections.singletonMap("digest", "SHA-256"));
        String key1 = bp.writeBlob(new BlobContext(new StringBlob(FOO), "mydocid", null));
        assertEquals(FOO_SHA256, key1); // sanity check, but that's not what this test is about

        // compute the digest using the async digest helper
        String digest = new ComputeDigestHelper("test", key1).computeDigest(bp.getStore());
        assertEquals(FOO_SHA256, digest);
        // missing blob
        digest = new ComputeDigestHelper("test", "nosuchkey").computeDigest(bp.getStore());
        assertNull(digest);
    }

    @Test
    public void testReplaceDigest() {
        DocumentModel doc = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob(FOO));
        doc.create();
        session.save();

        ComputeDigestHelper helper = new ComputeDigestHelper("test", FOO_SHA256);
        helper.newKey = BAR_SHA256;
        helper.digest = BAR_SHA256;

        helper.replaceDigestAllRepositories();
        session.save();
        doc.refresh();

        // check the blob is new
        ManagedBlob blob = (ManagedBlob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals(BAR_SHA256, blob.getKey());
        assertEquals(BAR_SHA256, blob.getDigest());
    }

    @Test
    public void testCoreSessionReplaceBlobDigest() {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob(FOO));
        doc1.create();
        DocumentModel doc2 = session.createDocumentModel("/", "doc", SYS_FILE);
        doc2.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob(BAR));
        doc2.create();
        session.save();

        try (CapturingEventListener listener = new CapturingEventListener(BLOB_DIGEST_UPDATED)) {

            // make doc1 point to doc2's blob
            String oldDigest = session.replaceBlobDigest(doc1.getRef(), FOO_SHA256, BAR_SHA256, BAR_SHA256);
            assertEquals(FOO_SHA256, oldDigest);

            // check event
            List<Event> events = listener.getCapturedEvents();
            assertEquals(1, events.size());
            Map<String, Serializable> properties = events.get(0).getContext().getProperties();
            assertEquals(FOO_SHA256, properties.get(BLOB_DIGEST_UPDATED_OLD_KEY));
            assertEquals(FOO_SHA256, properties.get(BLOB_DIGEST_UPDATED_OLD_DIGEST));
            assertEquals(BAR_SHA256, properties.get(BLOB_DIGEST_UPDATED_NEW_KEY));
            assertEquals(BAR_SHA256, properties.get(BLOB_DIGEST_UPDATED_NEW_DIGEST));
        }
        session.save();
        doc1.refresh();

        // check the blob is new
        ManagedBlob blob = (ManagedBlob) doc1.getPropertyValue(SYSFILE_BLOB);
        assertEquals(BAR_SHA256, blob.getKey());
        assertEquals(BAR_SHA256, blob.getDigest());
    }

    @Test
    public void computeAndReplaceDigestThrowsForUnknownProvider() {
        ComputeDigestHelper helper = new ComputeDigestHelper("nosuchprovider", "key");
        assertThrows(RepositoryException.class, helper::computeAndReplaceDigest);
    }

    @Test
    public void computeAndReplaceDigestIsNoOpWhenBlobMissing() {
        // no blob stored for this key: computeDigest returns null, so nothing is replaced
        ComputeDigestHelper helper = new ComputeDigestHelper("test", "nosuchkey");

        helper.computeAndReplaceDigest();

        assertNull(helper.newKey); // no replacement happened
    }

    @Test
    public void computeAndReplaceDigestIsNoOpWhenKeyAlreadyEqualsDigest() {
        DocumentModel doc = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob(FOO));
        doc.create();
        session.save();

        // the blob's key already equals its digest, so the helper short-circuits without replacing
        ComputeDigestHelper helper = new ComputeDigestHelper("test", FOO_SHA256);

        helper.computeAndReplaceDigest();

        assertEquals(FOO_SHA256, helper.digest);
        assertNull(helper.newKey); // no replacement happened
    }

}
