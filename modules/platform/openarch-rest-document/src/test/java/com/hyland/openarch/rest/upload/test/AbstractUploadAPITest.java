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
package com.hyland.openarch.rest.upload.test;

import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static com.hyland.openarch.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static com.hyland.openarch.ecm.core.api.impl.blob.AbstractBlob.UTF_8;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static com.hyland.openarch.rest.document.test.ContentTestHelper.getJsonPath;
import static com.hyland.openarch.rest.upload.UploadAPI.PATH;
import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.local.WithUser;
import com.hyland.openarch.rest.document.test.AbstractAPITest;
import com.hyland.openarch.rest.document.test.ContentTestHelper;
import com.hyland.openarch.rest.upload.Upload;
import com.hyland.openarch.rest.upload.handler.UploadHandler;
import com.hyland.openarch.rest.upload.test.config.UploadApiTestConfiguration;
import com.hyland.openarch.rest.utils.Versions;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.ws.rs.core.Response.StatusType;
import net.minidev.json.JSONArray;

@Features(UploadFeature.class)
@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-store-contrib.xml")
@Import(UploadApiTestConfiguration.class)
public abstract class AbstractUploadAPITest extends AbstractAPITest {

    private static final String PATH_CREATE = PATH + "/create";

    protected static final String USER = TEST_ADMIN_USER;

    protected static final JSONComparator COMPARATOR = new CustomComparator(NON_EXTENSIBLE);

    @Autowired
    protected UploadHandler handler;

    @BeforeEach
    public void runCheckUploadHandler() {
        checkHandler();
    }

    @BeforeEach
    public void checkCurrentUser() {
        RepositoryPrincipal pal = RepositoryPrincipal.getCurrent();
        assertNotNull(pal);
        assertEquals(USER, pal.getId());
    }

    @AfterEach
    public void cleanupTransientStores() {
        cleanupTransientStore();
    }

    /**
     * Check the handler is the expected one.
     */
    protected abstract void checkHandler();

    protected abstract void cleanupTransientStore();

