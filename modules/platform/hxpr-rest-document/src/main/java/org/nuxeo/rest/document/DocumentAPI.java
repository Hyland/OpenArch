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
package org.nuxeo.rest.document;

// We keep jakarta MediaType.APPLICATION_JSON_PATCH import here as there in no spring equivalent
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON;
import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.QueryParameters.CONDITION;
import static org.nuxeo.rest.utils.QueryParameters.ENFORCE_SYS_NAME;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.links.LinkParameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PathRefUtils;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import org.nuxeo.rest.swagger.annotation.ApiResponse412;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterCondition;
import org.nuxeo.rest.swagger.annotation.ParameterDocId;
import org.nuxeo.rest.swagger.annotation.ParameterDocPath;
import org.nuxeo.rest.swagger.annotation.ParameterEnforceSysName;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Document CRUD Rest API.
 */
@RestController
@RequestMapping(path = AbstractDocumentAPI.PATH, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
@Tag(name = "Document", description = "Repository CRUD API")
@Authenticated
@Validated
@CrossOrigin
public class DocumentAPI extends AbstractDocumentAPI {

    ObjectMapper mapper;

    public DocumentAPI(DocumentManager documentManager, ObjectMapper mapper) {
        super(documentManager);
        this.mapper = mapper;
    }

    @GetMapping(path = "/{docId}", consumes = ALL_VALUE)
    @Operation(summary = "Returns the document with given id.", operationId = "getDocumentById", responses = {
            // linking does not appear to work inside @ApiResponses so using responses = ...
            @ApiResponse(responseCode = "200", description = "Returns the document with given id in given repository.", links = {
                    @Link(name = "parent", operationId = "get", parameters = {
                            @LinkParameter(name = "docId", expression = "$response.body#/parentId") }) }) })
    @ApiResponse404DocumentNotFound
    public DocumentDTO getDocumentById(@PathVariable("docId") @ParameterDocId String docId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        return documentManager.getDocument(repoId, new IdRef(docId), true, false); // with audit event
    }

    @GetMapping(path = DOC_PATH + "/{*docPath}", consumes = ALL_VALUE)
    @Operation(summary = "Returns the document with given path.", operationId = "getDocumentByPath")
    @ApiResponse200(description = "Returns the document with given path in given repository.")
    @ApiResponse404DocumentNotFound
    public DocumentDTO getDocumentByPath(
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @PathVariable() @ParameterDocPath String docPath) {
        return documentManager.getDocument(repoId, PathRefUtils.getPathRef(docPath), true, false); // with audit event
    }

    @GetMapping(path = "/{docId}/ancestors", consumes = ALL_VALUE)
    @Operation(summary = "Returns the document's ancestors.", operationId = "getDocumentAncestors")
    @ApiResponse200Document(description = "Returns an object containing a list of the document's ancestors.")
    @ApiResponse404DocumentNotFound
    public DocumentAncestorsDTO getDocumentAncestors(@PathVariable("docId") @ParameterDocId String docId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        return documentManager.getDocumentAncestors(repoId, new IdRef(docId));
    }

    @PostMapping(path = "/{docId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Creates a document under parent with given id.", operationId = "createDocumentUnderParentById")
    @ApiResponse200DocumentPost
    @ApiResponse404ParentDocumentNotFound
    public DocumentDTO createDocumentById(
            @PathVariable("docId") @Parameter(description = "The parent document id.") String parentId,
            @RequestParam(name = ENFORCE_SYS_NAME, defaultValue = "false") @ParameterEnforceSysName boolean enforceSysName,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody(required = false) DocumentDTO doc) {
        return documentManager.createDocument(repoId, new IdRef(parentId), doc, enforceSysName);
    }

    @PostMapping(path = DOC_PATH + "/{*docPath}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Creates a document under parent with given path.", operationId = "createDocumentUnderParentByPath")
    @ApiResponse200DocumentPost
    @ApiResponse404ParentDocumentNotFound
    public DocumentDTO createDocumentByPath(
            @RequestParam(name = ENFORCE_SYS_NAME, defaultValue = "false") @ParameterEnforceSysName boolean enforceSysName,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @PathVariable() @ParameterDocPath String docPath,
            @RequestBody(required = false) DocumentDTO doc) {
        return documentManager.createDocument(repoId, PathRefUtils.getPathRef(docPath), doc, enforceSysName);
    }

    @PutMapping(path = "/{docId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Updates the document with given id.", operationId = "updateDocumentById")
    @ApiResponse200Document
    @ApiResponse400SingleAcePerPrincipal
    @ApiResponse404DocumentNotFound
    @ApiResponse412(description = "Condition evaluated to false.")
    public DocumentDTO updateDocumentById(@PathVariable("docId") @ParameterDocId String docId,
            @RequestParam(name = CONDITION, defaultValue = "") @ParameterCondition String condition,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody(required = false) DocumentDTO doc) {
        return documentManager.updateDocument(repoId, new IdRef(docId), doc, condition);
    }

    @PutMapping(path = DOC_PATH + "/{*docPath}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Updates the document with given path.", operationId = "updateDocumentByPath")
    @ApiResponse200Document
    @ApiResponse400SingleAcePerPrincipal
    @ApiResponse404DocumentNotFound
    @ApiResponse412(description = "Condition evaluated to false.")
    public DocumentDTO updateDocumentByPath(
            @RequestParam(name = CONDITION, defaultValue = "") @ParameterCondition String condition,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @PathVariable() @ParameterDocPath String docPath,
            @RequestBody(required = false) DocumentDTO doc) {
        return documentManager.updateDocument(repoId, PathRefUtils.getPathRef(docPath), doc, condition);
    }

    @PatchMapping(path = "/{docId}", consumes = APPLICATION_JSON_PATCH_JSON)
    @Operation(summary = "Patches the document with given id.", operationId = "patchDocumentById")
    @ApiResponse200Document
    @ApiResponse400SingleAcePerPrincipal
    @ApiResponse400PatchMissingField
    @ApiResponse404DocumentNotFound
    @ApiResponse412(description = "Condition evaluated to false.")
    public DocumentDTO patchDocumentById(@PathVariable("docId") @ParameterDocId String docId,
            @RequestParam(name = CONDITION, defaultValue = "") @ParameterCondition String condition,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody(required = false) Object patch) throws JacksonException {
        return documentManager.patchDocument(repoId, new IdRef(docId), mapper.writeValueAsString(patch), condition);
    }

    @PatchMapping(path = DOC_PATH + "/{*docPath}", consumes = APPLICATION_JSON_PATCH_JSON)
    @Operation(summary = "Patches the document with given path.", operationId = "patchDocumentByPath")
    @ApiResponse200Document
    @ApiResponse400SingleAcePerPrincipal
    @ApiResponse400PatchMissingField
    @ApiResponse404DocumentNotFound
    @ApiResponse412(description = "Condition evaluated to false.")
    public DocumentDTO patchDocumentByPath(
            @RequestParam(name = CONDITION, defaultValue = "") @ParameterCondition String condition,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @PathVariable() @ParameterDocPath String docPath,
            @RequestBody(required = false) Object patch) throws JacksonException {
        return documentManager.patchDocument(repoId, PathRefUtils.getPathRef(docPath),
                mapper.writeValueAsString(patch), condition);
    }

    @DeleteMapping(path = "/{docId}", consumes = ALL_VALUE)
    @Operation(summary = "Deletes the document with given id.", operationId = "deleteDocumentById")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse204DocumentDeleted
    @ApiResponse404DocumentNotFound
    public void deleteDocumentById(@PathVariable("docId") @ParameterDocId String docId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        documentManager.deleteDocument(repoId, new IdRef(docId));
    }

    @DeleteMapping(path = DOC_PATH + "/{*docPath}", consumes = ALL_VALUE)
    @Operation(summary = "Deletes the document with given path.", operationId = "deleteDocumentByPath")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse204DocumentDeleted
    @ApiResponse404DocumentNotFound
    public void deleteDocumentByPath(
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @PathVariable() @ParameterDocPath String docPath) {
        documentManager.deleteDocument(repoId, PathRefUtils.getPathRef(docPath));
    }
}
