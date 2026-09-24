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
package org.nuxeo.rest.download.test;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import jakarta.ws.rs.core.Response.Status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.impl.UserPrincipal;
import com.hyland.openarch.ecm.core.event.test.CapturingEventListener;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import org.nuxeo.rest.download.DownloadManager;
import org.nuxeo.rest.download.DownloadManager.BlobStreamingOutput;
import com.hyland.openarch.runtime.api.login.LoginService;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.cache")
public class DownloadManagerTest {

    private static final String CONTENT = "Hello World";

    private static final String FILENAME = "myFile.txt";

    @RuntimeInject
    protected CoreSession session;

    @RuntimeInject
    protected LoginService loginService;

    protected String repoId;

    DownloadManager downloadManager;

    MockHttpServletRequest request;

    @BeforeEach
    public void before() {
        repoId = session.getRepositoryName();
        request = new MockHttpServletRequest();
        this.downloadManager = new DownloadManager(request);
    }

    @AfterEach
    public void after() {
        repoId = null;
    }

    @Test
    public void testBasicDownloadGet() throws IOException {
        doTestBasicDownload(false);
    }

    @Test
    public void testBasicDownloadHead() throws IOException {
        doTestBasicDownload(true);
    }

    protected void doTestBasicDownload(boolean head) throws IOException {
        // ascii filename is used directly
        doTestBasicDownload(head, "cafe.txt", "filename=cafe.txt");
        // non-ascii filename gets RFC2231 encoding
        doTestBasicDownload(head, "caf\u00e9.txt", "filename*=UTF-8''caf%C3%A9.txt");
    }

    protected void doTestBasicDownload(boolean head, String filename, String filenameInHeader) throws IOException {
        createDownloadManager(head, null);
        // blob to download
        String blobValue = CONTENT + " Caf\u00e9";
        String mimeType = "text/plain";
        String encoding = "ISO-8859-1";
        String digest = "12345";
        Blob blob = createBlob(blobValue, mimeType, encoding, filename, digest);
        DocumentModel doc = createDocument(blob);
        Instant lastModified = (Instant) doc.getPropertyValue(SYS_MODIFIED);

        // send download request
        ResponseEntity<StreamingResponseBody> resp = downloadManager.download(repoId, doc.getId(),
                SYSFILE_BLOB, filename, false);

        // assert headers
        checkResponseHeader('"' + digest + '"', "ETag", resp);
        checkResponseHeader("attachment; " + filenameInHeader, "Content-Disposition", resp);
        checkResponseHeader("bytes", "Accept-Ranges", resp);
        checkResponseHeader(String.valueOf(blob.getLength()), "Content-Length", resp);
        assertEquals(blob.getLength(), resp.getHeaders().getContentLength());
        checkResponseHeader(String.valueOf(lastModified.toEpochMilli()), "Last-Modified", resp);
        // assert mime type
        assertEquals(mimeType, resp.getHeaders().getContentType().toString());
        // check that the blob gets returned (except if HEAD)
        checkResponseBlob(head ? null : blobValue, resp);
    }

    private void createDownloadManager(boolean head, HttpHeaders headers) {
        request.setMethod(head ? "HEAD" : "GET");
        if (headers != null) {
            headers.forEach((key, value) ->
            {
                if (value != null && value.get(0) != null)
                    request.addHeader(key, value.get(0));
            });
        }
        downloadManager = new DownloadManager(request);
    }

    @Test
    public void testETagHeaderNone() throws IOException {
        doTestETagHeader(null);
    }

    @Test
    public void testETagHeaderNotMatched() throws IOException {
        doTestETagHeader(FALSE);
    }

    @Test
    public void testETagHeaderMatched() throws IOException {
        doTestETagHeader(TRUE);
    }

    protected void doTestETagHeader(Boolean match) throws IOException {
        // Given a blob
        Blob blob = createBlob(CONTENT, null, null, FILENAME, "12345");
        DocumentModel doc = createDocument(blob);

        String digestToTest;
        if (match == null) {
            digestToTest = null;
        } else if (TRUE.equals(match)) {
            digestToTest = "12345";
        } else {
            digestToTest = "78787";
        }

        // When I send a request a given digest
        HttpHeaders headers = mockHeaders("If-None-Match", '"' + digestToTest + '"');
        createDownloadManager(false, headers);
        ResponseEntity<StreamingResponseBody> resp = download(doc.getId());

        // Then the response differs if the digest match
        if (TRUE.equals(match)) {
            assertFalse(resp.hasBody());
            assertEquals(Status.NOT_MODIFIED.getStatusCode(), resp.getStatusCode().value());
        } else {
            checkResponseBlob(CONTENT, resp);
            checkResponseHeader('"' + blob.getDigest() + '"', "Etag", resp);
        }
    }

