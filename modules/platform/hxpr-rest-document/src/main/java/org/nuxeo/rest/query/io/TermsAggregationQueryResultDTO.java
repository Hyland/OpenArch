/*
 * (C) Copyright 2025 Hyland (http://www.hyland.com/) and others.
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

import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.query.io.serializer.AggregationsBucketSerializer;
import org.nuxeo.rest.query.spec.QuerySpecification;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Schema(name = "QueryResult", title = "Query Result", description = "Represents a query result.")
public record TermsAggregationQueryResultDTO(
        @Schema(name = "repositoryId", description = "The id of the internal repository.", defaultValue = "default") //
        String repositoryId, //
        @Schema(name = "query", description = "The HXQL query.", example = "SELECT * FROM SysContent") //
        String query, //
        @Schema(name = "limit", description = "Max number of documents retrieved by the given query.", example = "100", defaultValue = "0") //
        long limit, //
        @Schema(name = "offset", description = "Actual document offset; for pagination support.", example = "0", defaultValue = "0") //
        long offset, //
        @Schema(name = "trackTotalCount", description = "If true the total count of the query was computed.") //
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean trackTotalCount, //
        @Schema(name = "totalCountIsTruncated", description = "If true the total count of the query was truncated.") //
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean totalCountIsTruncated, //
        @Schema(name = "documents", description = "The list of retrieved documents by the given query.") //
        List<DocumentDTO> documents, //
        @Schema(name = "totalCount", description = "The total number of documents that match the query: is approximated if trackTotalCount is set to false.")
        //
        @JsonInclude(JsonInclude.Include.NON_NULL) Long totalCount,
        @Schema(name = "aggregationsBuckets", description = "The list of aggregation buckets computed for the query.")//
        @JsonSerialize(using = AggregationsBucketSerializer.class) List<StringTermsBucket> aggregationsBuckets) {

    public TermsAggregationQueryResultDTO {

        if (documents == null) {
            documents = List.of();
        }

        if (isFalse(trackTotalCount)) {
            // trackTotalCount is false or null, keep it as is and remove totalCount as it is not relevant
            totalCount = null;
        } else if (isTrue(trackTotalCount)) {
            // when we track the total count the presence of totalCount is enough, we don't need this extra boolean
            trackTotalCount = null;
        }

        if (isFalse(totalCountIsTruncated)) {
            // totalCountIsTruncated is false or null, remove it
            totalCountIsTruncated = null;
        }
        // else: totalCountIsTruncated is true, keep it as is

        if (aggregationsBuckets == null) {
            aggregationsBuckets = List.of();
        }
    }

    public TermsAggregationQueryResultDTO(QuerySpecification spec, List<DocumentDTO> documents, Long totalCount,
                                          Boolean totalCountIsTruncated, List<StringTermsBucket> aggregationsBuckets) {
        this(spec.repositoryId(), spec.query(), spec.limit(), spec.offset(), spec.trackTotalCount(),
                totalCountIsTruncated, documents, totalCount, aggregationsBuckets);
    }

    @JsonProperty("count")
    public long count() {
        return documents().size();
    }
}
