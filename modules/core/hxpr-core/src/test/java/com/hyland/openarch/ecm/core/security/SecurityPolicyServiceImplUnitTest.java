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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.security.config.SecurityPolicyConfig;
import com.hyland.openarch.runtime.api.Framework;

import com.hyland.openarch.config.Configurable;
import com.hyland.openarch.config.ConfigurationService;

/**
 * Pure unit tests for {@link SecurityPolicyServiceImpl}: config-map construction, the {@code hasPermission} policy
 * loop, and configurable registration (complementing the runtime-based build/applyUpdate tests).
 */
class SecurityPolicyServiceImplUnitTest {

    private final SecurityService securityService = mock(SecurityService.class);

    private final InternalDocument doc = mock(InternalDocument.class);

    private final RepositoryPrincipal principal = mock(RepositoryPrincipal.class);

    private static SecurityPolicy policy(String name) {
        SecurityPolicy policy = mock(SecurityPolicy.class);
        when(policy.getName()).thenReturn(name);
        when(policy.getConfig()).thenReturn(mock(SecurityPolicyConfig.class));
        return policy;
    }

    @Test
    void constructorExposesPoliciesAndConfigMap() {
        SecurityPolicy p1 = policy("p1");
        SecurityPolicyConfig config = p1.getConfig();
        SecurityPolicyServiceImpl service = new SecurityPolicyServiceImpl(securityService, "repo", List.of(p1));

        assertEquals(List.of(p1), service.getSecurityPolicies());
        assertEquals(Map.of("p1", config), service.getSecurityPolicyConfigMap());
    }

    @Test
    void hasPermissionTrueWhenAllApplicablePoliciesGrant() {
        when(securityService.expandPrincipal(principal)).thenReturn(Set.of("u:alice"));
        when(securityService.getAllPermissionsContaining("Read")).thenReturn(Set.of("Read"));
        SecurityPolicy p1 = policy("p1");
        when(p1.appliesToDocument(doc, principal, Set.of("Read"))).thenReturn(true);
        when(p1.hasPermission(doc, principal, Set.of("u:alice"), "Read", Set.of("Read"))).thenReturn(true);
        SecurityPolicyServiceImpl service = new SecurityPolicyServiceImpl(securityService, "repo", List.of(p1));

        assertTrue(service.hasPermission(doc, principal, "Read"));
    }

    @Test
    void hasPermissionFalseWhenAPolicyDenies() {
        when(securityService.expandPrincipal(principal)).thenReturn(Set.of("u:alice"));
        when(securityService.getAllPermissionsContaining("Read")).thenReturn(Set.of("Read"));
        SecurityPolicy p1 = policy("p1");
        when(p1.appliesToDocument(any(), any(), any())).thenReturn(true);
        when(p1.hasPermission(any(), any(), any(), any(), any())).thenReturn(false);
        SecurityPolicyServiceImpl service = new SecurityPolicyServiceImpl(securityService, "repo", List.of(p1));

        assertFalse(service.hasPermission(doc, principal, "Read"));
    }

    @Test
    void hasPermissionSkipsNonApplicablePolicies() {
        when(securityService.expandPrincipal(principal)).thenReturn(Set.of("u:alice"));
        when(securityService.getAllPermissionsContaining("Read")).thenReturn(Set.of("Read"));
        SecurityPolicy p1 = policy("p1");
        when(p1.appliesToDocument(any(), any(), any())).thenReturn(false);
        SecurityPolicyServiceImpl service = new SecurityPolicyServiceImpl(securityService, "repo", List.of(p1));

        assertTrue(service.hasPermission(doc, principal, "Read"));
        verify(p1, never()).hasPermission(any(), any(), any(), any(), any());
    }

    @Test
    void registersAndUnregistersConfigurablePolicies() {
        SecurityPolicy configurablePolicy = mock(SecurityPolicy.class,
                withSettings().extraInterfaces(Configurable.class));
        when(configurablePolicy.getName()).thenReturn("cp");
        when(configurablePolicy.getConfig()).thenReturn(mock(SecurityPolicyConfig.class));
        SecurityPolicyServiceImpl service = new SecurityPolicyServiceImpl(securityService, "repo",
                List.of(configurablePolicy));

        ConfigurationService configService = mock(ConfigurationService.class);
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(ConfigurationService.class)).thenReturn(configService);
            service.registerConfigurables();
            service.unregisterConfigurables();
        }

        verify(configService).registerConfigurable(any());
        verify(configService).unregisterConfigurable(any());
    }

    @Test
    void registerIgnoresNonConfigurablePolicies() {
        SecurityPolicy p1 = policy("p1"); // not Configurable
        SecurityPolicyServiceImpl service = new SecurityPolicyServiceImpl(securityService, "repo", List.of(p1));

        ConfigurationService configService = mock(ConfigurationService.class);
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(ConfigurationService.class)).thenReturn(configService);
            service.registerConfigurables();
        }

        verify(configService, never()).registerConfigurable(any());
    }

    @Test
    void registerIsNoOpWhenNoConfigurationService() {
        SecurityPolicy p1 = policy("p1");
        SecurityPolicyServiceImpl service = new SecurityPolicyServiceImpl(securityService, "repo", List.of(p1));

        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(ConfigurationService.class)).thenReturn(null);
            assertDoesNotThrow(service::registerConfigurables);
        }
    }
}
