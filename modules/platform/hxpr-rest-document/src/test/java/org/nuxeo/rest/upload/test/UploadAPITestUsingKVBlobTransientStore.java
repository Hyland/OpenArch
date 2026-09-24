/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.upload.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static org.nuxeo.rest.document.test.ContentTestHelper.getJsonPath;
import static org.nuxeo.rest.upload.UploadAPI.PATH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.CREATED;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.transientstore.TransientStorageComponent;
import com.hyland.openarch.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.upload.UploadAPI;
import org.nuxeo.rest.upload.handler.DefaultUploadHandler;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.nuxeo.rest.upload.test.config.UploadApiTestConfiguration;
import org.nuxeo.rest.utils.Versions;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.AopTestUtils;

import net.minidev.json.JSONArray;

@Features({UploadFeature.class})
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-kvstore-contrib.xml")
@Import(UploadApiTestConfiguration.class)
@SpringBootTest(classes = { UploadAPI.class }, properties = { "nuxeo.upload.handler.s3.enabled=false" })
@DirtiesContext
public class UploadAPITestUsingKVBlobTransientStore extends AbstractAPITest {

    protected static final String REPO_ID = "test";

    @Autowired
    protected UploadHandler handler;

    @BeforeEach
    public void runCheckUploadHandler() {
        // this is a session scoped bean and will be injected as a proxy
        assertInstanceOf(DefaultUploadHandler.class, AopTestUtils.getTargetObject(handler));
    }

    @AfterEach
    public void cleanupTransientStores() {
        ((TransientStorageComponent) Framework.getService(TransientStoreService.class)).cleanUpStores();
    }

    @Test
    public void checkUploadWithChunks() throws Exception {
        HttpHeaders headers = contentType(TEXT_PLAIN);

        // send chunk 0
        var result = checkResponseWithPayload(ACCEPTED, POST, headers, "test content",
                PATH + "?fileName={fileName}&chunked=true&chunkCount=3&chunkIndex=0&encoding={encoding}",
                "testfile.txt", "ISO-8859-1");
        result.andExpectAll(header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1), jsonPath("id").exists());
        String uploadId = getJsonPath(result, "id");
        int fileSize = getJsonPath(result, "fileSize");
        JSONArray actualChunkIndex = getJsonPath(result, "chunkIndexes");
        JSONArray expectedChunkIndex = new JSONArray();
        expectedChunkIndex.add(0);

        assertEquals(expectedChunkIndex, actualChunkIndex);
        assertEquals(-1, fileSize);

        // send chunk 1
        result = checkResponseWithPayload(ACCEPTED, POST, headers, "in chunks",
                PATH + "?id={id}&chunked=true&chunkCount=3&chunkIndex=1", uploadId).andExpect(
                header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1));
        fileSize = getJsonPath(result, "fileSize");
        actualChunkIndex = getJsonPath(result, "chunkIndexes");
        expectedChunkIndex.add(1);

        assertEquals(expectedChunkIndex, actualChunkIndex);
        assertEquals(-1, fileSize);

        // send chunk 2
        result = checkResponseWithPayload(CREATED, POST, headers, " but this time ",
                PATH + "?id={id}&chunked=true&chunkCount=3&chunkIndex=2", uploadId).andExpect(
                header().string(CONTENT_TYPE, Versions.APP_HXCS_JSON_V1));
        fileSize = getJsonPath(result, "fileSize");
        actualChunkIndex = getJsonPath(result, "chunkIndexes");
        expectedChunkIndex.add(2);

        assertEquals(expectedChunkIndex, actualChunkIndex);
        assertEquals(36, fileSize);

        // check actual file creation
        Blob blob = handler.getUpload(uploadId).getBlob();
        assertNotNull(blob);
        assertEquals("testfile.txt", blob.getFilename());
        assertEquals(fileSize, blob.getLength());
        assertEquals(TEXT_PLAIN, blob.getMimeType());
        assertNotNull(blob.getFile());
        assertTrue(blob.getFile().exists());
    }

    private HttpHeaders contentType(String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(CONTENT_TYPE, contentType);
        return headers;
    }

}