    @Test
    public void testETagHeaderNoDigest() throws IOException {
        DocumentModel doc = createDocument();

        HttpHeaders headers = mockHeaders("If-None-Match", "\"a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e\"");
        createDownloadManager(false, headers);
        ResponseEntity<StreamingResponseBody> resp = download(doc.getId());

        assertFalse(resp.hasBody());
        assertEquals(HttpStatus.NOT_MODIFIED, resp.getStatusCode());
    }

    @Test
    public void testWantDigestSHA256() throws IOException {
        doTestWantDigest(Arrays.asList("SHA-256"), "SHA-256=pZGm1Av0IEBKARczz7exkNYsZb8LzaMrV7J32a2fFG4=", null);
    }

    @Test
    public void testWantDigestUnknown() throws IOException {
        doTestWantDigest(Arrays.asList("nosuchalgo"), null, null);
    }

    protected void doTestWantDigest(List<String> wanted, String expectedDigest, String expectedContentSHA256)
            throws IOException {
        DocumentModel doc = createDocument();
        long length = 11;

        HttpHeaders headers = mockHeaders("Want-Digest", wanted);
        createDownloadManager(false, headers);

        ResponseEntity<StreamingResponseBody> resp = download(doc.getId());

        checkResponseHeader(String.valueOf(length), "Content-Length", resp);
        assertEquals(length, resp.getHeaders().getContentLength());
        checkResponseHeader("\"a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e\"", "ETag", resp);
        if (expectedDigest != null) {
            checkResponseHeader(expectedDigest, "Digest", resp);
        }
    }

    /**
     * @since 9.3
     */
    @Test
    public void testDocumentDefaultDownload() throws IOException {
        // blob to download
        DocumentModel doc = createDocument();
        session.save();

        // send download request, should return main content
        ResponseEntity<StreamingResponseBody> resp = download(doc.getId());
        checkResponseBlob(CONTENT, resp);
    }

    @Test
    public void testGeneratePresignedUrlForWrongDocId() {
        long expiration = 5000; // global expiry set to 3600s
        int statusCode = assertThrows(RepositoryException.class, () ->
            downloadUrl("docid", expiration)).getStatusCode();
        assertEquals(HttpStatus.NOT_FOUND.value(), statusCode);
    }

