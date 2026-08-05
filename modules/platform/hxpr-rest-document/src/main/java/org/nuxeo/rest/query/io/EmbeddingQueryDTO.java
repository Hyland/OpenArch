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

import static org.nuxeo.ecm.core.query.nxql.NXQLQueryBuilder.DEFAULT_SELECT_STATEMENT;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.DEFAULT_LIMIT_VALUE;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Represents a vector search query.
 */
@Schema(name = "VectorQuery", title = "Vector Query", description = "Represents a vector search query.")
public record EmbeddingQueryDTO( //
                                 @Schema(name = "repositoryId", description = "The id of the internal repository.", defaultValue = "default") //
                                 String repositoryId,
                                 @Schema(name = "embeddingType", description = "A wildcard query for the embedding type.", defaultValue = "*") //
                                 String embeddingType,
                                 @Schema(name = "vector", description = "The vector to search for.") //
                                 List<Double> vector,
                                 @Schema(name = "chunkFTS", description = "An optional space-separated list of terms to search for in the chunk.") //
                                 String chunkFTS,
                                 @Schema(name = "query", description = "The HXQL query used to filter the embeddings by the corresponding document.", example = DEFAULT_SELECT_STATEMENT, defaultValue = DEFAULT_SELECT_STATEMENT) //
                                 String query,
                                 @Schema(name = "limit", description = "Max number of embeddings retrieved by the given query.", example = "100", defaultValue = "10") //
                                 Long limit,
                                 @Schema(name = "offset", description = "The initial embedding offset; for pagination support.", example = "0", defaultValue = "0") //
                                 long offset,
                                 @Schema(name = "trackTotalCount", description = "If true, the total count of embeddings retrieved by the query will be computed.", defaultValue = "false") //
                                 boolean trackTotalCount,
                                 @Schema(name = "trackTotalCountUpTo", description = "Max number of embeddings to count. This can be set to -1 to remove the limit but it will be expensive on large indexes.", example = "100") //
                                 Integer trackTotalCountUpTo) {
    public EmbeddingQueryDTO {
        if (StringUtils.isBlank(query)) {
            query = DEFAULT_SELECT_STATEMENT;
        }
        if (limit == null) {
            limit = DEFAULT_LIMIT_VALUE;
        }
    }

    public EmbeddingQueryDTO(String repositoryId, String embeddingType, List<Double> vector, String chunkFTS, String query, long limit, long offset) {
        this(repositoryId, embeddingType, vector, chunkFTS, query, limit, offset, true, null);
    }
}
