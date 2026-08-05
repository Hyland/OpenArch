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
package org.nuxeo.rest.version;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.springframework.http.HttpMethod.POST;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentConstants;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.rest.acl.ACEMapper;
import org.nuxeo.rest.acl.ACLMapper;
import org.nuxeo.rest.checkin.CheckInAPI;
import org.nuxeo.rest.document.DocumentAPI;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.rest.document.DocumentMapper;
import org.nuxeo.rest.document.ObjectMapperConfiguration;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.document.test.DocumentAPITestConfiguration;
import org.nuxeo.rest.document.test.RestSingleDocumentFeature;
import org.nuxeo.rest.document.utils.ACLChangesHandler;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Features(RestSingleDocumentFeature.class)
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-write-types-contrib.xml")
@SpringBootTest(classes = { DocumentAPI.class, CheckInAPI.class, DocumentManager.class, DocumentMapper.class,
        ObjectMapperConfiguration.class, ACLMapper.class, ACEMapper.class, ACLChangesHandler.class, VersionAPI.class })
@Import(DocumentAPITestConfiguration.class)
class VersionAPITest extends AbstractAPITest {

    @RuntimeInject
    protected CoreSession coreSession;

    protected DocumentModel doc;

    protected DocumentModel anotherDoc;

    @BeforeEach
    public void setUp() {
        doc = coreSession.createDocumentModel("/", "foo", "MyFile");
        doc.create();
        coreSession.save();
    }

    @AfterEach
    public void tearDown() {
        coreSession.removeDocument(doc.getRef());
        if (anotherDoc != null) {
            coreSession.removeDocument(anotherDoc.getRef());
        }
        coreSession.save();
    }

    @Test
    void shouldRestoreVersionSuccessfully() throws Exception {
        DocumentRef docRef = new IdRef(doc.getId());
        // create a version 1 with checkin
        DocumentRef versionRef = coreSession.checkIn(docRef);
        String versionId = coreSession.getDocument(versionRef).getId();
        // modify the working copy sys_title to "bar"
        doc.setPropertyValue("sys_title", "bar");
        coreSession.save();
        doc = coreSession.getDocument(doc.getRef());
        //checkin to the version 2
        coreSession.checkIn(docRef);
        coreSession.save();

        // restore to the version 1
        checkResponseWithPayload(OK, POST, REPO_ID, null, PATH + "/" + versionId + "/restore");

        // check that the title is foo again
        doc = coreSession.getDocument(doc.getRef());
        assertEquals("foo", doc.getPropertyValue("sys_title"));
        // the version of working copy is unchanged after restoring the version 1.0
        assertEquals(2L, doc.getVersion());
        assertEquals(0L, doc.getMinorVersion());
        assertNull(doc.getPropertyValue(DocumentConstants.SYSVER_IS_VERSION));
        assertNull(doc.getPropertyValue(DocumentConstants.SYSVER_CREATED));
        assertNull(doc.getPropertyValue(DocumentConstants.SYSVER_CREATOR));
        assertNull(doc.getPropertyValue(DocumentConstants.SYSVER_TITLE));
        assertNull(doc.getPropertyValue(DocumentConstants.SYSVER_DESCRIPTION));
    }

    @Test
    void shouldRestoreVersionReturn412WhenDocumentIsNotVersioniable() throws Exception {
        DocumentRef docRef = new IdRef(doc.getId());
        // create a version 1 with checkin
        DocumentRef versionRef = coreSession.checkIn(docRef);
        String versionId = coreSession.getDocument(versionRef).getId();
        doc = coreSession.getDocument(doc.getRef());
        doc.setPrimaryTypeAndMixinTypes(SYS_FILE, Set.of());
        coreSession.save();
        // when document is not versionable anymore, 412 should be returned
        checkResponseWithPayload(PRECONDITION_FAILED, POST, REPO_ID, null, PATH + "/" + versionId + "/restore");
    }

    @Test
    void shouldRestoreVersionReturn422WhenDocumentIsNotVersion() throws Exception {
        anotherDoc = coreSession.createDocumentModel("/", "Another File", "MyFile");
        anotherDoc.create();
        coreSession.save();
        checkResponseWithPayload(UNPROCESSABLE_ENTITY, POST, REPO_ID, null, PATH + "/" + anotherDoc.getId() + "/restore");
    }

    @Test
    void shouldRestoreVersionReturn404WhenDocumentDoesNotExist() throws Exception {
        checkResponseWithPayload(NOT_FOUND, POST, REPO_ID, null, PATH + "/notExistingDocument/restore");
    }
}
