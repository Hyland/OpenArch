/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.core.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.security.Access.GRANT;
import static org.nuxeo.ecm.core.api.security.Access.UNKNOWN;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ_WRITE;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, MockitoFeature.class })
public class TestACL {

    @Mock
    @RuntimeService
    protected AdministratorGroupsProvider administratorGroupsProvider;

    private ACL acl;

    @BeforeEach
    public void doBefore() {
        when(administratorGroupsProvider.getAdministratorsGroups()).thenReturn(
                Collections.singletonList("theAdministrators"));
    }

    @BeforeEach
    public void setUp() {
        acl = new ACLImpl();
    }

    @AfterEach
    public void tearDown() {
        acl = null;
    }

    private Access getAccess(ACL acl, String principal, String permission) {
        return acl.getAccess(List.of(principal), Set.of(permission));
    }

    @Test
    public void testCheckAccess() {
        acl.add(ACE.builder().user("joe").permission(EVERYTHING).build());

        assertSame(GRANT, getAccess(acl, "u:joe", READ));
        assertSame(UNKNOWN, getAccess(acl, "u:jack", READ));
    }

    @Test
    public void testImmediate() {
        ACL createdAcl = acl;
        createdAcl.add(ACE.builder().user("john").permission("Sing").build());
        createdAcl.add(ACE.builder().user("anne").permission("Joke").isGranted(false).build());

        // check that the ACL has already been affected by editing
        assertTrue(getAccess(acl, "u:john", "Sing").toBoolean());
        assertFalse(getAccess(acl, "u:anne", "Joke").toBoolean());
    }

    @Test
    public void itCanAddExistingPermission() {
        // Given an ACL
        ACL acl = getInheritedReadWriteACL();
        // When i set Permission to a user
        acl.add(ACE.builder().user("john").permission(READ_WRITE).creator("john").build());

        // Then he still have access and local ACL have an entry
        assertEquals(Access.GRANT, getAccess(acl, "u:john", READ_WRITE));

        assertEquals(2, acl.size());
        assertEquals(ACE.builder().user("john").permission(READ_WRITE).creator("john").build(), acl.get(1));

    }

    @Test
    public void itCanAddPermission() {
        // Given an ACL
        ACL acl = getInheritedReadWriteACL();

        assertEquals(Access.UNKNOWN, getAccess(acl, "u:john", "comment"));

        // When i set Permission to a user
        acl.add(ACE.builder().user("john").permission("comment").creator("john").build());

        // Then he still have access and local ACL have an entry
        assertEquals(Access.GRANT, getAccess(acl, "u:john", "comment"));

        assertEquals(2, acl.size());
        assertEquals(ACE.builder().user("john").permission("comment").creator("john").build(), acl.get(1));
    }

    @Test
    public void itShouldNotAddPermissionTwice() {
        // Given an ACL
        ACL acl = getInheritedReadWriteACL();

        // When i set Permission to a user
        boolean hasChanged = acl.add(ACE.builder().user("john").permission(READ_WRITE).build());
        assertTrue(hasChanged);
        // When i call the operation another time
        hasChanged = acl.add(ACE.builder().user("john").permission(READ_WRITE).build());
        // Then nothing should change
        assertFalse(hasChanged);
        assertEquals(2, acl.size());
    }

    @Test
    public void itCanBlockInheritance() {
        // Given an ACL
        ACL acl = getInheritedReadWriteACL();

        // When i add the permission to a user with blocking inheritance
        acl.blockInheritance("johnId");
        // XXXX
        // acl.add(ACE.builder().user("johnId").permission(READ_WRITE).creator("johnId").build());

        // Then he still have access and local ACL have an entry
        assertEquals(Access.GRANT, getAccess(acl, "u:johnId", READ_WRITE));

        // Other user don't have access anymore
        assertEquals(Access.DENY, getAccess(acl, "u:jackId", READ_WRITE));

        // Administrators still have access
        assertEquals(Access.GRANT, getAccess(acl, "g:theAdministrators", READ_WRITE));

        // unblock the inheritance
        acl.unblockInheritance();
        assertEquals(Access.GRANT, getAccess(acl, "johnId", READ_WRITE));
        assertEquals(Access.GRANT, getAccess(acl, "jackId", READ_WRITE));
    }

