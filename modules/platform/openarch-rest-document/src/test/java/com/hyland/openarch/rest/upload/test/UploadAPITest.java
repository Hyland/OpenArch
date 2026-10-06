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
package com.hyland.openarch.rest.upload.test;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NOT_IMPLEMENTED;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.rest.document.test.ContentTestHelper.getJsonPath;
import static com.hyland.openarch.rest.upload.UploadAPI.PATH;
import static com.hyland.openarch.rest.upload.UploadExternalUriClient.UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.transientstore.TransientStorageComponent;
import com.hyland.openarch.ecm.core.transientstore.api.TransientStoreService;
import com.hyland.openarch.rest.upload.Upload;
import com.hyland.openarch.rest.upload.UploadAPI;
import com.hyland.openarch.rest.upload.UploadServiceImpl;
import com.hyland.openarch.rest.upload.UploadStatus;
import com.hyland.openarch.rest.upload.handler.DefaultUploadHandler;
import com.hyland.openarch.rest.utils.Versions;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.WithFrameworkProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.web.client.RestTemplate;

@Features(UploadFeature.class)
@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-store-contrib.xml")
@SpringBootTest(classes = { UploadAPI.class }, properties = { "nuxeo.upload.handler.s3.enabled=false" })
@DirtiesContext
public class UploadAPITest extends AbstractUploadAPITest {

    public static final String CONTENT_TYPE_URI_LIST = "text/uri-list";

    public static final String EXTERNAL_URI = "https://github.githubassets.com/assets/apple-touch-icon-144x144-b882e354c005.png";

    public static final String HYLAND_PNG = "hyland.png";

    public static final String MIME_TYPE = "image/png";

    public static final String MIME_TYPE_OVERRIDE = "image/x-hyland-test";

    public void checkHandler() {
        // this is a session scoped bean and will be injected as a proxy
        assertInstanceOf(DefaultUploadHandler.class, AopTestUtils.getTargetObject(handler));
    }

    public void cleanupTransientStore() {
        ((TransientStorageComponent) Framework.getService(TransientStoreService.class)).cleanUpStores();
    }

