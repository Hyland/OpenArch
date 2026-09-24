/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.CREATE_CHILD;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.CREATE_VERSION;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.MANAGE_LOCK;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.MANAGE_RETENTION;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.MANAGE_SECURITY;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.impl.UserPrincipal;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.api.security.impl.ACLImpl;
import com.hyland.openarch.ecm.core.security.config.SecurityLevelSecurityPolicyConfig;
import com.hyland.openarch.ecm.core.security.config.SecurityPolicyConfig;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-security-level-security-policy.xml")
public class TestSecurityPoliciesProperty {

    private static final String REPOSITORY = "test";

    private static final String POLICY_NAME = "ClearanceSecurityLevel";

    private static final String TYPE_NAME = "Contract";

    private static final String POLICY_MIXIN = "Clearance";

    private static final String POLICY_PROPERTY = "clearance_level";

    private static final String POLICY_USER_ATTRIBUTE = "clearance";

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    @Inject
    protected SecurityService securityService;

    @BeforeEach
    public void beforeClass() {
        // default config
        var config = securityService.getSecurityPolicyService(REPOSITORY).getSecurityPolicyConfigMap();

        // add custom security policy config
        SecurityPolicyConfig custom = new SecurityLevelSecurityPolicyConfig(null, Set.of(TYPE_NAME), Set.of(EVERYTHING),
                POLICY_MIXIN, POLICY_USER_ATTRIBUTE);
        config.put(POLICY_NAME, custom);

        securityService.setSecurityPolicies(REPOSITORY, config);
    }

    @Test
    public void testErrors() {
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.create();

        // cannot set an invalid security policy
        assertThrows(IllegalArgumentException.class, () -> {
            file.setPropertyValue(SYS_SECURITY_POLICIES, List.of("invalid_name"));
        });
    }

    private CoreSession getSessionWithRootPermissions(String userId, String... permissions) {
        ACL acl = new ACLImpl();
        for (String permission : permissions) {
            acl.add(ACE.builder().user(userId).permission(permission).build());
        }
        session.getDocument(new PathRef("/")).setACL(acl, false);
        NuxeoPrincipal principal = new UserPrincipal(userId);
        return CoreInstance.getCoreSession(REPOSITORY, principal);
    }

    @Test
    public void testAsAuthorizedUser() {
        // give rights to alice and bob on root
        CoreSession aliceSession = getSessionWithRootPermissions("alice", READ, WRITE, CREATE_CHILD, MANAGE_SECURITY);
        CoreSession bobSession = getSessionWithRootPermissions("bob", READ);

        DocumentModel file = aliceSession.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        // alice and bob have access
        assertTrue(aliceSession.hasPermission(file.getRef(), READ));
        assertTrue(bobSession.hasPermission(file.getRef(), READ));

        // file doesn't have the mixin
        assertFalse(file.hasMixin(POLICY_MIXIN));
        // set the security policy
        file.setPropertyValue(SYS_SECURITY_POLICIES, List.of(POLICY_NAME));
        // the mixin is now set
        assertTrue(file.hasMixin(POLICY_MIXIN));

        // because of the security policy, bob doesn't have permissions on the doc anymore
        assertFalse(bobSession.hasPermission(file.getRef(), READ));
        // because alice is the creator of the document, she still has permissions on the doc
        assertTrue(aliceSession.hasPermission(file.getRef(), READ));

        // now remove the security policy (as admin because alice can't)
        file = session.getDocument(file.getRef());
        file.setPropertyValue(SYS_SECURITY_POLICIES, List.of());
        // the mixin is now gone
        assertFalse(file.hasMixin(POLICY_MIXIN));
    }

    @Test
    public void testAutomaticAssociation() {
        // give rights to alice on root
        CoreSession aliceSession = getSessionWithRootPermissions("alice", READ, WRITE, CREATE_CHILD, MANAGE_SECURITY);
        CoreSession bobSession = getSessionWithRootPermissions("bob", READ);
        DocumentModel file = aliceSession.createDocumentModel("/", "contract", TYPE_NAME);
        file.create();
        aliceSession.save();
        // because of the security policy, bob doesn't have permissions on the doc
        assertFalse(bobSession.hasPermission(file.getRef(), READ));
    }

