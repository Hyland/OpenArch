/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.repository.RepositoryManager;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.runtime.mockito.MockitoFeature;
import com.hyland.openarch.runtime.mockito.RuntimeService;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features({ BlobManagerFeature.class, DocumentBlobServiceFeature.class, MockitoFeature.class })
@Deploy("org.nuxeo.ecm.core.api.tests:OSGI-INF/dummy-blob-provider.xml")
public class TestDocumentBlobService {

    private static final String DUMMY = "dummy";

    @Inject
    protected BlobService blobService;

    @Inject
    protected DocumentBlobService documentBlobService;

    @Mock
    @RuntimeService
    RepositoryManager repositoryManager;

    @BeforeEach
    public void mockRepositoryManager() {
        when(repositoryManager.getRepositoryNames()).thenReturn(Collections.emptyList());
    }

    @Test
    public void testGetSetMetadata() throws Exception {
        Blob b = Blobs.createBlob("foo");
        // write
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getRepositoryName()).thenReturn(DUMMY);
        when(doc.getId()).thenReturn("123");

        DummyBlobProvider.resetAllCounters();
        String key = documentBlobService.writeBlob(b, doc, "somexpath");
        assertEquals("1", key);

        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setMimeType("test/type");
        blobInfo.setEncoding("UTF-8");
        blobInfo.setFilename("doc.ext");
        blobInfo.setLength(Long.valueOf(123));
        blobInfo.setDigest("55667788");

        // read without prefix
        blobInfo.setKey("1");
        ManagedBlob blob = (ManagedBlob) documentBlobService.readBlob(blobInfo, doc, "somexpath");
        assertNotNull(blob);
        assertEquals(blobInfo.getKey(), blob.getKey());
        assertEquals(blobInfo.getMimeType(), blob.getMimeType());
        assertEquals(blobInfo.getEncoding(), blob.getEncoding());
        assertEquals(blobInfo.getFilename(), blob.getFilename());
        assertEquals(blobInfo.getLength().intValue(), blob.getLength());
        assertEquals(blobInfo.getDigest(), blob.getDigest());
        assertEquals("foo", blob.getString());

        // read with prefix
        blobInfo.setKey(DUMMY + ":1");
        blob = (ManagedBlob) documentBlobService.readBlob(blobInfo, doc, "somexpath");
        assertNotNull(blob);
        assertEquals(blobInfo.getKey(), blob.getKey());
        assertEquals(blobInfo.getMimeType(), blob.getMimeType());
        assertEquals(blobInfo.getEncoding(), blob.getEncoding());
        assertEquals(blobInfo.getFilename(), blob.getFilename());
        assertEquals(blobInfo.getLength().intValue(), blob.getLength());
        assertEquals(blobInfo.getDigest(), blob.getDigest());
        assertEquals("foo", blob.getString());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.tests:OSGI-INF/test-blob-dispatch.xml")
    public void testDispatch() throws Exception {
        DummyBlobProvider.resetAllCounters();
        // blob that's not a video gets stored on the first dummy repo
        Blob blob = Blobs.createBlob("foo", "text/plain");
        InternalDocument doc = mock(InternalDocument.class, "doc1");
        when(doc.getRepositoryName()).thenReturn(DUMMY);
        when(doc.getId()).thenReturn("123");

        String key = documentBlobService.writeBlob(blob, doc, "somexpath");
        assertEquals("dummy:1", key);
        // videos get stored in the second one
        blob = Blobs.createBlob("bar", "video/mp4");
        key = documentBlobService.writeBlob(blob, doc, "somexpath");
        assertEquals("dummy2:1", key);

        // read first one
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey("dummy:1");
        blob = documentBlobService.readBlob(blobInfo, doc, "somexpath");
        assertEquals("foo", IOUtils.toString(blob.getStream(), UTF_8));

        // read second one
        blobInfo.setKey("dummy2:1");
        blob = documentBlobService.readBlob(blobInfo, doc, "somexpath");
        assertEquals("bar", IOUtils.toString(blob.getStream(), UTF_8));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.tests:OSGI-INF/test-blob-dispatch-xpath.xml")
    public void testDispatchXPath() throws Exception {
        DummyBlobProvider.resetAllCounters();
        Blob blob = Blobs.createBlob("foo", "text/plain");
        InternalDocument doc = mock(InternalDocument.class, "doc1");
        when(doc.getRepositoryName()).thenReturn(DUMMY);
        when(doc.getId()).thenReturn("123");

        String key = documentBlobService.writeBlob(blob, doc, "content");
        assertEquals("dummy:1", key);

        // files/0/file gets stored in the second blob provider
        blob = Blobs.createBlob("bar", "text/plain");
        key = documentBlobService.writeBlob(blob, doc, "files/0/file");
        assertEquals("dummy2:1", key);
    }

    @Test
    public void testDocInContext() throws Exception {
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getRepositoryName()).thenReturn(DUMMY);
        when(doc.getId()).thenReturn("123");
        when(doc.getPropertyValue("myprop")).thenReturn("bar");

        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey(DummyBlobProvider.FROMDOC + ":myprop");

        // special blob provider that uses doc content
        ManagedBlob blob = (ManagedBlob) documentBlobService.readBlob(blobInfo, doc, "somexpath");
        assertNotNull(blob);
        assertEquals("bar", blob.getString());
    }

    @Test
    public void testDocInContextWithByteRange() throws Exception {
        // prepare a blob in the in-memory blob provider that allows byte ranges in keys
        Blob b = Blobs.createBlob("abcde1234567890fghij", "text/plain");
        BlobProvider membp = blobService.getBlobProvider(DummyBlobProvider.MEMWITHBYTERANGE);
        String key = membp.writeBlob(b);
        Long rangeStart = 5L;
        Long rangeEnd = 14L;

        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getRepositoryName()).thenReturn(DUMMY);
        when(doc.getId()).thenReturn("123");
        when(doc.getPropertyValue("key")).thenReturn(key);
        when(doc.getPropertyValue("rangeStart")).thenReturn(rangeStart);
        when(doc.getPropertyValue("rangeEnd")).thenReturn(rangeEnd);

        BlobInfo blobInfo = new BlobInfo();
        // only the blob provider is important, this implementation doesn't use the key value
        blobInfo.setKey(DummyBlobProvider.FROMDOC2 + ":unused");

        // resolving the blob will use the doc properties to get a byte range of another blob
        ManagedBlob blob = (ManagedBlob) documentBlobService.readBlob(blobInfo, doc, "somexpath");
        assertNotNull(blob);
        assertEquals("1234567890", blob.getString());
    }

    @Test
    public void testGetConvertedStreamReturnsNullForUnmanagedBlob() throws Exception {
        // a plain (non-managed) blob has no document blob provider
        assertNull(documentBlobService.getConvertedStream(Blobs.createBlob("foo"), "text/html", null));
    }

    @Test
    public void testBinariesGarbageCollectionNotInProgressByDefault() {
        assertFalse(documentBlobService.isBinariesGarbageCollectionInProgress());
    }

}
