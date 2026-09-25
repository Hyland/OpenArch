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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.api.security.Access;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.config.ACLSecurityPolicyConfig;

class ACLSecurityPolicyBehaviorTest {

    private static final String REPO = "repo";

    private static ACLSecurityPolicy newPolicy(List<ValidationViolation> violations) {
        return new ACLSecurityPolicy(REPO, "acl", new ACLSecurityPolicyConfig(), violations);
    }

    private static RepositoryPrincipal principal(boolean administrator) {
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.isAdministrator()).thenReturn(administrator);
        return principal;
    }

    @Test
    void validConfigProducesNoViolations() {
        List<ValidationViolation> violations = new ArrayList<>();

        newPolicy(violations);

        assertTrue(violations.isEmpty());
    }

    @Test
    void wrongPolicyNameProducesViolation() {
        List<ValidationViolation> violations = new ArrayList<>();

        new ACLSecurityPolicy(REPO, "not-acl", new ACLSecurityPolicyConfig(), violations);

        assertFalse(violations.isEmpty());
    }

    @Test
    void isHiddenAndGlobal() {
        var policy = newPolicy(new ArrayList<>());

        assertTrue(policy.isHidden());
        assertTrue(policy.isGlobal());
    }

    @Test
    void appliesToDocumentOnlyForNonAdministrators() {
        var policy = newPolicy(new ArrayList<>());
        InternalDocument doc = mock(InternalDocument.class);

        assertFalse(policy.appliesToDocument(doc, principal(true), Set.of("Read")));
        assertTrue(policy.appliesToDocument(doc, principal(false), Set.of("Read")));
    }

    @Test
    void hasPermissionDeniesWhenAclEmpty() {
        var policy = newPolicy(new ArrayList<>());
        ACL acl = mock(ACL.class);
        when(acl.isEmpty()).thenReturn(true);
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getEffectiveACL()).thenReturn(acl);

        assertFalse(policy.hasPermission(doc, principal(false), Set.of("group"), "Read", Set.of("Read")));
    }

    @Test
    void hasPermissionFollowsAclAccess() {
        var policy = newPolicy(new ArrayList<>());
        ACL acl = mock(ACL.class);
        when(acl.isEmpty()).thenReturn(false);
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getEffectiveACL()).thenReturn(acl);

        when(acl.getAccess(anyCollection(), anyCollection())).thenReturn(Access.GRANT);
        assertTrue(policy.hasPermission(doc, principal(false), Set.of("group"), "Read", Set.of("Read")));

        when(acl.getAccess(anyCollection(), anyCollection())).thenReturn(Access.DENY);
        assertFalse(policy.hasPermission(doc, principal(false), Set.of("group"), "Read", Set.of("Read")));
    }

    @Test
    void checkAllowedPropertyUpdateDeniesAclProperty() {
        var policy = newPolicy(new ArrayList<>());

        assertThrows(DocumentSecurityException.class, () -> policy.checkAllowedPropertyUpdate(SYS_ACL));
        policy.checkAllowedPropertyUpdate("otherProperty"); // must not throw
    }

    @Test
    void getQueryClauseFiltersByReadAcl() {
        var policy = newPolicy(new ArrayList<>());

        Predicate clause = policy.getQueryClause(new QueryParameters(principal(false), 0, 0),
                Set.of("Everyone", "group"));

        assertNotNull(clause);
    }

    @Test
    void restrictEffectivePermissionsSkipsAdministrators() {
        var policy = newPolicy(new ArrayList<>());
        SecurityService securityService = mock(SecurityService.class);
        List<String> effective = new ArrayList<>(List.of("Read", "Write"));
        InternalDocument doc = mock(InternalDocument.class);

        policy.restrictEffectivePermissions(effective, doc, principal(true), Set.of("group"), securityService);

        assertTrue(effective.contains("Read"));
        assertTrue(effective.contains("Write"));
    }

    @Test
    void restrictEffectivePermissionsRemovesDeniedPermissions() {
        var policy = newPolicy(new ArrayList<>());
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getAllPermissionsContainedIn("Read")).thenReturn(Set.of("Read"));
        when(securityService.getAllPermissionsContaining("Read")).thenReturn(Set.of("Read"));
        ACL acl = mock(ACL.class);
        when(acl.isEmpty()).thenReturn(false);
        when(acl.getAccess(anyCollection(), anyCollection())).thenReturn(Access.DENY);
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getEffectiveACL()).thenReturn(acl);

        List<String> effective = new ArrayList<>(List.of("Read"));
        policy.restrictEffectivePermissions(effective, doc, principal(false), Set.of("group"), securityService);

        assertFalse(effective.contains("Read"));
    }
}
