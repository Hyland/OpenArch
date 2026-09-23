/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.blob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILES_BLOBS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.BLOB_TYPE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

/**
 * Test class validating the behavior of the dirty binaries.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
public class TestDirtyBinaries {

    private static final String BLOBS_0_KEY = "sysfiles_blobs/0/blob";

    private static final String BLOBS_1_KEY = "sysfiles_blobs/1/blob";

    @Inject
    CoreSession session;

    @Test
    public void sessionDetectsDirtyBinaryAtDocumentCreation() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        Blob blob = Blobs.createBlob("content");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();
        // The session should detect the dirty binary
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).containsOnly(SYSFILE_BLOB);
        session.save();
        // There is no more dirty binary after the transaction is commited
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).isEmpty();
    }

    @Test
    public void sessionDetectsDirtyBinaryAfterDocumentUpdate() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();

        doc.refresh();
        Blob blob = Blobs.createBlob("content");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        // The session should detect the dirty binary
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).containsOnly(SYSFILE_BLOB);
        session.save();

        // Update another property
        doc.refresh();
        doc.setPropertyValue(SYS_DESCRIPTION, "new description");
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).isEmpty();
        session.save();

        // Update the existing blob
        doc.refresh();
        blob = Blobs.createBlob("new content");
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).containsOnly(SYSFILE_BLOB);
    }

    @Test
    public void sessionDetectsDirtyListOfBinariesAtCreation() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        Blob blob1 = Blobs.createBlob("content 1");
        Blob blob2 = Blobs.createBlob("content 2");
        doc.setPropertyValue(SYSFILES_BLOBS, List.of(Map.of(BLOB_TYPE, blob1), Map.of(BLOB_TYPE, blob2)));
        doc.create();
        // The session should detect the dirty binaries
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).containsExactlyInAnyOrder(BLOBS_0_KEY,
                BLOBS_1_KEY);
        session.save();
        // There is no more dirty binaries after the transaction is commited
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).isEmpty();
    }

    @Test
    public void sessionDetectsDirtyListOfBinariesAfterDocumentUpdate() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();

        doc.refresh();
        Blob blob1 = Blobs.createBlob("content 1");
        Blob blob2 = Blobs.createBlob("content 2");
        doc.setPropertyValue(SYSFILES_BLOBS, List.of(Map.of("blob", blob1), Map.of("blob", blob2)));

        // The session should detect the list of dirty binaries
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).containsExactlyInAnyOrder(BLOBS_0_KEY,
                BLOBS_1_KEY);
        session.save();
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).isEmpty();

        // Update the existing blob
        doc.refresh();
        blob2 = Blobs.createBlob("new content");
        doc.setPropertyValue(SYSFILES_BLOBS, List.of(Map.of("blob", blob1), Map.of("blob", blob2)));
        assertThat(session.getDirtyBinariesPaths(doc.getRef())).containsOnly(BLOBS_1_KEY);
    }
}
