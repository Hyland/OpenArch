/*
 * (C) Copyright 2025 Hyland (https://www.hyland.com/) and others.
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
 *     Abdul Mohammed
 */
package org.nuxeo.rest.checkin;

import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.rest.document.AbstractDocumentAPI;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import org.nuxeo.rest.swagger.annotation.ApiResponse409;
import org.nuxeo.rest.swagger.annotation.ApiResponse412;
import org.nuxeo.rest.swagger.annotation.ApiResponse422;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterCheckInMinor;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.nuxeo.runtime.springboot.transaction.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = AbstractDocumentAPI.PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON,
        APP_HXCS_JSON_V1 })
@Tag(name = "CheckIn", description = "Repository Document Check In API")
@Authenticated
public class CheckInAPI extends AbstractDocumentAPI {

    public CheckInAPI(DocumentManager documentManager) {
        super(documentManager);
    }

    @PostMapping("/{docId}/checkin")
    @Operation(summary = "Checks in the document, creating a version.", operationId = "checkin")
    @ApiResponse404DocumentNotFound
    @ApiResponse409(description = "The document is already checked in.")
    @ApiResponse412(description = "The document is not versionable.")
    @ApiResponse422(description = "A version cannot be checked in.")
    @ApiResponse200(description = "Returns the created document version.")
    @Transactional
    public DocumentDTO checkIn(
            @PathVariable("docId") @Parameter(description = "The id of the document to be checked in.") String docId,
            @RequestParam(value = "minor", required = false, defaultValue = "false") @ParameterCheckInMinor boolean minor,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        return documentManager.checkinDocument(repoId, new IdRef(docId), minor);
    }
}
