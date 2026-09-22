/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.document.test;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.springframework.http.HttpMethod.PUT;

import org.junit.jupiter.api.Test;
import org.nuxeo.rest.document.DocumentAPI;
import org.nuxeo.rest.document.DocumentMultipartController;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Upsert by id relies on client-supplied ids, which are only honored when {@code hxpr.core.allowSetIdOnCreation} is set.
 * This test runs with the property at its default (unset), so the feature must be refused rather than silently
 * degrading into repeated creates.
 */
@Features(RestSingleDocumentFeature.class)
@Import(DocumentAPITestConfiguration.class)
@SpringBootTest(classes = { DocumentAPI.class, DocumentMultipartController.class })
public class DocumentAPIUpsertByIdDisabledTest extends AbstractAPITest {

    @Test
    public void testUpsertByIdRequiresAllowSetIdOnCreation() throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, """
                {
                  "sys_parentId": "%s",
                  "sys_name": "byid",
                  "sys_primaryType": "SysFile"
                }
                """.formatted(ROOT_ID), PATH + "/some-id?upsert=true");
    }
}
