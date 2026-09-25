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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.config.RetentionSecurityPolicyConfig;
import com.hyland.openarch.runtime.mockito.MockitoFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.DELETE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ_WRITE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.RECORDS_CLEANER_GROUP;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE;


@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, MockitoFeature.class })
@Deploy("com.hyland.openarch.ecm.core.schema")
@Deploy("com.hyland.openarch.ecm.core")
public class RetentionSecurityPolicyTest {

    private static final String REPO_ID = "test";

    private static final String POLICY_NAME = "test";

    @Mock
    private SecurityService securityService;

    @Mock
    private SecurityPolicyService securityPolicyService;

    private RetentionSecurityPolicy retentionSecurityPolicy;

    private final List<ValidationViolation> violations = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        when(securityService.getSecurityPolicyService(REPO_ID)).thenReturn(securityPolicyService);
        retentionSecurityPolicy = new RetentionSecurityPolicy(REPO_ID, new RetentionSecurityPolicyConfig());
    }

    private void build() {
        new SecurityPolicyServiceImpl(securityService, REPO_ID,
                Map.of(POLICY_NAME, new RetentionSecurityPolicyConfig()), violations);
    }

    @Test
    public void build_testRetentionPolicyName() {
        build();
        assertEquals(1, violations.size());
    }

    @Test
    public void testAllowRead() {
        InternalDocument doc = mock(InternalDocument.class);
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.isMemberOf(RECORDS_CLEANER_GROUP)).thenReturn(false);
        boolean hasPermission = retentionSecurityPolicy.hasPermission(doc, principal, Set.of(),
                READ, Set.of(READ, EVERYTHING, READ_WRITE));
        assertTrue(hasPermission);
    }

    @Test
    public void testDisallowDelete() {
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.isUnderRetentionOrLegalHold()).thenReturn(true);
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.isMemberOf(RECORDS_CLEANER_GROUP)).thenReturn(false);
        boolean hasPermission = retentionSecurityPolicy.hasPermission(doc, principal, Set.of(),
                DELETE, Set.of(DELETE, EVERYTHING, READ_WRITE));
        assertFalse(hasPermission);
    }

    @Test
    public void testDisallowWrite() {
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.isUnderRetentionOrLegalHold()).thenReturn(true);
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        when(principal.isMemberOf(RECORDS_CLEANER_GROUP)).thenReturn(false);
        boolean hasPermission = retentionSecurityPolicy.hasPermission(doc, principal, Set.of(),
                WRITE, Set.of(WRITE, EVERYTHING, READ_WRITE));
        assertFalse(hasPermission);
    }

}
