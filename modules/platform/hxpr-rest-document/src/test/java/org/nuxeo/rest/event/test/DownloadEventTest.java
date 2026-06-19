/*
 * (C) Copyright 2023 Hyland (http://nuxeo.com/) and others.
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
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.UTF_8;
import static org.nuxeo.rest.download.DownloadAPI.PATH;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.springframework.http.HttpMethod.GET;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.document.DownloadEvent;
import org.nuxeo.ecm.core.event.test.CapturingEventListener;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.document.test.RestSingleDocumentFeature;
import org.nuxeo.rest.download.DownloadAPI;
import org.nuxeo.rest.download.DownloadManager;
import org.nuxeo.rest.download.DownloadStudioFormDataAPI;
import org.nuxeo.rest.utils.Versions;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.WithApplicationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;


@Features(RestSingleDocumentFeature.class)
@WithApplicationProperties("usage-application.properties")
@SpringBootTest(classes = { DownloadAPI.class, DownloadStudioFormDataAPI.class, DownloadManager.class })
class DownloadEventTest extends AbstractAPITest {

    private static final String EVENT_NAME = DownloadEvent.GENERIC_TYPE_KEY_PATTERN.formatted(DownloadEvent.EVENT_NAME_PREFIX, DownloadEvent.GENERIC_TYPE_KEY_SUFFIX);

    @RuntimeInject
    protected CoreSession coreSession;

    @Test
    void testUsageEvent() throws Exception {
        // create a document with a blob property
        String fileName = "hello.txt";
        String fileContent = "hello world!";
        DocumentModel doc = coreSession.createDocumentModel("/", "doc", "TestDocument");
        doc.setPropertyValue(SYSFILE_BLOB, new StringBlob(fileContent, TEXT_PLAIN, UTF_8, fileName));
        doc.create();
        coreSession.save();

        // download the blob
        try (CapturingEventListener listener = new CapturingEventListener(EVENT_NAME)) {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.valueOf(Versions.APP_HXCS_OCTET_STREAM_V1)));
            headers.add(HXCS_REPOSITORY, REPO_ID);

            checkResponse(OK, GET, headers, PATH + "/{id}/{sysFileBlob}/{fileName}", doc.getId(), SYSFILE_BLOB,
                    fileName);

            // verify that the event was captured
            assertEquals(1, listener.getCapturedEvents().size());
            Event event = listener.getCapturedEvents().getFirst();
            assertEquals(EVENT_NAME, event.getName());
            String actualJson = (String) event.getContext().getProperty(EVENT_DATA_PROPERTY);
            assert actualJson != null;
            String expectedJson = """
                {
                  "length": 12,
                  "mimeType": "text/plain",
                  "isPresignedUrl": false,
                  "presignedUrl": false
                }
                """;
            JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.STRICT);
        }
    }

    @Test
    @Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-presignedurl-provider-contrib.xml")
    void testUsageEventForGeneratePreSignedUrl() throws Exception {
        // create a document with a blob property
        String fileName = "demo.txt";
        String fileContent = "hello demo!";
        DocumentModel doc = coreSession.createDocumentModel("/", "doc", "TestDocument");
        doc.setPropertyValue(SYSFILE_BLOB, new StringBlob(fileContent, TEXT_PLAIN, UTF_8, fileName));
        doc.create();
        coreSession.save();

        // generate the presigned URL
        try (CapturingEventListener listener = new CapturingEventListener(EVENT_NAME)) {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.valueOf(Versions.APP_HXCS_OCTET_STREAM_V1)));
            headers.add(HXCS_REPOSITORY, REPO_ID);

            checkResponse(OK, GET, headers, PATH + "/url/{id}/{sysFileBlob}/{fileName}?expiration={expiration}",
                    doc.getId(), SYSFILE_BLOB, fileName, 5000);

            // verify that the event was captured
            assertEquals(1, listener.getCapturedEvents().size());
            Event event = listener.getCapturedEvents().getFirst();
            assertEquals(EVENT_NAME, event.getName());
            String actualJson2 = (String) event.getContext().getProperty(EVENT_DATA_PROPERTY);
            assert actualJson2 != null;
            String expectedJson2 = """
                {
                  "length": 11,
                  "mimeType": "text/plain",
                  "isPresignedUrl": true,
                  "presignedUrl": true
                }
                """;
            JSONAssert.assertEquals(expectedJson2, actualJson2, JSONCompareMode.STRICT);
        }
    }
}
