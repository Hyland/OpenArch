/*
 * (C) Copyright 2015-2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Estelle Giuly <egiuly@nuxeo.com>
 */
package org.nuxeo.ecm.core.io.download;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobService;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.event.test.CapturingEventListener;
import org.nuxeo.ecm.core.io.NginxConstants;
import org.nuxeo.ecm.core.io.download.DownloadService.DownloadContext;
import org.nuxeo.ecm.core.io.download.DownloadServiceImpl.Action;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreProvider;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.login.LoginService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.cache")
public class TestDownloadService {

    private static final String CONTENT = "this is a file au caf\u00e9";

    @Inject
    protected DownloadService downloadService;

    @Inject
    protected BlobService blobService;

    @Inject
    protected LoginService loginService;

    @Test
    public void testBasicDownloadGet() throws Exception {
        doTestBasicDownload(false);
    }

    @Test
    public void testBasicDownloadHead() throws Exception {
        doTestBasicDownload(true);
    }

    protected void doTestBasicDownload(boolean head) throws Exception {
        // ascii filename is used directly
        doTestBasicDownload(head, "cafe.txt", "filename=cafe.txt");
        // non-ascii filename gets RFC2231 encoding
        doTestBasicDownload(head, "caf\u00e9.txt", "filename*=UTF-8''caf%C3%A9.txt");
    }

    protected void doTestBasicDownload(boolean head, String filename, String filenameInHeader) throws Exception {
        // regular download
        doTestBasicDownload(head, filename, filenameInHeader, false);
        // download with empty=true in Content-Type
        doTestBasicDownload(head, filename, filenameInHeader, true);
    }

    protected void doTestBasicDownload(boolean head, String filename, String filenameInHeader, boolean empty)
            throws Exception {
        // blob to download
        String blobValue = "Hello World Caf\u00e9";
        String mimeType = "text/plain";
        if (empty) {
            mimeType += "; empty=true";
        }
        String encoding = "ISO-8859-1";
        String digest = "12345";
        Blob blob = Blobs.createBlob(blobValue, mimeType, encoding);
        blob.setFilename(filename);
        blob.setDigest(digest);
        Instant lastModified = Instant.parse("2001-02-03T04:05:06Z");

        // prepare mocks
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(head ? "HEAD" : "GET");

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = new DummyServletOutputStream(out);
        @SuppressWarnings("resource")
        PrintWriter printWriter = new PrintWriter(sos);
        when(response.getOutputStream()).thenReturn(sos);
        when(response.getWriter()).thenReturn(printWriter);

        // send download request
        DownloadContext context = DownloadContext.builder(request, response)
                                                 .blob(blob)
                                                 .lastModified(lastModified)
                                                 .build();
        downloadService.downloadBlob(context);

        // assert headers (mockito wants us to assert all header in same order they were set)
        if (empty) {
            verify(response, never()).setHeader(eq("ETag"), any());
        } else {
            verify(response).setHeader("ETag", '"' + digest + '"');
        }
        verify(response).setHeader("Content-Disposition", "attachment; " + filenameInHeader);
        verify(response).setHeader("Accept-Ranges", "bytes");
        // assert others interactions
        verify(response).setContentType(mimeType);
        verify(response).setCharacterEncoding(encoding);
        verify(response).setContentLengthLong(blob.getLength());
        if (empty) {
            verify(response, never()).setDateHeader(eq("Last-Modified"), anyLong());
        } else {
            verify(response).setDateHeader("Last-Modified", lastModified.toEpochMilli());
        }
        // check that the blob gets returned (except if HEAD)
        assertEquals(head ? "" : blobValue, out.toString(encoding));
    }

    @Test
    public void testETagHeaderNone() throws Exception {
        doTestETagHeader(null);
    }

    @Test
    public void testETagHeaderNotMatched() throws Exception {
        doTestETagHeader(FALSE);
    }

    @Test
    public void testETagHeaderMatched() throws Exception {
        doTestETagHeader(TRUE);
    }

