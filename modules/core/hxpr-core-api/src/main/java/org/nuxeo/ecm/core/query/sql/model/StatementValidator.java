/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.query.sql.model;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.schema.SchemaManager;

/**
 * Validates restrictions on a {@link Statement}.
 */
public record StatementValidator(SchemaManager schemaManager) {

    /**
     * Rejects security properties for non-administrator users anywhere in the statement.
     *
     * @param statement the statement
     * @param principal the current user, or {@code null} if there is none
     * @param schemaManager the schema manager
     * @throws QueryParseException if the statement uses a security property
     */
    public static void rejectSecurityProperties(Statement statement, NuxeoPrincipal principal,
            SchemaManager schemaManager) {
        if (principal != null && principal.isAdministrator()) {
            return;
        }
        new StatementValidator(schemaManager).check(statement);
    }

    private void check(Statement statement) {
        statement.getSelectClause().elements().forEach(this::check);
        if (statement.getWhereClause() != null) {
            check(statement.getWhereClause().predicate());
        }
        if (statement.getOrderByClause() != null) {
            statement.getOrderByClause().elements().forEach(orderBy -> check(orderBy.reference()));
        }
    }

    private void check(Operand operand) {
        switch (operand) {
            case Reference reference -> {
                if (schemaManager.isSecurityProperty(reference.name())) {
                    throw new QueryParseException("Disallowed system property: " + reference.name());
                }
            }
            case MultiExpression multiExpression -> multiExpression.predicates.forEach(this::check);
            case Predicate predicate -> {
                check(predicate.lvalue);
                check(predicate.rvalue);
            }
            case Function function when function.args() != null -> check(function.args());
            case OperandList list -> list.elements().forEach(this::check);
            case null, default -> {
                // literals and parameters hold no field references
            }
        }
    }
}
