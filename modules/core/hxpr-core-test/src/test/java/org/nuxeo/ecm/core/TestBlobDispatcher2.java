/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.io.InputStream;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.DefaultBlobDispatcher;
import org.nuxeo.ecm.core.blob.DocumentBlobService;
import org.nuxeo.ecm.core.blob.DocumentBlobServiceImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestBlobDispatcher2 {

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    @Inject
    protected DocumentBlobService documentBlobService;

    /**
     * Old blob which was created before dispatch was configured. It has no prefix but must still be dispatched
     * somewhere on read.
     */
    @Test
    public void testDispatchOldBlob() throws Exception {
        String foo = "foo";
        String foo_key = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

        // create a regular binary going to the default (non-dispatched) blob provider
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        Blob blob = Blobs.createBlob(foo, "text/plain");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();
        session.save();

        // check binary key
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        String key = ((ManagedBlob) blob).getKey();
        assertEquals(foo_key, key);

        // now install dispatch
        var dispatcher = (DefaultBlobDispatcher) ((DocumentBlobServiceImpl) documentBlobService).blobDispatcher;
        dispatcher.useRepositoryName = false;
        dispatcher.defaultProviderId = "test";
        try {
            // check that blob still readable
            doc = session.getDocument(doc.getRef());
            blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
            try (InputStream in = blob.getStream()) {
                assertEquals("foo", IOUtils.toString(in, UTF_8));
            }
        } finally {
            // restore default dispatcher for other tests
            dispatcher.useRepositoryName = true;
            dispatcher.defaultProviderId = null;
        }
    }

}
