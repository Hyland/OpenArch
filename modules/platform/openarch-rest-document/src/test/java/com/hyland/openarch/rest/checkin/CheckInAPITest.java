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
package com.hyland.openarch.rest.checkin;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.rest.document.AbstractDocumentAPI.PATH;
import static com.hyland.openarch.rest.document.test.ContentTestHelper.checkErrorResponse;
import static com.hyland.openarch.rest.document.test.ContentTestHelper.getJsonPath;
import static org.springframework.http.HttpMethod.POST;

import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.rest.acl.ACEMapper;
import com.hyland.openarch.rest.acl.ACLMapper;
import com.hyland.openarch.rest.document.DocumentAPI;
import com.hyland.openarch.rest.document.DocumentManager;
import com.hyland.openarch.rest.document.DocumentMapper;
import com.hyland.openarch.rest.document.ObjectMapperConfiguration;
import com.hyland.openarch.rest.document.test.AbstractAPITest;
import com.hyland.openarch.rest.document.utils.ACLChangesHandler;
import com.hyland.openarch.rest.upload.UploadService;
import com.hyland.openarch.rest.upload.handler.UploadHandler;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.test.runner.Deploy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-write-types-contrib.xml")
@Deploy("com.hyland.openarch.ecm.platform.usermanager")
@SpringBootTest(classes = { DocumentAPI.class, CheckInAPI.class, DocumentManager.class, DocumentMapper.class,
        ObjectMapperConfiguration.class, ACLMapper.class, ACEMapper.class, ACLChangesHandler.class })
class CheckInAPITest extends AbstractAPITest {

    @RuntimeInject
    protected CoreSession coreSession;

    @MockitoBean
    protected UploadService uploadService;

    @MockitoBean
    protected UploadHandler uploadHandler;

    protected DocumentModel doc;

    @BeforeEach
    public void setUp() {
        doc = coreSession.createDocumentModel("/", "My File", "MyFile");
        doc.create();
        coreSession.save();
    }

    @AfterEach
    public void tearDown() {
        coreSession.removeDocument(doc.getRef());
        coreSession.save();
    }

    @Test
    void testCheckIn() throws Exception {
        // assert the state of the document before the check in
        assertFalse(doc.isVersion());
        assertTrue(doc.isCheckedOut());
        assertEquals(0, doc.getVersion());
        assertEquals(0, doc.getMinorVersion());
        // check in the document
        String path = PATH + "/" + doc.getId() + "/checkin";
        ResultActions response = checkResponseWithPayload(OK, POST, REPO_ID, null, path);
        doc = coreSession.getDocument(doc.getRef());
        String versionId = getJsonPath(response, SYS_ID);
        DocumentModel version = coreSession.getDocument(new IdRef(versionId));
        // assert the state of the document after the check in
        assertFalse(doc.isVersion());
        assertFalse(doc.isCheckedOut());
        assertEquals(1, doc.getVersion());
        assertEquals(0, doc.getMinorVersion());
        // assert the state of the version
        assertEquals(doc.getId(), version.getParentRef().toString());
        assertTrue(version.isVersion());
        assertFalse(version.isCheckedOut());
        assertEquals(1, version.getVersion());
        assertEquals(0, version.getMinorVersion());
    }

    @Test
    void testCheckInMinor() throws Exception {
        // check in the document, creating a major version
        String path = PATH + "/" + doc.getId() + "/checkin?minor=true";
        ResultActions response = checkResponseWithPayload(OK, POST, REPO_ID, null, path);
        doc = coreSession.getDocument(doc.getRef());
        String versionId = getJsonPath(response, SYS_ID);
        DocumentModel version = coreSession.getDocument(new IdRef(versionId));
        // assert the state of the document after the check in
        assertEquals(0, doc.getVersion());
        assertEquals(1, doc.getMinorVersion());
        // assert the state of the version
        assertEquals(doc.getId(), version.getParentRef().toString());
        assertTrue(version.isVersion());
        assertEquals(0, version.getVersion());
        assertEquals(1, version.getMinorVersion());
    }

    @Test
    void testAlreadyCheckedIn() throws Exception {
        coreSession.checkIn(doc.getRef());
        String path = PATH + "/" + doc.getId() + "/checkin";
        ResultActions response = checkResponseWithPayload(CONFLICT, POST, REPO_ID, null, path);
        checkErrorResponse(CONFLICT, "Already checked in", response);
    }

    @Test
    void testCheckInVersion() throws Exception {
        DocumentRef versionRef = coreSession.checkIn(doc.getRef());
        String path = PATH + "/" + versionRef.toString() + "/checkin";
        ResultActions response = checkResponseWithPayload(UNPROCESSABLE_ENTITY, POST, REPO_ID, null, path);
        checkErrorResponse(UNPROCESSABLE_ENTITY, Pattern.compile("Version cannot be checked in: .*"), response);
    }

    @Test
    void testCheckInNotVersionable() throws Exception {
        doc.setPrimaryTypeAndMixinTypes(SYS_FILE, Set.of());
        coreSession.save();
        String path = PATH + "/" + doc.getId() + "/checkin";
        ResultActions response = checkResponseWithPayload(PRECONDITION_FAILED, POST, REPO_ID, null, path);
        checkErrorResponse(PRECONDITION_FAILED, Pattern.compile("Not versionable: .*"), response);
    }

    @Test
    void testFeatureFlagEnabled() throws Exception {
        checkResponseWithPayload(OK, POST, REPO_ID, null, PATH + "/" + doc.getId() + "/checkin");
    }

}
