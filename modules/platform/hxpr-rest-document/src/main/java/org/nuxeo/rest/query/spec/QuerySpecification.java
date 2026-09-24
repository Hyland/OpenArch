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

import com.hyland.openarch.ecm.core.query.QuerySyntax;
import com.hyland.openarch.ecm.core.query.sql.HXQLParser;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.spec.QueryAssembler.AssembledQuery;

/**
 * A query ready to run: the HXQL to execute, the values for its {@code :name} parameters, and how to page and count
 * it.
 * <p>
 * This is what a query manager works from. The parts of the request that only mattered while the query was being
 * built — predicates, the where clause definition — are not here, having already been folded in. A manager executing
 * a tree uses {@link #statement}, which has its parameters bound; one that takes a query as text uses {@link #query}
 * along with {@link #parameters}. {@link #boundQuery()} is what the result DTOs echo back to the caller.
 *
 * @param query the HXQL text to execute, whatever syntax the request used
 * @param syntax the syntax the request expressed its query in
 * @param sourceQuery the query as the request wrote it, which for the expr syntax is the expression itself
 */
public record QuerySpecification( //
        String repositoryId, //
        String query, //
        QuerySyntax syntax, //
        String sourceQuery, //
        Map<String, Object> parameters, //
        Statement statement, //
        long limit, //
        long offset, //
        String searchAfter, //
        long countUpTo, //
        boolean trackTotalCount, //
        int trackTotalCountUpTo, //
        List<String> sort) {

    public QuerySpecification {
        syntax = QuerySyntax.defaulted(syntax);
        if (sort == null) {
            sort = List.of();
        }
        if (parameters == null) {
            parameters = Map.of();
        }
    }

    /**
     * The query as the caller expressed it, which is what a response echoes back: the expression itself for the expr
     * syntax, {@code null} when there was no expression to filter with, or the statement with its parameters bound.
     */
    public String boundQuery() {
        if (syntax == QuerySyntax.EXPR) {
            // never the assembled statement, which an expr response cannot claim as the query it was given
            return sourceQuery;
        }
        return statement.toString();
    }

    public QuerySpecification(DocumentQueryDTO query, String queryString, long limit, long countUpTo,
            boolean trackTotalCount, int trackTotalCountUpTo) {
        this(query.repositoryId(), queryString, query.syntax(), query.query(), Map.of(), HXQLParser.parse(queryString),
                limit, query.offset(), query.searchAfter(), countUpTo, trackTotalCount, trackTotalCountUpTo,
                query.sort());
    }

    public QuerySpecification(DocumentAdvancedQueryDTO query, AssembledQuery assembled, long limit, long countUpTo,
            boolean trackTotalCount, int trackTotalCountUpTo) {
        this(query.repositoryId(), assembled.query(), query.syntax(), query.query(), query.parameters(),
                assembled.statement(), limit, query.offset(), query.searchAfter(), countUpTo, trackTotalCount,
                trackTotalCountUpTo, query.sort());
    }

}
