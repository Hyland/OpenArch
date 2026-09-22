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
package org.nuxeo.rest.security;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.security.SecurityPolicyAPI.API_PATH;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.security.ExternalUserRelationsSecurityPolicy;
import org.nuxeo.ecm.core.security.SecurityPolicy;
import org.nuxeo.ecm.core.security.SecurityPolicyService;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.rest.auth.AdministratorAccess;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse204;
import org.nuxeo.rest.swagger.annotation.ApiResponse400;
import org.nuxeo.rest.swagger.annotation.ApiResponse404;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import com.hyland.openarch.runtime.api.Framework;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * External User Relations Security Policy REST API
 */
@RestController
@RequestMapping(path = API_PATH, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON, APPLICATION_JSON_VALUE }, consumes = {
        APPLICATION_JSON_VALUE })
@Tag(name = "Security", description = "Security Policy API")
@Authenticated
@AdministratorAccess
public class ExternalUserRelationsSecurityPolicyAPI {

    public static final String STATUS_COUNT_KEY = "count";

    public static final String STATUS_LAST_MODIFIED_KEY = "lastModified";

    @GetMapping(path = "/{policyName}/externaluserrelations", consumes = ALL_VALUE)
    @ApiResponse404(description = "Repository or external user relations policy not found.")
    @ApiResponse200(description = "Returns the external user relations config for the policy.")
    @Operation(summary = "Returns the external user relations for a policy.", operationId = "getExternalUserRelations")
    public ExternalUserRelationsSecurityPolicyRelationsDTO doGet(
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId,
            @Parameter(description = "The policy name.") @PathVariable("policyName") String policyName) {
        checkRepoExists(repoId);
        return toDTO(getPolicy(repoId, policyName).getExternalUserRelations());
    }

    @PutMapping(path = "/{policyName}/externaluserrelations")
    @ResponseStatus(NO_CONTENT)
    @ApiResponse404(description = "Repository or external user relations policy not found.")
    @ApiResponse400(description = "Invalid external user relations config payload.")
    @ApiResponse204(description = "The provided external user relations config has been applied.")
    @Operation(summary = "Sets the external user relations for a policy.", operationId = "setExternalUserRelations")
    public void doPut(
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId,
            @Parameter(description = "The policy name.") @PathVariable("policyName") String policyName,
            @RequestBody @Valid ExternalUserRelationsSecurityPolicyRelationsDTO relationsConfig) {
        checkRepoExists(repoId);
        ExternalUserRelationsSecurityPolicy policy = getPolicy(repoId, policyName);
        policy.setExternalUserRelations(fromDTO(relationsConfig));
    }

    @GetMapping(path = "/{policyName}/externaluserrelations/status", consumes = ALL_VALUE)
    @ApiResponse404(description = "Repository or external user relations policy not found.")
    @ApiResponse200(description = "Returns the status of the external user relations.")
    @Operation(summary = "Returns the status of the external user relations for a policy.", operationId = "getExternalUserRelationsStatus")
    public Map<String, Object> doGetStatus(
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId,
            @Parameter(description = "The policy name.") @PathVariable("policyName") String policyName) {
        checkRepoExists(repoId);
        ExternalUserRelationsSecurityPolicy policy = getPolicy(repoId, policyName);
        return Map.of(STATUS_COUNT_KEY, policy.getExternalUserRelations().size(), STATUS_LAST_MODIFIED_KEY,
                policy.getExternalUserRelationsLastModified());
    }

    private void checkRepoExists(String repoId) {
        if (Framework.getService(RepositoryManager.class).getRepository(repoId) == null) {
            throw new NuxeoException(NOT_FOUND.getStatusCode());
        }
    }

    private ExternalUserRelationsSecurityPolicy getPolicy(String repoId, String policyName) {
        SecurityService securityService = Framework.getService(SecurityService.class);
        SecurityPolicyService securityPolicyService = securityService.getSecurityPolicyService(repoId);
        SecurityPolicy policy = securityPolicyService.getSecurityPolicies()
                                                     .stream()
                                                     .filter(p -> p.getName().equals(policyName))
                                                     .findFirst()
                                                     .orElse(null);
        if (policy instanceof ExternalUserRelationsSecurityPolicy relationsPolicy) {
            return relationsPolicy;
        }
        throw new NuxeoException(NOT_FOUND.getStatusCode());
    }

    /**
     * Converts the internal representation of the relations to the DTO object.
     *
     * @param internalRelations the internal representation object
     * @return the relations DTO object
     */
    private ExternalUserRelationsSecurityPolicyRelationsDTO toDTO(Map<String, Object> internalRelations) {
        @SuppressWarnings("unchecked")
        List<ExternalUserRelationsSecurityPolicyUserDTO> users = internalRelations.entrySet()
                .stream()
                .map(e -> new ExternalUserRelationsSecurityPolicyUserDTO(e.getKey(), (Map<String, List<String>>) e.getValue()))
                .toList();
        return new ExternalUserRelationsSecurityPolicyRelationsDTO(users);
    }

    /**
     * Converts the relations DTO object to the internal representation.
     *
     * @param dto the relations DTO object
     * @return the internal representation object
     */
    private Map<String, Map<String, List<String>>> fromDTO(ExternalUserRelationsSecurityPolicyRelationsDTO dto) {
        Map<String, Map<String, List<String>>> internalRelations = new HashMap<>();
        dto.users().forEach(user -> internalRelations.put(user.id(), user.relations()));
        return internalRelations;
    }

}
