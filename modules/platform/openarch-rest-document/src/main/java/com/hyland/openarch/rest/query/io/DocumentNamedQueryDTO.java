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
package com.hyland.openarch.rest.query.io;

import static com.hyland.openarch.rest.query.io.DocumentQueryDTO.DEFAULT_LIMIT_VALUE;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents an advanced query specification based on a named query.
 * <p>
 * Extra features compared to {@link DocumentQueryDTO}: see {@link DocumentAdvancedQueryDTO}.
 * <p>
 * Extra features compared to {@link DocumentAdvancedQueryDTO}: quick filter selection support.
 *
 * @see DocumentQueryDTO
 * @see DocumentAdvancedQueryDTO
 */
@Schema(name = "NamedQuery", title = "Named Query", description = "Represents a query referencing a pre-defined named query definition by name.")
public record DocumentNamedQueryDTO( //
        @Schema(name = "queryName", description = "Name of the query.") //
        String queryName, //
        @Schema(name = "repositoryId", description = "The id of the internal repository.", defaultValue = "default") //
        String repositoryId, //
        @Schema(name = "limit", description = "Max number of documents retrieved by the given query.", example = "100", defaultValue = "10") //
        Long limit, //
        @Schema(name = "offset", description = "The initial document offset; for pagination support.", example = "0", defaultValue = "0") //
        long offset, //
        @Schema(name = "searchAfter", description = "Opaque cursor token for efficient pagination. Use the nextSearchAfter value from a previous response. When provided, offset is ignored.") //
        String searchAfter, //
        @Schema(name = "trackTotalCount", description = "If true, the total count of documents retrieved by the query will be computed.") //
        Boolean trackTotalCount, //
        @Schema(name = "trackTotalCountUpTo", description = "Max number of documents to count. This can be set to -1 to remove the limit but it will be expensive on large indexes.", example = "100") //
        Integer trackTotalCountUpTo, //
        @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE) //
        Map<String, Object> parameters, //
        @ArraySchema(arraySchema = @Schema(description = "List of fields used to sort the result.", example = "[\"sys_title\"]")) //
        List<String> sort, //
        @ArraySchema(arraySchema = @Schema(description = "List of filters to be applied to the result.", example = "exclude documents that are not article: mydc_nature != 'article'")) //
        List<String> selectedQuickFilters) {

    public DocumentNamedQueryDTO {
        if (parameters == null) {
            parameters = Map.of();
        }
        if (sort == null) {
            sort = List.of();
        }
        if (selectedQuickFilters == null) {
            selectedQuickFilters = List.of();
        }
        if (limit == null) {
            limit = DEFAULT_LIMIT_VALUE;
        }
    }

    public DocumentNamedQueryDTO(String queryName, String repositoryId, Long limit, long offset,
            Boolean trackTotalCount, Integer trackTotalCountUpTo, Map<String, Object> parameters,
            List<String> sort, List<String> selectedQuickFilters) {
        this(queryName, repositoryId, limit, offset, null, trackTotalCount, trackTotalCountUpTo, parameters, sort,
                selectedQuickFilters);
    }

}
