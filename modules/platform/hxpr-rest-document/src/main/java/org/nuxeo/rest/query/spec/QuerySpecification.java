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

import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;

/**
 * A query ready to run: the NXQL to execute, plus how to page and count it.
 * <p>
 * This is what a query manager works from, and what the result DTOs echo back to the caller. The parts of the request
 * that only mattered while the query was being built — predicates, parameters, the where clause definition — are not
 * here, having already been folded into {@link #query}.
 */
public record QuerySpecification( //
        String repositoryId, //
        String query, //
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
    }

    public QuerySpecification(DocumentQueryDTO query, String queryString, long limit, long countUpTo,
            boolean trackTotalCount, int trackTotalCountUpTo) {
        this(query.repositoryId(), queryString, limit, query.offset(), query.searchAfter(), countUpTo, trackTotalCount,
                trackTotalCountUpTo, query.sort());
    }

    public QuerySpecification(DocumentAdvancedQueryDTO query, String queryString, long limit, long countUpTo,
            boolean trackTotalCount, int trackTotalCountUpTo) {
        this(query.repositoryId(), queryString, limit, query.offset(), query.searchAfter(), countUpTo, trackTotalCount,
                trackTotalCountUpTo, query.sort());
    }

}
