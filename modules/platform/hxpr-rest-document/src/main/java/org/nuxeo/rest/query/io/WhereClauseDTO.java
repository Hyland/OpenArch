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

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Rest representation of a query where clause.
 */
@Schema(name = "WhereClause", title = "Query Where Clause", description = "Represents an advanced query where clause definition.")
public record WhereClauseDTO( //
        String query, //
        List<RestPredicate> predicates) {

    public static WhereClauseDTO of(String query, List<RestPredicate> predicates) {
        return new WhereClauseDTO(query, predicates);
    }

    public WhereClauseDTO {
        if (predicates == null) {
            predicates = List.of();
        }
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
