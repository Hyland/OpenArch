/*
 * (C) Copyright 2021-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.rest.model;

// We keep jakarta MediaType.APPLICATION_JSON_PATCH import here as there in no spring equivalent
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON;
import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.model.ModelRequestConstants.PATCH_REQUEST_EXAMPLE;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_FOR_PROJECT_REQUEST_EXAMPLE;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_FOR_PROJECT_RESPONSE_EXAMPLE;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_REQUEST_EXAMPLE;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.QueryParameters.MODEL_VALIDATE_ONLY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.rest.auth.AdministratorAccess;
import org.nuxeo.rest.model.dto.Constants;
import org.nuxeo.rest.model.dto.ModelDTO;
import org.nuxeo.rest.model.validation.ModelValidator;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse204;
import org.nuxeo.rest.swagger.annotation.ApiResponse404RepositoryNotFound;
import org.nuxeo.rest.swagger.annotation.ApiResponse409;
import org.nuxeo.rest.swagger.annotation.ApiResponse422;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.nuxeo.rest.swagger.annotation.ParameterValidateOnly;
import org.nuxeo.rest.swagger.annotation.SwaggerRequestBody;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Model REST API.
 */
@RestController
@RequestMapping(path = Constants.API_PATH, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON,
        APPLICATION_JSON_VALUE })
@Tag(name = "Model", description = "Repository Model API")
@Authenticated
@CrossOrigin
public class ModelAPI {

    private static final Logger log = LoggerFactory.getLogger(ModelAPI.class);

    private final ModelManager modelManager;

    private final ObjectMapper mapper;

    private final ModelValidator modelValidator;

    public ModelAPI(ModelManager modelManager, ModelValidator modelValidator, ObjectMapper mapper) {
        this.modelManager = modelManager;
        this.modelValidator = modelValidator;
        this.mapper = mapper;
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse200(content = { @Content(schema = @Schema(implementation = ModelDTO.class), examples = {
            @ExampleObject(value = PUT_REQUEST_EXAMPLE) }) })
    private @interface ApiResponse200Model {
        @AliasFor(annotation = ApiResponse200.class, attribute = "description")
        String description() default "Returns the model for the repository.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse204()
    private @interface ApiResponse204ModelValid {
        @AliasFor(annotation = ApiResponse204.class, attribute = "description")
        String description() default "The content model provided is valid and can be applied.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse422()
    private @interface ApiResponse422ValidationError {
        @AliasFor(annotation = ApiResponse422.class, attribute = "description")
        String description() default "Unprocessable Entity representing a validation error when updating the content model.";
    }

    @GetMapping(consumes = ALL_VALUE)
    @Operation(summary = "Returns the model for the repository.", operationId = "getModel")
    @ApiResponse200Model
    @ApiResponse404RepositoryNotFound
    public ResponseEntity<ModelDTO> doGet(
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        checkRepoExists(repoId);

        return ResponseEntity.ok(ModelDTO.toDTO(modelManager.getSchemaManagerDTO(repoId)));
    }

    @PutMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Sets the model for the repository.", operationId = "setModel")
    @ApiResponse200Model(description = "Sets and returns the model for the repository.")
    @ApiResponse204ModelValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = { @Content(examples = { @ExampleObject(value = PUT_REQUEST_EXAMPLE) }) })
    @AdministratorAccess
    public ResponseEntity<ModelDTO> doPut(
            @RequestParam(value = MODEL_VALIDATE_ONLY, required = false, defaultValue = "false") @ParameterValidateOnly boolean validateOnly,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody(required = false) ModelDTO modelDTO) {
        checkRepoExists(repoId);
        modelValidator.validate(modelDTO);
        SchemaManager schemaManager = generateSchemaManagerBasedOnDto(repoId, modelDTO, validateOnly);
        if (!validateOnly) {
            modelManager.persistSchemaManager(repoId, schemaManager);
        }
        return validateOnly ? ResponseEntity.noContent().build() : doGet(repoId);
    }

    @PatchMapping(consumes = APPLICATION_JSON_PATCH_JSON)
    @Operation(summary = "Patches the model for the repository.", operationId = "patchModel")
    @ApiResponse200Model(description = "Patches and returns the model for the repository.")
    @ApiResponse204ModelValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = {
            @Content(schema = @Schema(type = "object"), examples = @ExampleObject(value = PATCH_REQUEST_EXAMPLE)) })
    @AdministratorAccess
    public ResponseEntity<ModelDTO> doPatch(
            @RequestParam(value = MODEL_VALIDATE_ONLY, required = false, defaultValue = "false") @ParameterValidateOnly boolean validateOnly,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody(required = false) Object patchBody) throws IOException {
        checkRepoExists(repoId);
        SchemaManager schemaManager = modelManager.applyPatch(repoId, mapper.writeValueAsString(patchBody),
                validateOnly);
        if (!validateOnly) {
            modelManager.persistSchemaManager(repoId, schemaManager);
        }
        return validateOnly ? ResponseEntity.noContent().build() : doGet(repoId);
    }

    @PutMapping(path = "/project/{projectId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Sets the model items specific to projects in the existing content model for the repository.", operationId = "setModelItemsForProject")
    @ApiResponse200(description = "Sets the model items specific to projects in the existing content model and returns the model for the repository.", content = {
        @Content(schema = @Schema(implementation = ModelDTO.class),
            examples = { @ExampleObject(value = PUT_FOR_PROJECT_RESPONSE_EXAMPLE) }) })
    @ApiResponse204ModelValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse409(description = "Conflict as content model item proposed for update already exists.")
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = { @Content(examples = { @ExampleObject(value = PUT_FOR_PROJECT_REQUEST_EXAMPLE) }) })
    @AdministratorAccess
    public ResponseEntity<ModelDTO> doPutForProject(@PathVariable("projectId") String projectId,
            @RequestParam(value = MODEL_VALIDATE_ONLY, required = false, defaultValue = "false") @ParameterValidateOnly boolean validateOnly,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody(required = false) ModelDTO modelDTO) {
        checkRepoExists(repoId);
        modelValidator.validate(Framework.getService(TypeService.class).getSchemaManager(repoId), modelDTO, projectId);
        ModelDTO modelDTOFromGet = doGet(repoId).getBody();
        ModelDTO consolidatedModelDTO = modelManager.createConsolidatedContentModel(modelDTOFromGet, modelDTO,
                projectId);
        SchemaManager schemaManager = generateSchemaManagerBasedOnDto(repoId, consolidatedModelDTO, validateOnly);
        if (!validateOnly) {
            modelManager.persistSchemaManager(repoId, schemaManager);
        }
        return validateOnly ? ResponseEntity.noContent().build() : doGet(repoId);
    }