    protected void doTestETagHeader(Boolean match) throws Exception {
        // Given a blob
        String blobValue = "Hello World";
        Blob blob = Blobs.createBlob(blobValue);
        blob.setFilename("myFile.txt");
        blob.setDigest("12345");

        String digestToTest;
        if (match == null) {
            digestToTest = null;
        } else if (TRUE.equals(match)) {
            digestToTest = "12345";
        } else {
            digestToTest = "78787";
        }

        // When I send a request a given digest
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("If-None-Match")).thenReturn('"' + digestToTest + '"');
        when(req.getMethod()).thenReturn("GET");

        HttpServletResponse resp = mock(HttpServletResponse.class);
        ServletOutputStream sos = new DummyServletOutputStream(out);
        @SuppressWarnings("resource")
        PrintWriter printWriter = new PrintWriter(sos);
        when(resp.getOutputStream()).thenReturn(sos);
        when(resp.getWriter()).thenReturn(printWriter);

        DownloadContext context = DownloadContext.builder(req, resp).blob(blob).build();
        downloadService.downloadBlob(context);

        verify(req, atLeast(1)).getHeader("If-None-Match");

        // Then the response differs if the digest match
        if (TRUE.equals(match)) {
            assertEquals(0, out.toByteArray().length);
            verify(resp).sendError(HttpServletResponse.SC_NOT_MODIFIED);
        } else {
            assertEquals(blobValue, out.toString());
            verify(resp).setHeader("ETag", '"' + blob.getDigest() + '"');
        }
    }

    @Test
    public void testETagHeaderNoDigest() throws Exception {
        String blobValue = "Hello World";
        Blob blob = Blobs.createBlob(blobValue);
        blob.setFilename("myFile.txt");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("If-None-Match")).thenReturn("\"a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e\"");
        when(req.getMethod()).thenReturn("GET");

        HttpServletResponse resp = mock(HttpServletResponse.class);
        ServletOutputStream sos = new DummyServletOutputStream(out);
        @SuppressWarnings("resource")
        PrintWriter printWriter = new PrintWriter(sos);
        when(resp.getOutputStream()).thenReturn(sos);
        when(resp.getWriter()).thenReturn(printWriter);

        DownloadContext context = DownloadContext.builder(req, resp).blob(blob).reason("test").build();
        downloadService.downloadBlob(context);

        verify(req, atLeastOnce()).getHeader("If-None-Match");
        assertEquals(0, out.toByteArray().length);
        verify(resp).sendError(HttpServletResponse.SC_NOT_MODIFIED);
    }

    @Test
    public void testWantDigestSHA256() throws Exception {
        doTestWantDigest(Arrays.asList("SHA-256"), "SHA-256=pZGm1Av0IEBKARczz7exkNYsZb8LzaMrV7J32a2fFG4=", null);
    }

    @Test
    public void testWantDigestUnknown() throws Exception {
        doTestWantDigest(Arrays.asList("nosuchalgo"), null, null);
    }

    protected void doTestWantDigest(List<String> wanted, String expectedDigest, String expectedContentSHA256)
            throws Exception {
        String blobValue = "Hello World";
        Blob blob = Blobs.createBlob(blobValue);
        blob.setFilename("myFile.txt");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeaders("Want-Digest")).thenReturn(Collections.enumeration(wanted));
        when(req.getMethod()).thenReturn("GET");

        HttpServletResponse resp = mock(HttpServletResponse.class);
        ServletOutputStream sos = new DummyServletOutputStream(out);
        @SuppressWarnings("resource")
        PrintWriter printWriter = new PrintWriter(sos);
        when(resp.getOutputStream()).thenReturn(sos);
        when(resp.getWriter()).thenReturn(printWriter);

        DownloadContext context = DownloadContext.builder(req, resp).blob(blob).build();
        downloadService.downloadBlob(context);

        verify(resp).setHeader("ETag", "\"a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e\"");
        if (expectedDigest != null) {
            verify(resp).setHeader("Digest", expectedDigest);
        }
    }

    /**
     * @since 9.3
     */
    @Test
    public void testDocumentDefaultDownloadAndPermission() throws Exception {
        // blob to download
        String blobValue = "Hello World";
        Blob blob = Blobs.createBlob(blobValue);
        blob.setFilename("myfile.txt");
        blob.setDigest("12345");

        // mock request
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");

        // mock response
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = new DummyServletOutputStream(out);
        @SuppressWarnings("resource")
        PrintWriter printWriter = new PrintWriter(sos);
        when(response.getOutputStream()).thenReturn(sos);
        when(response.getWriter()).thenReturn(printWriter);

        // mock document
        DocumentModel doc = mock(DocumentModel.class);
        // default Mocked Download Blob Holder returns sysfile_blob
        when(doc.getPropertyValue(SYSFILE_BLOB)).thenReturn(blob);

        // send download request, should return main content
        DownloadContext context = DownloadContext.builder(request, response).doc(doc).build();
        downloadService.downloadBlob(context);
        assertEquals(blobValue, out.toString());
    }

    /**
     * @since 9.3
     */
    @Test
    public void testAsyncDownload() throws IOException {
        // blob to download
        String blobValue = "Hello World";
        Blob blob = Blobs.createBlob(blobValue);
        blob.setFilename("myfile.txt");
        blob.setDigest("12345");

        // mock request
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");

        // mock response
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = new DummyServletOutputStream(out);
        @SuppressWarnings("resource")
        PrintWriter printWriter = new PrintWriter(sos);
        when(response.getOutputStream()).thenReturn(sos);
        when(response.getWriter()).thenReturn(printWriter);

        // principal
        NuxeoPrincipal principal = new UserPrincipal("bob");

        String key = downloadService.storeBlobs(Collections.singletonList(blob));
        TransientStoreService tss = Framework.getService(TransientStoreService.class);
        TransientStore ts = tss.getStore(DownloadService.TRANSIENT_STORE_STORE_NAME);
        ts.setCompleted(key, false);
        // do tests while logged in
        try (var __ = loginService.loginPrincipal(principal)) {
            // send download request for not completed stored blob, should be accepted
            out.reset();
            downloadService.downloadBlob(request, response, key, "download");
            assertEquals("", out.toString());
            verify(response, atLeastOnce()).setStatus(202);

            ts.setCompleted(key, true);
            ts.putParameter(key, DownloadService.TRANSIENT_STORE_PARAM_PROGRESS, 100);

            // send download request for complete stored blob
            out.reset();
            downloadService.downloadBlob(request, response, key, "download");
            assertEquals(blobValue, out.toString());
        }
    }

    @Test
    public void testDownloadNonExistingBlob() throws IOException {
        // mock request
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");

        // mock response
        HttpServletResponse response = mock(HttpServletResponse.class);

        // principal
        NuxeoPrincipal principal = new UserPrincipal("bob");

        String key = downloadService.storeBlobs(Collections.emptyList());
        TransientStoreService tss = Framework.getService(TransientStoreService.class);
        TransientStore ts = tss.getStore(DownloadService.TRANSIENT_STORE_STORE_NAME);
        ts.setCompleted(key, false);
        // do tests while logged in
        try (var __ = loginService.loginPrincipal(principal)) {
            // send download request for non existing key, should be not found
            downloadService.downloadBlob(request, response, "undefinedKey", "download");
            verify(response, atLeastOnce()).sendError(404);

            // send download request for non existing blob, should be not found
            out.reset();
            downloadService.downloadBlob(request, response, key, "download");
            verify(response, atLeastOnce()).sendError(404);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testTransientCleanup() throws IOException, InterruptedException { // NOSONAR (no need to assert anything)
        // transfert temporary file into a blob
        Path path = Files.createTempFile("pfouh", "pfouh");
        FileBlob blob = new FileBlob("pfouh");
        Files.move(path, blob.getFile().toPath(), REPLACE_EXISTING);

        // store the blob for downloading
        String key = downloadService.storeBlobs(Collections.singletonList(blob));

        // mock request
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");

        // mock response
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = new DummyServletOutputStream(out);
        @SuppressWarnings("resource")
        PrintWriter printWriter = new PrintWriter(sos);
        when(response.getOutputStream()).thenReturn(sos);
        when(response.getWriter()).thenReturn(printWriter);

        NuxeoPrincipal principal = new UserPrincipal("bob");

        // do tests while logged in
        try (var __ = loginService.loginPrincipal(principal)) {
            downloadService.downloadBlob(request, response, key, "download");
        }

        // do GC in underlying KeyValueBlobTransientStore
        TransientStoreService tss = Framework.getService(TransientStoreService.class);
        TransientStore ts = tss.getStore(DownloadService.TRANSIENT_STORE_STORE_NAME);
        ((TransientStoreProvider) ts).doGC();

        // the file is gone
        // TODO optimize copy of temporary blobs into a move for LocalBlobStore
        // TODO assertFalse(blob.getFile().exists());
    }

    @Test
    public void testDownloadTransientWithHeadBeforeGet() throws IOException {
        // store a blob in the transient store
        Blob blob = Blobs.createBlob("hello");
        String key = downloadService.storeBlobs(Collections.singletonList(blob));

        // === do a HEAD first ===

        // mock request
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("HEAD");

        // mock response
        HttpServletResponse response = mock(HttpServletResponse.class);

        downloadService.downloadBlob(request, response, key, "download");

        // === then do a GET ===

        // mock request
        request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");

        // mock response
        response = mock(HttpServletResponse.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ServletOutputStream sos = new DummyServletOutputStream(out);
        PrintWriter printWriter = new PrintWriter(sos);
        when(response.getOutputStream()).thenReturn(sos);
        when(response.getWriter()).thenReturn(printWriter);

        downloadService.downloadBlob(request, response, key, "download");

        // download succeeded
        assertEquals("hello", out.toString()); // decode with system default
    }

    @Test
    public void testGetDownloadPathAndAction() {
        DownloadServiceImpl downloadServiceImpl = new DownloadServiceImpl();
        String path = "nxfile/default/3727ef6b-cf8c-4f27-ab2c-79de0171a2c8/sysfiles_blobs/0/blob/image.png";
        Pair<String, Action> pair = downloadServiceImpl.getDownloadPathAndAction(path);
        assertEquals("default/3727ef6b-cf8c-4f27-ab2c-79de0171a2c8/sysfiles_blobs/0/blob/image.png", pair.getLeft());
        assertEquals(Action.DOWNLOAD_FROM_DOC, pair.getRight());

        path = "plop/default/3727ef6b-cf8c-4f27-ab2c-79de0171a2c8/sysfiles_blobs/0/blob/image.png";
        pair = downloadServiceImpl.getDownloadPathAndAction(path);
        assertNull(pair);
    }

    @Test
    public void testResolveBlobFromDownloadUrl() throws IOException {
        String repositoryName = "test";
        CoreSession session = CoreInstance.getCoreSession(repositoryName);
        DocumentModel doc = session.createDocumentModel("/", "James-Bond", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "Diamonds are forever");

        FileBlob blob = new FileBlob("Synopsis");
        String blobFilename = "synopsis.txt";
        blob.setFilename(blobFilename);

        Map<String, Object> fileMap = new HashMap<>();
        fileMap.put("blob", blob);
        List<Map<String, Object>> docFiles = new ArrayList<>();
        docFiles.add(fileMap);

        doc.setPropertyValue("sysfiles_blobs", docFiles);
        doc.create();
        session.save();

        String path = "nxfile/" + repositoryName + "/" + doc.getId() + "/sysfiles_blobs/0/blob/" + blobFilename;
        Blob resolvedBlob = downloadService.resolveBlobFromDownloadUrl(path);
        assertEquals(blob, resolvedBlob);
    }

    @Test
    public void testDownloadUrl() {
        // Windows path separators
        String filename = "C:\\My Documents\\foo.txt";
        String url = downloadService.getDownloadUrl("default", "1234", SYSFILE_BLOB, filename);
        assertEquals("nxfile/default/1234/sysfile_blob/foo.txt", url);
        // Unix path separators
        filename = "/home/john/foo.txt";
        url = downloadService.getDownloadUrl("default", "1234", SYSFILE_BLOB, filename);
        assertEquals("nxfile/default/1234/sysfile_blob/foo.txt", url);
    }

    @Test
    public void testDownloadBadEncoding() throws Exception {
        // blob to download
        String blobValue = "Hello World Caf\u00e9";
        String encoding = "no-such-charset"; // must not crash DownloadService
        Blob blob = Blobs.createBlob(blobValue.getBytes(), "text/plain", encoding);
        blob.setFilename("myFile.txt");

        // prepare mocks
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = new DummyServletOutputStream(out);
        @SuppressWarnings("resource")
        PrintWriter printWriter = new PrintWriter(sos);
        when(response.getOutputStream()).thenReturn(sos);
        when(response.getWriter()).thenReturn(printWriter);
        // throw exception when setting encoding
        doThrow(new IllegalArgumentException()).when(response).setCharacterEncoding(encoding);

        // send download request
        DownloadContext context = DownloadContext.builder(request, response).blob(blob).build();
        downloadService.downloadBlob(context);

        // check that the blob gets returned even though the encoding was illegal
        assertEquals(blobValue, out.toString()); // decode with system default
    }

    @Test
    public void testDownloadUrlWithURLSanitization() {
        String filename = "/home/john/foo.txt;jsessionid=FooBarBaz;otherparam=foobarbaz";
        String url = downloadService.getDownloadUrl("default", "1234", SYSFILE_BLOB, filename);
        assertEquals("nxfile/default/1234/sysfile_blob/foo.txt", url);

        filename = "/home/john/foo.txt;jsessionid=FooBarBaz";
        url = downloadService.getDownloadUrl("default", "1234", SYSFILE_BLOB, filename);
        assertEquals("nxfile/default/1234/sysfile_blob/foo.txt", url);
    }

    @Test
    public void testDownloadUrlWithSemiColonInTheFilename() {
        String filename = "/home/john/foo;bar.txt";
        String url = downloadService.getDownloadUrl("default", "1234", SYSFILE_BLOB, filename);
        assertEquals("nxfile/default/1234/sysfile_blob/foo%3Bbar.txt", url);
    }

    /**
     * @since 10.3
     */
    @Test
    public void testDownloadUrlWithChangeToken() throws IOException {
        String repositoryName = "test";
        CoreSession session = CoreInstance.getCoreSession(repositoryName);
        Framework.getProperties().setProperty("nuxeo.url", "http://localhost:8080/nuxeo");
        DocumentModel doc = session.createDocumentModel("/", "James-Bond", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "Spectre");

        FileBlob blob = new FileBlob("Synopsis");
        String blobFilename = "synopsis.txt";
        blob.setFilename(blobFilename);

        Map<String, Object> fileMap = new HashMap<>();
        fileMap.put("blob", blob);
        List<Map<String, Object>> docFiles = new ArrayList<>();
        docFiles.add(fileMap);

        doc.setPropertyValue("sysfiles_blobs", docFiles);
        doc.create();
        session.save();

        String pattern = "nxfile/test/.*/sysfile_blob/synopsis.txt\\?changeToken=[^&]+";
        String url = downloadService.getDownloadUrl(repositoryName, doc.getId(), SYSFILE_BLOB, "synopsis.txt",
                doc.getChangeToken());
        assertTrue(url.matches(pattern));
        url = downloadService.getDownloadUrl(doc, SYSFILE_BLOB, "synopsis.txt");
        assertTrue(url.matches(pattern));
    }

    @Test
    public void testDownloadLoggedIfNoByteRange() throws IOException {
        doTestDownloadLoggedOrNot(null, false, "Hello World", 1); // logged
    }

    @Test
    public void testDownloadNotLoggedIfHead() throws IOException {
        doTestDownloadLoggedOrNot(null, true, "", 0); // not logged
    }

    @Test
    public void testDownloadLoggedIfByteRangeFromStart() throws IOException {
        doTestDownloadLoggedOrNot("0-4", false, "Hello", 1); // logged
    }

    @Test
    public void testDownloadNotLoggedIfByteRangeOther() throws IOException {
        doTestDownloadLoggedOrNot("6-10", false, "World", 0); // not logged
    }

    protected void doTestDownloadLoggedOrNot(String range, boolean head, String expectedResult, int expectedSize)
            throws IOException {
        String blobValue = "Hello World";
        Blob blob = Blobs.createBlob(blobValue);
        blob.setFilename("myFile.txt");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(head ? "HEAD" : "GET");
        if (range != null) {
            when(req.getHeader("Range")).thenReturn("bytes=" + range);
        }

        HttpServletResponse resp = mock(HttpServletResponse.class);
        ServletOutputStream sos = new DummyServletOutputStream(out);
        @SuppressWarnings("resource")
        PrintWriter printWriter = new PrintWriter(sos);
        when(resp.getOutputStream()).thenReturn(sos);
        when(resp.getWriter()).thenReturn(printWriter);

        try (CapturingEventListener listener = new CapturingEventListener(DownloadService.EVENT_NAME)) {
            DownloadContext context = DownloadContext.builder(req, resp).blob(blob).reason("test").build();
            downloadService.downloadBlob(context);

            assertEquals(expectedResult, out.toString("UTF-8"));
            assertEquals(expectedSize, listener.getCapturedEventCount(DownloadService.EVENT_NAME));
        }
    }

    @Test
    public void testDownloadWithNginxAccel() throws IOException {
        Framework.getProperties().put(NginxConstants.X_ACCEL_ENABLED, "true");
        try {
            BlobProvider blobProvider = blobService.getBlobProvider("default");
            String digest = blobProvider.writeBlob(Blobs.createBlob(CONTENT));
            BlobInfo blobInfo = new BlobInfo();
            blobInfo.setKey(digest);
            blobInfo.setFilename("cafe.txt");
            blobInfo.setMimeType("text/plain");
            blobInfo.setEncoding("utf-8");
            Blob blob = blobProvider.readBlob(blobInfo);

            // mock request response
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader(NginxConstants.X_ACCEL_LOCATION_HEADER)).thenReturn("/protected_files");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            ServletOutputStream sos = new DummyServletOutputStream(b -> {
                throw new NuxeoException("Not supposed to write to response");
            });
            @SuppressWarnings("resource")
            PrintWriter printWriter = new PrintWriter(sos);
            when(resp.getOutputStream()).thenReturn(sos);
            when(resp.getWriter()).thenReturn(printWriter);

            // download it
            DownloadContext context = DownloadContext.builder(req, resp).blob(blob).reason("test").build();
            downloadService.downloadBlob(context);

            // assert headers (mockito wants us to assert all header in same order they were set)
            verify(resp).setHeader("ETag", '"' + digest + '"');
            verify(resp).setHeader("Content-Disposition", "attachment; filename=cafe.txt");
            verify(resp).setHeader("Accept-Ranges", "bytes");
            verify(resp).setHeader(NginxConstants.X_ACCEL_REDIRECT_HEADER,
                    "/protected_files/cd/cc/cdccc6b7fe935976712efda8c4d79893b704a8ca396d5d76b512304b201a1823");
            // assert others interactions
            verify(resp).setContentType("text/plain");
            verify(resp).setCharacterEncoding("utf-8");
            verify(resp).setContentLengthLong(blob.getLength());
            // assert we end the download
            verifyNoMoreInteractions(resp);
        } finally {
            Framework.getProperties().remove(NginxConstants.X_ACCEL_ENABLED);
        }
    }

}
