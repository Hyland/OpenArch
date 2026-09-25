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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.query.sql.model.BooleanLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.config.SecurityLevelSecurityPolicyConfig;
import com.hyland.openarch.runtime.api.Framework;

/**
 * Pure unit tests for the runtime behavior of {@link SecurityLevelSecurityPolicy}.
 * <p>
 * The schema manager is mocked so that the mixin's security property is resolvable, which is what the config-time tests
 * in {@code SecurityLevelSecurityPolicyTest} cannot do (see HXCS-6020: the content model is not ready at config time).
 */
class SecurityLevelSecurityPolicyBehaviorTest {

    private static final String REPO = "repo";

    private static final String NAME = "seclevel";

    private static final String MIXIN = "Clearance";

    private static final String USER_ATTRIBUTE = "clearance";

    private static final String LEVEL_PROPERTY = "docLevel";

    private static SecurityLevelSecurityPolicyConfig validConfig() {
        return new SecurityLevelSecurityPolicyConfig("project", Set.of("Contract"), Set.of(EVERYTHING), MIXIN,
                USER_ATTRIBUTE);
    }

    private static SecurityLevelSecurityPolicy newPolicy(SecurityLevelSecurityPolicyConfig config,
            List<ValidationViolation> violations) {
        return new SecurityLevelSecurityPolicy(REPO, NAME, config, violations);
    }

    /** Stubs the schema manager so that the policy's mixin exposes the given fields. */
    private static void stubMixinFields(MockedStatic<Framework> framework, String... fieldNames) {
        SecurityPolicyBehaviorTestSupport.stubMixinFields(framework, REPO, MIXIN, fieldNames);
    }