    @Test
    public void testAsUnauthorizedUser() {
        // give rights to alice on root, but not ManageSecurity
        CoreSession aliceSession = getSessionWithRootPermissions("alice", READ, WRITE, CREATE_CHILD);

        DocumentModel file = aliceSession.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        // cannot set the security policy
        List<String> policies = List.of(POLICY_NAME);
        assertThrows(DocumentSecurityException.class, () -> {
            file.setPropertyValue(SYS_SECURITY_POLICIES, policies);
        });
    }

    @Test
    public void testSysACLModificationAsAuthorizedUser() {
        // give rights to alice on root
        CoreSession aliceSession = getSessionWithRootPermissions("alice", READ, WRITE, CREATE_CHILD, MANAGE_SECURITY);

        DocumentModel file = aliceSession.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        ACL acl = file.getLocalACL();

        // alice can modify sys_acl (via DocumentModel.setACL)
        acl.add(ACE.builder().user("alice").permission(CREATE_VERSION).build());
        file.setACL(acl, false);
        assertTrue(aliceSession.hasPermission(file.getRef(), CREATE_VERSION));

        // alice can modify sys_acl (via DocumentModel.setPropertyValue)
        acl.add(ACE.builder().user("alice").permission(MANAGE_RETENTION).build());
        file.setPropertyValue(SYS_ACL, acl);
        // alice will not have the permission after the call to setPropertyValue

        // alice can modify sys_acl through another DocumentModel.setACL call
        acl.add(ACE.builder().user("alice").permission(MANAGE_LOCK).build());
        file.setACL(acl, false);
        assertTrue(aliceSession.hasPermission(file.getRef(), MANAGE_LOCK));
    }

    @Test
    public void testSysACLModificationAsUnauthorizedUser() {
        // give rights to alice on root, but not ManageSecurity
        CoreSession aliceSession = getSessionWithRootPermissions("alice", READ, WRITE, CREATE_CHILD);

        DocumentModel file = aliceSession.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        assertThrows(DocumentSecurityException.class, file::getLocalACL,
                "Privilege 'ManageSecurity' is not granted to 'alice'");
        ACL acl = new ACLImpl();

        // alice cannot modify sys_acl (via DocumentModel.setACL)
        acl.add(ACE.builder().user("alice").permission(CREATE_VERSION).build());
        assertThrows(DocumentSecurityException.class, () -> file.setACL(acl, false),
                "Privilege 'ManageSecurity' is not granted to 'alice'");

        // alice cannot modify sys_acl (via DocumentModel.setPropertyValue)
        assertThrows(DocumentSecurityException.class, () -> file.setPropertyValue(SYS_ACL, acl),
                "Property update is not allowed: sys_acl");

        // alice cannot modify sys_acl through DocumentModel.setACL
        assertThrows(DocumentSecurityException.class, () -> file.setACL(acl, false),
                "Privilege 'ManageSecurity' is not granted to 'alice'");
    }

