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
package com.hyland.openarch.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.runtime.api.Framework;

/**
 * Unit tests for the blob-provider resolution logic of {@link DocumentBlobServiceImpl}.
 */
class DocumentBlobServiceImplTest {

    private final DocumentBlobServiceImpl service = new DocumentBlobServiceImpl();

    @Test
    void readBlobReturnsNullWhenKeyIsNull() throws IOException {
        BlobInfo blobInfo = new BlobInfo(); // key is null
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getRepositoryName()).thenReturn("repo");

        assertNull(service.readBlob(blobInfo, doc, "file"));
    }

    @Test
    void readBlobThrowsWhenNoProviderForUnprefixedKey() {
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey("plainkey"); // no colon -> resolved through the dispatcher
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getRepositoryName()).thenReturn("repo");
        BlobDispatcher dispatcher = mock(BlobDispatcher.class);
        when(dispatcher.getBlobProvider("repo")).thenReturn("p");
        service.blobDispatcher = dispatcher;

        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            BlobService blobService = mock(BlobService.class);
            fw.when(() -> Framework.getService(BlobService.class)).thenReturn(blobService);
            when(blobService.getBlobProvider("p")).thenReturn(null);

            assertThrows(RepositoryException.class, () -> service.readBlob(blobInfo, doc, "file"));
        }
    }

    @Test
    void readBlobDelegatesToProviderForPrefixedKey() throws IOException {
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey("p:abc"); // prefix "p" identifies the provider directly
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getRepositoryName()).thenReturn("repo");
        Blob blob = mock(Blob.class);

        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            BlobService blobService = mock(BlobService.class);
            fw.when(() -> Framework.getService(BlobService.class)).thenReturn(blobService);
            BlobProvider blobProvider = mock(BlobProvider.class);
            when(blobService.getBlobProvider("p")).thenReturn(blobProvider);
            when(blobProvider.readBlob(any(BlobInfoContext.class))).thenReturn(blob);

            assertSame(blob, service.readBlob(blobInfo, doc, "file"));
        }
    }

    @Test
    void getDocumentBlobProviderReturnsProviderWhenDocumentAware() {
        Blob blob = mock(Blob.class);

        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            BlobService blobService = mock(BlobService.class);
            fw.when(() -> Framework.getService(BlobService.class)).thenReturn(blobService);
            // a provider that is also document-aware
            BlobProvider documentBlobProvider = mock(BlobProvider.class,
                    withSettings().extraInterfaces(DocumentBlobProvider.class));
            when(blobService.getBlobProvider(blob)).thenReturn(documentBlobProvider);

            assertSame(documentBlobProvider, service.getDocumentBlobProvider(blob));
        }
    }

    @Test
    void getDocumentBlobProviderReturnsNullForPlainProvider() {
        Blob blob = mock(Blob.class);

        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            BlobService blobService = mock(BlobService.class);
            fw.when(() -> Framework.getService(BlobService.class)).thenReturn(blobService);
            when(blobService.getBlobProvider(blob)).thenReturn(mock(BlobProvider.class));

            assertNull(service.getDocumentBlobProvider(blob));
        }
    }
}
