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
package com.hyland.openarch.rest.security;

// We keep jakarta MediaType.APPLICATION_JSON_PATCH import here as there in no spring equivalent
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON;
import static com.hyland.openarch.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static com.hyland.openarch.rest.security.SecurityPolicyRequestConstants.PATCH_REQUEST_EXAMPLE;
import static com.hyland.openarch.rest.security.SecurityPolicyRequestConstants.PATCH_RESPONSE_EXAMPLE;
import static com.hyland.openarch.rest.security.SecurityPolicyRequestConstants.PUT_FOR_PROJECT_REQUEST_EXAMPLE;
import static com.hyland.openarch.rest.security.SecurityPolicyRequestConstants.PUT_FOR_PROJECT_RESPONSE_EXAMPLE;
import static com.hyland.openarch.rest.security.SecurityPolicyRequestConstants.PUT_REQUEST_EXAMPLE;
import static com.hyland.openarch.rest.security.SecurityPolicyRequestConstants.PUT_RESPONSE_EXAMPLE;
import static com.hyland.openarch.rest.utils.Headers.HXCS_REPOSITORY;
import static com.hyland.openarch.rest.utils.OptimisticLocking.requireVersion;
import static com.hyland.openarch.rest.utils.Versions.APP_HXCS_JSON;
import static com.hyland.openarch.rest.utils.Versions.APP_HXCS_JSON_V1;
import static com.hyland.openarch.rest.utils.Versions.APP_HXCS_JSON_V2;
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

import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.api.repository.RepositoryManager;
import com.hyland.openarch.ecm.core.security.config.SecurityPolicyConfig;
import com.hyland.openarch.rest.security.SecurityPolicyManager.ConfigWithVersion;
import com.hyland.openarch.rest.auth.AdministratorAccess;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse200;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse204;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse400MissingVersion;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse404RepositoryNotFound;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse409OptimisticLock;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse409;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse422;
import com.hyland.openarch.rest.swagger.annotation.Authenticated;
import com.hyland.openarch.rest.swagger.annotation.ParameterRepoId;
import com.hyland.openarch.rest.swagger.annotation.ParameterValidateOnly;
import com.hyland.openarch.rest.swagger.annotation.SwaggerRequestBody;
import com.hyland.openarch.runtime.api.Framework;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

/**
 * Security Policy REST API.
 */
@RestController
@Tag(name = "Security", description = "Security Policy API")
@Authenticated
public class SecurityPolicyAPI {

    public static final String API_PATH = "/api/repository/security/policies"; // NOSONAR

    /** Same API with optimistic locking, see {@link SecurityPoliciesDTO}. */
    public static final String API_PATH_V2 = "/api/v2/repository/security/policies"; // NOSONAR

    /** Swagger tag grouping the v2 operations. */
    private static final String TAG_V2 = "Security v2";

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