    @Test
    public void testCreate() throws Exception {
        var result = checkResponse(CREATED, POST, PATH + "/create");
        result.andExpectAll(header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1), jsonPath("id").exists());
        String uploadId = getJsonPath(result, "id");
        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "fileSize": -1,
                  "partialFileSize": 0,
                  "hasContent": false,
                  "id": "%s",
                  "status": "INCOMPLETE",
                  "attachStatus": "PENDING"
                }
                """.formatted(uploadId), response);
    }

    @Test
    public void testCreateV1() throws Exception {
        var result = checkResponse(CREATED, POST, accept(Versions.APP_HXCS_JSON_V1), PATH + "/create");
        String uploadId = getJsonPath(result, "id");
        var uploadInfo = getUploadInfo(uploadId, NO_CONTENT);
        uploadInfo.andExpect(content().string(""));
    }

    @Test
    public void checkUploadWithExternalURI() throws Exception {
        long fileSize = new RestTemplate().getForObject(EXTERNAL_URI, byte[].class).length;
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        var result = checkResponseWithPayload(CREATED, POST, headers, EXTERNAL_URI, PATH + "?fileName={fileName}",
                HYLAND_PNG);

        String uploadId = getJsonPath(result, "id");
        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "fileName": "%s",
                  "fileSize": %s,
                  "partialFileSize": %s,
                  "hasContent": true,
                  "id": "%s",
                  "mimeType": "%s",
                  "status": "READY",
                  "attachStatus": "PENDING"
                }
                """.formatted(HYLAND_PNG, fileSize, fileSize, uploadId, MIME_TYPE), response);
        // check actual file creation
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals(HYLAND_PNG, blob.getFilename());
        assertEquals(fileSize, blob.getLength());
        assertEquals(MIME_TYPE, blob.getMimeType());
        assertNotNull(blob.getFile());
        assertTrue(blob.getFile().exists());
    }

    @Test
    public void checkUploadWithExternalURIAndExplicitMimeType() throws Exception {
        long fileSize = new RestTemplate().getForObject(EXTERNAL_URI, byte[].class).length;
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        var result = checkResponseWithPayload(CREATED, POST, headers, EXTERNAL_URI,
                PATH + "?fileName={fileName}&mimeType={mimeType}", HYLAND_PNG, CONTENT_TYPE_URI_LIST);

        String uploadId = getJsonPath(result, "id");
        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "fileName": "%s",
                  "fileSize": %s,
                  "partialFileSize": %s,
                  "hasContent": true,
                  "id": "%s",
                  "mimeType": "%s",
                  "status": "READY",
                  "attachStatus": "PENDING"
                }
                """.formatted(HYLAND_PNG, fileSize, fileSize, uploadId, MIME_TYPE), response);
        // if someone mistakenly declare mimetype as text/uri-list, the actual mimetype of the downloaded content is used instead
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals(HYLAND_PNG, blob.getFilename());
        assertEquals(fileSize, blob.getLength());
        assertEquals(MIME_TYPE, blob.getMimeType());
    }

    @Test
    public void checkIgnoreWhenMimetypeIsDeclaredAsUriList() throws Exception {
        long fileSize = new RestTemplate().getForObject(EXTERNAL_URI, byte[].class).length;
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        var result = checkResponseWithPayload(CREATED, POST, headers, EXTERNAL_URI,
                PATH + "?fileName={fileName}&mimeType={mimeType}", HYLAND_PNG, MIME_TYPE_OVERRIDE);

        String uploadId = getJsonPath(result, "id");
        var response = result.andReturn().getResponse();
        checkResponseBody("""
                {
                  "fileName": "%s",
                  "fileSize": %s,
                  "partialFileSize": %s,
                  "hasContent": true,
                  "id": "%s",
                  "mimeType": "%s",
                  "status": "READY",
                  "attachStatus": "PENDING"
                }
                """.formatted(HYLAND_PNG, fileSize, fileSize, uploadId, MIME_TYPE_OVERRIDE), response);
        // the requested mimeType wins over the one advertised by the external resource, the content is still the one
        // downloaded from it
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals(HYLAND_PNG, blob.getFilename());
        assertEquals(fileSize, blob.getLength());
        assertEquals(MIME_TYPE_OVERRIDE, blob.getMimeType());
    }

    @Test
    public void checkUploadWithExternalURISchemeHTTP() throws Exception {
        String uriName = "http://www.example.com/images/earth/earth-night.jpeg";
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        checkResponseWithPayload(BAD_REQUEST, POST, headers, uriName, PATH + "?fileName={fileName}",
                "earth-night.jpeg");
    }

    @Test
    public void checkUploadWithMalformedExternalURI() throws Exception {
        String uriName = "http:// malformed url /images/earth/earth-night.jpeg";
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        checkResponseWithPayload(BAD_REQUEST, POST, headers, uriName, PATH + "?fileName={fileName}",
                "earth-night.jpeg");
    }

    @Test
    public void checkUploadWithMultipleExternalURIs() throws Exception {
        String uriBody = """
                # Authorization: Bearer 0b79bab50daca910b000d4f1a2b675d604257e42
                http://www.example.com/images/earth/earth-night.jpeg
                https://www.example.com/images/earth/earth-day.jpeg
                """;
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        checkResponseWithPayload(BAD_REQUEST, POST, headers, uriBody, PATH + "?fileName={fileName}",
                "earth-night.jpeg");
    }

    @Test
    public void checkUploadWithSingleExternalURIWithFirstLineStartingWithComment() throws Exception {
        String uriBody = """
                # Authorization: Bearer 0b79bab50daca910b000d4f1a2b675d604257e42
                %s
                """.formatted(EXTERNAL_URI);
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        checkResponseWithPayload(CREATED, POST, headers, uriBody, PATH + "?fileName={fileName}", HYLAND_PNG);
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "")
    public void testUploadReturnsBadRequestWithEmptyUriAllowedHostList() throws Exception {
        assertUploadReturnsBadRequestStreamDownload();
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "google.com")
    public void testUploadReturnsBadRequestWhenUriHostNameNotInAllowedHostList() throws Exception {
        assertUploadReturnsBadRequestStreamDownload();
    }

    @Test
    public void testUploadWithHeaderMimeType() throws Exception {
        checkUploadWithURLPattern(PATH + "?fileName={fileName}&mimeType={mimeType};charset={encoding}");
    }

    @Test
    public void testCompleteUpload() throws Exception {
        Upload upload = createTestUpload();
        checkResponseWithPayload(NOT_IMPLEMENTED, POST, REPO_ID, "{}", PATH + "/%s/complete".formatted(upload.getId()));
    }

    @Test
    @WithMockUser(ALT_USER)
    public void testCompleteUploadOtherUser() throws Exception {
        Upload upload = createTestUpload();
        // not supported by default upload handler
        checkResponseWithPayload(NOT_IMPLEMENTED, POST, REPO_ID, "{}", PATH + "/%s/complete".formatted(upload.getId()));
    }

    @Test
    public void testRenewCredentialsError() throws Exception {
        String id = handler.newUpload().getId();
        // not supported by default upload handler
        checkResponse(NOT_IMPLEMENTED, POST, PATH + "/%s/renewCredentials".formatted(id));
    }

    @Test
    public void testRenewCredentialsWithNonExistentId() throws Exception {
        checkResponse(NOT_FOUND, POST,
                PATH + "/%s/renewCredentials".formatted("uploadId-9a55d665-1fe1-45bd-9638-ed647b82e530"));

    }

    @SuppressWarnings("resource") // don't close common pool!
    @Test
    public void testUploadStatus() throws Exception {
        var result = checkResponse(CREATED, POST, PATH + "/create");
        result.andExpectAll(header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1), jsonPath("id").exists());
        String uploadId = getJsonPath(result, "id");
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        result = checkResponseWithPayload(ACCEPTED, POST, headers, EXTERNAL_URI, PATH +
                        "?fileName={fileName}&mimeType=image/png&chunked=true&chunkCount=2&chunkIndex=0&id={id}"
                , HYLAND_PNG, uploadId);
        assertEquals(getJsonPath(result, Upload.UPLOAD_STATUS_FIELD_NAME), UploadStatus.INCOMPLETE.toString());

        Future<UploadStatus> futureStatus;
        UploadServiceImpl._testProcessingStatusBarrier = new CyclicBarrier(2);
        try {
            // observe status in a separate thread
            futureStatus = ForkJoinPool.commonPool().submit(() -> {
                try (var __ = Framework.loginUserOrSystem(USER)) {
                    // wait for processUpload to be in processing status
                    UploadServiceImpl._testProcessingStatusBarrier.await(10, SECONDS);
                    // read the status while processUpload is still held: getStatus re-reads the transient store
                    // on every call, so reading it after the barrier below would race the rest of the upload
                    Upload upload = handler.getUpload(uploadId);
                    UploadStatus observed = upload == null ? null : upload.getStatus();
                    // status has been observed, let processUpload continue
                    UploadServiceImpl._testProcessingStatusBarrier.await(10, SECONDS);
                    return observed;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            });
            // upload last chunk
            checkResponseWithPayload(CREATED, POST, headers, EXTERNAL_URI,
                    UploadAPI.PATH
                            + "?fileName={fileName}&mimeType=image/png&chunked=true&chunkCount=2&chunkIndex=1&id={id}",
                    HYLAND_PNG, uploadId);
        } finally {
            UploadServiceImpl._testProcessingStatusBarrier = null;
        }
        assertEquals(UploadStatus.PROCESSING, futureStatus.get(10, SECONDS));

        waitForStatus(uploadId, UploadStatus.READY);
    }

    @Test
    public void testUploadStatusError() throws Exception {
        var result = checkResponse(CREATED, POST, PATH + "/create");
        result.andExpectAll(header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1), jsonPath("id").exists());
        String uploadId = getJsonPath(result, "id");
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        result = checkResponseWithPayload(ACCEPTED, POST, headers, EXTERNAL_URI, PATH +
                        "?fileName={fileName}&mimeType=image/png&chunked=true&chunkCount=2&chunkIndex=0&id={id}"
                , HYLAND_PNG, uploadId);
        assertEquals(getJsonPath(result, Upload.UPLOAD_STATUS_FIELD_NAME), UploadStatus.INCOMPLETE.toString());
        Upload upload = handler.getUpload(uploadId);
        // enable flag for error simulation
        upload._testTriggerUploadError = true;
        try {
            headers = headers(Versions.APP_HXCS_JSON_V1, "");
            checkResponseWithPayload(INTERNAL_SERVER_ERROR, POST, headers, EXTERNAL_URI, PATH +
                            "?fileName={fileName}&mimeType=image/png&chunked=true&chunkCount=2&chunkIndex=1&id={id}"
                    , HYLAND_PNG, uploadId);
            assertEquals(UploadStatus.ERROR, handler.getUpload(uploadId).getStatus());
        } finally {
            upload._testTriggerUploadError = false;
        }
    }

    private void assertUploadReturnsBadRequestStreamDownload() throws Exception {
        String uriBody = "https://www.example.com/images/earth/earth-night.jpeg";
        HttpHeaders headers = headers(Versions.APP_HXCS_JSON_V1, CONTENT_TYPE_URI_LIST);
        checkResponseWithPayload(BAD_REQUEST, POST, headers, uriBody, PATH + "?fileName={fileName}",
                "earth-night.jpeg");
    }

    private void waitForStatus(String uploadId, UploadStatus expectedStatus) {
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Framework.loginUserOrSystem(USER);
                    Upload upload = handler.getUpload(uploadId);
                    if (upload == null) {
                        return false;
                    }
                    UploadStatus currentStatus = upload.getStatus();
                    return currentStatus == expectedStatus;
                });
    }

}
