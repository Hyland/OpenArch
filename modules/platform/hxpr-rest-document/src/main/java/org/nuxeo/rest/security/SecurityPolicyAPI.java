/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.rest.security;

// We keep jakarta MediaType.APPLICATION_JSON_PATCH import here as there in no spring equivalent
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON;
import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.security.SecurityPolicyAPI.API_PATH;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PATCH_REQUEST_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PATCH_RESPONSE_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PUT_FOR_PROJECT_REQUEST_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PUT_FOR_PROJECT_RESPONSE_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PUT_REQUEST_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PUT_RESPONSE_EXAMPLE;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import org.nuxeo.rest.auth.AdministratorAccess;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse204;
import org.nuxeo.rest.swagger.annotation.ApiResponse404RepositoryNotFound;
import org.nuxeo.rest.swagger.annotation.ApiResponse409;
import org.nuxeo.rest.swagger.annotation.ApiResponse422;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.nuxeo.rest.swagger.annotation.ParameterValidateOnly;
import org.nuxeo.rest.swagger.annotation.SwaggerRequestBody;
import org.nuxeo.runtime.api.Framework;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

/**
 * Security Policy REST API.
 */
@RestController
@RequestMapping(path = API_PATH, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
@Tag(name = "Security", description = "Security Policy API")
@Authenticated
@CrossOrigin
public class SecurityPolicyAPI {

    public static final String API_PATH = "/api/repository/security/policies"; // NOSONAR

    private static final String SECURITY_POLICY_VALIDATE_ONLY = "validateOnly";

    private final SecurityPolicyManager manager;

    private final ObjectMapper mapper;

    private final SecurityPolicyConfigMapper configMapper;


    public SecurityPolicyAPI(SecurityPolicyManager manager, ObjectMapper mapper,
            SecurityPolicyConfigMapper configMapper) {
        this.manager = manager;
        this.mapper = mapper;
        this.configMapper = configMapper;
    }

    public RepositoryManager getRepositoryManager() {
        return  Framework.getService(RepositoryManager.class);
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    @ApiResponse204(description = ApiResponse204SecurityPolicyValid.DESCRIPTION)
    private @interface ApiResponse204SecurityPolicyValid {
        public static final String DESCRIPTION = "The security policies provided are valid and can be applied.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    @ApiResponse200(content = {
            @Content(schema = @Schema(type = "object", additionalPropertiesSchema = SecurityPolicyConfigDTO.class), examples = @ExampleObject(PUT_RESPONSE_EXAMPLE)) })
    private @interface ApiResponse200PutContentExample {
        @AliasFor(annotation = ApiResponse.class, attribute = "description")
        String description() default "";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse422(description = ApiResponse422ValidationError.DESCRIPTION)
    private @interface ApiResponse422ValidationError {
        public static final String DESCRIPTION = "Validation error.";
    }

    @GetMapping(consumes = ALL_VALUE)
    @Operation(summary = "Returns the security policies for the repository.", operationId = "getSecurityPolicies")
    @ApiResponse200(description = "Returns the security policies for the repository.", content = {
            @Content(schema = @Schema(type = "object", additionalPropertiesSchema = SecurityPolicyConfigDTO.class),
                examples = @ExampleObject(PUT_RESPONSE_EXAMPLE)) })
    @ApiResponse404RepositoryNotFound
    public Map<String, SecurityPolicyConfigDTO> doGet(
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId) {
        // This endpoint is not restricted to administrators
        checkRepoExists(repoId);
        Map<String, SecurityPolicyConfig> configWithoutHidden = manager.getConfigWithoutHidden(repoId, null, false);
        return configMapper.convertToDTO(configWithoutHidden);
    }

    @PutMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Sets the security policies for the repository. "
            + "Changes to fixed security policies (such as acl, lock and retention) will be ignored.", operationId = "setSecurityPolicies")
    @ApiResponse200(description = "Sets and returns security policies for the repository.", content = {
            @Content(schema = @Schema(type = "object", additionalPropertiesSchema = SecurityPolicyConfigDTO.class),
                examples = @ExampleObject(PUT_FOR_PROJECT_RESPONSE_EXAMPLE)) })
    @ApiResponse204SecurityPolicyValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = @Content(examples = @ExampleObject(PUT_REQUEST_EXAMPLE)))
    @AdministratorAccess
    public ResponseEntity<Map<String, SecurityPolicyConfigDTO>> doPut(
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId,
            @ParameterValidateOnly @RequestParam(name = SECURITY_POLICY_VALIDATE_ONLY, defaultValue = "false") boolean validateOnly,
            @RequestBody Map<String, SecurityPolicyConfigDTO> policyConfigDTOMap) {
        Map<String, SecurityPolicyConfig> securityPolicyConfigMap = configMapper.convertFromDTO(policyConfigDTOMap);
        checkRepoExists(repoId);

        manager.put(repoId, validateOnly, securityPolicyConfigMap);
        Map<String, SecurityPolicyConfig> configWithoutHidden = manager.getConfigWithoutHidden(repoId, null,
                validateOnly);
        return configWithoutHidden == null ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(configMapper.convertToDTO(configWithoutHidden));
    }

    @PutMapping(path = "/project/{projectId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Sets security policies for a specific project. Any security policies in the request that "
            + "have the same name as a system-wide security policy or one for another project or will be ignored. "
            + "The projectId is automatically set in security policies in the request.", operationId = "setSecurityPoliciesForProject")
    @ApiResponse200PutContentExample(description = "Sets the security policy specific to a project and returns the security policies for the repository.")
    @ApiResponse204SecurityPolicyValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse409(description = "Conflict as security policy proposed for update already exists.")
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = { @Content(examples = @ExampleObject(PUT_FOR_PROJECT_REQUEST_EXAMPLE)) })
    @AdministratorAccess
    public ResponseEntity<Map<String, SecurityPolicyConfigDTO>> doPutForProject(@PathVariable("projectId") String projectId,
            @ParameterValidateOnly @RequestParam(name = SECURITY_POLICY_VALIDATE_ONLY, defaultValue = "false") boolean validateOnly,
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId,
            @RequestBody Map<String, SecurityPolicyConfigDTO> policyConfigDTOMap) {
        Map<String, SecurityPolicyConfig> securityPolicyConfigMap = configMapper.convertFromDTO(policyConfigDTOMap);
        checkRepoExists(repoId);

        manager.putProject(repoId, projectId, validateOnly, securityPolicyConfigMap);
        Map<String, SecurityPolicyConfig> configWithoutHidden = manager.getConfigWithoutHidden(repoId, projectId,
                validateOnly);
        return configWithoutHidden == null ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(configMapper.convertToDTO(configWithoutHidden));
    }

    @PatchMapping(consumes = APPLICATION_JSON_PATCH_JSON )
    @Operation(summary = "Patches the security policies for the repository. "
            + "Changes to or even tests on fixed security policies (such as acl, lock and retention) will result in an error.", operationId = "patchSecurityPolicies")
    @ApiResponse200(description = "Patches and returns the security policies for the repository.", content = {
            @Content(schema = @Schema(type = "object", additionalPropertiesSchema = SecurityPolicyConfigDTO.class),
                examples = @ExampleObject(PATCH_RESPONSE_EXAMPLE)) })
    @ApiResponse204SecurityPolicyValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = @Content(schema = @Schema(type = "object"), examples = @ExampleObject(PATCH_REQUEST_EXAMPLE)))
    @AdministratorAccess
    public ResponseEntity<Map<String, SecurityPolicyConfigDTO>> doPatch(
            @ParameterValidateOnly @RequestParam(name = SECURITY_POLICY_VALIDATE_ONLY, defaultValue = "false") boolean validateOnly,
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId,
            @RequestBody Object patchBody) throws IOException {
        checkRepoExists(repoId);
        manager.patch(mapper, repoId, validateOnly, patchBody);
        Map<String, SecurityPolicyConfig> configWithoutHidden = manager.getConfigWithoutHidden(repoId, null, validateOnly);
        return configWithoutHidden == null ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(configMapper.convertToDTO(configWithoutHidden));
    }


    private void checkRepoExists(String repoId) {
        if (getRepositoryManager().getRepository(repoId) == null) {
            throw new NuxeoException(NOT_FOUND.value());
        }
    }

}
