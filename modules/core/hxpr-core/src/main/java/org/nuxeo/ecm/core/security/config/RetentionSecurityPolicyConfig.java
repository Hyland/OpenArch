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
package org.nuxeo.ecm.core.security.config;

import static org.nuxeo.ecm.core.api.security.SecurityConstants.DELETE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;

import java.util.List;
import java.util.Set;

import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.RetentionSecurityPolicy;

/**
 * Configuration for a Retention Security Policy.
 * The {@code "name"} of the policy ({@code "retention"}) is the key in the map of security policies.
 * The {@code "engine"} value cannot be changed.
 * Json example:
 * <pre>
 *   {
 *     "engine": "retention"
 *   }
 * </pre>
 */

public class RetentionSecurityPolicyConfig extends AbstractSecurityPolicyConfig {

    public RetentionSecurityPolicyConfig() {
        super(RetentionSecurityPolicy.RETENTION_ENGINE_NAME, null, null, Set.of(DELETE, WRITE), null);
    }

    @Override
    public SecurityPolicyConfig withProjectId(String projectId) {
        return this;
    }

    @Override
    public RetentionSecurityPolicy newSecurityPolicy(String repositoryName, String name, List<ValidationViolation> violations) {
        return new RetentionSecurityPolicy(repositoryName, name, this, violations);
    }
}
