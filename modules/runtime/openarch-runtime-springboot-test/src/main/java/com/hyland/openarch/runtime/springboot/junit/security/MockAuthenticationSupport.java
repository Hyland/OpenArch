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
package com.hyland.openarch.runtime.springboot.junit.security;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.api.local.WithUser;
import com.hyland.openarch.ecm.platform.usermanager.UserManager;
import com.hyland.openarch.runtime.api.Framework;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sets up Spring Security context for tests that use mock or {@link UserManager} implementations.
 * <p>
 * When {@link WithUser} is present, this support populates the {@link SecurityContextHolder} with a
 * {@link TestingAuthenticationToken} whose principal name is the user ID from the annotation (UUID). If the user is
 * {@link DummyLoginFeature#TEST_ANONYMOUS_USER}, the context is cleared so that requests fail with 401.
 */
public final class MockAuthenticationSupport {

    private MockAuthenticationSupport() {
        // utility
    }

    /**
     * Authenticates the current thread for the given {@link WithUser} annotation.
     * <p>
     * If {@code withUser} is {@code null} or its {@link WithUser#value()} is not
     * {@link DummyLoginFeature#TEST_ANONYMOUS_USER}, an authenticated token is placed in the
     * {@link SecurityContextHolder}. Otherwise the context is cleared, causing subsequent requests to receive a 401.
     */
    public static void authenticate(WithUser withUser) {
        if (DummyLoginFeature.TEST_ANONYMOUS_USER.equals(resolveUsername(withUser))) {
            SecurityContextHolder.clearContext();
            return;
        }
        Authentication auth = new TestingAuthenticationToken(resolveUserId(withUser), null, List.of());
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Clears the Spring Security context after a test.
     */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    private static String resolveUsername(WithUser withUser) {
        if (withUser == null || StringUtils.isBlank(withUser.value())) {
            return DummyLoginFeature.TEST_ADMIN_USER_NAME;
        }
        return withUser.value();
    }

    private static String resolveUserId(WithUser withUser) {
        if (withUser == null || StringUtils.isBlank(withUser.id())) {
            return DummyLoginFeature.TEST_ADMIN_USER;
        }
        String id = withUser.id();
        // If only the username was customised (id is still the annotation default), look up the
        // actual UUID via the UserManager so that the authentication token carries the right principal.
        if (DummyLoginFeature.TEST_ADMIN_USER.equals(id)
                && !StringUtils.isBlank(withUser.value())
                && !DummyLoginFeature.TEST_ADMIN_USER_NAME.equals(withUser.value())) {
            UserManager userManager = Framework.getService(UserManager.class);
            if (userManager != null) {
                List<RepositoryPrincipal> found = userManager.searchPrincipalsByName(withUser.value());
                if (!found.isEmpty()) {
                    return found.getFirst().getId();
                }
            }
            // Fallback: use the username value as-is as the principal identifier
            return withUser.value();
        }
        return id;
    }
}