    @Test
    @Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-presignedurl-provider-contrib.xml")
    public void testGeneratePresignedUrlWithoutFilename() throws IOException {
        // blob to download
        DocumentModel doc = createDocument();
        session.save();
        long expiration = 5000; // global expiry set to 3600s
        var resp = downloadUrl(doc.getId(), expiration, null);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-presignedurl-provider-contrib.xml")
    public void testGeneratePresignedUrlOnGlobalExpiryPost() throws IOException {

        DocumentModel doc = createDocument();
        session.save();
        long expiration = 5000; // global expiry set to 3600s
        ResponseEntity<String> resp = downloadUrl(doc.getId(), expiration);
        checkResponseUrl(resp, 3600);
    }

    @Test
    public void testDownloadNonExistingBlob() {

        // blob to download
        DocumentModel doc = createDocument();
        String docId = doc.getId();
        // principal
        RepositoryPrincipal principal = new UserPrincipal("bob");

        // do tests while logged in
        try (var __ = loginService.loginPrincipal(principal)) {
            // send download request for non existing doc, should be not found
            RepositoryException responseException = assertThrows(RepositoryException.class, () ->
                downloadManager.download(repoId, "foo", SYSFILE_BLOB, FILENAME, false));
            assertEquals(HttpStatus.NOT_FOUND.value(), responseException.getStatusCode());
            assertEquals("No document found", responseException.getMessage());
            // send download request for non existing prop, should be not found
            responseException = assertThrows(RepositoryException.class, () ->
                downloadManager.download(repoId, docId, "foo:bar", FILENAME, false));
            assertEquals(HttpStatus.NOT_FOUND.value(), responseException.getStatusCode());
            assertEquals("No document found", responseException.getMessage());
        }
    }

    @Test
    public void testDownloadBadEncoding() throws IOException {
        // blob to download
        String blobValue = CONTENT + " Caf\u00e9";
        String encoding = "no-such-charset";
        Blob blob = Blobs.createBlob(blobValue.getBytes(), "text/plain", encoding);
        blob.setFilename(FILENAME);
        DocumentModel doc = createDocument(blob);

        // send download request
        ResponseEntity<StreamingResponseBody> resp = download(doc.getId());
        // check that the blob gets returned even though the encoding was illegal
        BlobStreamingOutput out = (BlobStreamingOutput)resp.getBody();
        assertNotNull(out);
        try (Reader reader = new InputStreamReader(out.getBlob().getStream(), UTF_8)) {
            assertEquals(blobValue, IOUtils.toString(reader));
        }
    }

    @Test
    public void testDownloadLoggedIfNoByteRange() throws IOException {
        doTestDownloadLoggedOrNot(null, false, CONTENT, 1); // logged
    }

    @Test
    public void testDownloadNotLoggedIfHead() throws IOException {
        doTestDownloadLoggedOrNot(null, true, null, 0); // not logged
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
        DocumentModel doc = createDocument();
        HttpHeaders headers = mockHeaders("Range", range == null ? null : "bytes=" + range);
        createDownloadManager(head, headers);

        try (CapturingEventListener listener = new CapturingEventListener(DownloadManager.EVENT_NAME)) {
            ResponseEntity<StreamingResponseBody> resp = download(doc.getId());
            checkResponseBlob(expectedResult, resp);
            assertEquals(expectedSize, listener.getCapturedEventCount(DownloadManager.EVENT_NAME));
        }
    }

    protected HttpHeaders mockHeaders(String name, Object value) {
        HttpHeaders headers = new HttpHeaders();
        if (name != null) {
            if (value instanceof String) {
                headers.add(name, (String) value);
            } else if (value instanceof List) {
                headers.addAll(name, (List<String>) value);
            }
        }
        return headers;
    }

    protected ResponseEntity<StreamingResponseBody> download(String docId) throws IOException {
        return downloadManager.download(repoId, docId, SYSFILE_BLOB, FILENAME, false);
    }

    protected ResponseEntity<String> downloadUrl(String docId, long expirationSeconds, String fileName) throws IOException {
        return downloadManager.generatePresignedUrl(repoId, docId, SYSFILE_BLOB, fileName, expirationSeconds);
    }

    protected ResponseEntity<String> downloadUrl(String docId, long expirationSeconds) throws IOException {
        return downloadManager.generatePresignedUrl(repoId, docId, SYSFILE_BLOB, FILENAME, expirationSeconds);
    }

    protected DocumentModel createDocument() {
        Blob blob = createBlob(CONTENT, null, null, FILENAME, null);
        return createDocument(blob);
    }

    protected Blob createBlob(String content, String mimeType, String encoding, String filename, String digest) {
        Blob blob = Blobs.createBlob(content);
        if (mimeType != null) {
            blob.setMimeType(mimeType);
        }
        if (encoding != null) {
            blob.setEncoding(encoding);
        }
        blob.setFilename(filename);
        if (digest != null) {
            blob.setDigest(digest);
        }
        return blob;
    }

    protected DocumentModel createDocument(Blob blob) {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();
        session.save();
        doc.refresh();
        return doc;
    }

    protected void checkResponseBlob(String content, ResponseEntity<StreamingResponseBody> resp) throws IOException {
        if (content == null) {
            assertFalse(resp.hasBody());
        } else {
            BlobStreamingOutput out = (BlobStreamingOutput)resp.getBody();
            assertNotNull(out);
            assertNotNull(out.getBlob());
            String encoding = out.getBlob().getEncoding();
            if (encoding == null) {
                encoding = UTF_8.name();
            }
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            out.writeTo(bout);
            assertEquals(content, bout.toString(encoding));
        }
    }

    protected void checkResponseUrl(ResponseEntity<String> resp, long expiration) {
        String url = resp.getBody();
        assertTrue(containsPreSignedURLWithExpiration(url, expiration));
    }

    public static boolean containsPreSignedURLWithExpiration(String urlString, long expiration) {

        // Regular expression to match expiration timestamp in the URL
        Pattern pattern = Pattern.compile("Expires=(\\d+)");
        Matcher matcher = pattern.matcher(urlString);
        // Check if the URL contains a pre-signed URL with expiration
        boolean containsPreSignedURL = urlString.contains("https://s3.amazonaws.com") && urlString.contains("Expires=");
        if (containsPreSignedURL) {
            // If the URL contains "Expires=", extract and parse the expiration timestamp
            if (matcher.find()) {
                String expirationString = matcher.group(1);
                long expirationTimestamp = Long.parseLong(expirationString);
                assertEquals(expiration, expirationTimestamp);
                return true;
            }
        }
        return false;
    }

    protected void checkResponseHeader(String expectedValue, String name, ResponseEntity resp) {
        assertEquals(expectedValue, resp.getHeaders().get(name).get(0));
    }

}
