/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.copy;

import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.api.IdRef;
import org.nuxeo.rest.document.AbstractDocumentAPI;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Document Copy Rest API.
 */
@RestController
@RequestMapping(path = AbstractDocumentAPI.PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON,
        APP_HXCS_JSON_V1 }, consumes = APPLICATION_JSON_VALUE)
@Tag(name = "Copy", description = "Repository Document Copy API")
@Authenticated
public class CopyAPI extends AbstractDocumentAPI {

    public CopyAPI(DocumentManager documentManager) {
        super(documentManager);
    }

    @PostMapping("/{docId}/copy")
    @Operation(summary = "Copies the source document inside the destination folder", description = "Copy a given document id to a given destination folder.  Requires request body with target name and targetParentId", operationId = "copy")
    @ApiResponse200(description = "Returns the document information.")
    @ApiResponse404DocumentNotFound
    public DocumentDTO copy(
            @PathVariable("docId") @Parameter(description = "The source document id to be copied.") String docId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody CopyDTO copyRequestBody) {
        String name = copyRequestBody.name();
        String targetParentId = copyRequestBody.targetParentId();

        if (StringUtils.isBlank(name)) {
            throw new RepositoryException("Missing document name to copy document: name",
                    BAD_REQUEST.value());
        }
        if (StringUtils.isBlank(targetParentId)) {
            throw new RepositoryException("Missing destination folder id to copy document: targetParentId",
                    BAD_REQUEST.value());
        }

        return documentManager.copyDocument(repoId, new IdRef(docId), new IdRef(targetParentId), name);
    }
}
