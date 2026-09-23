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
package org.nuxeo.ecm.core.security;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation.Type;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import com.hyland.openarch.runtime.mockito.MockitoFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, MockitoFeature.class })
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core")
@Deploy("org.nuxeo.ecm.core.tests:OSGI-INF/test-security-policy-service.xml")
public class SecurityPolicyServiceImplTest {

    private static final String REPO_ID = "test";

    private static final String PROJECT_ID = null;

    private static final Set<String> CONTENT_TYPES_A = Set.of("ContentA");

    private static final Set<String> CONTENT_TYPES_B = Set.of("ContentB");

    private static final Set<String> CONTENT_TYPES_C = Set.of("ContentC");

    private static final Set<String> CONTENT_TYPES_D = Set.of("ContentD");

    private static final Set<String> CONTENT_TYPES_E = Set.of("ContentE");

    private static final Set<String> PERMISSIONS_A = Set.of(READ);

    private static final String MIXIN_1 = "Mixin1";

    private static final String MIXIN_2 = "Mixin2";

    private static final String MIXIN_3 = "Mixin3";

    private static final String MIXIN_4 = "Mixin4";

    private static final TestSecurityPolicyConfig POLICY_1 = new TestSecurityPolicyConfig(PROJECT_ID, CONTENT_TYPES_A, PERMISSIONS_A, MIXIN_1, true);

    private static final TestSecurityPolicyConfig POLICY_2 = new TestSecurityPolicyConfig(PROJECT_ID, CONTENT_TYPES_B, PERMISSIONS_A, MIXIN_2, false);

    private static final TestSecurityPolicyConfig POLICY_3 = new TestSecurityPolicyConfig(PROJECT_ID, CONTENT_TYPES_C, PERMISSIONS_A, MIXIN_3, false);

    private static final TestSecurityPolicyConfig POLICY_4 = new TestSecurityPolicyConfig(PROJECT_ID, CONTENT_TYPES_D, PERMISSIONS_A, MIXIN_4, false);

    private static final TestSecurityPolicyConfig POLICY_5 = new TestSecurityPolicyConfig(PROJECT_ID, CONTENT_TYPES_E, PERMISSIONS_A, MIXIN_3, false);

    private static final String POLICY_NAME_1 = "policy1";

    private static final String POLICY_NAME_2 = "policy2";

    private static final String POLICY_NAME_3 = "policy3";

    private static final String POLICY_NAME_4 = "policy4";

    private static final String POLICY_NAME_5 = "policy5";

    @Mock
    private SecurityService securityService;

    private SecurityPolicyService securityPolicyService;

