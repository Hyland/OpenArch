/*
 * (C) Copyright 2024 Hyland (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ecm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation.Type;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, MockitoFeature.class })
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core")
@Deploy("org.nuxeo.ecm.core.tests:OSGI-INF/test-security-policy-service.xml")
public class AbstractSecurityPolicyTest {

    private static final String REPO_ID = "test";

    private static final String PROJECT_ID = null;

    private static final Set<String> CONTENT_TYPES = Set.of(SYS_FILE);

    private static final Set<String> PERMISSIONS = Set.of(READ);

    private static final String MIXIN = "Mixin1";

    private static final String POLICY_NAME = "policy";

    @Mock
    private SecurityService securityService;

    @Mock
    private SecurityPolicyService securityPolicyService;

    private final List<ValidationViolation> violations = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        when(securityService.getSecurityPolicyService(REPO_ID)).thenReturn(securityPolicyService);
    }

    private void build(Set<String> contentTypes, Set<String> permissions, String mixin) {
        new SecurityPolicyServiceImpl(securityService, REPO_ID,
                Map.of(POLICY_NAME, new TestSecurityPolicyConfig(PROJECT_ID, contentTypes, permissions, mixin, false)),
                violations);
    }

    @Test
    public void build_noMixin() {
        build(CONTENT_TYPES, PERMISSIONS, null);

        assertEquals(1, violations.size());
        assertEquals("The mixin must be set on security policy \"policy\"",
                violations.get(0).getMessage(null));
    }

    @Test
    public void build_blankMixin() {
        build(CONTENT_TYPES, PERMISSIONS, " ");

        assertEquals(1, violations.size());
        assertEquals("The mixin must be set on security policy \"policy\"",
                violations.get(0).getMessage(null));
    }

    @Disabled("HXCS-6020 Cannot be checked at config time, the content model may not be ready yet")
    @Test
    public void build_invalidMixin() {
        build(CONTENT_TYPES, PERMISSIONS, "invalid");

        assertEquals(1, violations.size());
        assertEquals("Unknown mixin \"invalid\" on security policy \"policy\"",
                violations.get(0).getMessage(null));
    }

    @Test
    public void build_noContentTypes() {
        build(Set.of(), PERMISSIONS, MIXIN);

        assertEquals(1, violations.size());
        assertEquals("There are no content types set on the \"policy\" security policy",
                violations.get(0).getMessage(null));
    }

    @Test
    public void build_blankContentType() {
        build(Set.of(" "), PERMISSIONS, MIXIN);

        assertEquals(1, violations.size());
        assertEquals("An element of contentTypes on security policy \"policy\" is blank",
                violations.get(0).getMessage(null));
    }

    @Disabled("HXCS-6020 Cannot be checked at config time, the content model may not be ready yet")
    @Test
    public void build_unknownContentTypes() {
        build(Set.of("invalid1", "invalid2"), PERMISSIONS, MIXIN);

        assertEquals(2, violations.size());
        assertEquals(Set.of(
                "An element of contentTypes \"invalid1\" on security policy \"policy\" is unknown",
                "An element of contentTypes \"invalid2\" on security policy \"policy\" is unknown"),
                violations.stream().map(violation -> violation.getMessage(null)).collect(Collectors.toSet()));
        assertEquals(Set.of(Type.WARNING),
                violations.stream().map(ValidationViolation::getType).collect(Collectors.toSet()));
    }

    @Test
    public void build_noPermissions() {
        build(CONTENT_TYPES, Set.of(), MIXIN);

        assertEquals(1, violations.size());
        assertEquals("There are no permissions set on the \"policy\" security policy",
                violations.get(0).getMessage(null));
    }

    @Test
    public void build_blankPermission() {
        build(CONTENT_TYPES, Set.of(" "), MIXIN);

        assertEquals(1, violations.size());
        assertEquals("An element of permissions on security policy \"policy\" is blank",
                violations.get(0).getMessage(null));
    }

    @Test
    public void build_unknownPermissions() {
        build(CONTENT_TYPES, Set.of("invalid1", "invalid2"), MIXIN);

        assertEquals(2, violations.size());
        assertEquals(Set.of(
                "An element of permissions \"invalid1\" on security policy \"policy\" is unknown",
                "An element of permissions \"invalid2\" on security policy \"policy\" is unknown"),
                violations.stream().map(violation -> violation.getMessage(null)).collect(Collectors.toSet()));
        assertEquals(Set.of(Type.WARNING),
                violations.stream().map(ValidationViolation::getType).collect(Collectors.toSet()));
    }
}