    @Test
    public void testHasPermissionAndEffectivePermissions() {
        // for this test we want a security policy that just manages Write
        var config = securityService.getSecurityPolicyService(REPOSITORY).getSecurityPolicyConfigMap();
        SecurityPolicyConfig custom = new SecurityLevelSecurityPolicyConfig(null, Set.of(TYPE_NAME), Set.of(WRITE),
                POLICY_MIXIN, POLICY_USER_ATTRIBUTE);
        config.put(POLICY_NAME, custom);
        securityService.setSecurityPolicies(REPOSITORY, config);

        // give enough rights to alice on root to create the doc
        CoreSession aliceSession = getSessionWithRootPermissions("alice", READ, WRITE, CREATE_CHILD, MANAGE_SECURITY);
        DocumentModel aliceFile = aliceSession.createDocumentModel("/", "contract", TYPE_NAME);
        aliceFile.create();
        aliceFile.setPropertyValue(POLICY_PROPERTY, 100L); // doc has high level, 100
        aliceSession.save();
        DocumentRef ref = aliceFile.getRef();

        // bob has basic Read/Write access through ACL
        CoreSession bobSession = getSessionWithRootPermissions("bob", READ, WRITE);
        // bob is a user with low security clearance (no access to docs level > 10)
        bobSession.getPrincipal().setProperties(Map.of(POLICY_USER_ATTRIBUTE, 10L));
        DocumentModel bobFile = bobSession.getDocument(ref);

        // because of the security policy, bob loses Write permission on the doc (but still has Read)
        assertTrue(bobSession.hasPermission(ref, READ));
        assertFalse(bobSession.hasPermission(ref, WRITE));
        assertEquals(Set.of(READ), bobFile.getEffectivePermissions());

        // if the doc gets deassociated from the security policy, then Write is restored
        aliceFile.setPropertyValue(SYS_SECURITY_POLICIES, null);
        // keep data that would prevent access if the security policy was active
        aliceFile.setMixins(Set.of(POLICY_MIXIN));
        aliceFile.setPropertyValue(POLICY_PROPERTY, 100L);
        aliceSession.save();
        // security policy is not taken into account because it's been removed
        assertTrue(bobSession.hasPermission(ref, READ));
        assertTrue(bobSession.hasPermission(ref, WRITE));
        assertEquals(Set.of(READ, WRITE), bobFile.getEffectivePermissions());
    }

    /**
     * Test setting/removing multiple policies and the impact it has on the mixins.
     * <p>
     * Importantly, this tests the case where different policies may use the same mixin.
     */
    @Test
    public void testSetSecurityPolicies() {
        List<String> oldPolicies;
        List<String> newPolicies;
        Set<String> oldMixins;
        Map<String, String> policyMixins;
        Set<String> newMixins;

        // adding policies from scratch
        oldPolicies = List.of();
        newPolicies = List.of("a", "b", "c");
        oldMixins = Set.of("MyMixin"); // MyMixin is a user-managed mixin independent from security policies
        policyMixins = Map.of("a", "AAA", "b", "BBB", "c", "AAA"); // "c" has same mixin as "a"
        newMixins = SecurityPolicyServiceImpl.computeNewMixins(oldPolicies, newPolicies, oldMixins, policyMixins);
        assertEquals(Set.of("MyMixin", "AAA", "BBB"), newMixins);

        // remove all policies
        oldPolicies = List.of("a", "b", "c");
        newPolicies = List.of();
        oldMixins = Set.of("MyMixin", "AAA", "BBB");
        policyMixins = Map.of("a", "AAA", "b", "BBB", "c", "AAA"); // "c" has same mixin as "a"
        newMixins = SecurityPolicyServiceImpl.computeNewMixins(oldPolicies, newPolicies, oldMixins, policyMixins);
        assertEquals(Set.of("MyMixin"), newMixins);

        // simple case with no two policies having the same mixin
        oldPolicies = List.of("a", "b");
        newPolicies = List.of("b", "c");
        oldMixins = Set.of("MyMixin", "AAA", "BBB");
        policyMixins = Map.of("a", "AAA", "b", "BBB", "c", "CCC");
        newMixins = SecurityPolicyServiceImpl.computeNewMixins(oldPolicies, newPolicies, oldMixins, policyMixins);
        assertEquals(Set.of("MyMixin", "BBB", "CCC"), newMixins);

        // case where the added and removed policies have the same mixin
        oldPolicies = List.of("a", "b");
        newPolicies = List.of("b", "c");
        oldMixins = Set.of("MyMixin", "AAA", "BBB");
        policyMixins = Map.of("a", "AAA", "b", "BBB", "c", "AAA"); // "c" has same mixin as "a"
        newMixins = SecurityPolicyServiceImpl.computeNewMixins(oldPolicies, newPolicies, oldMixins, policyMixins);
        assertEquals(Set.of("MyMixin", "AAA", "BBB"), newMixins);
    }

}
