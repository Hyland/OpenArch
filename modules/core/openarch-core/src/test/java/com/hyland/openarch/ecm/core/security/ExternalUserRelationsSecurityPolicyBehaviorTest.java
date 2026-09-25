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
package com.hyland.openarch.ecm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.query.sql.model.BooleanLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.config.ExternalUserRelationsSecurityPolicyConfig;
import com.hyland.openarch.runtime.api.Framework;

/**
 * Pure unit tests for the runtime behavior of {@link ExternalUserRelationsSecurityPolicy}.
 * <p>
 * The schema manager is mocked so that the mixin's employee-id property is resolvable, and relations are injected
 * through {@link ExternalUserRelationsSecurityPolicy#setLocalConfiguration}, avoiding the runtime services that the
 * config-time tests cannot exercise (HXCS-6020).
 */
class ExternalUserRelationsSecurityPolicyBehaviorTest {

    private static final String REPO = "repo";

    private static final String NAME = "eur";

    private static final String MIXIN = "WDObject";

    private static final String USER_ATTRIBUTE = "wd_empId";

    private static final String EMPLOYEE_PROPERTY = "wd_empId";

    private static final String HR_ADMIN = "HRAdmin";

    private static ExternalUserRelationsSecurityPolicyConfig validConfig() {
        return new ExternalUserRelationsSecurityPolicyConfig("project", Set.of("Resume"), Set.of(EVERYTHING), MIXIN,
                List.of(HR_ADMIN, "manager"), USER_ATTRIBUTE);
    }

    private static ExternalUserRelationsSecurityPolicy newPolicy(ExternalUserRelationsSecurityPolicyConfig config,
            List<ValidationViolation> violations) {
        return new ExternalUserRelationsSecurityPolicy(REPO, NAME, config, violations);
    }

    /** Parses the persisted config map into a RelationsState and applies it to the policy for the given repository. */
    private static void applyConfig(ExternalUserRelationsSecurityPolicy policy, String repository,
            Map<String, Object> config) {
        policy.setLocalState(repository, policy.fromConfigMap(repository, config));
    }

    private static void stubMixinFields(MockedStatic<Framework> framework, String... fieldNames) {
        SecurityPolicyBehaviorTestSupport.stubMixinFields(framework, REPO, MIXIN, fieldNames);
    }

