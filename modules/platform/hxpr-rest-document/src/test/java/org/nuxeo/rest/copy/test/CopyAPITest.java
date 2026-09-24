/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.copy.test;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.nuxeo.rest.document.test.ContentTestHelper.checkErrorResponse;
import static org.nuxeo.rest.document.test.ContentTestHelper.getJsonPath;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import jakarta.ws.rs.core.Response.Status;

import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.rest.copy.CopyAPI;
import org.nuxeo.rest.document.DocumentAPI;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.document.test.DocumentAPITestConfiguration;
import org.nuxeo.rest.document.test.RestSingleDocumentFeature;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Copy Rest API tests.
 */
@Features(RestSingleDocumentFeature.class)
@Import({ DocumentAPITestConfiguration.class })
@SpringBootTest(classes = { CopyAPI.class, DocumentAPI.class })
public class CopyAPITest extends AbstractAPITest {

    @RuntimeInject
    DocumentModel doc;

    private String getCopyPath() {
        return PATH + "/" + doc.getId() + "/copy";
    }

    private JSONObject getCopyBody(Boolean setName, Boolean setTargetParentId) throws JSONException {
        String name = UUID.randomUUID().toString();
        String targetParentId = doc.getParentRef().toString();
        JSONObject payload = new JSONObject();
        if (setName) {
            payload.put("name", name);
        }
        if (setTargetParentId) {
            payload.put("targetParentId", targetParentId);
        }
        return payload;
    }

    private ResultActions checkCopyApiResponseAsJsonPath(Status status, HttpMethod method, String payload, String path)
            throws Exception {
        return checkResponseWithPayload(status, method, REPO_ID, payload, path);
    }

    private ResultActions getDocument(String docId) throws Exception {
        String path = DocumentAPI.PATH + "/" + docId;
        return checkResponse(OK, GET, REPO_ID, path);
    }

    private void assertProperties(ResultActions expectedResult, ResultActions actualResult, String... jsonPaths)
            throws Exception {
        for (String propertyName : jsonPaths) {
            String expected = getJsonPath(expectedResult, propertyName);
            String actual = getJsonPath(actualResult, propertyName);
            assertEquals(expected, actual);
        }
    }

    private void assertOriginalAndCopiedFiles(String originalFileId, String copiedFileId) throws Exception {
        var originalFile = getDocument(originalFileId);
        var copiedFile = getDocument(copiedFileId);
        assertProperties(originalFile, copiedFile, SYS_PARENT_ID, SYS_PRIMARY_TYPE);
    }

    @Test
    public void testCopyDocument() throws Exception {
        JSONObject payload = getCopyBody(true, true);
        var copiedResult = checkCopyApiResponseAsJsonPath(OK, POST, payload.toString(), getCopyPath());
        assertOriginalAndCopiedFiles(doc.getId(), getJsonPath(copiedResult, SYS_ID));
        String actualName = getJsonPath(copiedResult, SYS_NAME);
        String actualParentId = getJsonPath(copiedResult, SYS_PARENT_ID);
        assertEquals(payload.get("name"), actualName);
        assertEquals(payload.get("targetParentId"), actualParentId);
    }

    /**
     * The handler cannot copy anything without a body, and read the one it declared optional straight away, so
     * the JSON literal null produced a NullPointerException and a 500 (HXCS-8835). The body is required now, and
     * Spring rejects the request before the handler runs.
     */
    @Test
    public void testCopyWithoutBodyIsABadRequest() throws Exception {
        var result = checkCopyApiResponseAsJsonPath(BAD_REQUEST, POST, "null", getCopyPath());
        checkErrorResponse(BAD_REQUEST, "Required request body is missing", result);
    }

    @Test
    public void testCopyDocumentWithoutTargetParentIdException() throws Exception {
        JSONObject payload = getCopyBody(true, false);
        var copiedResult = checkCopyApiResponseAsJsonPath(BAD_REQUEST, POST, payload.toString(), getCopyPath());
        checkErrorResponse(BAD_REQUEST, "Missing destination folder id to copy document: targetParentId", copiedResult);
    }

    @Test
    public void testCopyDocumentWithoutNameException() throws Exception {
        JSONObject payload = getCopyBody(false, true);
        var copiedResult = checkCopyApiResponseAsJsonPath(BAD_REQUEST, POST, payload.toString(), getCopyPath());
        checkErrorResponse(BAD_REQUEST, "Missing document name to copy document: name", copiedResult);
    }
}
