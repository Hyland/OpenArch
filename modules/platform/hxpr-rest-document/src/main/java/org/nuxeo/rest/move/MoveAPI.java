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
package org.nuxeo.rest.move;

import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.QueryParameters.CONDITION;
import static org.nuxeo.rest.utils.QueryParameters.IF;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import org.nuxeo.rest.document.AbstractDocumentAPI;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import org.nuxeo.rest.swagger.annotation.ApiResponse412;
import org.nuxeo.rest.swagger.annotation.ApiResponse422;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterCondition;
import org.nuxeo.rest.swagger.annotation.ParameterIf;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Document Move Rest API.
 */
@RestController
@RequestMapping(path = AbstractDocumentAPI.PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON,
        APP_HXCS_JSON_V1 }, consumes = APPLICATION_JSON_VALUE)
@Tag(name = "Move", description = "Repository Document Move API")
@Authenticated
public class MoveAPI extends AbstractDocumentAPI {

    public MoveAPI(DocumentManager documentManager) {
        super(documentManager);
    }

    @PostMapping("/{docId}/move")
    @Operation(summary = "Moves the source document to the provided destination", //
            description = "Move a given document to a given destination. Requires a request body with targetParentId.", //
            operationId = "move")
    @ApiResponse200(description = "Returns the document information.")
    @ApiResponse404DocumentNotFound
    @ApiResponse412(description = "Condition evaluated to false.")
    @ApiResponse422(description = "The document cannot be moved because it is a version.")
    public DocumentDTO move(
            @PathVariable("docId") @Parameter(description = "The document id to be moved.") String docId,
            @RequestParam(name = CONDITION, defaultValue = "") @ParameterCondition String hxql,
            @RequestParam(name = IF, defaultValue = "") @ParameterIf String expr,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody MoveDTO moveRequestBody) {
        String targetParentId = moveRequestBody.targetParentId();
        if (StringUtils.isBlank(targetParentId)) {
            throw new NuxeoException("Missing destination folder id to move document: targetParentId",
                    BAD_REQUEST.value());
        }
        return documentManager.moveDocument(repoId, new IdRef(docId), new IdRef(targetParentId),
                toUpdateCondition(hxql, expr));
    }
}
