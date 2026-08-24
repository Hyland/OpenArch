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
package org.nuxeo.rest.query.spec;

import java.util.List;
import java.util.Map;

import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.spec.QueryAssembler.AssembledQuery;

/**
 * A query ready to run: the HXQL to execute, the values for its {@code :name} parameters, and how to page and count
 * it.
 * <p>
 * This is what a query manager works from. The parts of the request that only mattered while the query was being
 * built — predicates, the where clause definition — are not here, having already been folded into {@link #query}. The
 * parameters are, since they are bound where the query is executed; {@link #boundQuery} is the same query with them
 * bound, which is what the result DTOs echo back to the caller.
 */
public record QuerySpecification( //
        String repositoryId, //
        String query, //
        Map<String, Object> parameters, //
        String boundQuery, //
        long limit, //
        long offset, //
        String searchAfter, //
        long countUpTo, //
        boolean trackTotalCount, //
        int trackTotalCountUpTo, //
        List<String> sort) {

    public QuerySpecification {
        if (sort == null) {
            sort = List.of();
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public QuerySpecification(DocumentQueryDTO query, String queryString, long limit, long countUpTo,
            boolean trackTotalCount, int trackTotalCountUpTo) {
        this(query.repositoryId(), queryString, Map.of(), queryString, limit, query.offset(), query.searchAfter(),
                countUpTo, trackTotalCount, trackTotalCountUpTo, query.sort());
    }

    public QuerySpecification(DocumentAdvancedQueryDTO query, AssembledQuery assembled, long limit, long countUpTo,
            boolean trackTotalCount, int trackTotalCountUpTo) {
        this(query.repositoryId(), assembled.query(), query.parameters(), assembled.boundQuery(), limit,
                query.offset(), query.searchAfter(), countUpTo, trackTotalCount, trackTotalCountUpTo, query.sort());
    }

}
