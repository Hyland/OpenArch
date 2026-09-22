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

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.rest.document.DocumentAPI;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.rest.document.DocumentMultipartController;
import org.nuxeo.rest.document.UpsertDocumentResult;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Features(RestSingleDocumentFeature.class)
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-update-primary-type-contrib.xml")
@Import(DocumentAPITestConfiguration.class)
@SpringBootTest(classes = { DocumentAPI.class,
        DocumentMultipartController.class }, properties = "hxpr.core.allowSetIdOnCreation=true")
public class DocumentAPIUpsertTest extends AbstractAPITest {

    private static final String NOTE_PATH = PATH + "/path/note?upsert=true";

    private static final String HIDDEN_PATH = PATH + "/path/restricted/hidden?upsert=true";

    @Autowired
    protected DocumentManager documentManager;

    @RuntimeInject
    protected CoreSession coreSession;

    @Test
    public void testUpsertByPathCreatesThenMergeUpdates() throws Exception {
        String create = """
                {
                  "sys_title": "First",
                  "sys_description": "keep",
                  "sys_primaryType": "SysFile"
                }
                """;
        // absent -> created (201)
        checkResponseWithPayload(CREATED, PUT, REPO_ID, create, NOTE_PATH).andExpectAll(
                jsonPath(SYS_TITLE).value("First"),
                jsonPath(SYS_NAME).value("note"),
                jsonPath(SYS_DESCRIPTION).value("keep"));

        String update = """
                {
                  "sys_title": "Second",
                  "sys_primaryType": "SysFile"
                }
                """;
        // present -> updated (200); sys_description not sent, so merge preserves it
        checkResponseWithPayload(OK, PUT, REPO_ID, update, NOTE_PATH).andExpectAll(
                jsonPath(SYS_TITLE).value("Second"),
                jsonPath(SYS_DESCRIPTION).value("keep"));
    }

    @Test
    public void testUpsertByIdCreatesThenMergeUpdates() throws Exception {
        String id = "my-upsert-id";
        String byIdPath = PATH + "/" + id + "?upsert=true";

        // absent -> created (201) with the client-supplied id, placed under the parent from the body
        String create = """
                {
                  "sys_parentId": "%s",
                  "sys_name": "byid",
                  "sys_title": "First",
                  "sys_description": "keep",
                  "sys_primaryType": "SysFile"
                }
                """.formatted(ROOT_ID);
        checkResponseWithPayload(CREATED, PUT, REPO_ID, create, byIdPath).andExpectAll(
                jsonPath(SYS_ID).value(id),
                jsonPath(SYS_TITLE).value("First"),
                jsonPath(SYS_DESCRIPTION).value("keep"));

        // same id -> updated (200); sys_description not sent, so merge preserves it
        String update = """
                {
                  "sys_parentId": "%s",
                  "sys_title": "Second",
                  "sys_primaryType": "SysFile"
                }
                """.formatted(ROOT_ID);
        checkResponseWithPayload(OK, PUT, REPO_ID, update, byIdPath).andExpectAll(
                jsonPath(SYS_ID).value(id),
                jsonPath(SYS_TITLE).value("Second"),
                jsonPath(SYS_DESCRIPTION).value("keep"));
    }

    @Test
    public void testUpsertByIdUpdateDoesNotRequireParentId() throws Exception {
        String id = "my-upsert-id-no-parent";
        String byIdPath = PATH + "/" + id + "?upsert=true";

        checkResponseWithPayload(CREATED, PUT, REPO_ID, """
                {
                  "sys_parentId": "%s",
                  "sys_name": "noparent",
                  "sys_title": "First",
                  "sys_primaryType": "SysFile"
                }
                """.formatted(ROOT_ID), byIdPath);

        // an unmentioned parent is not a re-parenting request
        checkResponseWithPayload(OK, PUT, REPO_ID, """
                {
                  "sys_title": "Second",
                  "sys_primaryType": "SysFile"
                }
                """, byIdPath).andExpect(jsonPath(SYS_TITLE).value("Second"));
    }

