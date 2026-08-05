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
package org.nuxeo.rest.upload.test;

import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.nuxeo.rest.upload.UploadAPI.PATH;
import static org.springframework.http.HttpMethod.POST;

import org.junit.jupiter.api.Test;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.upload.UploadAPI;
import org.nuxeo.rest.upload.test.config.UploadApiTestConfiguration;
import org.nuxeo.rest.utils.Versions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;

@SpringBootTest(classes = { UploadAPI.class, UploadApiTestConfiguration.class })
public class ExternalUrlFFDisabledTest extends AbstractAPITest {

    @Test
    public void testUploadReturnsBadRequestWhenExternalURIFeatureFlagIsDisabled() throws Exception {
        String uriBody = "https://www.example.com/images/earth/earth-night.jpeg";
        HttpHeaders headers = new HttpHeaders();
        headers.add(ACCEPT, Versions.APP_HXCS_JSON_V1);
        headers.add(CONTENT_TYPE, UploadAPITest.CONTENT_TYPE_URI_LIST);
        checkResponseWithPayload(BAD_REQUEST, POST, headers, uriBody, PATH + "?fileName={fileName}", "earth-night.jpeg");
    }
}
