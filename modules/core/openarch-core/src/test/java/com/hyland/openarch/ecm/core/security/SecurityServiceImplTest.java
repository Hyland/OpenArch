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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYONE;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;

/**
 * Unit tests for the pure principal-expansion logic of {@link SecurityServiceImpl}.
 */
class SecurityServiceImplTest {

    private final SecurityServiceImpl service = new SecurityServiceImpl();

    @Test
    void expandPrincipalIncludesUserGroupsEveryoneAndExternalMappings() {
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.getId()).thenReturn("alice");
        when(principal.getAllGroups()).thenReturn(List.of("g1", "g2"));
        when(principal.getExternalUserMappings()).thenReturn(Set.of("ext-alice"));
        when(principal.getExternalGroupMappings()).thenReturn(Set.of("ext-g1"));

        Set<String> principals = service.expandPrincipal(principal);

        assertTrue(principals.contains("u:alice"));
        assertTrue(principals.contains(EVERYONE));
        assertTrue(principals.contains("g:g1"));
        assertTrue(principals.contains("g:g2"));
        assertTrue(principals.contains("u:ext-alice"));
        assertTrue(principals.contains("g:ext-g1"));
        assertEquals(6, principals.size());
    }

    @Test
    void expandPrincipalWithNoGroupsOrMappings() {
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.getId()).thenReturn("bob");
        when(principal.getAllGroups()).thenReturn(List.of());
        when(principal.getExternalUserMappings()).thenReturn(Set.of());
        when(principal.getExternalGroupMappings()).thenReturn(Set.of());

        Set<String> principals = service.expandPrincipal(principal);

        assertEquals(Set.of("u:bob", EVERYONE), principals);
    }
}
