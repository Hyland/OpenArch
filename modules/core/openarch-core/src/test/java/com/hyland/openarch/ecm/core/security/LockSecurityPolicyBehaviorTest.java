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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.config.LockSecurityPolicyConfig;
import com.hyland.openarch.runtime.api.Framework;

class LockSecurityPolicyBehaviorTest {

    private static final String REPO = "repo";

    /** Stubs the security service so the {@code lock} config (which manages the WRITE permission) can be built. */
    private static void stubSecurityService(MockedStatic<Framework> framework) {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getAllPermissions()).thenReturn(Set.of(WRITE));
        when(securityService.getAllPermissionsContainedIn(WRITE)).thenReturn(Set.of(WRITE));
        framework.when(() -> Framework.getService(SecurityService.class)).thenReturn(securityService);
    }

    private static LockSecurityPolicy newPolicy(String name, List<ValidationViolation> violations) {
        return new LockSecurityPolicy(REPO, name, new LockSecurityPolicyConfig(), violations);
    }

    private static RepositoryPrincipal principal(String id, boolean administrator) {
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.getId()).thenReturn(id);
        when(principal.isAdministrator()).thenReturn(administrator);
        return principal;
    }

    @Test
    void validConfigProducesNoViolations() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubSecurityService(framework);
            List<ValidationViolation> violations = new ArrayList<>();

            newPolicy("lock", violations);

            assertTrue(violations.isEmpty());
        }
    }

    @Test
    void wrongPolicyNameProducesViolation() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubSecurityService(framework);
            List<ValidationViolation> violations = new ArrayList<>();

            newPolicy("not-lock", violations);

            assertFalse(violations.isEmpty());
        }
    }

    @Test
    void isHiddenAndGlobal() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubSecurityService(framework);
            var policy = newPolicy("lock", new ArrayList<>());

            assertTrue(policy.isHidden());
            assertTrue(policy.isGlobal());
        }
    }

    @Test
    void appliesToDocumentForWriteWhenNotIgnored() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubSecurityService(framework);
            var policy = newPolicy("lock", new ArrayList<>());
            InternalDocument doc = mock(InternalDocument.class);

            // administrators bypass
            assertFalse(policy.appliesToDocument(doc, principal("admin", true), Set.of(WRITE)));
            // non-write requests are not restricted
            assertFalse(policy.appliesToDocument(doc, principal("bob", false), Set.of("Read")));
            // write requests are restricted
            assertTrue(policy.appliesToDocument(doc, principal("bob", false), Set.of(WRITE)));

            // ... unless the lock policy is explicitly ignored on this thread
            try {
                LockSecurityPolicy.setIgnoreLockPolicy(true);
                assertFalse(policy.appliesToDocument(doc, principal("bob", false), Set.of(WRITE)));
            } finally {
                LockSecurityPolicy.setIgnoreLockPolicy(false);
            }
        }
    }

    @Test
    void hasPermissionAllowsUnlockedOrOwnLock() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubSecurityService(framework);
            var policy = newPolicy("lock", new ArrayList<>());
            RepositoryPrincipal bob = principal("bob", false);

            // no lock -> allowed
            InternalDocument unlocked = mock(InternalDocument.class);
            when(unlocked.getLock()).thenReturn(null);
            assertTrue(policy.hasPermission(unlocked, bob, Set.of(), WRITE, Set.of(WRITE)));

            // locked by the same user -> allowed
            InternalDocument ownLock = mock(InternalDocument.class);
            when(ownLock.getLock()).thenReturn(new Lock("bob", Instant.now()));
            assertTrue(policy.hasPermission(ownLock, bob, Set.of(), WRITE, Set.of(WRITE)));

            // locked by another user -> denied
            InternalDocument otherLock = mock(InternalDocument.class);
            when(otherLock.getLock()).thenReturn(new Lock("alice", Instant.now()));
            assertFalse(policy.hasPermission(otherLock, bob, Set.of(), WRITE, Set.of(WRITE)));
        }
    }

    @Test
    void getQueryClauseIsNull() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubSecurityService(framework);
            var policy = newPolicy("lock", new ArrayList<>());

            assertNull(policy.getQueryClause(new QueryParameters(principal("bob", false), 0, 0), Set.of()));
        }
    }

    @Test
    void restrictEffectivePermissionsSkipsAdministrators() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            stubSecurityService(framework);
            var policy = newPolicy("lock", new ArrayList<>());
            InternalDocument doc = mock(InternalDocument.class);
            List<String> effective = new ArrayList<>(List.of(WRITE));

            policy.restrictEffectivePermissions(effective, doc, principal("admin", true), Set.of(), mock(SecurityService.class));

            assertTrue(effective.contains(WRITE));
        }
    }
}
