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

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(name = "TermsAggregationsQuery", description = "Represents a terms aggregations query.")
public record TermsAggregationsQueryDTO( //
                                         @Schema(name = "query", description = "The HXQL query to filter the documents on which to run the terms aggregation.")
                                         DocumentQueryDTO query,
                                         @Schema(name = "limit", description = "Max number of aggregation buckets retrieved by the given query.", example = "100", defaultValue = "10") //
                                         int limit,
                                         @Schema(name = "searchTerm", description = "The search term to filter the terms aggregation results.") //
                                         String searchTerm,
                                         @JsonProperty(required = true)
                                         @Schema(name = "termsAggregationProperty", description = "The property containing the array of keys to run the terms aggregation on.") //
                                         String termsAggregationProperty) {
}
