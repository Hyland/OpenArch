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
package com.hyland.openarch.ecm.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
public class TestBlobsExtractor {

    @Inject
    protected CoreSession session;

    private DocumentModel newDocument(String type) {
        DocumentModel doc = session.createDocumentModel("/", "doc", type);
        doc.create();
        return doc;
    }

    protected Blob createBlob(String filename) {
        return Blobs.createBlob("dummy", null, null, filename);
    }

    @Test
    public void testGetBlobs() {
        DocumentModel doc = newDocument("ComplexDoc");
        doc.addMixin("WithFile");

        Blob blob1 = createBlob("file1.txt");
        Blob blob2 = createBlob("file2.txt");
        Blob blob3 = createBlob("file3.txt");
        doc.setPropertyValue(SYSFILE_BLOB, blob1);
        doc.setPropertyValue("cmpf_attachedFile", Map.of("vignettes", List.of( //
                Map.of("content", blob2), //
                Map.of("content", blob3))));

        List<Blob> blobs = new BlobsExtractor().getBlobs(doc);
        assertEquals(3, blobs.size());
        assertTrue(blobs.contains(blob1));
        assertTrue(blobs.contains(blob2));
        assertTrue(blobs.contains(blob3));
    }

    @Test
    public void testGetBlobsEmpty() {
        DocumentModel doc = newDocument("ComplexDoc");
        // don't create sysfile_blob
        List<Blob> blobs = new BlobsExtractor().getBlobs(doc);
        assertEquals(0, blobs.size());
    }

    @Test
    public void testWithRepositoryConfiguration() {
        DocumentModel doc = newDocument("ComplexDoc");
        doc.addMixin("WithFile");

        List<Map<String, Object>> vignettes = new ArrayList<>();
        Map<String, Object> vignette = new HashMap<>();
        vignette.put("width", 0L);
        vignette.put("height", 0L);
        Blob blob1 = createBlob("file1.txt");
        vignette.put("content", blob1);
        vignettes.add(vignette);

        vignette = new HashMap<>();
        vignette.put("width", 0L);
        vignette.put("height", 0L);
        Blob blob2 = createBlob("file2.txt");
        vignette.put("content", blob2);
        vignettes.add(vignette);

        Map<String, Object> attachedFile = new HashMap<>();
        attachedFile.put("name", "some name");
        attachedFile.put("vignettes", vignettes);
        doc.setPropertyValue("cmpf_attachedFile", attachedFile);

        Blob blob3 = createBlob("file3.txt");
        doc.setPropertyValue(SYSFILE_BLOB, blob3);

        BlobsExtractor extractor;
        List<Blob> blobs;

        // no config
        Map<String, Set<String>> config = Map.of();
        extractor = new BlobsExtractor(config);
        blobs = extractor.getBlobs(doc);
        assertEquals(0, blobs.size());

        // all blobs
        extractor = new BlobsExtractor();
        blobs = extractor.getBlobs(doc);
        assertEquals(3, blobs.size());
        assertTrue(blobs.contains(blob1));
        assertTrue(blobs.contains(blob2));
        assertTrue(blobs.contains(blob3));

        // only vignettes
        config = Map.of("ComplexDoc", Set.of("cmpf_attachedFile/vignettes/*/content"));
        extractor = new BlobsExtractor(config);
        blobs = extractor.getBlobs(doc);
        assertEquals(2, blobs.size());
        assertTrue(blobs.contains(blob1));
        assertTrue(blobs.contains(blob2));

        // only the blob of file
        config = Map.of("ComplexDoc", Set.of(SYSFILE_BLOB));
        extractor = new BlobsExtractor(config);
        blobs = extractor.getBlobs(doc);
        assertEquals(1, blobs.size());
        assertTrue(blobs.contains(blob3));
    }

}
