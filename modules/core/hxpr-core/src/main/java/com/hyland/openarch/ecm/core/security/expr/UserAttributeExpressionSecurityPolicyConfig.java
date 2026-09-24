/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.security.expr;

import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;

import java.util.List;
import java.util.Set;

import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.config.AbstractSecurityPolicyConfig;

public class UserAttributeExpressionSecurityPolicyConfig extends AbstractSecurityPolicyConfig {

    public UserAttributeExpressionSecurityPolicyConfig() {
        super(UserAttributeExpressionSecurityPolicy.USER_ATTRIBUTE_EXPRESSION_ENGINE_NAME, null, null, Set.of(READ),
                null);
    }

    public UserAttributeExpressionSecurityPolicyConfig(String engine, String projectId, Set<String> contentTypes,
            Set<String> permissions, String mixin) {
        super(engine, projectId, contentTypes, permissions, mixin);
    }

    @Override
    public UserAttributeExpressionSecurityPolicyConfig withProjectId(String projectId) {
        return new UserAttributeExpressionSecurityPolicyConfig(getEngine(), projectId, getContentTypes(),
                getPermissions(), getMixin());
    }

    @Override
    public UserAttributeExpressionSecurityPolicy newSecurityPolicy(String repositoryName, String name,
            List<ValidationViolation> violations) {
        return new UserAttributeExpressionSecurityPolicy(repositoryName, name, this, violations);
    }
}
