/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.CREATE_CHILD;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.CREATE_VERSION;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.DELETE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.DELETE_CHILD;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYONE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ_VERSION;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ_WRITE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE_VERSION;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import com.hyland.openarch.ecm.core.api.impl.UserPrincipal;
import com.hyland.openarch.ecm.core.api.security.UserVisiblePermission;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

/**
 * @author <a href="mailto:gracinet@nuxeo.com">Georges Racinet</a>
 * @author <a href="mailto:ogrisel@nuxeo.com">Olivier Grisel</a>
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core:OSGI-INF/SecurityService.xml")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/permissions-contrib.xml")
public class TestSecurityService {

    private static final String CUSTOM_COMPOUND_PERM = "CustomCompoundPerm";

    private static final String CUSTOM_ATOMIC_PERM = "CustomAtomicPerm";

    @Inject
    private SecurityService service;

    @Test
    public void testDefaultPermissions() {
        Set<String> permissions = service.getAllPermissionsContaining(READ);
        assertEquals(Set.of(READ, READ_WRITE, EVERYTHING), permissions);
    }

    protected List<String> permStrings(List<UserVisiblePermission> perms) {
        return perms.stream().map(UserVisiblePermission::getPermission).toList();
    }

    @Test
    // deploy a new atomic permission and a new compound permission
    @Deploy("org.nuxeo.ecm.core.tests:permissions-override1-contrib.xml")
    public void testOverridedPermissions1() {
        // test how previous permissions where affected by the override
        Set<String> permissions = service.getAllPermissionsContaining(READ);
        assertEquals(Set.of(READ, CUSTOM_COMPOUND_PERM, READ_WRITE, EVERYTHING), permissions);

        // test the new permissions
        permissions = service.getAllPermissionsContaining(CUSTOM_COMPOUND_PERM);
        assertEquals(Set.of(CUSTOM_COMPOUND_PERM, EVERYTHING), permissions);

        permissions = service.getAllPermissionsContaining(CUSTOM_ATOMIC_PERM);
        assertEquals(Set.of(CUSTOM_ATOMIC_PERM, CUSTOM_COMPOUND_PERM, EVERYTHING), permissions);
    }

    @Test
    // deploy a new atomic permission and a new compound permission
    @Deploy("org.nuxeo.ecm.core.tests:permissions-override2-contrib.xml")
    public void testOverriddenPermissions2() {
        // check default permissions where not affected by the override
        testDefaultPermissions();

        // test the new permission
        Set<String> permissions = service.getAllPermissionsContaining(CUSTOM_ATOMIC_PERM);
        assertEquals(Set.of(CUSTOM_ATOMIC_PERM, READ_WRITE, WRITE, EVERYTHING), permissions);
    }

    @Test
    public void testGetPrincipalsToCheck() {
        NuxeoPrincipal principal = new UserPrincipal("bob", List.of("vps", "males"), false);
        Set<String> principals = service.expandPrincipal(principal);
        assertEquals(Set.of("u:bob", "g:vps", "g:males", EVERYONE), principals);
    }

    @Test
    public void testAllPermissionsContainedIn() {
        Set<String> permissions = service.getAllPermissionsContainedIn(READ);
        assertEquals(Set.of(READ), permissions);
        permissions = service.getAllPermissionsContainedIn(READ_WRITE);
        assertEquals(Set.of(WRITE, WRITE_VERSION, READ, READ_VERSION, DELETE, CREATE_VERSION, DELETE_CHILD, READ_WRITE,
                CREATE_CHILD), permissions);
    }

}