    @GetMapping(path = API_PATH, consumes = ALL_VALUE, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
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

    @GetMapping(path = API_PATH_V2, consumes = ALL_VALUE, produces = { APP_HXCS_JSON_V2, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
    @Operation(summary = "Returns the security policies and their version for the repository.", operationId = "getSecurityPoliciesV2", tags = {
            TAG_V2 })
    @ApiResponse200(description = "Returns the security policies and their version for the repository.", content = {
            @Content(schema = @Schema(implementation = SecurityPoliciesDTO.class)) })
    @ApiResponse404RepositoryNotFound
    public SecurityPoliciesDTO doGetV2(
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId) {
        // This endpoint is not restricted to administrators
        checkRepoExists(repoId);
        // the policies and the version come from one read of the persisted configuration, so a client sending the version
        // back is writing on top of the very policies it was given
        return getPersistedPoliciesDTO(repoId, null);
    }

    @PutMapping(path = API_PATH, consumes = APPLICATION_JSON_VALUE, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
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

    @PutMapping(path = API_PATH_V2, consumes = APPLICATION_JSON_VALUE, produces = { APP_HXCS_JSON_V2, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
    @Operation(summary = "Sets the security policies for the repository, using optimistic locking. "
            + "Changes to fixed security policies (such as acl, lock and retention) will be ignored.", operationId = "setSecurityPoliciesV2", tags = {
                    TAG_V2 })
    @ApiResponse200(description = "Sets and returns the security policies and their new version for the repository.", content = {
            @Content(schema = @Schema(implementation = SecurityPoliciesDTO.class)) })
    @ApiResponse204SecurityPolicyValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse409OptimisticLock
    @ApiResponse400MissingVersion
    @ApiResponse422ValidationError
    @AdministratorAccess
    public ResponseEntity<SecurityPoliciesDTO> doPutV2(
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId,
            @ParameterValidateOnly @RequestParam(name = SECURITY_POLICY_VALIDATE_ONLY, defaultValue = "false") boolean validateOnly,
            @RequestBody SecurityPoliciesDTO policies) {
        checkRepoExists(repoId);
        Map<String, SecurityPolicyConfig> config = configMapper.convertFromDTO(policies.policies());
        manager.put(repoId, validateOnly, config, requireVersion(policies.version()));
        if (validateOnly) {
            return ResponseEntity.noContent().build();
        }
        // report the policies and their version from one read, rather than pairing the version this write produced with
        // separately read policies: a write landing in between would make the two describe different states
        return ResponseEntity.ok(getPersistedPoliciesDTO(repoId, null));
    }

    @PutMapping(path = API_PATH + "/project/{projectId}", consumes = APPLICATION_JSON_VALUE, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
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

    @PutMapping(path = API_PATH_V2 + "/project/{projectId}", consumes = APPLICATION_JSON_VALUE, produces = { APP_HXCS_JSON_V2, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
    @Operation(summary = "Sets security policies for a specific project, using optimistic locking. Any security policies "
            + "in the request that have the same name as a system-wide security policy or one for another project will "
            + "be ignored. The projectId is automatically set in security policies in the request.", operationId = "setSecurityPoliciesForProjectV2", tags = {
                    TAG_V2 })
    @ApiResponse200(description = "Sets the security policies specific to a project and returns the security policies and their new version for the repository.", content = {
            @Content(schema = @Schema(implementation = SecurityPoliciesDTO.class)) })
    @ApiResponse204SecurityPolicyValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse409OptimisticLock
    @ApiResponse400MissingVersion
    @ApiResponse422ValidationError
    @AdministratorAccess
    public ResponseEntity<SecurityPoliciesDTO> doPutForProjectV2(@PathVariable("projectId") String projectId,
            @ParameterValidateOnly @RequestParam(name = SECURITY_POLICY_VALIDATE_ONLY, defaultValue = "false") boolean validateOnly,
            @ParameterRepoId @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) String repoId,
            @RequestBody SecurityPoliciesDTO policies) {
        checkRepoExists(repoId);
        Map<String, SecurityPolicyConfig> config = configMapper.convertFromDTO(policies.policies());
        manager.putProject(repoId, projectId, validateOnly, config, requireVersion(policies.version()));
        if (validateOnly) {
            return ResponseEntity.noContent().build();
        }
        // the policies and their version come from one read, see doPutV2
        return ResponseEntity.ok(getPersistedPoliciesDTO(repoId, projectId));
    }

    @PatchMapping(path = API_PATH, consumes = APPLICATION_JSON_PATCH_JSON, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
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
            throw new RepositoryException(NOT_FOUND.value());
        }
    }

    /**
     * The v2 body for the persisted security policies, of the whole repository or of one project: the policies as DTOs,
     * carrying the version they were read at.
     *
     * @param repoId the repository id
     * @param projectId the project to restrict the policies to, or {@code null} for all of them
     */
    private SecurityPoliciesDTO getPersistedPoliciesDTO(String repoId, String projectId) {
        ConfigWithVersion config = manager.getPersistedConfigWithoutHidden(repoId, projectId);
        return SecurityPoliciesDTO.of(configMapper.convertToDTO(config.config()), config.version());
    }

}
