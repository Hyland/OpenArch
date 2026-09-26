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

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.query.QuerySyntax;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Rest representation of a query where clause.
 */
@Schema(name = "WhereClause", title = "Query Where Clause", description = "Represents an advanced query where clause definition.")
public record WhereClauseDTO( //
        @Schema(name = "query", description = "The query, in the syntax given by the syntax field. An hxql query provides the statement the request selects from, an expr query only filters it.", example = "SELECT * FROM SysContent") //
        String query, //
        @Schema(name = "syntax", description = "The syntax the query is expressed in; absent for the default hxql syntax.") //
        @JsonInclude(Include.NON_NULL) QuerySyntax syntax, //
        List<RestPredicate> predicates) {

    public static WhereClauseDTO of(String query, List<RestPredicate> predicates) {
        return new WhereClauseDTO(query, null, predicates);
    }

    /** A definition whose query is an expression, filtering whatever statement the request selects from. */
    public static WhereClauseDTO ofExpr(String expr, List<RestPredicate> predicates) {
        return new WhereClauseDTO(expr, QuerySyntax.EXPR, predicates);
    }

    public WhereClauseDTO {
        // the default syntax needs no mention
        syntax = QuerySyntax.nonDefault(syntax);
        if (predicates == null) {
            predicates = List.of();
        }
    }

    /**
     * Whether this definition provides the statement to select from, which only an HXQL query does; an expression is
     * just one more filter.
     */
    public boolean hasStatement() {
        // a null syntax is really hxql, the default the constructor leaves unmentioned
        return QuerySyntax.defaulted(syntax) == QuerySyntax.HXQL && StringUtils.isNotBlank(query);
    }

    /**
     * Rest representation of a query where clause predicate.
     */
    @Schema(name = "Predicate", title = "Query Predicate", description = "Represents an advanced query predicate definition.")
    public record RestPredicate( //
            String searchParameter, //
            String operator, //
            List<RestPredicateParameter> parameters) {

        public static RestPredicate of(String searchParameter, String operator, String parameter) {
            return new RestPredicate(searchParameter, operator, parameter, null);
        }

        public static RestPredicate ofStatic(String searchParameter, String operator, String defaultValue) {
            return new RestPredicate(searchParameter, operator, null, defaultValue);
        }

        public static RestPredicate of(String searchParameter, String operator, String parameter1,
                String parameter2) {
            return new RestPredicate(searchParameter, operator,
                    List.of(RestPredicateParameter.of(parameter1), RestPredicateParameter.of(parameter2)));
        }

        public RestPredicate {
            if (parameters == null) {
                parameters = List.of();
            }
        }

        public RestPredicate(String searchParameter, String operator, String parameter, String defaultValue) {
            this(searchParameter, operator, List.of(RestPredicateParameter.of(parameter, defaultValue)));
        }

    }

    /**
     * Rest representation of a query where clause predicate parameter, with default value support.
     */
    @Schema(name = "PredicateParameter", title = "Query Predicate Parameter", description = "Represents an advanced query predicate parameter definition.")
    public record RestPredicateParameter(String name, Object defaultValue) {

        public static RestPredicateParameter of(String parameter) {
            return new RestPredicateParameter(parameter);
        }

        public static RestPredicateParameter of(String parameter, Object defaultValue) {
            return new RestPredicateParameter(parameter, defaultValue);
        }

        public RestPredicateParameter(String parameter) {
            this(parameter, null);
        }

    }
}
