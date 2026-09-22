/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.event.test;

import static jakarta.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.UTF_8;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.document.UploadEvent;
import org.nuxeo.ecm.core.event.test.CapturingEventListener;
import org.nuxeo.rest.document.DocumentAPI;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.document.test.DocumentAPITestConfiguration;
import org.nuxeo.rest.document.test.RestSingleDocumentFeature;
import org.nuxeo.rest.upload.Upload;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.nuxeo.rest.upload.handler.UploadHandlerConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.WithApplicationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;


@Features(RestSingleDocumentFeature.class)
@WithApplicationProperties("usage-application.properties")
@SpringBootTest(classes = { UploadHandlerConfig.class, DocumentAPITestConfiguration.class, DocumentAPI.class })
class UploadEventTest extends AbstractAPITest {

    private static final String EVENT_NAME = UploadEvent.GENERIC_TYPE_KEY_PATTERN.formatted(UploadEvent.EVENT_NAME_PREFIX, UploadEvent.GENERIC_TYPE_KEY_SUFFIX);

    @Autowired
    UploadHandler handler;

    @Test
    void testUsageEvent() throws Exception {
        try (CapturingEventListener listener = new CapturingEventListener(EVENT_NAME)) {
            // create a new upload and add a blob to it
            Upload upload = handler.newUpload();
            String content = "test content";
            upload.addBlob(Blobs.createBlob(content, TEXT_PLAIN, UTF_8, "test.txt"));

            // create a document referencing the upload
            String creationJson = """
                {
                  "sys_title": "My File",
                  "sysfile_blob": {
                    "uploadId": "%s"
                  },
                  "sys_primaryType": "TestDocument"
                }
                """.formatted(upload.getId());
            checkResponseWithPayload(OK, POST, APPLICATION_JSON_VALUE, REPO_ID, creationJson, PATH + "/{id}", ROOT_ID);

            // verify that the event was captured
            assertEquals(1, listener.getCapturedEvents().size());
            Event event = listener.getCapturedEvents().getFirst();
            assertEquals(EVENT_NAME, event.getName());

            // Validate UploadEvent metadata
            String actualJson = (String) event.getContext().getProperty(EVENT_DATA_PROPERTY);
            assert actualJson != null;
            String expectedJson = """
                {
                  "mimeType": "%s",
                  "length": %d,
                  "isPresignedUrl": false,
                  "presignedUrl": false
                }
                """.formatted(TEXT_PLAIN, content.length());
            JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.STRICT);
        }
    }

}
