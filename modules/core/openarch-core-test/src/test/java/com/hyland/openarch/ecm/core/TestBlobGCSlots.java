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
package com.hyland.openarch.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.blob.AbstractBlobGarbageCollector;
import com.hyland.openarch.ecm.core.blob.DocumentBlobService;
import com.hyland.openarch.ecm.core.blob.DocumentBlobServiceImpl;
import com.hyland.openarch.ecm.core.blob.ManagedBlob;
import com.hyland.openarch.ecm.core.blob.binary.BinaryManagerStatus;
import com.hyland.openarch.ecm.core.blob.binary.BlobKeySlot;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

/**
 * Tests the blob garbage collection partitioned by key space slots.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/disable-schedulers.xml")
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/test-blob-gc-slots.xml")
public class TestBlobGCSlots {

    private static final int NUM_DOCS = 20;

    private int previousPrefixLength;

    private long previousWriteTTL;

    @Inject
    private CoreFeature coreFeature;

    @Inject
    private CoreSession session;

    @Inject
    private DocumentBlobService documentBlobService;

    @BeforeEach
    public void setUp() {
        previousWriteTTL = AbstractBlobGarbageCollector.GC_WRITE_TTL_SECONDS;
        AbstractBlobGarbageCollector.GC_WRITE_TTL_SECONDS = 0;
        previousPrefixLength = DocumentBlobServiceImpl.GC_SLOT_PREFIX_LENGTH;
    }

    @AfterEach
    public void tearDown() {
        AbstractBlobGarbageCollector.GC_WRITE_TTL_SECONDS = previousWriteTTL;
        DocumentBlobServiceImpl.GC_SLOT_PREFIX_LENGTH = previousPrefixLength;
    }

    @Test
    public void testGCDefaultPartitioning() {
        assertEquals(1, DocumentBlobServiceImpl.GC_SLOT_PREFIX_LENGTH);
        List<BlobKeySlot> slots = new ArrayList<>();
        DocumentBlobServiceImpl service = new DocumentBlobServiceImpl() {
            @Override
            public BinaryManagerStatus garbageCollectBinaries(boolean delete, BlobKeySlot slot) {
                slots.add(slot);
                BinaryManagerStatus status = new BinaryManagerStatus();
                status.add(1, 3);
                status.addGC(2, 5);
                return status;
            }
        };
        BinaryManagerStatus status = service.garbageCollectBinaries(true);
        assertEquals(BlobKeySlot.partition(1), slots);
        assertEquals(17, status.getNumBinaries());
        assertEquals(51, status.getSizeBinaries());
        assertEquals(34, status.getNumBinariesGC());
        assertEquals(85, status.getSizeBinariesGC());

        slots.clear();
        service.garbageCollectBinaries(true, BlobKeySlot.ALL);
        assertEquals(List.of(BlobKeySlot.ALL), slots);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2 })
    public void testGCFullCycle(int prefixLength) throws IOException {
        DocumentBlobServiceImpl.GC_SLOT_PREFIX_LENGTH = prefixLength;
        // GC blobs from previous tests
        documentBlobService.garbageCollectBinaries(true);

        // blobs in the default provider (no prefix in the stored key) and in another one (prefixed key)
        List<DocumentModel> docs = new ArrayList<>();
        for (int i = 0; i < NUM_DOCS; i++) {
            DocumentModel doc = session.createDocumentModel("/", "doc" + i, SYS_FILE);
            if (i % 2 == 0) {
                doc.setPropertyValue("mydc_source", "other");
            }
            doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("content " + i + " for " + prefixLength));
            doc.create();
            docs.add(doc);
        }
        session.save();
        String otherKey = ((ManagedBlob) docs.get(0).getPropertyValue(SYSFILE_BLOB)).getKey();
        assertTrue(otherKey.startsWith("other:"), otherKey);

        // remove a quarter of the documents
        List<DocumentModel> kept = new ArrayList<>();
        for (int i = 0; i < NUM_DOCS; i++) {
            if (i % 4 == 0) {
                session.removeDocument(docs.get(i).getRef());
            } else {
                kept.add(docs.get(i));
            }
        }
        session.save();
        coreFeature.waitForAsyncCompletion();

        // dry run
        BinaryManagerStatus status = documentBlobService.garbageCollectBinaries(false);
        assertEquals(NUM_DOCS - kept.size(), status.getNumBinariesGC());
        assertEquals(kept.size(), status.getNumBinaries());

        // real GC
        status = documentBlobService.garbageCollectBinaries(true);
        assertEquals(NUM_DOCS - kept.size(), status.getNumBinariesGC());
        assertEquals(kept.size(), status.getNumBinaries());

        // nothing left to collect, and the kept blobs are still there
        status = documentBlobService.garbageCollectBinaries(true);
        assertEquals(0, status.getNumBinariesGC());
        assertEquals(kept.size(), status.getNumBinaries());
        for (DocumentModel doc : kept) {
            ManagedBlob blob = (ManagedBlob) session.getDocument(doc.getRef()).getPropertyValue(SYSFILE_BLOB);
            assertTrue(blob.getString().startsWith("content "));
        }
    }

    @Test
    public void testGCCycleContinuesAfterSlotFailure() {
        RuntimeException failure1 = new RuntimeException("slot 3");
        RuntimeException failure2 = new RuntimeException("slot Z");
        List<BlobKeySlot> slots = new ArrayList<>();
        DocumentBlobServiceImpl service = new DocumentBlobServiceImpl() {
            @Override
            public BinaryManagerStatus garbageCollectBinaries(boolean delete, BlobKeySlot slot) {
                slots.add(slot);
                if (slot.id().equals("3")) {
                    throw failure1;
                }
                if (slot.isOther()) {
                    throw failure2;
                }
                BinaryManagerStatus status = new BinaryManagerStatus();
                status.add(1, 0);
                return status;
            }
        };
        DocumentBlobServiceImpl.GC_SLOT_PREFIX_LENGTH = 1;
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.garbageCollectBinaries(true));
        assertSame(failure1, e);
        assertEquals(List.of(failure2), List.of(e.getSuppressed()));
        assertEquals(BlobKeySlot.partition(1), slots);

        // a single slot is run alone
        slots.clear();
        BinaryManagerStatus status = service.garbageCollectBinaries(true, new BlobKeySlot("a"));
        assertEquals(1, status.getNumBinaries());
        assertEquals(List.of(new BlobKeySlot("a")), slots);

        // with 256 slots, only the other slot fails
        slots.clear();
        DocumentBlobServiceImpl.GC_SLOT_PREFIX_LENGTH = 2;
        e = assertThrows(RuntimeException.class, () -> service.garbageCollectBinaries(false));
        assertSame(failure2, e);
        assertEquals(257, slots.size());
    }

}