    @Test
    public void testUpsertByIdCreateRequiresParentIdAndName() throws Exception {
        // unlike the update, the create needs to know where to put the document and how to name it
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, """
                {
                  "sys_name": "orphan",
                  "sys_primaryType": "SysFile"
                }
                """, PATH + "/no-parent-id?upsert=true");
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, """
                {
                  "sys_parentId": "%s",
                  "sys_primaryType": "SysFile"
                }
                """.formatted(ROOT_ID), PATH + "/no-name?upsert=true");
    }

    @Test
    public void testUpsertRequiresABody() throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, null, NOTE_PATH);
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, null, PATH + "/some-id?upsert=true");
    }

    @Test
    public void testUpsertByPathOnUnreadableDocumentIsForbidden() throws Exception {
        // a folder the alt user can write in, holding a document they cannot read
        checkResponseWithPayload(CREATED, PUT, REPO_ID, """
                {
                  "sys_primaryType": "SysFolder",
                  "sys_acl": [
                    { "user": { "id": "%s" }, "permission": "Everything", "granted": true }
                  ]
                }
                """.formatted(ALT_USER), PATH + "/path/restricted?upsert=true");
        checkResponseWithPayload(CREATED, PUT, REPO_ID, """
                {
                  "sys_primaryType": "SysFile",
                  "sys_acl": [
                    { "user": { "id": "__Everyone__" }, "permission": "Everything", "granted": false }
                  ]
                }
                """, HIDDEN_PATH);

        // the document exists but is invisible: say so, rather than attempting a create that can only collide
        checkResponseWithPayloadWithUser(FORBIDDEN, PUT, APPLICATION_JSON, REPO_ID, """
                {
                  "sys_title": "Mine now",
                  "sys_primaryType": "SysFile"
                }
                """, HIDDEN_PATH, ALT_USER);
    }

    @Test
    public void testUpsertByPathToleratesTrailingSlash() throws Exception {
        String body = """
                {
                  "sys_primaryType": "SysFile"
                }
                """;
        // a trailing slash on the path is tolerated; the document is named "trailing" under the root
        checkResponseWithPayload(CREATED, PUT, REPO_ID, body, PATH + "/path/trailing/?upsert=true").andExpect(
                jsonPath(SYS_NAME).value("trailing"));
        // a path with no document name (the root) is rejected rather than updating the root
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, body, PATH + "/path/?upsert=true");
    }

    @Test
    public void testUpsertRejectsCondition() throws Exception {
        // a condition (optimistic concurrency) is not supported together with upsert -> 400, by path or by id
        String body = """
                {
                  "sys_primaryType": "SysFile"
                }
                """;
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, body, PATH + "/path/note?upsert=true&condition=foo");
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, body, PATH + "/some-id?upsert=true&condition=foo");
    }

    @Test
    public void testUpsertByIdRejectsReparent() throws Exception {
        String id = "my-upsert-id-reparent";
        String folderId = TransactionHelper.runInTransaction(() -> {
            DocumentModel folder = coreSession.createDocumentModel("/", "afolder", "SysFolder");
            folder.create();
            coreSession.save();
            return folder.getId();
        });
        String byIdPath = PATH + "/" + id + "?upsert=true";

        // create at the root
        checkResponseWithPayload(CREATED, PUT, REPO_ID, """
                {
                  "sys_parentId": "%s",
                  "sys_name": "movable",
                  "sys_primaryType": "SysFile"
                }
                """.formatted(ROOT_ID), byIdPath);

        // upsert the same id with a different parent -> re-parenting rejected (409)
        checkResponseWithPayload(CONFLICT, PUT, REPO_ID, """
                {
                  "sys_parentId": "%s",
                  "sys_primaryType": "SysFile"
                }
                """.formatted(folderId), byIdPath);
    }

    @Test
    public void testUpsertByPathRejectsBlankName() {
        // a blank name would otherwise resolve to the parent itself; refuse rather than update the wrong document
        DocumentDTO dto = new DocumentDTO();
        dto.setPrimaryType("SysFile");
        var rootRef = new PathRef("/");
        NuxeoException e = assertThrows(NuxeoException.class,
                () -> documentManager.upsertDocument(REPO_ID, rootRef, dto, false));
        assertEquals(BAD_REQUEST.getStatusCode(), e.getStatusCode());
    }

    @Test
    public void testUpsertByIdRejectsBlankId() {
        // a blank id would otherwise build an invalid ref; refuse rather than fail obscurely
        DocumentDTO dto = new DocumentDTO();
        dto.setPrimaryType("SysFile");
        var rootRef = new IdRef(ROOT_ID);
        NuxeoException e = assertThrows(NuxeoException.class,
                () -> documentManager.upsertDocument(REPO_ID, rootRef, dto, true));
        assertEquals(BAD_REQUEST.getStatusCode(), e.getStatusCode());
    }

    @Test
    public void testConcurrentUpsertByPathConvergesToOneDocument() throws Exception {
        int nbThreads = 5;
        CountDownLatch ready = new CountDownLatch(nbThreads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger createdCount = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < nbThreads; i++) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    DocumentDTO dto = new DocumentDTO();
                    dto.setName("shared");
                    dto.setPrimaryType("SysFile");
                    // upsert needs an ambient transaction, which in a request is the one the transaction filter opens
                    UpsertDocumentResult r = TransactionHelper.runInTransaction(
                            () -> documentManager.upsertDocument(REPO_ID, new PathRef("/"), dto, false));
                    if (r.created()) {
                        createdCount.incrementAndGet();
                    }
                } catch (Throwable e) {
                    unexpected.add(e);
                }
            });
            threads.add(t);
            t.start();
        }
        ready.await();
        go.countDown();
        for (Thread t : threads) {
            t.join();
        }

        // the create-race retry means no thread fails, exactly one creates, and a single document exists
        assertTrue(unexpected.isEmpty(), "unexpected errors: " + unexpected);
        assertEquals(1, createdCount.get());
        assertTrue(TransactionHelper.runInTransaction(() -> coreSession.exists(new PathRef("/shared"))));
        long count = TransactionHelper.runInTransaction(() -> coreSession.getChildren(new PathRef("/"))
                                                                         .stream()
                                                                         .filter(d -> "shared".equals(d.getName()))
                                                                         .count());
        assertEquals(1, count);
    }
}
