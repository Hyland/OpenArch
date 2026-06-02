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

import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYTHING;

import java.util.List;
import java.util.Set;

import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.ACLSecurityPolicy;

/**
 * Configuration for an ACL Security Policy.
 * The {@code "name"} of the policy ({@code "acl"}) is the key in the map of security policies.
 * The {@code "engineName"} value cannot be changed.
 * Json example:
 * <pre>
 *   {
 *     "engine": "acl"
 *   }
 * </pre>
 */
public class ACLSecurityPolicyConfig extends AbstractSecurityPolicyConfig {

    public ACLSecurityPolicyConfig() {
        super(ACLSecurityPolicy.ACL_ENGINE_NAME, null, null, Set.of(EVERYTHING), null);
    }

    @Override
    public SecurityPolicyConfig withProjectId(String projectId) {
        return this;
    }

    @Override
    public ACLSecurityPolicy newSecurityPolicy(String repositoryName, String name, List<ValidationViolation> violations) {
        return new ACLSecurityPolicy(repositoryName, name, this, violations);
    }

}
