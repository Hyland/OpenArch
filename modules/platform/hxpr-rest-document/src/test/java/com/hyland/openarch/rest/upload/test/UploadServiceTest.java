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

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hyland.openarch.rest.upload.test.UploadAPITest.CONTENT_TYPE_URI_LIST;
import static com.hyland.openarch.rest.upload.test.UploadAPITest.EXTERNAL_URI;
import static com.hyland.openarch.rest.upload.test.UploadAPITest.HYLAND_PNG;
import static com.hyland.openarch.rest.upload.test.UploadAPITest.MIME_TYPE;
import static com.hyland.openarch.rest.upload.test.UploadAPITest.MIME_TYPE_OVERRIDE;
import static org.springframework.http.HttpStatus.CREATED;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.rest.upload.Upload;
import com.hyland.openarch.rest.upload.UploadDTO;
import com.hyland.openarch.rest.upload.UploadServiceImpl;
import com.hyland.openarch.rest.upload.handler.DefaultUploadHandler;
import com.hyland.openarch.rest.upload.handler.UploadHandler;
import com.hyland.openarch.runtime.mockito.MockitoFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import org.springframework.http.ResponseEntity;

@ExtendWith(FeaturesExtension.class)
@Features({RuntimeFeature.class, CoreFeature.class, MockitoFeature.class})
public class UploadServiceTest {

    @Test
    public void testUploadServiceWithDirectFileUpload() throws IOException {
        UploadHandler handler = new DefaultUploadHandler();
        String body = "test content";
        String fileName = "test.txt";
        String mimeType = "text/plain";
        String mimeTypeWithEncoding = "text/plain;charset=utf-8";
        UploadDTO uploadDTO = new UploadDTO(null, fileName, mimeTypeWithEncoding, null, null, false, 0, 0);
        var response = new UploadServiceImpl().handleUpload(handler, mimeType, uploadDTO, new ByteArrayInputStream(body.getBytes(UTF_8)));
        assertSuccessfulUploadCreated(response, fileName, mimeType);

        uploadDTO = new UploadDTO(null, fileName, null, null, null, false, 0, 0);
        response = new UploadServiceImpl().handleUpload(handler, mimeType, uploadDTO, new ByteArrayInputStream(body.getBytes(UTF_8)));
        assertSuccessfulUploadCreated(response, fileName, mimeType);
    }

    @Test
    public void testUploadServiceWithExternalURI() throws IOException {
        UploadHandler handler = new DefaultUploadHandler();
        UploadDTO uploadDTO = new UploadDTO(null, HYLAND_PNG, null, null, null, false, 0, 0);
        var response = new UploadServiceImpl().handleUpload(handler, CONTENT_TYPE_URI_LIST, uploadDTO, new ByteArrayInputStream(EXTERNAL_URI.getBytes(UTF_8)));
        assertSuccessfulUploadCreated(response, HYLAND_PNG, MIME_TYPE);
    }

    @Test
    public void testUploadServiceWithExternalURIAndExplicitMimeType() throws IOException {
        UploadHandler handler = new DefaultUploadHandler();
        UploadDTO uploadDTO = new UploadDTO(null, HYLAND_PNG, MIME_TYPE_OVERRIDE, null, null, false, 0, 0);
        var response = new UploadServiceImpl().handleUpload(handler, CONTENT_TYPE_URI_LIST, uploadDTO, new ByteArrayInputStream(EXTERNAL_URI.getBytes(UTF_8)));
        assertSuccessfulUploadCreated(response, HYLAND_PNG, MIME_TYPE_OVERRIDE);
    }