    private final List<ValidationViolation> violations = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        mocksUsingSecurityPolicyService(new SecurityPolicyServiceImpl(securityService, REPO_ID));
    }

    private void mocksUsingSecurityPolicyService(SecurityPolicyService securityPolicyService) {
        this.securityPolicyService = securityPolicyService;
        when(securityService.getSecurityPolicyService(REPO_ID)).thenReturn(securityPolicyService);
    }

    private void init(Map<String, SecurityPolicyConfig> config) {
        SecurityPolicyService service = new SecurityPolicyServiceImpl(securityService, REPO_ID, config, violations);
        assertEquals(0, violations.size());
        mocksUsingSecurityPolicyService(service);
    }

    private void build(Map<String, SecurityPolicyConfig> config) {
        new SecurityPolicyServiceImpl(securityService, REPO_ID, config, violations);
    }

    private SecurityPolicyService applyUpdate(Map<String, SecurityPolicyConfig> config, String projectId) {
        return securityPolicyService.applyUpdate(config, projectId, violations);
    }

    @Test
    public void build_blankSecurityPolicyName() {
        build(Map.of("", POLICY_1));

        assertEquals(1, violations.size());
        assertEquals("A security policy name must not be blank", violations.get(0).getMessage(null));
    }

    @Test
    public void build_duplicateMixinSchemas() {
        build(Map.of(POLICY_NAME_3, POLICY_3, POLICY_NAME_5, POLICY_5));
        assertEquals(0, violations.size());
    }

    @Disabled("Not implemented, requires db search")
    @Test
    public void build_unsecuredDocuments() {
        init(Map.of(POLICY_NAME_1, POLICY_1, //
                POLICY_NAME_2, POLICY_2, //
                POLICY_NAME_3, POLICY_3, //
                POLICY_NAME_4, POLICY_4));

        // - POLICY_1 has no documents using the mixin, so no warning
        // - POLICY_2 has documents using the mixin, so results in a warning
        // - POLICY_3 has documents using the mixin, but the new POLICY_5 has the same mixin, so no warning
        // - POLICY_4 is unchanged, so no warning
        // - POLICY_5 is a new policy, so no warning
        build(Map.of(POLICY_NAME_4, POLICY_4, POLICY_NAME_5, POLICY_5));

        assertEquals(1, violations.size());
        assertEquals("Removing the security policy \"policy2\", would leave documents unsecured",
                violations.get(0).getMessage(null));
    }

    @Test
    public void applyUpdate_errorIfChangeProject() {
        init(Map.of(POLICY_NAME_2, POLICY_2.withProjectId("someproject")));

        applyUpdate(Map.of(POLICY_NAME_2, POLICY_2), "otherproject");
        assertEquals(1, violations.size());
        ValidationViolation violation = violations.get(0);
        assertEquals(Type.ERROR, violation.getType());
        assertEquals("Security policy \"policy2\" is already defined in another project or is system-wide",
                violation.getMessage(null));
    }

    @Test
    public void applyUpdate_errorIfChangeProjectFromSystemWide() {
        init(Map.of(POLICY_NAME_2, POLICY_2.withProjectId(null))); // make it system-wide

        applyUpdate(Map.of(POLICY_NAME_2, POLICY_2), "otherproject");
        assertEquals(1, violations.size());
        ValidationViolation violation = violations.get(0);
        assertEquals(Type.ERROR, violation.getType());
        assertEquals("Security policy \"policy2\" is already defined in another project or is system-wide",
                violation.getMessage(null));
    }

    @Test
    public void applyUpdate_errorIfConflictingProject() {
        init(Map.of());

        applyUpdate(Map.of(POLICY_NAME_2, POLICY_2.withProjectId("someproject")), "otherproject");
        assertEquals(1, violations.size());
        ValidationViolation violation = violations.get(0);
        assertEquals(Type.ERROR, violation.getType());
        assertEquals("Security policy \"policy2\" is has conflicting projects", violation.getMessage(null));
    }

    @Test
    public void applyUpdate_warnIfHiddenProvided() {
        init(Map.of(POLICY_NAME_1, POLICY_1)); // policy 1 is hidden

        // try to set policy 2 with the same name
        SecurityPolicyService service = applyUpdate(Map.of(POLICY_NAME_1, POLICY_2), PROJECT_ID);
        assertEquals(1, violations.size());
        ValidationViolation violation = violations.get(0);
        assertEquals(Type.WARNING, violation.getType());
        assertEquals("Security policy \"policy1\" may not be modified - ignored", violation.getMessage(null));

        // the update was ignored, the original is returned
        List<SecurityPolicy> res = service.getSecurityPolicies();
        assertEquals(1, res.size());
        SecurityPolicy policy = res.get(0);
        assertEquals(POLICY_NAME_1, policy.getName());
        assertEquals(MIXIN_1, policy.getConfig().getMixin());
    }

    @Test
    public void applyUpdate_returnIncludesHidden() {
        init(Map.of(POLICY_NAME_1, POLICY_1, POLICY_NAME_2, POLICY_2));

        SecurityPolicyService service = applyUpdate(Map.of(POLICY_NAME_2, POLICY_2), PROJECT_ID);
        assertEquals(0, violations.size());

        List<SecurityPolicy> res = service.getSecurityPolicies();
        assertEquals(2, res.size());
        assertEquals(Set.of(POLICY_NAME_1, POLICY_NAME_2), res.stream().map(SecurityPolicy::getName).collect(toSet()));
    }

    @Test
    public void applyUpdate_setsCorrectProject() {
        init(Map.of());

        SecurityPolicyService service = applyUpdate(Map.of(POLICY_NAME_2, POLICY_2), "otherproject");
        assertEquals(0, violations.size());

        List<SecurityPolicy> res = service.getSecurityPolicies();
        assertEquals(1, res.size());
        SecurityPolicy policy = res.get(0);
        assertEquals(POLICY_NAME_2, policy.getName());
        assertEquals(REPO_ID, policy.getRepositoryName());
        assertEquals("otherproject", policy.getConfig().getProjectId());
    }

}
