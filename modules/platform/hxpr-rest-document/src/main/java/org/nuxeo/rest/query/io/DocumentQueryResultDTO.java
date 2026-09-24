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

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import java.util.List;

import com.hyland.openarch.ecm.core.query.QuerySyntax;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.query.spec.QuerySpecification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a query result.
 *
 * @see DocumentQueryDTO
 * @see DocumentAdvancedQueryDTO
 * @see DocumentNamedQueryDTO
 */
@Schema(name = "QueryResult", title = "Query Result", description = "Represents a query result.")
public record DocumentQueryResultDTO( //
        @Schema(name = "repositoryId", description = "The id of the internal repository.", defaultValue = "default") //
        String repositoryId, //
        @Schema(name = "query", description = "The query, in the syntax it was expressed in.", example = "SELECT * FROM SysContent") //
        String query, //
        @Schema(name = "syntax", description = "The syntax the query is expressed in; absent for the default hxql syntax.") //
        @JsonInclude(Include.NON_NULL) QuerySyntax syntax, //
        @Schema(name = "limit", description = "Max number of documents retrieved by the given query.", example = "100", defaultValue = "10") //
        Long limit, //
        @Schema(name = "offset", description = "Actual document offset; for pagination support.", example = "0", defaultValue = "0") //
        long offset, //
        @Schema(name = "trackTotalCount", description = "If true the total count of the query was computed.") //
        @JsonInclude(Include.NON_NULL) Boolean trackTotalCount, //
        @Schema(name = "totalCountIsTruncated", description = "If true the total count of the query was truncated.") //
        @JsonInclude(Include.NON_NULL) Boolean totalCountIsTruncated, //
        @Schema(name = "documents", description = "The list of retrieved documents by the given query.") //
        List<DocumentDTO> documents, //
        @Schema(name = "totalCount", description = "The total number of documents that match the query: is approximated if trackTotalCount is set to false.") //
        @JsonInclude(Include.NON_NULL) Long totalCount, //
        @Schema(name = "nextSearchAfter", description = "Opaque cursor token to retrieve the next page. Use this value in the searchAfter field of the next request.") //
        @JsonInclude(Include.NON_NULL) String nextSearchAfter) {

    public DocumentQueryResultDTO {
        // the default syntax needs no mention in the response
        syntax = QuerySyntax.nonDefault(syntax);
        if (documents == null) {
            documents = List.of();
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

    public DocumentQueryResultDTO(String repositoryId, String query, long limit, long offset, Boolean trackTotalCount,
            Boolean totalCountIsTruncated, List<DocumentDTO> documents, Long totalCount) {
        this(repositoryId, query, null, limit, offset, trackTotalCount, totalCountIsTruncated, documents, totalCount,
                null);
    }

    public DocumentQueryResultDTO(QuerySpecification spec, List<DocumentDTO> documents, long totalCount) {
        this(spec, documents, totalCount, false, null);
    }

    public DocumentQueryResultDTO(QuerySpecification spec, List<DocumentDTO> documents, long totalCount,
            boolean totalCountTruncated) {
        this(spec, documents, totalCount, totalCountTruncated, null);
    }

    public DocumentQueryResultDTO(QuerySpecification spec, List<DocumentDTO> documents, long totalCount,
            boolean totalCountTruncated, String nextSearchAfter) {
        this(spec.repositoryId(), spec.boundQuery(), spec.syntax(), spec.limit(), spec.offset(),
                spec.trackTotalCount(), totalCountTruncated, documents, totalCount, nextSearchAfter);
    }

    @JsonProperty("count")
    public long count() {
        return documents().size();
    }

}