    private static RepositoryPrincipal principal(String id, Map<String, Object> properties, boolean administrator) {
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.getId()).thenReturn(id);
        when(principal.getProperties()).thenReturn(properties);
        when(principal.isAdministrator()).thenReturn(administrator);
        return principal;
    }

    @Test
    void validConfigProducesNoViolations() {
        List<ValidationViolation> violations = new ArrayList<>();

        newPolicy(validConfig(), violations);

        assertTrue(violations.isEmpty());
    }

    @Test
    void blankUserAttributeProducesViolation() {
        List<ValidationViolation> violations = new ArrayList<>();
        var config = new SecurityLevelSecurityPolicyConfig("project", Set.of("Contract"), Set.of(EVERYTHING), MIXIN,
                null);

        newPolicy(config, violations);

        assertFalse(violations.isEmpty());
    }

    @Test
    void blankMixinProducesViolation() {
        List<ValidationViolation> violations = new ArrayList<>();
        var config = new SecurityLevelSecurityPolicyConfig("project", Set.of("Contract"), Set.of(EVERYTHING), null,
                USER_ATTRIBUTE);

        newPolicy(config, violations);

        assertFalse(violations.isEmpty());
    }

    @Test
    void emptyContentTypesAndPermissionsProduceViolations() {
        // empty permissions makes the constructor consult the SecurityService, so it must be available
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(SecurityService.class)).thenReturn(mock(SecurityService.class));
            List<ValidationViolation> violations = new ArrayList<>();
            var config = new SecurityLevelSecurityPolicyConfig("project", Set.of(), Set.of(), MIXIN, USER_ATTRIBUTE);

            newPolicy(config, violations);

            assertFalse(violations.isEmpty());
        }
    }

    @Test
    void hasPermissionDeniesWhenPrincipalHasNoLevel() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, LEVEL_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());
            RepositoryPrincipal principal = principal("alice", Map.of(), false);
            InternalDocument doc = mock(InternalDocument.class);

            assertFalse(policy.hasPermission(doc, principal, Set.of(), "Read", Set.of()));
        }
    }

    @Test
    void hasPermissionDeniesWhenMixinHasSeveralProperties() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, LEVEL_PROPERTY, "another");
            var policy = newPolicy(validConfig(), new ArrayList<>());
            RepositoryPrincipal principal = principal("alice", Map.of(USER_ATTRIBUTE, 3L), false);
            InternalDocument doc = mock(InternalDocument.class);

            assertFalse(policy.hasPermission(doc, principal, Set.of(), "Read", Set.of()));
        }
    }

    @Test
    void hasPermissionAllowsWhenDocumentHasNoLevel() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, LEVEL_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());
            RepositoryPrincipal principal = principal("alice", Map.of(USER_ATTRIBUTE, 3L), false);
            InternalDocument doc = mock(InternalDocument.class);
            when(doc.getPropertyValue(LEVEL_PROPERTY)).thenReturn(null);

            assertTrue(policy.hasPermission(doc, principal, Set.of(), "Read", Set.of()));
        }
    }

    @Test
    void hasPermissionComparesPrincipalAndDocumentLevels() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, LEVEL_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());
            InternalDocument doc = mock(InternalDocument.class);
            when(doc.getPropertyValue(LEVEL_PROPERTY)).thenReturn(2L);

            assertTrue(policy.hasPermission(doc, principal("alice", Map.of(USER_ATTRIBUTE, 3L), false), Set.of(), "Read",
                    Set.of()));
            assertFalse(policy.hasPermission(doc, principal("bob", Map.of(USER_ATTRIBUTE, 1L), false), Set.of(), "Read",
                    Set.of()));
        }
    }

    @Test
    void hasPermissionReadsLevelFromStringOrNumber() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, LEVEL_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());
            InternalDocument doc = mock(InternalDocument.class);
            when(doc.getPropertyValue(LEVEL_PROPERTY)).thenReturn(2L);

            // string attribute value is parsed
            assertTrue(policy.hasPermission(doc, principal("s", Map.of(USER_ATTRIBUTE, "5"), false), Set.of(), "Read",
                    Set.of()));
            // an unsupported attribute type is treated as no level -> deny
            assertFalse(policy.hasPermission(doc, principal("b", Map.of(USER_ATTRIBUTE, Boolean.TRUE), false), Set.of(),
                    "Read", Set.of()));
        }
    }

    @Test
    void getQueryClauseReturnsFalseWhenMixinInvalid() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, LEVEL_PROPERTY, "another");
            var policy = newPolicy(validConfig(), new ArrayList<>());
            var queryParams = new QueryParameters(principal("alice", Map.of(USER_ATTRIBUTE, 3L), false), 0, 0);

            assertSame(BooleanLiteral.LIT_FALSE, policy.getQueryClause(queryParams, Set.of()));
        }
    }

    @Test
    void getQueryClauseBuildsClauseWithAndWithoutLevel() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, LEVEL_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());

            Operand withLevel = policy.getQueryClause(
                    new QueryParameters(principal("alice", Map.of(USER_ATTRIBUTE, 3L), false), 0, 0), Set.of());
            Operand withoutLevel = policy.getQueryClause(
                    new QueryParameters(principal("bob", Map.of(), false), 0, 0), Set.of());

            assertNotNull(withLevel);
            assertNotSame(BooleanLiteral.LIT_FALSE, withLevel);
            assertNotNull(withoutLevel);
            assertNotSame(BooleanLiteral.LIT_FALSE, withoutLevel);
        }
    }

    @Test
    void appliesToDocumentSkipsAdministratorAndCreator() {
        var policy = newPolicy(validConfig(), new ArrayList<>());
        InternalDocument doc = mock(InternalDocument.class);

        // administrators bypass the policy
        assertFalse(policy.appliesToDocument(doc, principal("admin", Map.of(), true), Set.of("Read")));

        // the creator bypasses the policy
        when(doc.getPropertyValue(SYS_CREATOR)).thenReturn("alice");
        assertFalse(policy.appliesToDocument(doc, principal("alice", Map.of(), false), Set.of("Read")));
    }

    @Test
    void appliesToDocumentRequiresPolicyListedInDocument() {
        var policy = newPolicy(validConfig(), new ArrayList<>());
        RepositoryPrincipal principal = principal("bob", Map.of(), false);
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getPropertyValue(SYS_CREATOR)).thenReturn("someone-else");

        // not listed in sys_secpol -> policy does not apply
        when(doc.getPropertyValue(SYS_SECURITY_POLICIES)).thenReturn(null);
        assertFalse(policy.appliesToDocument(doc, principal, Set.of("Read")));

        // listed in sys_secpol -> policy applies
        when(doc.getPropertyValue(SYS_SECURITY_POLICIES)).thenReturn(List.of(NAME));
        assertTrue(policy.appliesToDocument(doc, principal, Set.of("Read")));
    }

    @Test
    void checkAllowedMixinUpdateDeniesConfiguredMixin() {
        var policy = newPolicy(validConfig(), new ArrayList<>());

        assertThrows(DocumentSecurityException.class, () -> policy.checkAllowedMixinUpdate(MIXIN));
        policy.checkAllowedMixinUpdate("SomeOtherMixin"); // must not throw
    }

    @Test
    void checkAllowedPropertyUpdateDeniesSecurityProperty() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubMixinFields(framework, LEVEL_PROPERTY);
            var policy = newPolicy(validConfig(), new ArrayList<>());

            assertThrows(DocumentSecurityException.class, () -> policy.checkAllowedPropertyUpdate(LEVEL_PROPERTY));
            policy.checkAllowedPropertyUpdate("unrelated"); // must not throw
        }
    }
}
