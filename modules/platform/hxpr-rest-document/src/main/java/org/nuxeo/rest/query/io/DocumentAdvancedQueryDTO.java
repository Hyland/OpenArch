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
package org.nuxeo.rest.query.io;

import static org.nuxeo.rest.query.spec.QueryAssembler.DEFAULT_SELECT_STATEMENT;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.DEFAULT_LIMIT_VALUE;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.rest.query.spec.NamedQueryDefinition;

/**
 * Represents an advanced query specification.
 * <p>
 * Extra supported features compared to {@link DocumentQueryDTO}:
 * <ul>
 * <li>parameters support.
 * <li>where clause definition with predicates (that can reference parameters and specify a default value).
 * <li>quick filter clauses support (to be consistent with quick filter definitions defined in a
 * {@link NamedQueryDefinition}).
 *
 * @see DocumentQueryDTO
 * @see DocumentNamedQueryDTO
 */
@Schema(name = "AdvancedQuery", title = "Advanced Query", description = "Represents an advanced query.")
public record DocumentAdvancedQueryDTO( //
        @Schema(name = "repositoryId", description = "The id of the internal repository", defaultValue = "default") //
        String repositoryId, //
        @Schema(name = "query", description = "The HXQL query.", example = "SELECT * FROM SysContent") //
        String query, //
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
        @ArraySchema(arraySchema = @Schema(description = "List of fields used to sort the result.", example = "[\"sys_title\"]")) //
        List<String> sort, //
        Map<String, Object> parameters, //
        WhereClauseDTO whereClauseDefinition, //
        @ArraySchema(arraySchema = @Schema(description = "List of filters to be applied to the result.", example = "exclude documents that are not article: mydc_nature != 'article'")) //
        List<String> quickFilterClauses) {

    public static final String QUERY_STRING_PROP = "q";

    public static final String LIMIT_PROP = "limit";

    public static final String OFFSET_PROP = "offset";

    public static final String SEARCH_AFTER_PROP = "searchAfter";

    public DocumentAdvancedQueryDTO {
        if (StringUtils.isBlank(query)
                && (whereClauseDefinition == null || StringUtils.isBlank(whereClauseDefinition.query()))) {
            query = DEFAULT_SELECT_STATEMENT;
        }
        if (sort == null) {
            sort = List.of();
        }
        if (parameters == null) {
            parameters = Map.of();
        }
        if (quickFilterClauses == null) {
            quickFilterClauses = List.of();
        }
        if (limit == null) {
            limit = DEFAULT_LIMIT_VALUE;
        }
    }

    public DocumentAdvancedQueryDTO(String repositoryId) {
        this(repositoryId, DEFAULT_SELECT_STATEMENT, null, 0, null, true, null, null, null, null, null);
    }

    public DocumentAdvancedQueryDTO(DocumentQueryDTO query) {
        this(query.repositoryId(), query.query(), query.limit(), query.offset(), query.searchAfter(),
                query.trackTotalCount(), query.trackTotalCountUpTo(), query.sort(), null, null, null);
    }

    public DocumentAdvancedQueryDTO(String repositoryId, String query, Long limit, long offset, Boolean trackTotalCount,
            Integer trackTotalCountUpTo, List<String> sort) {
        this(repositoryId, query, limit, offset, null, trackTotalCount, trackTotalCountUpTo, sort, null, null, null);
    }

    public DocumentAdvancedQueryDTO(String repositoryId, String query, Long limit, long offset, boolean trackTotalCount, int trackTotalCountUpTo, List<String> sort, Map<String, Object> parameters, WhereClauseDTO whereClauseDefinition,
            List<String> quickFilterClauses) {
        this(repositoryId, query, limit, offset, null, trackTotalCount, trackTotalCountUpTo, sort, parameters, whereClauseDefinition,
                quickFilterClauses);
    }

    public DocumentAdvancedQueryDTO(DocumentNamedQueryDTO query, NamedQueryDefinition definition) {
        this(query.repositoryId(), null, query.limit(), query.offset(), query.searchAfter(), query.trackTotalCount(),
                query.trackTotalCountUpTo(),
                buildSort(query, definition),
                query.parameters(),
                definition.whereClauseDefinition(),
                buildQuickFilterClauses(query, definition));
    }

    protected static List<String> buildSort(DocumentNamedQueryDTO query, NamedQueryDefinition definition) {
        // explicit sort infos?
        if (!query.sort().isEmpty()) {
            return query.sort();
        }
        // quick filters sort infos?
        if (!query.selectedQuickFilters().isEmpty()) {
            List<String> qfSortInfos = query.selectedQuickFilters()
                                            .stream()
                                            .map(definition::getQuickFilter)
                                            .filter(Objects::nonNull)
                                            .map(QuickFilterDTO::sort)
                                            .flatMap(List::stream)
                                            .filter(Objects::nonNull)
                                            .toList();
            if (!qfSortInfos.isEmpty()) {
                return qfSortInfos;
            }
        }
        // definition sort infos
        return definition.sort();
    }

    protected static List<String> buildQuickFilterClauses(DocumentNamedQueryDTO query,
            NamedQueryDefinition definition) {
        return query.selectedQuickFilters()
                    .stream()
                    .map(definition::getQuickFilter)
                    .filter(Objects::nonNull)
                    .map(QuickFilterDTO::clause)
                    .filter(Objects::nonNull)
                    .toList();
    }

}
