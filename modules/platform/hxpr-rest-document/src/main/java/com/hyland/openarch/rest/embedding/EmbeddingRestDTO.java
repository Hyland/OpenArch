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
package com.hyland.openarch.rest.embedding;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_BLOB_REF;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_DOCUMENT_ID;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_LOCATION;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_SCORE;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TEXT;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TYPE;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_VECTOR;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import com.hyland.openarch.ecm.core.api.EmbeddingConstants;
import com.hyland.openarch.ecm.core.model.LocationModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Embedding representation.
 */
@Schema(name = "Embedding", title = "Embedding Body", description = "Represents the Embedding API's HTTP Post body.")
public record EmbeddingRestDTO(
        @Schema(description = "The unique identifier for the embedding.", requiredMode = Schema.RequiredMode.REQUIRED)//
        @JsonProperty(EmbeddingConstants.EMBEDDING_PROPERTY_ID) //
        String id,

        @Schema(description = "The unique identifier for the document the embedding is associated with.", //
                requiredMode = Schema.RequiredMode.REQUIRED) //
        @JsonProperty(EMBEDDING_PROPERTY_DOCUMENT_ID) //
        String docId,

        @Schema(description = "A label for the embedding type (used to ensure semantic queries are against vectors of the same type).", //
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 256) //
        @JsonProperty(EMBEDDING_PROPERTY_TYPE) //
        String type,

        @Schema(description = "The vector as a list of floating point numbers.", //
                requiredMode = Schema.RequiredMode.REQUIRED) //
        @JsonProperty(EMBEDDING_PROPERTY_VECTOR) //
        List<Double> vector,

        @Schema(description = "Text label for the vector or the original chunk for text embeddings.", //
                requiredMode = Schema.RequiredMode.REQUIRED) //
        @JsonProperty(EMBEDDING_PROPERTY_TEXT) //
        @JsonInclude(NON_NULL) //
        String text,

        @Schema(description = "Blob label for embeddings.", //
                requiredMode = Schema.RequiredMode.NOT_REQUIRED) //
        @JsonProperty(EMBEDDING_PROPERTY_BLOB_REF) //
        String blobref,

        @Schema(description = "Location the vector is derived from within the original document. For example this could be the page and paragraph number or coordinates of a bounding box.")
        //
        @JsonProperty(EMBEDDING_PROPERTY_LOCATION) //
        @JsonInclude(NON_NULL) //
        LocationModel location,

        @Schema(description = "A score representing the relevance of the hit") //
        @JsonProperty(EMBEDDING_PROPERTY_SCORE) //
        @JsonInclude(NON_NULL) //
        Double score) {

    public EmbeddingRestDTO(String id, String docId, EmbeddingRestDTO embeddingDTO) {
        this(id, docId, embeddingDTO.type(), embeddingDTO.vector(), embeddingDTO.text(), embeddingDTO.blobref(), embeddingDTO.location(), embeddingDTO.score());
    }
}
