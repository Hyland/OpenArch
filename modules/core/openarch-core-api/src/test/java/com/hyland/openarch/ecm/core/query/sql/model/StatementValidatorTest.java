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
package com.hyland.openarch.ecm.core.query.sql.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.schema.SchemaManager;

public class StatementValidatorTest {

    protected static final List<Statement> QUERIES = List.of( //
            new Statement(new SelectClause(List.of(new Reference("sys_acl/*/principal"))), new FromClause()), //
            new Statement().withPredicate(
                    new Predicate(new Reference("sys_racl"), Operator.EQ, new StringLiteral("u:bob"))), //
            new Statement().withOrderByClause(
                    new OrderByClause(List.of(new OrderByExpr(new Reference("sys_secpol"), false)))));

    protected static final SchemaManager SCHEMA_MANAGER = schemaManager();

    protected static SchemaManager schemaManager() {
        SchemaManager schemaManager = mock(SchemaManager.class);
        when(schemaManager.isSecurityProperty("sys_acl/*/principal")).thenReturn(true);
        when(schemaManager.isSecurityProperty("sys_racl")).thenReturn(true);
        when(schemaManager.isSecurityProperty("sys_secpol")).thenReturn(true);
        return schemaManager;
    }

    @Test
    public void testForbiddenProperties() {
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        for (Statement statement : QUERIES) {
            assertThrows(QueryParseException.class,
                    () -> StatementValidator.rejectSecurityProperties(statement, principal, SCHEMA_MANAGER));
        }
    }

    @Test
    public void testAdministratorBypass() {
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.isAdministrator()).thenReturn(true);
        for (Statement statement : QUERIES) {
            assertDoesNotThrow(
                    () -> StatementValidator.rejectSecurityProperties(statement, principal, SCHEMA_MANAGER));
        }
    }
}
