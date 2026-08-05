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

import static org.nuxeo.ecm.core.query.nxql.NXQLQueryBuilder.DEFAULT_SELECT_STATEMENT;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.nuxeo.ecm.core.query.nxql.SortInfo;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a query specification.
 *
 * @param limit: the maximum number of documents to retrieve, or 0 for all of them.
 * @param offset: the offset (starting at 0) into the list of documents.
 */
@Schema(name = "Query", title = "Query", description = "Represents a query.")
public record DocumentQueryDTO( //
        @Schema(name = "repositoryId", description = "The id of the internal repository.", defaultValue = "default") //
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
        @ArraySchema(arraySchema = @Schema(name = "sort", description = "List of fields used to sort the result.", example = "[\"sys_title\"]")) //
        List<String> sort) {

    public static final String QUERY_STRING_PROP = "q";

    public static final String LIMIT_PROP = "limit";

    public static final String OFFSET_PROP = "offset";

    public static final String SEARCH_AFTER_PROP = "searchAfter";

    public static final String SORT_PROP = "sort";

    public static final Long DEFAULT_LIMIT_VALUE = 10L;

    public DocumentQueryDTO {
        if (StringUtils.isBlank(query)) {
            query = DEFAULT_SELECT_STATEMENT;
        }
        if (sort == null) {
            sort = List.of();
        }
        if (limit == null) {
            limit = DEFAULT_LIMIT_VALUE;
        }
    }

    public DocumentQueryDTO(String repositoryId) {
        this(repositoryId, null, null, 0, null, true, null, null);
    }

    public DocumentQueryDTO(String repositoryId, String query, Long limit, long offset, List<String> sort) {
        this(repositoryId, query, limit, offset, null, true, null, sort);
    }

    public DocumentQueryDTO(String repositoryId, String query, Long limit, long offset, Boolean trackTotalCount,
            Integer trackTotalCountUpTo, List<String> sort) {
        this(repositoryId, query, limit, offset, null, trackTotalCount, trackTotalCountUpTo, sort);
    }

    public static List<SortInfo> getSortInfos(List<String> sort) {
        if (sort == null) {
            return List.of();
        }
        return sort.stream().flatMap(sortString -> {
            if (StringUtils.isBlank(sortString)) {
                return Stream.empty();
            }
            // split by comma to support multiple sort fields in one string
            return Arrays.stream(sortString.split(","));
        }).map(sortInfo -> {
            sortInfo = sortInfo.trim();
            if (StringUtils.isBlank(sortInfo)) {
                return null;
            }
            String sortColumn = sortInfo;
            boolean sortAscending = true;
            if (Strings.CI.endsWith(sortInfo, " asc")) {
                sortColumn = sortColumn.substring(0, sortColumn.length() - 4).trim();
            } else if (Strings.CI.endsWith(sortInfo, " desc")) {
                sortAscending = false;
                sortColumn = sortColumn.substring(0, sortColumn.length() - 5).trim();
            }
            return new SortInfo(sortColumn, sortAscending);
        }).filter(Objects::nonNull).toList();
    }

}
