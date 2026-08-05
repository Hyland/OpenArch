/*
 * (C) Copyright 2010-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.ecm.core.query.nxql;

import java.util.List;

public record PredicateDefinition( //
        String searchParameter, //
        String operator, //
        String operatorXPath, //
        String type, //
        List<PredicateFieldDefinition> fieldDefinitions, //
        String hint) {

    public static final String ATOMIC_PREDICATE = "atomic";

    public static final String SUB_CLAUSE_PREDICATE = "subClause";

    public PredicateDefinition {
        if (type == null) {
            type = ATOMIC_PREDICATE;
        }
        if (fieldDefinitions == null) {
            fieldDefinitions = List.of();
        }
    }

    public PredicateDefinition(String searchParameter, String operator, String parameter) {
        this(searchParameter, operator, parameter, null, null);
    }

    public PredicateDefinition(String searchParameter, String operator, String parameter, String defaultValue) {
        this(searchParameter, operator, parameter, defaultValue, null);
    }

    public PredicateDefinition(String searchParameter, String operator, String parameter, String defaultValue,
            String hint) {
        this(searchParameter, operator, null, null, List.of(new PredicateFieldDefinition(parameter, defaultValue)),
                hint);
    }

}