    protected ResultActions getUploadInfo(String uploadId, StatusType expectedStatus) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(ACCEPT, Versions.APP_HXCS_JSON_V1);
        return checkResponse(expectedStatus, GET, headers, PATH + "/%s".formatted(uploadId));
    }

    protected Upload createTestUpload() {
        Upload upload = handler.newUpload();
        assertEquals(USER, upload.getUser());
        upload.addBlob(Blobs.createBlob("test content", TEXT_PLAIN, UTF_8, "test.txt"));
        return upload;
    }

    protected Upload createTestEmptyUpload() {
        Upload upload = handler.newUpload();
        assertEquals(USER, upload.getUser());
        return upload;
    }

    protected HttpHeaders accept(String accept) {
        return headers(accept, null);
    }

    protected HttpHeaders contentType(String contentType) {
        return headers(null, contentType);
    }

    protected HttpHeaders headers(String accept, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        if (isNotEmpty(accept)) {
            headers.add(ACCEPT, accept);
        }
        if (isNotEmpty(contentType)) {
            headers.add(CONTENT_TYPE, contentType);
        }
        return headers;
    }

    protected JSONComparator getComparator() {
        return COMPARATOR;
    }

    protected void checkResponseBody(String expected, MockHttpServletResponse response, String encoding)
            throws Exception {
        response.setCharacterEncoding(encoding);
        String actual = response.getContentAsString();
        JSONAssert.assertEquals(expected, actual, getComparator());
    }

    protected void checkResponseBody(String expected, MockHttpServletResponse response) throws Exception {
        checkResponseBody(expected, response, UTF_8);
    }

    protected void checkUnauthenticated(HttpMethod method, String path) throws Exception {
        var result = mvc.perform(request(method, path).with(csrf()));
        result.andExpect(status().isUnauthorized());
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testCreateUnauthenticated() throws Exception {
        checkUnauthenticated(POST, PATH_CREATE);
    }

    @Test
    public void testCreateV0() throws Exception {
        checkResponse(NOT_ACCEPTABLE, GET, accept("application/vnd.hxcs.v0+json"), PATH_CREATE);
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testUploadUnauthenticated() throws Exception {
        checkUnauthenticated(POST, PATH);
    }

    @Test
    public void testUpload() throws Exception {
        checkUploadWithURLPattern(PATH + "?fileName={fileName}&mimeType={mimeType}&encoding={encoding}");
    }

    @Test
    public void testUploadBodyEncodingBase64() throws Exception {
        String body = "Contenu accentué du fichier";
        String inputBody = Base64.getEncoder().encodeToString(body.getBytes(UTF_8));
        checkUploadWithURLPattern(
                PATH + "?fileName={fileName}&mimeType={mimeType}&encoding={encoding}&bodyEncoding=base64",
                inputBody, CREATED, body);
    }

    @Test
    public void testUploadBodyEncodingBase64Truncated() throws Exception {
        // proper base64 for "abcd" is "YWJjZA", try when truncating the last char
        checkUploadWithURLPattern(
                PATH + "?fileName={fileName}&mimeType={mimeType}&encoding={encoding}&bodyEncoding=base64", //
                "YWJjZ", BAD_REQUEST, null);
    }

    @Test
    public void testUploadBodyEncodingBase64InvalidBody() throws Exception {
        checkUploadWithURLPattern(
                PATH + "?fileName={fileName}&mimeType={mimeType}&encoding={encoding}&bodyEncoding=base64",
                "!!!not-base64!!!", BAD_REQUEST, null);
    }

    @Test
    public void testUploadBodyEncodingInvalid() throws Exception {
        checkUploadWithURLPattern(
                PATH + "?fileName={fileName}&mimeType={mimeType}&encoding={encoding}&bodyEncoding=invalid", "",
                BAD_REQUEST, null);
    }

    protected void checkUploadWithURLPattern(String pattern) throws Exception {
        String body = "Contenu accentué du fichier";
        checkUploadWithURLPattern(pattern, body, CREATED, body);
    }

    protected void checkUploadWithURLPattern(String pattern, String inputBody, StatusType expectedStatus,
            String expectedBody) throws Exception {
        byte[] data = inputBody.getBytes(UTF_8);
        String fileName = "Fichier accentué.txt";
        var result = checkResponseWithPayload(expectedStatus, POST,
                contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE), pattern, data, fileName, TEXT_PLAIN, UTF_8);
        if (expectedStatus != CREATED) {
            // error cases don't need to check result body
            return;
        }
        long fileSize = expectedBody.getBytes(UTF_8).length;
        String uploadId = ContentTestHelper.getJsonPath(result, "id");
        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "fileName": "%s",
                  "fileSize": %s,
                  "partialFileSize": %s,
                  "hasContent": true,
                  "id": "%s",
                  "encoding": "UTF-8",
                  "mimeType": "text/plain",
                  "status": "READY",
                  "attachStatus": "PENDING"
                }
                """.formatted(fileName, fileSize, fileSize, uploadId), response);
        // check actual file creation
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals(fileName, blob.getFilename());
        assertEquals(fileSize, blob.getLength());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals(expectedBody, blob.getString());
        assertNotNull(blob.getFile());
        assertTrue(blob.getFile().exists());
    }

    @Test
    public void testUploadEmpty() throws Exception {
        var result = checkResponse(CREATED, POST, contentType(APPLICATION_OCTET_STREAM_VALUE), PATH).andExpect(
                jsonPath("id").exists());
        String uploadId = getJsonPath(result, "id");
        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "fileName": "file.bin",
                  "fileSize": 0,
                  "partialFileSize": 0,
                  "hasContent": true,
                  "id": "%s",
                  "mimeType":"application/octet-stream",
                  "status": "READY",
                  "attachStatus": "PENDING"
                }
                """.formatted(uploadId), response);
        getUploadInfo(uploadId, OK);
    }

    @Test
    public void testUploadFileWithContentTypeMultipartFormDataShouldReturnHttp415() throws Exception {
        byte[] data = "Contenu accentué du fichier".getBytes(StandardCharsets.UTF_8);
        String fileName = "Fichier accentué.txt";
        checkResponseWithPayloadWithContentTypeMultipartFormData(UNSUPPORTED_MEDIA_TYPE, POST, REPO_ID, null,
                PATH, new MockMultipartFile[] { new MockMultipartFile("file", fileName, TEXT_PLAIN, data) });
    }

    @Test
    public void testReUploadSameId() throws Exception {
        HttpHeaders headers = contentType(TEXT_PLAIN);
        var result = checkResponseWithPayload(CREATED, POST, headers, "test content",
                PATH + "?fileName={fileName}&mimeType={mimeType}", "testfile.txt", TEXT_PLAIN);
        result.andExpect(header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1));
        String uploadId = getJsonPath(result, "id");
        getUploadInfo(uploadId, OK);

        result = checkResponseWithPayload(CREATED, POST, headers, "test content modified",
                PATH + "?id={id}&fileName={fileName}&mimeType={mimeType}", uploadId, "testfile2.txt", TEXT_PLAIN);
        result.andExpect(header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1));
        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "fileName": "testfile2.txt",
                  "fileSize": 21,
                  "partialFileSize": 21,
                  "hasContent": true,
                  "id": "%s",
                  "mimeType": "text/plain",
                  "status": "READY",
                  "attachStatus": "PENDING"
                }
                """.formatted(uploadId), response);
        getUploadInfo(uploadId, OK);

        // check actual file creation
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals("testfile2.txt", blob.getFilename());
        assertEquals(21, blob.getLength());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals("test content modified", blob.getString());
        assertNotNull(blob.getFile());
        assertTrue(blob.getFile().exists());
    }

    @Test
    @WithUser(ALT_USER)
    public void testReUploadOtherUser() throws Exception {
        Upload upload = createTestUpload();
        String uploadId = upload.getId();

        // check original upload content
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals("test.txt", blob.getFilename());
        assertEquals(12, blob.getLength());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals("test content", blob.getString());
        assertNotNull(blob.getFile());
        assertTrue(blob.getFile().exists());

        checkResponseWithPayload(FORBIDDEN, POST, contentType(TEXT_PLAIN), "test content modified",
                PATH + "?id={id}&fileName={fileName}&mimeType={mimeType}", uploadId, "testfile2.txt", TEXT_PLAIN);

        // check content has not changed
        blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals("test.txt", blob.getFilename());
        assertEquals(12, blob.getLength());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals("test content", blob.getString());
        assertNotNull(blob.getFile());
        assertTrue(blob.getFile().exists());
    }

    @Test
    public void testUploadChunks() throws Exception {
        HttpHeaders headers = contentType(TEXT_PLAIN);
        var result = checkResponseWithPayload(ACCEPTED, POST, headers, "test content",
                PATH + "?fileName={fileName}&chunked=true&chunkCount=3&chunkIndex=0&encoding={encoding}",
                "testfile.txt", "ISO-8859-1");
        result.andExpectAll(header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1), jsonPath("id").exists());
        String uploadId = getJsonPath(result, "id");
        int fileSize = getJsonPath(result, "fileSize");
        int partialFileSize = getJsonPath(result, "partialFileSize");
        JSONArray actualChunkIndex = getJsonPath(result, "chunkIndexes");
        JSONArray expectedChunkIndex = new JSONArray();
        expectedChunkIndex.add(0);
        assertEquals(expectedChunkIndex, actualChunkIndex);
        assertEquals(-1, fileSize);
        assertEquals(12, partialFileSize);

        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "chunkCount": 3,
                  "chunkIndexes": [
                    0
                  ],
                  "chunksCompleted": false,
                  "fileName": "testfile.txt",
                  "fileSize": -1,
                  "partialFileSize": 12,
                  "id": "%s",
                  "hasContent": true,
                  "mimeType": "text/plain",
                  "encoding": "ISO-8859-1",
                  "status": "INCOMPLETE",
                  "attachStatus": "PENDING"
                }
                """.formatted(uploadId), response);
        getUploadInfo(uploadId, ACCEPTED);

        // send another chunk (not the next one)
        result = checkResponseWithPayload(ACCEPTED, POST, headers, "in chunks",
                PATH + "?id={id}&chunked=true&chunkCount=3&chunkIndex=2", uploadId).andExpect(
                        header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1));
        fileSize = getJsonPath(result, "fileSize");
        partialFileSize = getJsonPath(result, "partialFileSize");
        actualChunkIndex = getJsonPath(result, "chunkIndexes");
        expectedChunkIndex.add(2);

        assertEquals(expectedChunkIndex, actualChunkIndex);
        assertEquals(-1, fileSize);
        assertEquals(21, partialFileSize);

        getUploadInfo(uploadId, ACCEPTED);
        response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "chunkCount": 3,
                  "chunkIndexes": [
                    0,
                    2
                  ],
                  "chunksCompleted": false,
                  "fileName": "testfile.txt",
                  "fileSize": -1,
                  "partialFileSize": 21,
                  "hasContent": true,
                  "id": "%s",
                  "mimeType": "text/plain",
                  "encoding": "ISO-8859-1",
                  "status": "INCOMPLETE",
                  "attachStatus": "PENDING"
                }
                """.formatted(uploadId), response);
        // send last chunk
        result = checkResponseWithPayload(CREATED, POST, headers, " but this time ",
                PATH + "?id={id}&chunked=true&chunkCount=3&chunkIndex=1", uploadId).andExpect(
                        header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1));

        fileSize = getJsonPath(result, "fileSize");
        partialFileSize = getJsonPath(result, "partialFileSize");
        actualChunkIndex = getJsonPath(result, "chunkIndexes");
        // chunk 1 is inserted between 0 and 2 in the chunk indexes
        expectedChunkIndex.add(1, 1);

        assertEquals(expectedChunkIndex, actualChunkIndex);
        assertEquals(36, fileSize);
        assertEquals(fileSize, partialFileSize);

        response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "chunkCount": 3,
                  "chunkIndexes": [
                    0,
                    1,
                    2
                  ],
                  "chunksCompleted": true,
                  "fileName": "testfile.txt",
                  "fileSize": 36,
                  "partialFileSize": 36,
                  "hasContent": true,
                  "id": "%s",
                  "mimeType": "text/plain",
                  "encoding": "ISO-8859-1",
                  "status": "READY",
                  "attachStatus": "PENDING"
                }
                """.formatted(uploadId), response);
        getUploadInfo(uploadId, OK);
        // check actual file creation
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals("testfile.txt", blob.getFilename());
        assertEquals(36, blob.getLength());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals("test content but this time in chunks", blob.getString());
        assertNotNull(blob.getFile());
        assertTrue(blob.getFile().exists());
    }

    @Test
    public void testUploadSingleChunk() throws Exception {
        // a chunked upload with chunkCount=1 must transition the status to READY after the only chunk
        // is received (regression test for HXCS-8375)
        HttpHeaders headers = contentType(TEXT_PLAIN);
        String body = "test content";
        var result = checkResponseWithPayload(CREATED, POST, headers, body,
                PATH + "?fileName={fileName}&chunked=true&chunkCount=1&chunkIndex=0&encoding={encoding}",
                "testfile.txt", "ISO-8859-1");
        result.andExpectAll(header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1), jsonPath("id").exists());
        String uploadId = getJsonPath(result, "id");
        long fileSize = body.getBytes(UTF_8).length;
        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "chunkCount": 1,
                  "chunkIndexes": [
                    0
                  ],
                  "chunksCompleted": true,
                  "fileName": "testfile.txt",
                  "fileSize": %s,
                  "partialFileSize": %s,
                  "hasContent": true,
                  "id": "%s",
                  "mimeType": "text/plain",
                  "encoding": "ISO-8859-1",
                  "status": "READY",
                  "attachStatus": "PENDING"
                }
                """.formatted(fileSize, fileSize, uploadId), response);
        getUploadInfo(uploadId, OK);
        // check actual file creation
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals("testfile.txt", blob.getFilename());
        assertEquals(fileSize, blob.getLength());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals(body, blob.getString());
        assertNotNull(blob.getFile());
        assertTrue(blob.getFile().exists());
    }

    @Test
    public void testUploadChunksNoChunkIndex() throws Exception {
        HttpHeaders headers = contentType(TEXT_PLAIN);
        checkResponseWithPayload(BAD_REQUEST, POST, headers, "test content",
                PATH + "?fileName={fileName}&chunked=true&chunkCount=3", "testfile.txt");
    }

    @Test
    public void testUploadChunksNoChunkCount() throws Exception {
        HttpHeaders headers = contentType(TEXT_PLAIN);
        checkResponseWithPayload(BAD_REQUEST, POST, headers, "test content",
                PATH + "?fileName={fileName}&chunked=true&chunkIndex=0", "testfile.txt");
    }

    @Test
    public void testUploadWithNonExistentId() throws Exception {
        HttpHeaders headers = contentType(TEXT_PLAIN);
        checkResponseWithPayload(NOT_FOUND, POST, headers, "in chunks",
                PATH + "?id={id}&chunked=true&chunkCount=3&chunkIndex=2",
                "uploadId-9a55d665-1fe1-45bd-9638-ed647b82e530");

    }

    @Test
    public void testUploadWithEmptyMimeTypeShouldReturnApplicationOctetStream() throws Exception {
        HttpHeaders headers = contentType(null);
        var result = checkResponseWithPayload(CREATED, POST, headers, "test content",
                PATH + "?fileName={fileName}&mimeType=",
                "testfile.txt");
        result.andExpectAll(header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1), jsonPath("id").exists());
        String uploadId = getJsonPath(result, "id");

        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "fileName": "testfile.txt",
                  "fileSize": 12,
                  "partialFileSize": 12,
                  "id": "%s",
                  "hasContent": true,
                  "mimeType": "%s",
                  "status": "READY",
                  "attachStatus": "PENDING"
                }
                """.formatted(uploadId, APPLICATION_OCTET_STREAM_VALUE), response);
        getUploadInfo(uploadId, OK);

        // check actual file creation
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals("testfile.txt", blob.getFilename());
        assertEquals(12, blob.getLength());
        assertEquals(APPLICATION_OCTET_STREAM_VALUE, blob.getMimeType());
        assertEquals("test content", blob.getString());
        assertNotNull(blob.getFile());
        assertTrue(blob.getFile().exists());
    }

    @Test
    public void testUploadInfo() throws Exception {
        Upload upload = createTestUpload();
        String uploadId = upload.getId();
        var result = checkResponse(OK, GET, PATH + "/" + uploadId).andExpect(
                header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1));
        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "fileName": "test.txt",
                  "fileSize": 12,
                  "partialFileSize": 12,
                  "hasContent": true,
                  "id": "%s",
                  "encoding": "UTF-8",
                  "mimeType": "text/plain",
                  "status": "INCOMPLETE",
                  "attachStatus": "PENDING"
                }
                """.formatted(uploadId), response);
    }

    @Test
    @WithUser(ALT_USER)
    public void testUploadInfoOtherUser() throws Exception {
        Upload upload = createTestUpload();
        checkResponse(FORBIDDEN, GET, PATH + "/" + upload.getId());
    }

    @Test
    public void testUploadInfoWithWrongIdFormat() throws Exception {
        checkResponse(BAD_REQUEST, GET, PATH + "/d783h03-32gdg-2389dfz");
    }

    @Test
    public void testUploadInfoWithNonExistentId() throws Exception {
        checkResponse(NOT_FOUND, GET, PATH + "/uploadId-9a55d665-1fe1-45bd-9638-ed647b82e530");
    }

    @Test
    public void testUploadInfoWithUploadWithoutContent() throws Exception {
        Upload upload = createTestEmptyUpload();
        checkResponse(NO_CONTENT, GET, PATH + "/" + upload.getId());
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testUploadInfoUnauthenticated() throws Exception {
        Upload upload = createTestUpload();
        checkUnauthenticated(GET, PATH + "/" + upload.getId());
    }

    @Test
    public void testCancel() throws Exception {
        Upload upload = createTestUpload();
        String id = upload.getId();
        assertNotNull(handler.getUpload(id));
        checkResponse(NO_CONTENT, DELETE, PATH + "/" + id);
        assertNull(handler.getUpload(id));
    }

    @Test
    @WithUser(ALT_USER)
    public void testCancelOtherUser() throws Exception {
        Upload upload = createTestUpload();
        String id = upload.getId();
        assertNotNull(handler.getUpload(id));
        checkResponse(FORBIDDEN, DELETE, PATH + "/" + id);
        assertNotNull(handler.getUpload(id));
    }

    @Test
    public void testCancelWithWrongIdFormat() throws Exception {
        checkResponse(BAD_REQUEST, DELETE, PATH + "/d783h03-32gdg-2389dfz");
    }

    @Test
    public void testCancelWithNonExistentId() throws Exception {
        checkResponse(NOT_FOUND, DELETE, PATH + "/uploadId-9a55d665-1fe1-45bd-9638-ed647b82e530");
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testCancelUnauthenticated() throws Exception {
        Upload upload = createTestUpload();
        String id = upload.getId();
        assertNotNull(handler.getUpload(id));
        checkUnauthenticated(DELETE, PATH + "/" + id);
        assertNotNull(handler.getUpload(id));
    }

    @Test
    public void testCompleteUploadWithWrongIdFormat() throws Exception {
        checkResponse(BAD_REQUEST, POST, PATH + "/d783h03-32gdg-2389dfz/complete");
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testCompleteUploadUnauthenticated() throws Exception {
        String id = handler.newUpload().getId();
        // upload not completed
        checkUnauthenticated(GET, PATH + "/%s/complete".formatted(id));
    }

    @Test
    public void testRenewCredentialsErrorWithWrongIdFormat() throws Exception {
        checkResponse(BAD_REQUEST, POST, PATH + "/d783h03-32gdg-2389dfz/renewCredentials");
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testRenewCredentialsUnauthenticated() throws Exception {
        String id = handler.newUpload().getId();
        checkUnauthenticated(POST, PATH + "/%s/renewCredentials".formatted(id));
    }

    @Test
    @WithUser(ALT_USER)
    public void testRenewCredentialsOtherUser() throws Exception {
        String id = handler.newUpload().getId();
        checkResponse(FORBIDDEN, POST, PATH + "/%s/renewCredentials".formatted(id));
    }
}
