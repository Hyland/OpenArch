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
package org.nuxeo.rest.acl;

import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import com.hyland.openarch.ecm.core.api.repository.RepositoryManager;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.repository.RepositoryService;
import com.hyland.openarch.ecm.core.security.SecurityService;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.rest.document.AbstractDocumentAPI;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import com.hyland.openarch.runtime.api.Framework;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = AbstractDocumentAPI.PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON,
        APP_HXCS_JSON_V1 })
@Tag(name = "Document Permissions", description = "Document Permissions and ACL API")
@Authenticated
public class DocumentPermissionsAPI extends AbstractDocumentAPI {

    PermissionsManager permissionsManager;

    ACLMapper aclMapper;

    PermissionsMapper permissionsMapper;

    public DocumentPermissionsAPI(DocumentManager documentManager, PermissionsManager permissionsManager, ACLMapper aclMapper, PermissionsMapper permissionsMapper) {
        super(documentManager);
        this.permissionsManager = permissionsManager;
        this.aclMapper = aclMapper;
        this.permissionsMapper = permissionsMapper;
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Parameter(description = "The document id for which the ACL are fetched.")
    private @interface ParameterDocId {
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Parameter(array = @ArraySchema(schema = @Schema(type = "string")), description = "The list of users to fetch the ACL on the document.")
    private @interface ParameterUserIds {
    }

    @GetMapping("/{docId}/fulleffectiveacl")
    @Operation(summary = "Fetch the full list of effective ACL on the document.", //
            description = "Fetch the full list of effective ACL on the document for the given list of users.", //
            operationId = "fulleffectiveacl")
    @ApiResponse200(description = "Returns the document full list of effective ACL.", content = {
        @Content(schema = @Schema(implementation = ListEffectiveACLDTO.class)) }
    )
    @ApiResponse404DocumentNotFound
    public ListEffectiveACLDTO getDocumentFullEffectiveACL(@ParameterDocId @PathVariable("docId") String docId,
            @ParameterUserIds @RequestParam(name = "usernames", required = false, defaultValue = "") List<String> userIds,
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId) {
        // Check if the current user has permissions on the document
        checkRepoExists(repoId);
        var session = getSession(repoId);
        DocumentRef docRef = new IdRef(docId);
        authorizeUser(session, docRef);
        checkDocumentExists(session, docRef);

        List<NuxeoPrincipal> users = getUsersPrincipals(userIds);
        var mapACL = new HashMap<String, List<ACE>>();
        users.forEach(userInfo -> {
            var expanded = permissionsManager.expandUserPrincipal(userInfo);
            var acl = permissionsManager.fetchFullListACEForUser(expanded, session, docRef);
            mapACL.put(userInfo.getId(), acl);
        });

        return aclMapper.fullEffectivePermissionsToDTO(docId, mapACL);
    }

    @GetMapping("/{docId}/fulleffectivepermissions")
    @Operation(summary = "Fetch the full list of effective permissions on the document.", //
            description = "Fetch the full list of effective permissions on the document for the given list of users.", //
            operationId = "fulleffectivepermissions")
    @ApiResponse200(description = "Returns the document full list of effective permissions.", content = {
        @Content(schema = @Schema(implementation = ListEffectivePermissionsDTO.class)) }
    )
    @ApiResponse404DocumentNotFound
    public ListEffectivePermissionsDTO getDocumentFullEffectivePermissions(
            @ParameterDocId @PathVariable("docId") String docId,
            @ParameterUserIds @RequestParam(name = "usernames", required = false, defaultValue = "") List<String> userIds,
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId) {
        // Check if the current user has permissions on the document
        checkRepoExists(repoId);
        var session = getSession(repoId);

        DocumentRef docRef = new IdRef(docId);
        authorizeUser(session, docRef);
        checkDocumentExists(session, docRef);

        var users = getUsersPrincipals(userIds);
        var securityPolicyService = Framework.getService(SecurityService.class)
                                             .getSecurityPolicyService(session.getRepositoryName());
        var repositoryService = Framework.getService(RepositoryService.class);
        var internalSession = repositoryService.getSession(repoId);
        var mapPermissions = new HashMap<String, Set<String>>();
        users.forEach(principal -> {
            var permissions = securityPolicyService.getEffectivePermissions(internalSession.getDocumentByUUID(docId),
                    principal);
            mapPermissions.put(principal.getId(), permissions);
        });

        return permissionsMapper.mapPermissionsToDTO(docId, mapPermissions);
    }

    private List<NuxeoPrincipal> getUsersPrincipals(List<String> usernames) {
        // Sanitized the list of usernames
        var sanitizedUsernames = new ArrayList<String>();
        usernames.forEach(username -> {
            var usernameSanitized = username == null ? username : username.replaceAll("[\n\r]", "_");
            if (!StringUtils.isBlank(usernameSanitized)) {
                sanitizedUsernames.add(usernameSanitized);
            }
        });
        // Fetch the updated list of users with their groups from IDP
        UserManager userManager = Framework.getService(UserManager.class);
        return userManager.getPrincipals(sanitizedUsernames);
    }

    private CoreSession getSession(String repoId) {
        return CoreInstance.getCoreSession(repoId);
    }

    private void authorizeUser(CoreSession session, DocumentRef docRef) {
        if (!NuxeoPrincipal.getCurrent().isAdministrator()
                && !session.hasPermission(docRef, SecurityConstants.EVERYTHING)) {
            throw new NuxeoException(UNAUTHORIZED.value());
        }
    }

    private void checkRepoExists(String repoId) {
        RepositoryManager repositoryManager = Framework.getService(RepositoryManager.class);
        if (repositoryManager.getRepository(repoId) == null) {
            throw new NuxeoException(NOT_FOUND.value());
        }
    }

    private void checkDocumentExists(CoreSession session, DocumentRef docRef) {
        if (!session.exists(docRef)) {
            throw new NuxeoException(NOT_FOUND.value());
        }
    }
}
