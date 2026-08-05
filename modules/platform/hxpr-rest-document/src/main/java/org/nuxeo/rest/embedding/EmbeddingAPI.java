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
package org.nuxeo.rest.embedding;

import static org.nuxeo.rest.document.DocumentManager.BASE_DOCUMENT_PATH;
import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse401NotAuthorized;
import org.nuxeo.rest.swagger.annotation.ApiResponse403;
import org.nuxeo.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import org.nuxeo.rest.swagger.annotation.ParameterDocId;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Embeddings CRUD Rest API.
 */
@RestController
@RequestMapping(path = EmbeddingAPI.PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON, APP_HXCS_JSON_V1 })
@Tag(name = "Embeddings", description = "Embeddings API")
@ApiResponse401NotAuthorized
@ApiResponse403(description = "Access forbidden to document.")
public class EmbeddingAPI {
    public static final String PATH = BASE_DOCUMENT_PATH;

    private final EmbeddingManager embeddingManager;

    public EmbeddingAPI(EmbeddingManager embeddingManager) {
        this.embeddingManager = embeddingManager;
    }

    @GetMapping(value = "/{docId}/embedding/{embeddingId}")
    @Operation(summary = "Get an embedding by id.", operationId = "getEmbedding")
    @ApiResponse200(description = "Returns the embedding")
    @ApiResponse404DocumentNotFound
    public EmbeddingRestDTO getEmbedding(
            @PathVariable("docId") @Parameter(description = "The document id.") String docId,
            @PathVariable("embeddingId") @ParameterDocId String embeddingId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        return embeddingManager.getEmbedding(repoId, docId, embeddingId);
    }
}
