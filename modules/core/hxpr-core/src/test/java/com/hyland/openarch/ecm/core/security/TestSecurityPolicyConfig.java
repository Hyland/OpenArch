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

import java.util.List;
import java.util.Set;

import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.config.AbstractSecurityPolicyConfig;

public class TestSecurityPolicyConfig extends AbstractSecurityPolicyConfig {

    private final boolean hidden;

    public TestSecurityPolicyConfig(String projectId, Set<String> contentTypes, Set<String> permissions,
            String mixin, boolean hidden) {
        super(TestSecurityPolicy.TEST_ENGINE, projectId, contentTypes, permissions, mixin);
        this.hidden = hidden;
    }

    public boolean isHidden() {
        return hidden;
    }

    @Override
    public AbstractSecurityPolicyConfig withProjectId(String projectId) {
        return new TestSecurityPolicyConfig(projectId, getContentTypes(), getPermissions(), getMixin(), isHidden());
    }

    @Override
    public TestSecurityPolicy newSecurityPolicy(String repositoryName, String name, List<ValidationViolation> violations) {
        return new TestSecurityPolicy(repositoryName, name, this, violations);
    }

}
