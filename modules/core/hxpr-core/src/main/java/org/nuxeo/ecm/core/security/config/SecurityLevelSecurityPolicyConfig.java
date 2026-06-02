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
package org.nuxeo.ecm.core.security.config;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.SecurityLevelSecurityPolicy;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Configuration for Security Level Security Policies.
 * The {@code "name"} of the policy is the key in the map of security policies.
 * The {@code "engine"} value cannot be changed.
 * The {@code "projectId"} is optional and indicates if it is associated with a Studio project.
 * The {@code "contentTypes"} is a list of content types to which the policy applies.
 * The {@code "mixin"} identifies the business security property used to store the security level of the document.
 * The {@code "userAttribute"} is the user attribute used to store the user's security level.
 * Json example:
 * <pre>
 *   {
 *     "engine": "securitylevel",
 *     "projectId": "12345",
 *     "contentTypes": ["Contract"],
 *     "mixin": "Clearance",
 *     "userAttribute": "clearance"
 *   }
 * </pre>
 */
public class SecurityLevelSecurityPolicyConfig extends AbstractSecurityPolicyConfig {

    private final String userAttribute;

    // Used in Json deserialization
    public SecurityLevelSecurityPolicyConfig() {
        this(null, null, null, null, null);
    }

    public SecurityLevelSecurityPolicyConfig(String projectId, Set<String> contentTypes, Set<String> permissions,
            String mixin, String userAttribute) {
        super(SecurityLevelSecurityPolicy.SECURITY_LEVEL_ENGINE_NAME, projectId, contentTypes, permissions, mixin);
        this.userAttribute = userAttribute;
    }

    @Override
    public SecurityLevelSecurityPolicyConfig withProjectId(String projectId) {
        return new SecurityLevelSecurityPolicyConfig(projectId, getContentTypes(), getPermissions(), getMixin(),
                getUserAttribute());
    }

    @Override
    public SecurityLevelSecurityPolicy newSecurityPolicy(String repositoryName, String name,
            List<ValidationViolation> violations) {
        return new SecurityLevelSecurityPolicy(repositoryName, name, this, violations);
    }

    @JsonInclude
    public String getUserAttribute() {
        return userAttribute;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SecurityLevelSecurityPolicyConfig config)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(userAttribute, config.userAttribute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userAttribute);
    }
}