    private void checkRepoExists(String repoId) {
        RepositoryManager repositoryManager = Framework.getService(RepositoryManager.class);
        if (repositoryManager.getRepository(repoId) == null) {
            throw new NuxeoException(NOT_FOUND.value());
        }
    }

    @NonNull
    private SchemaManager generateSchemaManagerBasedOnDto(String repoId, ModelDTO modelDTO, boolean validateOnly) {
        SchemaManager schemaManager;
        Map<String, Object> dto = modelDTO.toMap();
        try {
            schemaManager = modelManager.createSchemaManagerDTO(repoId, dto, validateOnly);
        } catch (NullPointerException | ClassCastException | RuntimeServiceException e) {
            log.debug("Invalid model", e);
            throw new NuxeoException(BAD_REQUEST.value());
        }
        return schemaManager;
    }

    @GetMapping(path = "/project/{projectId}")
    @Operation(summary = "Returns the model specific to a project for the repository.", operationId = "getModelItemsForProject")
    @ApiResponse200(description = "The model items specific to a project in the existing content model and returns the model for the repository.", content = {
            @Content(schema = @Schema(implementation = ModelDTO.class), examples = {
                    @ExampleObject(value = PUT_FOR_PROJECT_RESPONSE_EXAMPLE) }) })
    @ApiResponse404RepositoryNotFound
    @AdministratorAccess
    public ResponseEntity<ModelDTO> doGetForProject(@PathVariable("projectId") String projectId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        checkRepoExists(repoId);
        if (StringUtils.isBlank(projectId)) {
            return doGet(repoId);
        }
        return ResponseEntity.ok(ModelDTO.toDTO(modelManager.getSchemaManagerDTO(repoId, projectId)));
    }

}
