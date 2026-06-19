/*
 * (C) Copyright 2024 Hyland (https://www.hyland.com/) and others.
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
 *
 * Contributors:
 *     Gildas Lefevre
 */
package org.nuxeo.rest.acl.test;

import static jakarta.ws.rs.core.Response.Status.OK;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.nuxeo.rest.document.test.ContentTestHelper.getJsonPath;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.rest.acl.ACEMapper;
import org.nuxeo.rest.acl.ACLMapper;
import org.nuxeo.rest.acl.DocumentPermissionsAPI;
import org.nuxeo.rest.acl.PermissionsManager;
import org.nuxeo.rest.acl.PermissionsMapper;
import org.nuxeo.rest.document.DocumentAPI;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.rest.document.DocumentMapper;
import org.nuxeo.rest.document.ObjectMapperConfiguration;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.document.test.RestSingleDocumentFeature;
import org.nuxeo.rest.document.utils.ACLChangesHandler;
import org.nuxeo.rest.upload.UploadHandlerCleaner;
import org.nuxeo.rest.upload.config.UploadServiceConfiguration;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.nuxeo.rest.upload.handler.UploadHandlerConfig;
import org.nuxeo.rest.utils.Versions;
import org.nuxeo.rest.vocabulary.config.VocabularyAPIConfig;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.ws.rs.core.Response.Status;
import net.minidev.json.JSONArray;

/**
 * Permissions REST API tests.
 */
@Features(RestSingleDocumentFeature.class)
@SpringBootTest(classes = { DocumentPermissionsAPI.class,
        PermissionsMapper.class, DocumentAPI.class })
@MockitoBean(types = { UploadHandler.class })
@Import({ ACLChangesHandler.class, //
    ACEMapper.class, //
    ACLMapper.class, //
    DocumentMapper.class, //
    DocumentManager.class, //
    ObjectMapperConfiguration.class, //
    UploadHandlerConfig.class, //
    UploadHandlerCleaner.class, //
    UploadServiceConfiguration.class, //
    VocabularyAPIConfig.class  }) //
public class DocumentPermissionsAPITest extends AbstractAPITest {

    @MockitoBean
    PermissionsManager permissionsManager;

    @RuntimeInject
    DocumentModel doc;

    private String getDocumentACLPath(String docId, List<String> usernames) {
        var usernamesPath = usernames.stream().map(user -> "usernames=" + user).collect(Collectors.toList());
        return PATH + "/" + docId + "/fulleffectiveacl?" + String.join("&", usernamesPath);
    }

    private String getDocumentPermissionsPath(String docId, List<String> usernames) {
        var usernamesPath = usernames.stream().map(user -> "usernames=" + user).collect(Collectors.toList());
        return PATH + "/" + docId + "/fulleffectivepermissions?" + String.join("&", usernamesPath);
    }

    @Test
    @WithUser(value = ALT_USER, id = "123")
    void getListACLOnDocumentReturnUnauthorizedIfUserNotAdmin() throws Exception {
        checkResponse(Status.UNAUTHORIZED, getDocumentACLPath(doc.getId(), List.of("user1")));
    }

    @Test
    void getListACLOnDocumentReturnNotFoundIfRepoDoesNotExist() throws Exception {
        checkResponse(Status.NOT_FOUND, "unknown", getDocumentACLPath(doc.getId(), List.of("user1")));
    }

    @Test
    @WithUser(value = ALT_USER, id = "123")
    void getListPermissionsOnDocumentReturnUnauthorizedIfUserNotAdmin() throws Exception {
        checkResponse(Status.UNAUTHORIZED, getDocumentPermissionsPath(doc.getId(), List.of("user1")));
    }

    @Test
    void getListPermissionsOnDocumentReturnNotFoundIfRepoDoesNotExist() throws Exception {
        checkResponse(Status.NOT_FOUND, "unknown", getDocumentPermissionsPath(doc.getId(), List.of("user1")));
    }


    @Test
    void getListPermissionsOnDocumentReturnNotFoundIfDocumentDoesNotExist() throws Exception {
        checkResponse(Status.NOT_FOUND, getDocumentPermissionsPath("unknownDoc", List.of("user1")));
    }

    @Test
    void getListACLDocumentReturnOkIfUsernamesIsEmpty() throws Exception {
        checkResponse(Status.OK, getDocumentPermissionsPath(doc.getId(), List.of()));
    }

    @Test
    void getListPermissionsOnDocumentReturnOkIfUsernamesIsEmpty() throws Exception {
        checkResponse(Status.OK, getDocumentACLPath(doc.getId(), List.of()));
    }

    @Test
    void getListACLDocumentReturnOkIfWithUsernames() throws Exception {
        checkResponse(Status.OK, getDocumentACLPath(doc.getId(), List.of("user1")));
    }

    @Test
    @Disabled("HXCS-7794/HXCS-7832 Disabled until we find a way to find out if a user is an admin")
    // documentApiEffectivePermissions is filled, because the test user has admin permissions in the principal
    // but permissionsApiEffectivePermissions is empty, because the user has no admin permissions in the principal
    void getListPermissionsOnDocumentReturnOkWithUsernames() throws Exception {
        // Get the effective permissions on the document from permissions API and document API and compare them
        ResultActions permissionsResult = checkResponse(OK,
            PATH + "/" + doc.getId());
        JSONArray documentApiEffectivePermissions = getJsonPath(permissionsResult.andReturn().getResponse().getContentAsString(),
            "sys_effectivePermissions");
        ResultActions result = checkResponse(Status.OK,
            getDocumentPermissionsPath(doc.getId(), List.of(NuxeoPrincipal.getCurrent().getId())));
        JSONArray permissionsApiEffectivePermissions = getJsonPath(result.andReturn().getResponse().getContentAsString(),
            "fullEffectivePermissions[0].userPermissions");
        Assertions.assertTrue(permissionsApiEffectivePermissions.containsAll(documentApiEffectivePermissions));
    }

    protected ResultActions checkResponse(Status expectedStatus, String repoId, String url) throws Exception {
        if (repoId == null) {
            repoId = REPO_ID;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_JSON);
        headers.add(HXCS_REPOSITORY, repoId);
        headers.add(HttpHeaders.ACCEPT, Versions.APP_HXCS_JSON_V1);

        return checkResponse(expectedStatus, GET, headers, url);
    }

    protected ResultActions checkResponse(Status expectedStatus, String url) throws Exception {
        return checkResponse(expectedStatus, REPO_ID, url);
    }

}
