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
package org.nuxeo.ecm.core.security.expr;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.config.AbstractSecurityPolicyConfig;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Configuration for Expression Security Policies.
 * <p>
 * The {@code "expression"} is the expression evaluated to grant access. Json example:
 *
 * <pre>
 *   {
 *     "engine": "expression",
 *     "projectId": "12345",
 *     "contentTypes": ["Contract"],
 *     "mixin": "Clearance",
 *     "expression": "1 < 2"
 *   }
 * </pre>
 */
public class ExpressionSecurityPolicyConfig extends AbstractSecurityPolicyConfig {

    private final String expression;

    // Used in Json deserialization
    public ExpressionSecurityPolicyConfig() {
        this(null, null, null, null, null);
    }

    public ExpressionSecurityPolicyConfig(String projectId, Set<String> contentTypes, Set<String> permissions,
            String mixin, String expression) {
        super(ExpressionSecurityPolicy.EXPRESSION_ENGINE_NAME, projectId, contentTypes, permissions, mixin);
        this.expression = expression;
    }

    @Override
    public ExpressionSecurityPolicyConfig withProjectId(String projectId) {
        return new ExpressionSecurityPolicyConfig(projectId, getContentTypes(), getPermissions(), getMixin(),
                getExpression());
    }

    @Override
    public ExpressionSecurityPolicy newSecurityPolicy(String repositoryName, String name,
            List<ValidationViolation> violations) {
        return new ExpressionSecurityPolicy(repositoryName, name, this, violations);
    }

    @JsonInclude
    public String getExpression() {
        return expression;
    }

    @Override
    public boolean equals(Object o) {
        return this == o //
                || o instanceof ExpressionSecurityPolicyConfig that //
                        && super.equals(o) //
                        && Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), expression);
    }

}
