/*
 * (C) Copyright 2022 Hyland (https://hyland.com/) and others.
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
package org.nuxeo.rest.move.test;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Map;

import jakarta.ws.rs.core.Response.Status;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.rest.document.DocumentAPI;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.document.test.ContentTestHelper;
import org.nuxeo.rest.document.test.DocumentAPITestConfiguration;
import org.nuxeo.rest.document.test.RestSingleDocumentFeature;
import org.nuxeo.rest.move.MoveAPI;
import org.nuxeo.rest.utils.QueryParameters;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Move Rest API tests.
 */
@Features(RestSingleDocumentFeature.class)
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
    public void testMoveDocumentWithoutTargetParentIdException() throws Exception {
        var result = checkMoveApiResponse(BAD_REQUEST, "{}");
        ContentTestHelper.checkErrorResponse(BAD_REQUEST,
                "Missing destination folder id to move document: targetParentId", result);
    }
}
