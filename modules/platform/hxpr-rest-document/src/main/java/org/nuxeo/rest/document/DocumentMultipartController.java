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
package org.nuxeo.rest.document;

import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.QueryParameters.ENFORCE_SYS_NAME;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;

import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PathRefUtils;
import org.nuxeo.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterDocId;
import org.nuxeo.rest.swagger.annotation.ParameterDocPath;
import org.nuxeo.rest.swagger.annotation.ParameterEnforceSysName;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.nuxeo.runtime.springboot.transaction.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(path = AbstractDocumentAPI.PATH, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON,
        APPLICATION_JSON_VALUE }, consumes = MULTIPART_FORM_DATA_VALUE)
@Tag(name = "Document", description = "Repository CRUD API")
@Authenticated
public class DocumentMultipartController extends AbstractDocumentAPI {

    public static final String DOCUMENT_REQUEST_PART = "main";

    public DocumentMultipartController(DocumentManager documentManager) {
        super(documentManager);
    }

    @PostMapping("/{docId}")
    @Operation(summary = "Creates a document under parent with given id.", operationId = "createDocumentUnderParentById")
    @RequestBodyMultiPart
    @ApiResponse200DocumentPost
    @ApiResponse404ParentDocumentNotFound
    @Transactional
    public DocumentDTO createDocumentByIdMultiPart(
            @PathVariable("docId") @Parameter(description = "The parent document id.") String parentId,
            @RequestParam(name = ENFORCE_SYS_NAME, defaultValue = "false") @ParameterEnforceSysName boolean enforceSysName,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestPart(name = DOCUMENT_REQUEST_PART) DocumentDTO doc, HttpServletRequest request)
            throws IOException, ServletException {
        return documentManager.uploadAndCreateDocument(repoId, new IdRef(parentId), doc, enforceSysName,
                request.getParts());
    }

    @PostMapping(DOC_PATH + "/{*docPath}")
    @Operation(summary = "Creates a document under parent with given path.", operationId = "createDocumentUnderParentByPath")
    @RequestBodyMultiPart
    @ApiResponse200DocumentPost
    @ApiResponse404ParentDocumentNotFound
    @Transactional
    public DocumentDTO createDocumentByPathMultiPart(
            @RequestParam(name = ENFORCE_SYS_NAME, defaultValue = "false") @ParameterEnforceSysName boolean enforceSysName,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @PathVariable() @ParameterDocPath String docPath, @RequestPart(DOCUMENT_REQUEST_PART) DocumentDTO doc,
            HttpServletRequest request) throws IOException, ServletException {
        return documentManager.uploadAndCreateDocument(repoId, PathRefUtils.getPathRef(docPath), doc, enforceSysName,
                request.getParts());
    }

    @PutMapping("/{docId}")
    @Operation(summary = "Updates the document with given id.", operationId = "updateDocumentById")
    @RequestBodyMultiPart
    @ApiResponse200Document
    @ApiResponse400SingleAcePerPrincipal
    @ApiResponse404DocumentNotFound
    @Transactional
    public DocumentDTO updateDocumentByIdMultiPart(@PathVariable("docId") @ParameterDocId String docId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestPart(DOCUMENT_REQUEST_PART) DocumentDTO doc, HttpServletRequest request)
            throws IOException, ServletException {
        return documentManager.uploadAndUpdateDocument(repoId, new IdRef(docId), doc, request.getParts());
    }

    @PutMapping(DOC_PATH + "/{*docPath}")
    @Operation(summary = "Updates the document with given path.", operationId = "updateDocumentByPath")
    @RequestBodyMultiPart
    @ApiResponse200Document
    @ApiResponse400SingleAcePerPrincipal
    @ApiResponse404DocumentNotFound
    @Transactional
    public DocumentDTO updateDocumentByPathMultiPart(
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @PathVariable() @ParameterDocPath String docPath, @RequestPart(DOCUMENT_REQUEST_PART) DocumentDTO doc,
            HttpServletRequest request) throws IOException, ServletException {
        return documentManager.uploadAndUpdateDocument(repoId, PathRefUtils.getPathRef(docPath),
                doc, request.getParts());
    }
}
