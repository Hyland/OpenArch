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
package org.nuxeo.rest.model;

// We keep jakarta MediaType.APPLICATION_JSON_PATCH import here as there in no spring equivalent
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON;
import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.model.ModelRequestConstants.PATCH_REQUEST_EXAMPLE;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_FOR_PROJECT_REQUEST_EXAMPLE;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_FOR_PROJECT_REQUEST_EXAMPLE_V2;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_FOR_PROJECT_RESPONSE_EXAMPLE;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_FOR_PROJECT_RESPONSE_EXAMPLE_V2;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_REQUEST_EXAMPLE;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_REQUEST_EXAMPLE_V2;
import static org.nuxeo.rest.model.ModelRequestConstants.PUT_RESPONSE_EXAMPLE_V2;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.OptimisticLocking.requireVersion;
import static org.nuxeo.rest.utils.QueryParameters.MODEL_VALIDATE_ONLY;
import static org.nuxeo.rest.utils.ValidationStatus.conflictOnValidationFailure;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V2;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.schema.TypeService.ModelWithVersion;
import org.nuxeo.ecm.core.schema.dto.ContentModelDTO;
import org.nuxeo.rest.auth.AdministratorAccess;
import org.nuxeo.rest.model.validation.ModelValidator;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse204;
import org.nuxeo.rest.swagger.annotation.ApiResponse400MissingVersion;
import org.nuxeo.rest.swagger.annotation.ApiResponse404RepositoryNotFound;
import org.nuxeo.rest.swagger.annotation.ApiResponse409;
import org.nuxeo.rest.swagger.annotation.ApiResponse409OptimisticLock;
import org.nuxeo.rest.swagger.annotation.ApiResponse422;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.nuxeo.rest.swagger.annotation.ParameterValidateOnly;
import org.nuxeo.rest.swagger.annotation.SwaggerRequestBody;
import com.hyland.openarch.runtime.RuntimeServiceException;
import com.hyland.openarch.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Model REST API.
 * <p>
 * Two versions of the read/set operations are exposed side by side:
 * <ul>
 * <li><b>v1</b> ({@value Constants#API_PATH}): the original, version-unaware API.</li>
 * <li><b>v2</b> ({@value Constants#API_PATH_V2}): adds <b>optimistic locking</b> through an integer {@code version}
 * counter on the model. A client reads the current {@code version} (via {@code GET}), computes its update and sends it
 * back with the {@code version} it read; if the persisted {@code version} has moved, the update is rejected with a
 * {@code 409 Conflict} and the caller should retry after a fresh read (ideally with exponential backoff and jitter).</li>
 * </ul>
 * {@code PATCH} has no v2: because a patch is a delta applied to the current model server-side, the single
 * {@code PATCH} operation is made atomic (read-modify-conditional-write with internal retry) with no client-supplied
 * {@code version} and no change to its request or response.
 * <p>
 * Method-level paths and {@code produces} (rather than a class-level base path) are used because the two versions
 * live under different roots and expose different media types: the v1 operations produce {@code vnd.hxcs.v1+json} and
 * the v2 operations produce {@code vnd.hxcs.v2+json}.
 */
@RestController
@Tag(name = "Model", description = "Repository Model API")
@Authenticated
public class ModelAPI {

    private static final Logger log = LoggerFactory.getLogger(ModelAPI.class);

    /** Swagger tag grouping the v2 operations. */
    private static final String TAG_V2 = "Model v2";

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
    @ApiResponse200(content = { @Content(schema = @Schema(implementation = ContentModelDTO.class), examples = {
            @ExampleObject(value = PUT_REQUEST_EXAMPLE) }) })
    private @interface ApiResponse200Model {
        @AliasFor(annotation = ApiResponse200.class, attribute = "description")
        String description() default "Returns the model for the repository.";
    }

    /** v2 counterpart of {@link ApiResponse200Model}: the example carries the optimistic-locking {@code version}. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse200(content = { @Content(schema = @Schema(implementation = ContentModelDTO.class), examples = {
            @ExampleObject(value = PUT_RESPONSE_EXAMPLE_V2) }) })
    private @interface ApiResponse200ModelV2 {
        @AliasFor(annotation = ApiResponse200.class, attribute = "description")
        String description() default "Returns the model and its version for the repository.";
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

    // ------------------------------------------------------------------------
    // GET
    // ------------------------------------------------------------------------

    @GetMapping(path = Constants.API_PATH, consumes = ALL_VALUE, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON,
            APPLICATION_JSON_VALUE })
    @Operation(summary = "Returns the model for the repository.", operationId = "getModel")
    @ApiResponse200Model
    @ApiResponse404RepositoryNotFound
    public ResponseEntity<ContentModelDTO> doGet(
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        checkRepoExists(repoId);

        return ResponseEntity.ok(modelManager.getContentModel(repoId));
    }

    @GetMapping(path = Constants.API_PATH_V2, consumes = ALL_VALUE, produces = { APP_HXCS_JSON_V2, APP_HXCS_JSON,
            APPLICATION_JSON_VALUE })
    @Operation(summary = "Returns the model and its version for the repository.", operationId = "getModelV2", tags = {
            TAG_V2 })
    @ApiResponse200ModelV2
    @ApiResponse404RepositoryNotFound
    public ResponseEntity<ContentModelDTO> doGetV2(
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        checkRepoExists(repoId);
        ModelWithVersion current = modelManager.getModelWithVersion(repoId);
        return ResponseEntity.ok(current.model().withVersion(current.version()));
    }

    // ------------------------------------------------------------------------
    // PUT (set full model)
    // ------------------------------------------------------------------------

    @PutMapping(path = Constants.API_PATH, consumes = APPLICATION_JSON_VALUE, produces = { APP_HXCS_JSON_V1,
            APP_HXCS_JSON, APPLICATION_JSON_VALUE })
    @Operation(summary = "Sets the model for the repository.", operationId = "setModel")
    @ApiResponse200Model(description = "Sets and returns the model for the repository.")
    @ApiResponse204ModelValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse409(description = "The content model failed request-level validation: a field default value is not of"
            + " the declared type, a constraint bound cannot be read, or a primary type omits the system schema.")
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = { @Content(examples = { @ExampleObject(value = PUT_REQUEST_EXAMPLE) }) })
    @AdministratorAccess
    public ResponseEntity<ContentModelDTO> doPut(
            @RequestParam(value = MODEL_VALIDATE_ONLY, required = false, defaultValue = "false") @ParameterValidateOnly boolean validateOnly,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody ContentModelDTO modelDTO) {
        checkRepoExists(repoId);
        // this endpoint reports a model it refuses as 409 for backward compatibility; the v2 one reports the 422 the
        // validation failure carries, leaving 409 to mean only that the version sent is stale
        return conflictOnValidationFailure(() -> {
            modelValidator.validate(modelDTO);
            SchemaManager schemaManager = generateSchemaManagerBasedOnDto(repoId, modelDTO, validateOnly);
            if (!validateOnly) {
                modelManager.persistSchemaManager(repoId, schemaManager);
            }
            return validateOnly ? ResponseEntity.noContent().build() : doGet(repoId);
        });
    }

    @PutMapping(path = Constants.API_PATH_V2, consumes = APPLICATION_JSON_VALUE, produces = { APP_HXCS_JSON_V2,
            APP_HXCS_JSON, APPLICATION_JSON_VALUE })
    @Operation(summary = "Sets the model for the repository, using optimistic locking.", operationId = "setModelV2", tags = {
            TAG_V2 })
    @ApiResponse200ModelV2(description = "Sets and returns the model and its new version for the repository.")
    @ApiResponse204ModelValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse409OptimisticLock
    @ApiResponse400MissingVersion
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = { @Content(examples = { @ExampleObject(value = PUT_REQUEST_EXAMPLE_V2) }) })
    @AdministratorAccess
    public ResponseEntity<ContentModelDTO> doPutV2(
            @RequestParam(value = MODEL_VALIDATE_ONLY, required = false, defaultValue = "false") @ParameterValidateOnly boolean validateOnly,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody ContentModelDTO dto) {
        checkRepoExists(repoId);
        // a v2 payload carries a version, whether or not this request goes on to write: a validate-only request
        // describes the write it is checking, and that write is conditioned on a version
        long expectedVersion = requireVersion(dto.version());
        // the version is not part of the content: drop it so it is neither validated nor persisted as a model item
        ContentModelDTO content = dto.withoutVersion();
        modelValidator.validate(content);
        // Diff against the persisted snapshot the write is conditioned on rather than the node-local model, which can
        // lag behind it while a cluster invalidation is in flight. The write only succeeds while the persisted version
        // is still the one sent, and content never changes without the version changing, so the state a successful write
        // lands on is the very one validated here.
        ContentModelDTO currentModel = modelManager.getModelWithVersion(repoId).model();
        SchemaManager schemaManager = generateSchemaManagerBasedOnSnapshot(currentModel, content, validateOnly);
        if (validateOnly) {
            return ResponseEntity.noContent().build();
        }
        modelManager.persistSchemaManager(repoId, schemaManager, expectedVersion);
        return doGetV2(repoId);
    }

    // ------------------------------------------------------------------------
    // PATCH (JSON patch)
    // ------------------------------------------------------------------------

    // PATCH has a single version: the patch is a delta applied to the current model server-side, so it is made atomic
    // (read-modify-conditional-write with internal retry) without any client-supplied version. Its request and response
    // are therefore unchanged from before optimistic locking existed.
    @PatchMapping(path = Constants.API_PATH, consumes = APPLICATION_JSON_PATCH_JSON, produces = { APP_HXCS_JSON_V1,
            APP_HXCS_JSON, APPLICATION_JSON_VALUE })
    @Operation(summary = "Patches the model for the repository.", operationId = "patchModel")
    @ApiResponse200Model(description = "Patches and returns the model for the repository.")
    @ApiResponse204ModelValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = {
            @Content(schema = @Schema(type = "object"), examples = @ExampleObject(value = PATCH_REQUEST_EXAMPLE)) })
    @AdministratorAccess
    public ResponseEntity<ContentModelDTO> doPatch(
            @RequestParam(value = MODEL_VALIDATE_ONLY, required = false, defaultValue = "false") @ParameterValidateOnly boolean validateOnly,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody Object patchBody) {
        checkRepoExists(repoId);
        // applies and persists the patch atomically (read-modify-conditional-write with retry); transparent to callers
        modelManager.applyPatch(repoId, mapper.writeValueAsString(patchBody), validateOnly);
        return validateOnly ? ResponseEntity.noContent().build() : doGet(repoId);
    }

    // ------------------------------------------------------------------------
    // PUT for project
    // ------------------------------------------------------------------------

    @PutMapping(path = Constants.API_PATH + "/project/{projectId}", consumes = APPLICATION_JSON_VALUE, produces = {
            APP_HXCS_JSON_V1, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
    @Operation(summary = "Sets the model items specific to projects in the existing content model for the repository.", operationId = "setModelItemsForProject")
    @ApiResponse200(description = "Sets the model items specific to projects in the existing content model and returns the model for the repository.", content = {
        @Content(schema = @Schema(implementation = ContentModelDTO.class),
            examples = { @ExampleObject(value = PUT_FOR_PROJECT_RESPONSE_EXAMPLE) }) })
    @ApiResponse204ModelValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse409(description = "Conflict as content model item proposed for update already exists.")
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = { @Content(examples = { @ExampleObject(value = PUT_FOR_PROJECT_REQUEST_EXAMPLE) }) })
    @AdministratorAccess
    public ResponseEntity<ContentModelDTO> doPutForProject(@PathVariable("projectId") String projectId,
            @RequestParam(value = MODEL_VALIDATE_ONLY, required = false, defaultValue = "false") @ParameterValidateOnly boolean validateOnly,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody ContentModelDTO modelDTO) {
        checkRepoExists(repoId);
        // this endpoint reports a model it refuses as 409, see doPut
        return conflictOnValidationFailure(() -> {
            modelValidator.validate(Framework.getService(TypeService.class).getSchemaManager(repoId), modelDTO,
                    projectId);
            ContentModelDTO current = modelManager.getContentModel(repoId);
            ContentModelDTO consolidated = modelManager.createConsolidatedContentModel(current, modelDTO, projectId);
            SchemaManager schemaManager = generateSchemaManagerBasedOnDto(repoId, consolidated, validateOnly);
            if (!validateOnly) {
                modelManager.persistSchemaManager(repoId, schemaManager);
            }
            return validateOnly ? ResponseEntity.noContent().build() : doGet(repoId);
        });
    }

    @PutMapping(path = Constants.API_PATH_V2 + "/project/{projectId}", consumes = APPLICATION_JSON_VALUE, produces = {
            APP_HXCS_JSON_V2, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
    @Operation(summary = "Sets the model items specific to projects in the existing content model for the repository, using optimistic locking.", operationId = "setModelItemsForProjectV2", tags = {
            TAG_V2 })
    @ApiResponse200(description = "Sets the model items specific to projects in the existing content model and returns the model and its new version for the repository.", content = {
            @Content(schema = @Schema(implementation = ContentModelDTO.class),
                examples = { @ExampleObject(value = PUT_FOR_PROJECT_RESPONSE_EXAMPLE_V2) }) })
    @ApiResponse204ModelValid
    @ApiResponse404RepositoryNotFound
    @ApiResponse409OptimisticLock
    @ApiResponse400MissingVersion
    @ApiResponse422ValidationError
    @SwaggerRequestBody(content = { @Content(examples = { @ExampleObject(value = PUT_FOR_PROJECT_REQUEST_EXAMPLE_V2) }) })
    @AdministratorAccess
    public ResponseEntity<ContentModelDTO> doPutForProjectV2(@PathVariable("projectId") String projectId,
            @RequestParam(value = MODEL_VALIDATE_ONLY, required = false, defaultValue = "false") @ParameterValidateOnly boolean validateOnly,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody ContentModelDTO dto) {
        checkRepoExists(repoId);
        // a v2 payload carries a version, whether or not this request goes on to write, see doPutV2
        long expectedVersion = requireVersion(dto.version());
        // the version is not part of the content: drop it so it is neither validated nor persisted as a model item
        ContentModelDTO contributed = dto.withoutVersion();
        // Everything is derived from the snapshot this write is conditioned on: the project-ownership checks, the
        // consolidation and the diff all describe the same persisted state, which the node-local model may lag behind
        // while a cluster invalidation is in flight.
        ContentModelDTO currentModel = modelManager.getModelWithVersion(repoId).model();
        modelValidator.validate(modelManager.asSchemaManager(currentModel), contributed, projectId);
        ContentModelDTO consolidated = modelManager.createConsolidatedContentModel(currentModel, contributed,
                projectId);
        SchemaManager schemaManager = generateSchemaManagerBasedOnSnapshot(currentModel, consolidated, validateOnly);
        if (validateOnly) {
            return ResponseEntity.noContent().build();
        }
        modelManager.persistSchemaManager(repoId, schemaManager, expectedVersion);
        return doGetV2(repoId);
    }

    // ------------------------------------------------------------------------
    // GET for project
    // ------------------------------------------------------------------------

    @GetMapping(path = Constants.API_PATH + "/project/{projectId}", produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON,
            APPLICATION_JSON_VALUE })
    @Operation(summary = "Returns the model specific to a project for the repository.", operationId = "getModelItemsForProject")
    @ApiResponse200(description = "The model items specific to a project in the existing content model and returns the model for the repository.", content = {
            @Content(schema = @Schema(implementation = ContentModelDTO.class), examples = {
                    @ExampleObject(value = PUT_FOR_PROJECT_RESPONSE_EXAMPLE) }) })
    @ApiResponse404RepositoryNotFound
    @AdministratorAccess
    public ResponseEntity<ContentModelDTO> doGetForProject(@PathVariable("projectId") String projectId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        checkRepoExists(repoId);
        if (StringUtils.isBlank(projectId)) {
            return doGet(repoId);
        }
        return ResponseEntity.ok(modelManager.getContentModel(repoId, projectId));
    }

    @GetMapping(path = Constants.API_PATH_V2 + "/project/{projectId}", produces = { APP_HXCS_JSON_V2, APP_HXCS_JSON,
            APPLICATION_JSON_VALUE })
    @Operation(summary = "Returns the model specific to a project for the repository.", operationId = "getModelItemsForProjectV2", tags = {
            TAG_V2 })
    @ApiResponse200(description = "The model items specific to a project in the existing content model and its version.", content = {
            @Content(schema = @Schema(implementation = ContentModelDTO.class), examples = {
                    @ExampleObject(value = PUT_FOR_PROJECT_RESPONSE_EXAMPLE_V2) }) })
    @ApiResponse404RepositoryNotFound
    @AdministratorAccess
    public ResponseEntity<ContentModelDTO> doGetForProjectV2(@PathVariable("projectId") String projectId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        checkRepoExists(repoId);
        if (StringUtils.isBlank(projectId)) {
            return doGetV2(repoId);
        }
        ModelWithVersion current = modelManager.getModelWithVersion(repoId);
        return ResponseEntity.ok(current.model().filterByProject(projectId).withVersion(current.version()));
    }

    // ------------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------------

    private void checkRepoExists(String repoId) {
        RepositoryManager repositoryManager = Framework.getService(RepositoryManager.class);
        if (repositoryManager.getRepository(repoId) == null) {
            throw new NuxeoException(NOT_FOUND.value());
        }
    }

    /** Builds the schema manager for a requested model, diffing it against the node-local model. */
    @NonNull
    private SchemaManager generateSchemaManagerBasedOnDto(String repoId, ContentModelDTO dto, boolean validateOnly) {
        ContentModelDTO base = modelManager.getContentModel(repoId);
        return generateSchemaManagerBasedOnSnapshot(base, dto, validateOnly);
    }

    /**
     * Builds the schema manager for a requested model, diffing it against the given persisted model snapshot. Used by
     * the optimistic-locking writes, which validate against the very snapshot their write is conditioned on.
     */
    @NonNull
    private SchemaManager generateSchemaManagerBasedOnSnapshot(ContentModelDTO base, ContentModelDTO dto,
            boolean validateOnly) {
        try {
            return modelManager.createSchemaManager(base, dto, validateOnly);
        } catch (NullPointerException | ClassCastException | RuntimeServiceException e) {
            log.debug("Invalid model", e);
            throw new NuxeoException(BAD_REQUEST.value());
        }
    }

}
