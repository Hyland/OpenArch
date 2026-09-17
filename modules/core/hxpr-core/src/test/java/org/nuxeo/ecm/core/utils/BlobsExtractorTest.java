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
package org.nuxeo.ecm.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.model.InternalDocument.BlobAccessor;

class BlobsExtractorTest {

    @Test
    void shouldAcceptAllXPathsWithNoArgConstructor() {
        BlobsExtractor extractor = new BlobsExtractor();

        assertTrue(extractor.isInterestingXPath("content", "File"));
        assertTrue(extractor.isInterestingXPath("files/0/file", "AnyType"));
    }

    @Test
    void shouldRejectNullDocBlobPaths() {
        assertThrows(NullPointerException.class, () -> new BlobsExtractor(null));
    }

    @Test
    void shouldNormalizeKeySuffixFromConfiguredPaths() {
        BlobsExtractor extractor = new BlobsExtractor(Map.of("File", Set.of("content/key")));

        // the "/key" suffix is stripped, so "content" is the interesting path
        assertTrue(extractor.isInterestingXPath("content", "File"));
    }

    @Test
    void shouldMatchIndexedPathsAgainstWildcards() {
        BlobsExtractor extractor = new BlobsExtractor(Map.of("File", Set.of("files/*/file")));

        assertTrue(extractor.isInterestingXPath("files/0/file", "File"));
        assertTrue(extractor.isInterestingXPath("files/42/file", "File"));
    }

    @Test
    void shouldRejectPathsNotConfiguredForType() {
        BlobsExtractor extractor = new BlobsExtractor(Map.of("File", Set.of("content")));

        assertFalse(extractor.isInterestingXPath("content", "Note"));
        assertFalse(extractor.isInterestingXPath("other", "File"));
    }

    @Test
    void shouldCollectNonNullBlobsFromInterestingXPaths() {
        Blob blob = mock(Blob.class);
        BlobAccessor withBlob = mock(BlobAccessor.class);
        when(withBlob.getXPath()).thenReturn("content");
        when(withBlob.getBlob()).thenReturn(blob);

        BlobAccessor withoutBlob = mock(BlobAccessor.class);
        when(withoutBlob.getXPath()).thenReturn("thumbnail");
        when(withoutBlob.getBlob()).thenReturn(null);

        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getType()).thenReturn("File");
        doAnswer(invocation -> {
            Consumer<BlobAccessor> visitor = invocation.getArgument(0);
            visitor.accept(withBlob);
            visitor.accept(withoutBlob);
            return null;
        }).when(doc).visitBlobs(any());

        List<Blob> blobs = new BlobsExtractor().getBlobs(doc);

        assertEquals(1, blobs.size());
        assertEquals(blob, blobs.get(0));
    }

    @Test
    void shouldSkipBlobsFromUninterestingXPaths() {
        BlobAccessor accessor = mock(BlobAccessor.class);
        when(accessor.getXPath()).thenReturn("thumbnail");

        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getType()).thenReturn("File");
        doAnswer(invocation -> {
            Consumer<BlobAccessor> visitor = invocation.getArgument(0);
            visitor.accept(accessor);
            return null;
        }).when(doc).visitBlobs(any());

        BlobsExtractor extractor = new BlobsExtractor(Map.of("File", Set.of("content")));
        List<Blob> blobs = extractor.getBlobs(doc);

        assertTrue(blobs.isEmpty());
    }
}
