/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.version;

import static com.hyland.openarch.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static com.hyland.openarch.rest.utils.Headers.HXCS_REPOSITORY;
import static com.hyland.openarch.rest.utils.QueryParameters.USER_CHANGE;
import static com.hyland.openarch.rest.utils.Versions.APP_HXCS_JSON;
import static com.hyland.openarch.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.rest.document.AbstractDocumentAPI;
import com.hyland.openarch.rest.document.DocumentDTO;
import com.hyland.openarch.rest.document.DocumentManager;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse200;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse412;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse422;
import com.hyland.openarch.rest.swagger.annotation.Authenticated;
import com.hyland.openarch.rest.swagger.annotation.ParameterRepoId;
import com.hyland.openarch.rest.swagger.annotation.ParameterUserChange;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = AbstractDocumentAPI.PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON,
        APP_HXCS_JSON_V1 })
@Tag(name = "Version", description = "Repository Document Version API")
@Authenticated
public class VersionAPI extends AbstractDocumentAPI {

    public VersionAPI(DocumentManager documentManager) {
        super(documentManager);
    }

    @PostMapping("/{versionId}/restore")
    @Operation(summary = "Restore a document from given version", operationId = "restoreVersion")
    @ApiResponse404DocumentNotFound
    @ApiResponse412(description = "The document is not versionable.")
    @ApiResponse422(description = "The document to be restored is not a version")
    @ApiResponse200(description = "Returns a current working copy")
    public DocumentDTO restoreVersion(
            @PathVariable("versionId") @Parameter(description = "The id of the version to be restored") String versionId,
            @RequestParam(name = USER_CHANGE, defaultValue = "true") @ParameterUserChange boolean userChange,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        return documentManager.restoreVersion(repoId, new IdRef(versionId), userChange);
    }
}
