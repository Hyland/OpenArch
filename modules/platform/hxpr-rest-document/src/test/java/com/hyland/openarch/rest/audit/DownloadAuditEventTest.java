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
package com.hyland.openarch.rest.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static com.hyland.openarch.ecm.core.api.impl.blob.AbstractBlob.UTF_8;
import static com.hyland.openarch.rest.download.DownloadAPI.PATH;
import static com.hyland.openarch.rest.download.DownloadManager.CTX_BLOB_FILENAME;
import static com.hyland.openarch.rest.download.DownloadManager.CTX_BLOB_XPATH;
import static com.hyland.openarch.rest.download.DownloadManager.CTX_DOWNLOAD_REASON;
import static com.hyland.openarch.rest.download.DownloadManager.CTX_EXTENDED_INFOS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.Serializable;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.event.CoreEventConstants;
import com.hyland.openarch.ecm.core.api.event.DocumentEventTypes;
import com.hyland.openarch.ecm.core.api.impl.blob.StringBlob;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.test.CapturingEventListener;
import com.hyland.openarch.rest.document.test.AbstractAPITest;
import com.hyland.openarch.rest.document.test.RestSingleDocumentFeature;
import com.hyland.openarch.rest.download.DownloadAPI;
import com.hyland.openarch.rest.download.DownloadManager;
import com.hyland.openarch.rest.download.DownloadStudioFormDataAPI;
import com.hyland.openarch.rest.utils.Versions;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.WithApplicationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = { DownloadStudioFormDataAPI.class, DownloadAPI.class, DownloadManager.class })
@Features(RestSingleDocumentFeature.class)
@WithApplicationProperties("audit-application.properties")
class DownloadAuditEventTest extends AbstractAPITest {

    @RuntimeInject
    protected CoreSession coreSession;

    @Test
    void testDocumentDownloadedEvent() throws Exception {
        // create a document with a blob property
        String fileName = "hello.txt";
        String fileContent = "hello world!";
        DocumentModel doc = coreSession.createDocumentModel("/", "doc", "TestDocument");
        doc.setPropertyValue(SYSFILE_BLOB, new StringBlob(fileContent, TEXT_PLAIN, UTF_8, fileName));
        doc.create();
        coreSession.save();

        // download the blob
        try (CapturingEventListener listener = new CapturingEventListener(DocumentEventTypes.DOCUMENT_DOWNLOADED)) {
            MvcResult asyncResult = mvc.perform(get(String.join("/", PATH, doc.getId(), SYSFILE_BLOB, fileName))
                                    .accept(Versions.APP_HXCS_OCTET_STREAM_V1)
                                    .headers(getRepositoryHttpHeaders(REPO_ID)))
                                    .andExpect(request().asyncStarted())
                                    .andReturn();

            mvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk());

            // verify that the event was captured
            assertEquals(1, listener.getCapturedEvents().size());
            Event event = listener.getCapturedEvents().get(0);
            assertEquals(DocumentEventTypes.DOCUMENT_DOWNLOADED, event.getName());
            var extendedInfos = (Map<String, Serializable>) event.getContext().getProperty(CTX_EXTENDED_INFOS);
            assertEquals("hello.txt", extendedInfos.get(CTX_BLOB_FILENAME));
            assertEquals("sysfile_blob", extendedInfos.get(CTX_BLOB_XPATH));
            assertEquals("download", extendedInfos.get(CTX_DOWNLOAD_REASON));
        }
    }

    @Test
    @Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-presignedurl-provider-contrib.xml")
    void testPreSignedUrlFlagForDownLoadEvent() throws Exception {
        // create a document with a blob property
        String fileName = "test.txt";
        String fileContent = "hello Test!";
        DocumentModel doc = coreSession.createDocumentModel("/", "doc", "TestDocument");
        doc.setPropertyValue(SYSFILE_BLOB, new StringBlob(fileContent, TEXT_PLAIN, UTF_8, fileName));
        doc.create();
        coreSession.save();

        // generate the presigned URL
        try (CapturingEventListener listener = new CapturingEventListener(DocumentEventTypes.DOCUMENT_DOWNLOADED)) {
            mvc.perform(get(String.join("/", PATH, "url", doc.getId(), SYSFILE_BLOB, fileName))
                   .accept(Versions.APP_HXCS_OCTET_STREAM_V1)
                   .headers(getRepositoryHttpHeaders(REPO_ID))
                   .queryParam("expiration", "5000"))
               .andExpect(status().isOk());

            // verify that the event was captured
            assertEquals(1, listener.getCapturedEvents().size());
            Event event = listener.getCapturedEvents().get(0);
            assertEquals(DocumentEventTypes.DOCUMENT_DOWNLOADED, event.getName());
            // verify the isPresignedUrl flag
            assertTrue((Boolean) event.getContext().getProperty(CoreEventConstants.IS_PRESIGNED_URL));
        }
    }

}