    @Test
    public void itShouldAddInheritanceEvenIfItAlreadyHasPermission() {
        // Given an ACL
        ACL acl = getInheritedReadWriteACL();
        acl.add(ACE.builder().user("john").permission(READ_WRITE).build());

        // When i add the permission to a user with blocking inheritance
        acl.blockInheritance("john");
        acl.add(ACE.builder().user("john").permission(READ_WRITE).creator("john").build());

        // Then he still have access and local ACL have an entry
        assertEquals(Access.GRANT, getAccess(acl, "u:john", READ_WRITE));
    }

    @Test
    public void itCanRemovePermissionsToAUser() {
        // Given an ACL
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("john").permission(READ_WRITE).build());
        acl.add(ACE.builder().user("jack").permission(READ_WRITE).build());
        acl.add(ACE.builder().user("jack").permission("comment").build());
        acl.add(ACE.builder().user("jerry").permission(READ_WRITE).build());

        assertEquals(Access.GRANT, getAccess(acl, "u:jack", READ_WRITE));
        assertEquals(4, acl.size());

        // When i call the removePermission operation
        boolean hasChanged = acl.removeByPrincipal("u:jack");

        // Then the user doesn't have any permission anymore
        assertTrue(hasChanged);
        assertEquals(Access.UNKNOWN, getAccess(acl, "u:jack", READ_WRITE));
        assertEquals(Access.UNKNOWN, getAccess(acl, "u:jack", "comment"));
        assertEquals(2, acl.size());
    }

    @Test
    public void itDoesNotChangeSecurityWhenRemovingNonExistentUser() {
        // Given an ACL
        ACL acl = new ACLImpl();

        assertEquals(Access.UNKNOWN, getAccess(acl, "u:jack", READ_WRITE));

        // When i call the removePermission operation
        boolean hasChanged = acl.removeByPrincipal("u:jack");

        // Then the user doesn't have any permission anymore
        assertFalse(hasChanged);
        assertEquals(Access.UNKNOWN, getAccess(acl, "u:jack", READ_WRITE));
    }

    @Test
    public void itCanRemovePermissionGivenItsId() {
        // Given an ACL
        ACL acl = new ACLImpl();
        String jack = "jack";
        acl.add(ACE.builder().user("john").permission(READ_WRITE).build());
        acl.add(ACE.builder().user(jack).permission(READ_WRITE).build());
        acl.add(ACE.builder().user(jack).permission("comment").build());
        ACE ace = ACE.builder().user("jerry").permission(READ_WRITE).build();
        acl.add(ace);

        assertEquals(Access.GRANT, getAccess(acl, "u:jack", READ_WRITE));
        assertEquals(Access.GRANT, getAccess(acl, "u:jerry", READ_WRITE));
        assertEquals(4, acl.size());

        // When i call the removePermission operation
        boolean hasChanged = acl.remove(ace);

        // Then the user doesn't have any permission anymore
        assertTrue(hasChanged);
        assertEquals(Access.GRANT, getAccess(acl, "u:jack", READ_WRITE));
        assertEquals(Access.GRANT, getAccess(acl, "u:jack", "comment"));
        assertEquals(Access.UNKNOWN, getAccess(acl, "u:jerry", READ_WRITE));
        assertEquals(3, acl.size());
    }

    @Test
    public void testMultipleNewPermissionsWithBlockInheritance() {
        // Given an ACL
        ACL acl = getInheritedReadWriteACL();

        // Make a first call to fill cache
        assertEquals(Access.UNKNOWN, getAccess(acl, "john", "comment"));

        // When i set Permission to a user with inheritance block
        acl.blockInheritance("john");
        acl.add(ACE.builder().user("john").permission(READ_WRITE).creator("john").build());

        // only john have Permission
        assertEquals(Access.GRANT, getAccess(acl, "u:john", READ_WRITE));
        assertEquals(Access.DENY, getAccess(acl, "u:jack", READ_WRITE));
        assertEquals(Access.DENY, getAccess(acl, "u:jerry", READ_WRITE));

        acl.add(ACE.builder().user("jack").permission(READ_WRITE).creator("john").build());
        acl.add(ACE.builder().user("jerry").permission(READ_WRITE).creator("john").build());

        // Check jack and jerry have permission, even with inheritance block
        assertEquals(Access.GRANT, getAccess(acl, "u:john", READ_WRITE));
        assertEquals(Access.GRANT, getAccess(acl, "u:jack", READ_WRITE));
        assertEquals(Access.GRANT, getAccess(acl, "u:jerry", READ_WRITE));
    }

    private ACL getInheritedReadWriteACL() {
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().principal(SecurityConstants.EVERYONE).permission(SecurityConstants.READ_WRITE).build());
        return acl;
    }

}
