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
package org.nuxeo.rest.query.io;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import org.nuxeo.rest.embedding.EmbeddingRestDTO;
import org.nuxeo.rest.query.spec.QuerySpecification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Results of a vector search.
 */
@Schema(name = "VectorSearchResult", title = "Vector Search Results", description = "The results of a vector search query.")
public record EmbeddingQueryResultDTO( //
                                       @Schema(name = "repositoryId", description = "The id of the internal repository.", defaultValue = "default") //
                                       String repositoryId,
                                       @Schema(name = "vector", description = "The supplied vector") //
                                       List<Double> vector,
                                       @Schema(name = "query", description = "The HXQL query.", example = "SELECT * FROM SysContent") //
                                       String query,
                                       @Schema(name = "limit", description = "Max number of embeddings retrieved by the given query.", example = "100", defaultValue = "0") //
                                       long limit,
                                       @Schema(name = "offset", description = "Actual embedding offset; for pagination support.", example = "0", defaultValue = "0") //
                                       long offset,
                                       @Schema(name = "trackTotalCount", description = "If true the total count of the query was computed.")
                                       //
                                       @JsonInclude(Include.NON_NULL) //
                                       Boolean trackTotalCount,
                                       @Schema(name = "totalCountIsTruncated", description = "If true the total count of the query was truncated.")
                                       //
                                       @JsonInclude(Include.NON_NULL) //
                                       Boolean totalCountIsTruncated,
                                       @Schema(name = "embeddings", description = "The list of retrieved embeddings.") //
                                       List<EmbeddingRestDTO> embeddings,
                                       @Schema(name = "totalCount", description = "The total number of embeddings that match the query: is approximated if trackTotalCount is set to false.")
                                       //
                                       @JsonInclude(Include.NON_NULL) //
                                       Long totalCount) {

    public EmbeddingQueryResultDTO {
        if (embeddings == null) {
            embeddings = List.of();
        }
        if (isFalse(trackTotalCount)) {
            totalCount = null;
        }
        if (isTrue(trackTotalCount)) {
            // when we track the total count the presence of totalCount is enough, we don't need this extra boolean
            trackTotalCount = null;
        }
        if (isFalse(totalCountIsTruncated)) {
            totalCountIsTruncated = null;
        }
    }

    public EmbeddingQueryResultDTO(QuerySpecification spec, List<Double> vector, List<EmbeddingRestDTO> embeddings, Long totalCount) {
        this(spec, vector, embeddings, totalCount, false);
    }

    public EmbeddingQueryResultDTO(QuerySpecification spec, List<Double> vector, List<EmbeddingRestDTO> embeddings, Long totalCount,
                                   Boolean totalCountTruncated) {
        this(spec.repositoryId(), vector, spec.boundQuery(), spec.limit(), spec.offset(), spec.trackTotalCount(),
                totalCountTruncated, embeddings, totalCount);
    }

    @JsonProperty("count")
    public long count() {
        return embeddings().size();
    }

}
