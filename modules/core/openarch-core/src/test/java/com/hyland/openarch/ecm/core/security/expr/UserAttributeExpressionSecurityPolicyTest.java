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
package com.hyland.openarch.ecm.core.security.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.query.sql.model.BooleanLiteral;

/**
 * Unit tests for the non-expression branches of {@link UserAttributeExpressionSecurityPolicy}. The policy is built with
 * an {@code EVERYTHING} config so construction needs no runtime services.
 */
class UserAttributeExpressionSecurityPolicyTest {

    private static UserAttributeExpressionSecurityPolicy newPolicy() {
        UserAttributeExpressionSecurityPolicyConfig config = new UserAttributeExpressionSecurityPolicyConfig(
                UserAttributeExpressionSecurityPolicy.USER_ATTRIBUTE_EXPRESSION_ENGINE_NAME, null, Set.of(),
                Set.of(EVERYTHING), null);
        return new UserAttributeExpressionSecurityPolicy("repo", config);
    }

    @Test
    void isHiddenAndGlobal() {
        UserAttributeExpressionSecurityPolicy policy = newPolicy();
        assertTrue(policy.isHidden());
        assertTrue(policy.isGlobal());
    }

    @Test
    void administratorIsAlwaysGranted() {
        UserAttributeExpressionSecurityPolicy policy = newPolicy();
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.isAdministrator()).thenReturn(true);

        assertTrue(policy.hasPermission(mock(InternalDocument.class), principal, Set.of(), "Read", Set.of()));
    }

    @Test
    void grantsWhenNoSecurityExpressionAttribute() {
        UserAttributeExpressionSecurityPolicy policy = newPolicy();
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.isAdministrator()).thenReturn(false);
        when(principal.getUserAttributes()).thenReturn(Map.of());

        assertTrue(policy.hasPermission(mock(InternalDocument.class), principal, Set.of(), "Read", Set.of()));
    }

    @Test
    void queryClauseIsTrueWhenNoSecurityExpressionAttribute() {
        UserAttributeExpressionSecurityPolicy policy = newPolicy();
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.getUserAttributes()).thenReturn(Map.of());
        QueryParameters queryParams = mock(QueryParameters.class);
        when(queryParams.principal()).thenReturn(principal);

        assertEquals(BooleanLiteral.LIT_TRUE, policy.getQueryClause(queryParams, Set.of()));
    }
}
