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
package com.hyland.openarch.rest.move.test;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static com.hyland.openarch.rest.document.AbstractDocumentAPI.PATH;
import static com.hyland.openarch.rest.document.test.ContentTestHelper.checkErrorResponse;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.regex.Pattern;

import jakarta.ws.rs.core.Response.Status;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.rest.document.DocumentAPI;
import com.hyland.openarch.rest.document.test.AbstractAPITest;
import com.hyland.openarch.rest.document.test.ContentTestHelper;
import com.hyland.openarch.rest.document.test.DocumentAPITestConfiguration;
import com.hyland.openarch.rest.document.test.RestSingleDocumentFeature;
import com.hyland.openarch.rest.move.MoveAPI;
import com.hyland.openarch.rest.utils.QueryParameters;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Move Rest API tests.
 */
@Features(RestSingleDocumentFeature.class)
@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-write-types-contrib.xml")
@Import({ DocumentAPITestConfiguration.class })
@SpringBootTest(classes = { MoveAPI.class, DocumentAPI.class })
public class MoveAPITest extends AbstractAPITest {

    @RuntimeInject
    DocumentModel doc;

    protected DocumentModel folder;

    @RuntimeInject
    protected CoreSession coreSession;

    @BeforeEach
    public void setUp() {
        folder = coreSession.createDocumentModel("/", "My Folder", SYS_FOLDER);
        folder.create();
        coreSession.save();
    }

    private String getMoveBody() throws JSONException {
        String targetParentId = folder.getRef().toString();
        return new JSONObject(Map.of("targetParentId", targetParentId)).toString();
    }

    private ResultActions checkMoveApiResponse(Status status, String payload) throws Exception {
        return checkMoveApiResponse(status, payload, null);
    }

    private ResultActions checkMoveApiResponse(Status status, String payload, String params) throws Exception {
        String path = PATH + "/" + doc.getId() + "/move";
        if (params != null) {
            path += params;
        }
        return checkResponseWithPayload(status, POST, REPO_ID, payload, path);
    }

    /**
     * Sends the move request with the condition passed as a proper query parameter.
     * <p>
     * The value must not be appended to the path, as characters like {@code &} (used by the {@code &&} operator of the
     * expression syntax) would be parsed as query parameter separators and silently truncate the condition.
     */
    private ResultActions checkMoveApiResponseWithParam(Status status, String payload, String paramName,
            String paramValue) throws Exception {
        String path = PATH + "/" + doc.getId() + "/move";
        var requestBuilder = getBaseRequestBuilder(POST, getRepositoryHttpHeaders(REPO_ID), path);
        requestBuilder.queryParam(paramName, paramValue);
        requestBuilder.contentType(MediaType.APPLICATION_JSON);
        requestBuilder.content(payload);
        return mvc.perform(requestBuilder).andExpect(status().is(status.getStatusCode()));
    }

    @Test
    public void testMoveDocument() throws Exception {
        // parent is not the folder
        assertNotEquals(doc.getParentRef().toString(), folder.getId());
        // move file
        checkMoveApiResponse(OK, getMoveBody()).andExpectAll(
                // moved document is the doc
                jsonPath(SYS_ID).value(doc.getId()),
                // parent is the folder
                jsonPath(SYS_PARENT_ID).value(folder.getId()));
    }

    @Test
    public void testConditionalMove() throws Exception {
        // try to move file with condition that fails
        String condition = "sys_title = 'no-such-title'";
        checkMoveApiResponse(PRECONDITION_FAILED, getMoveBody(), "?" + QueryParameters.CONDITION + "=" + condition);
        // move with condition that succeeds
        condition = "sys_title = 'title'";
        checkMoveApiResponse(OK, getMoveBody(), "?" + QueryParameters.CONDITION + "=" + condition).andExpectAll(
                // moved document is the doc
                jsonPath(SYS_ID).value(doc.getId()),
                // parent is the folder
                jsonPath(SYS_PARENT_ID).value(folder.getId()));
    }

    @Test
    public void testConditionalMoveIf() throws Exception {
        // try to move file with expression condition that fails
        String expr = "content.sys_title == \"no-such-title\"";
        checkMoveApiResponseWithParam(PRECONDITION_FAILED, getMoveBody(), QueryParameters.IF, expr);
        // a conjunction failing only on its second operand, to check that the whole expression is received
        expr = "content.sys_title == \"title\" && content.sys_creator == \"no-such-user\"";
        checkMoveApiResponseWithParam(PRECONDITION_FAILED, getMoveBody(), QueryParameters.IF, expr);
        // move with expression condition that succeeds
        expr = "content.sys_title == \"title\" && content.sys_creator == user.id";
        checkMoveApiResponseWithParam(OK, getMoveBody(), QueryParameters.IF, expr).andExpectAll(
                // moved document is the doc
                jsonPath(SYS_ID).value(doc.getId()),
                // parent is the folder
                jsonPath(SYS_PARENT_ID).value(folder.getId()));
    }

    /**
     * Same defect as the copy handler: an optional body, dereferenced for targetParentId on the next line, so
     * the JSON literal null answered 500 (HXCS-8835). The body is required now.
     */
    @Test
    public void testMoveWithoutBodyIsABadRequest() throws Exception {
        var result = checkMoveApiResponse(BAD_REQUEST, "null");
        checkErrorResponse(BAD_REQUEST, "Required request body is missing", result);
    }

    @Test
    public void testMoveDocumentWithoutTargetParentIdException() throws Exception {
        var result = checkMoveApiResponse(BAD_REQUEST, "{}");
        ContentTestHelper.checkErrorResponse(BAD_REQUEST,
                "Missing destination folder id to move document: targetParentId", result);
    }

    @Test
    public void testMoveVersion() throws Exception {
        DocumentModel versionableDocument = coreSession.createDocumentModel("/", "versionable", "MyFile");
        versionableDocument.create();
        coreSession.save();
        DocumentRef versionRef = coreSession.checkIn(versionableDocument.getRef());
        coreSession.save();

        String path = PATH + "/" + versionRef + "/move";
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, POST, REPO_ID, getMoveBody(), path);
        checkErrorResponse(UNPROCESSABLE_ENTITY, Pattern.compile("Cannot move a version: .*"), result);
    }
}