    private static RepositoryPrincipal principal(String id, Map<String, Object> properties) {
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.getId()).thenReturn(id);
        when(principal.getProperties()).thenReturn(properties);
        return principal;
    }

    @Test
    void validConfigProducesNoViolations() {
        List<ValidationViolation> violations = new ArrayList<>();

        newPolicy(validConfig(), violations);

        assertTrue(violations.isEmpty());
    }

    @Test
    void emptyRelationsProduceViolation() {
        List<ValidationViolation> violations = new ArrayList<>();
        var config = new ExternalUserRelationsSecurityPolicyConfig("project", Set.of("Resume"), Set.of(EVERYTHING),
                MIXIN, List.of(), USER_ATTRIBUTE);

        newPolicy(config, violations);

        assertFalse(violations.isEmpty());
    }

    @Test
    void blankUserAttributeProducesViolation() {
        List<ValidationViolation> violations = new ArrayList<>();
        var config = new ExternalUserRelationsSecurityPolicyConfig("project", Set.of("Resume"), Set.of(EVERYTHING),
                MIXIN, List.of(HR_ADMIN), null);

        newPolicy(config, violations);

        assertFalse(violations.isEmpty());
    }

    @Test
    void hasPermissionDeniesWhenPrincipalHasNoExternalId() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, EMPLOYEE_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());
            InternalDocument doc = mock(InternalDocument.class);

            // null properties, missing attribute, non-string and blank all resolve to "no external id" -> deny
            assertFalse(policy.hasPermission(doc, principal("p", null), Set.of(), "Read", Set.of()));
            assertFalse(policy.hasPermission(doc, principal("p", Map.of()), Set.of(), "Read", Set.of()));
            assertFalse(policy.hasPermission(doc, principal("p", Map.of(USER_ATTRIBUTE, 123L)), Set.of(), "Read",
                    Set.of()));
            assertFalse(policy.hasPermission(doc, principal("p", Map.of(USER_ATTRIBUTE, "  ")), Set.of(), "Read",
                    Set.of()));
        }
    }

    @Test
    void hasPermissionDeniesWhenMixinInvalid() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, EMPLOYEE_PROPERTY, "another");
            var policy = newPolicy(validConfig(), new ArrayList<>());
            InternalDocument doc = mock(InternalDocument.class);

            assertFalse(policy.hasPermission(doc, principal("p", Map.of(USER_ATTRIBUTE, "bob")), Set.of(), "Read",
                    Set.of()));
        }
    }

    @Test
    void hasPermissionHandlesDocumentEmployee() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, EMPLOYEE_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());
            RepositoryPrincipal bob = principal("p", Map.of(USER_ATTRIBUTE, "bob"));

            InternalDocument noEmployee = mock(InternalDocument.class);
            when(noEmployee.getPropertyValue(EMPLOYEE_PROPERTY)).thenReturn(null);
            assertFalse(policy.hasPermission(noEmployee, bob, Set.of(), "Read", Set.of()));

            InternalDocument ownDoc = mock(InternalDocument.class);
            when(ownDoc.getPropertyValue(EMPLOYEE_PROPERTY)).thenReturn("bob");
            assertTrue(policy.hasPermission(ownDoc, bob, Set.of(), "Read", Set.of()));

            InternalDocument otherDoc = mock(InternalDocument.class);
            when(otherDoc.getPropertyValue(EMPLOYEE_PROPERTY)).thenReturn("alice");
            assertFalse(policy.hasPermission(otherDoc, bob, Set.of(), "Read", Set.of()));
        }
    }

    @Test
    void hasPermissionAllowsWhenRelationExists() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, EMPLOYEE_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());
            // alice's HRAdmin is bob -> bob has an HRAdmin relation to alice
            applyConfig(policy, REPO, Map.of("relations", Map.of("alice", Map.of(HR_ADMIN, List.of("bob"))),
                    "lastModified", "2024-08-01T12:23:54Z"));

            InternalDocument aliceDoc = mock(InternalDocument.class);
            when(aliceDoc.getPropertyValue(EMPLOYEE_PROPERTY)).thenReturn("alice");

            assertTrue(policy.hasPermission(aliceDoc, principal("p", Map.of(USER_ATTRIBUTE, "bob")), Set.of(), "Read",
                    Set.of()));
        }
    }

    @Test
    void getQueryClauseReturnsFalseWhenMixinInvalid() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, EMPLOYEE_PROPERTY, "another");
            var policy = newPolicy(validConfig(), new ArrayList<>());
            var queryParams = new QueryParameters(principal("p", Map.of(USER_ATTRIBUTE, "bob")), 0, 0);

            assertSame(BooleanLiteral.LIT_FALSE, policy.getQueryClause(queryParams, Set.of()));
        }
    }

    @Test
    void getQueryClauseBuildsClauseWithAndWithoutExternalId() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, EMPLOYEE_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());
            applyConfig(policy, REPO, Map.of("relations", Map.of("alice", Map.of(HR_ADMIN, List.of("bob"))),
                    "lastModified", "2024-08-01T12:23:54Z"));

            Operand withId = policy.getQueryClause(
                    new QueryParameters(principal("p", Map.of(USER_ATTRIBUTE, "bob")), 0, 0), Set.of());
            Operand withoutId = policy.getQueryClause(new QueryParameters(principal("p", Map.of()), 0, 0), Set.of());

            assertNotNull(withId);
            assertNotSame(BooleanLiteral.LIT_FALSE, withId);
            assertNotNull(withoutId);
            assertNotSame(BooleanLiteral.LIT_FALSE, withoutId);
        }
    }

    @Test
    void setLocalStateIgnoresOtherRepository() {
        var policy = newPolicy(validConfig(), new ArrayList<>());

        applyConfig(policy, "other-repo", Map.of("relations", Map.of("alice", Map.of(HR_ADMIN, List.of("bob")))));

        assertTrue(policy.getExternalUserRelations().isEmpty());
    }

    @Test
    void fromConfigMapTreatsInvalidRelationsAsEmpty() {
        var policy = newPolicy(validConfig(), new ArrayList<>());

        applyConfig(policy, REPO, Map.of("relations", "not-a-map", "lastModified", "not-a-date"));

        assertTrue(policy.getExternalUserRelations().isEmpty());
        assertNull(policy.getExternalUserRelationsLastModified());
    }

    @Test
    void configMapRoundTripsRelations() {
        var policy = newPolicy(validConfig(), new ArrayList<>());
        Map<String, Object> relations = Map.of("alice", Map.of(HR_ADMIN, List.of("bob")));

        applyConfig(policy, REPO, Map.of("relations", relations, "lastModified", "2024-08-01T12:23:54Z"));

        // getExternalUserRelations inverts back to the public "alice -> HRAdmin -> [bob]" form
        assertEquals(relations, policy.getExternalUserRelations());
        assertNotNull(policy.getExternalUserRelationsLastModified());

        // toConfigMap exposes the persisted shape
        Map<String, Object> local = policy.toConfigMap(policy.getLocalState(REPO));
        assertNotNull(local.get("relations"));
        assertEquals("2024-08-01T12:23:54Z", local.get("lastModified"));
    }
}
