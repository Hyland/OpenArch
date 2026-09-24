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
package org.nuxeo.rest.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
public class RolesAllowedInterceptorUnitTest {

    @Mock
    private RepositoryPrincipal currentUser;

    @Spy
    private RolesAllowedInterceptor rolesAllowedInterceptor;

    @BeforeEach
    public void setup() {
        when(rolesAllowedInterceptor.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    public void currentUserHasRequiredRole() {
        when(currentUser.isMemberOf("admin")).thenReturn(true);
        List<String> rolesAllowed = List.of("admin");

        rolesAllowedInterceptor.checkRolesAllowed(rolesAllowed);
    }

    @Test
    public void currentUserDoesNotHaveRequiredRole() {
        when(currentUser.isMemberOf("admin")).thenReturn(false);
        List<String> rolesAllowed = List.of("admin");

        assertThrows(AccessDeniedException.class, () -> rolesAllowedInterceptor.checkRolesAllowed(rolesAllowed));
    }

    @Test
    public void currentUserHasOneOfManyRequiredRoles() {
        when(currentUser.isMemberOf("admin")).thenReturn(false);
        when(currentUser.isMemberOf("user")).thenReturn(true);
        List<String> rolesAllowed = List.of("admin", "user");

        rolesAllowedInterceptor.checkRolesAllowed(rolesAllowed);
    }

    @Test
    public void currentUserDoesNotHaveAnyOfManyRequiredRoles() {
        when(currentUser.isMemberOf("admin")).thenReturn(false);
        when(currentUser.isMemberOf("user")).thenReturn(false);
        List<String> rolesAllowed = List.of("admin", "user");

        assertThrows(AccessDeniedException.class, () -> rolesAllowedInterceptor.checkRolesAllowed(rolesAllowed));
    }

}
