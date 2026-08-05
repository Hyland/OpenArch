/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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

import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.is;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.rest.document.DocumentAPI;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.rest.document.DocumentMultipartController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = { DocumentAPI.class, DocumentMultipartController.class, DocumentManager.class })
public class DocumentAPIByIdTest extends AbstractDocumentAPITest {

    @Autowired
    DocumentManager documentManager;

    @Override
    protected String getRootApiPath() {
        return String.format("%s/%s", PATH, ROOT_ID);
    }

    @Override
    protected String getApiPath(String id, String path) {
        return String.format("%s/%s", PATH, id);
    }

    @Test
    public void testGetByIdEmpty() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, getApiPath("", ""));
    }

    @Test
    public void testShouldNotDeleteRoot() throws Exception {
        checkResponse(FORBIDDEN, DELETE, REPO_ID, getApiPath(ROOT_ID, PATH));
    }

    @Test
    public void testGetDocumentAncestorsForRoot() throws Exception {
        checkResponse(OK, GET, REPO_ID, String.format("%s/ancestors", getRootApiPath())).andExpect(
                jsonPath("ancestors").doesNotExist());
    }

    private DocumentDTO createDocument(String primaryType, String title, String parentId) {
        DocumentDTO document = new DocumentDTO();
        document.setPrimaryType(primaryType);
        document.setTitle(title);
        return documentManager.createDocument(REPO_ID, new IdRef(parentId), document, true);
    }

    @Test
    public void testDocumentAncestors() throws Exception {
        // create a folder on the root and a file on the folder
        DocumentDTO folder = createDocument(SYS_FOLDER, "Folder", ROOT_ID);
        DocumentDTO file = createDocument(SYS_FILE, "File", folder.getId());

        // get the ancestors of the file
        var result = checkResponse(OK, GET, REPO_ID, String.format("%s/ancestors", getApiPath(file.getId(), PATH)));
        result.andExpect(jsonPath("ancestors.length()", is(2)))
              .andExpectAll(jsonPath("ancestors[0]." + SYS_ID).value(folder.getId()),
                      jsonPath("ancestors[1]." + SYS_ID).value(file.getId()));

        // cleanup
        checkResponse(NO_CONTENT, DELETE, REPO_ID, getApiPath(file.getId(), PATH));
        checkResponse(NO_CONTENT, DELETE, REPO_ID, getApiPath(folder.getId(), PATH));
    }

}