    @Test
    public void testUploadServiceWithExternalURIAndUriListMimetype() throws IOException {
        UploadHandler handler = new DefaultUploadHandler();
        UploadDTO uploadDTO = new UploadDTO(null, HYLAND_PNG, CONTENT_TYPE_URI_LIST, null, null, false, 0, 0);
        var response = new UploadServiceImpl().handleUpload(handler, CONTENT_TYPE_URI_LIST, uploadDTO, new ByteArrayInputStream(EXTERNAL_URI.getBytes(UTF_8)));
        //if mimetype is set to text/uri-list, the service should set the mimetype based on the external resource
        assertSuccessfulUploadCreated(response, HYLAND_PNG, MIME_TYPE);
    }

    @Test
    public void testUploadServiceWithExternalURIAndBlankMimeTypeUsesTheExternalResourceOne() throws IOException {
        UploadHandler handler = new DefaultUploadHandler();
        UploadDTO uploadDTO = new UploadDTO(null, HYLAND_PNG, " ", null, null, false, 0, 0);
        var response = new UploadServiceImpl().handleUpload(handler, CONTENT_TYPE_URI_LIST, uploadDTO, new ByteArrayInputStream(EXTERNAL_URI.getBytes(UTF_8)));
        assertSuccessfulUploadCreated(response, HYLAND_PNG, MIME_TYPE);
    }

    @Test
    public void testUploadServiceWithExternalURIWithFeatureFlagDisabledReturnsBadRequest() {
        UploadHandler handler = new DefaultUploadHandler();
        String uri = "https://www.example.com/test.docx";
        UploadDTO uploadDTO = new UploadDTO(null, "test.docx", CONTENT_TYPE_URI_LIST, null, null, false, 0, 0);
        UploadServiceImpl uploadService = new UploadServiceImpl();
        ByteArrayInputStream data = new ByteArrayInputStream(uri.getBytes(UTF_8));

        RepositoryException repositoryException = assertThrows(RepositoryException.class, () -> {
            uploadService.handleUpload(handler, CONTENT_TYPE_URI_LIST, uploadDTO, data);
        });

        assertEquals(BAD_REQUEST.getStatusCode(), repositoryException.getStatusCode());
    }

    @Test
    public void whenUploadNotFoundWithUploadId_throwExceptionWithNotFound() {
        UploadHandler handler = new DefaultUploadHandler();
        String mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        UploadDTO uploadDTO = new UploadDTO("abcd", "test.docx", mimeType, null, null, false, 0, 0);
        UploadServiceImpl uploadService = new UploadServiceImpl();
        ByteArrayInputStream data = new ByteArrayInputStream("test".getBytes());

        RepositoryException repositoryException = assertThrows(RepositoryException.class, () -> {
            uploadService.handleUpload(handler, mimeType, uploadDTO, data);
        });

        assertEquals(NOT_FOUND.getStatusCode(), repositoryException.getStatusCode());
    }

    @Test
    public void whenUploadWithMultipleExternalUrls_returnBadRequest() {
        UploadHandler handler = new DefaultUploadHandler();
        String externalUri = "http://abc.com\nhttp://xyz.com";
        UploadDTO uploadDTO = new UploadDTO(null, "test.docx", CONTENT_TYPE_URI_LIST, null, null, false, 0, 0);
        UploadServiceImpl uploadService = new UploadServiceImpl();
        ByteArrayInputStream data = new ByteArrayInputStream(externalUri.getBytes());

        RepositoryException repositoryException = assertThrows(RepositoryException.class, () -> {
            uploadService.handleUpload(handler, CONTENT_TYPE_URI_LIST, uploadDTO, data);
        });

        assertEquals(BAD_REQUEST.getStatusCode(), repositoryException.getStatusCode());
    }

    private void assertSuccessfulUploadCreated(ResponseEntity<Upload> response, String fileName, String mimeType) {
        assertEquals(CREATED, response.getStatusCode());
        Upload upload = response.getBody();
        assertNotNull(upload);
        assertNotNull(upload.getId());
        assertEquals(fileName, upload.getFileName());
        assertEquals(mimeType, upload.getMimeType());
        Blob blob = upload.getBlob();
        assertEquals(fileName, blob.getFilename());
        assertEquals(mimeType, blob.getMimeType());
    }

}
