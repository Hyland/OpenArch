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

import java.util.List;
import java.util.Set;

import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.SecurityPolicy;

import com.fasterxml.jackson.annotation.JsonInclude;

public interface SecurityPolicyConfig {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String getEngine();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String getMixin();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String getProjectId();

    /**
     * @return the content types this policy will be applied to. An empty set is returned if there are none.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Set<String> getContentTypes();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Set<String> getPermissions();

    /**
     * Creates a new instance with the same values, except for {@code productId} which is set to the value provided.
     * @param projectId to be set
     * @return a new instance of this with projectId set
     */
    SecurityPolicyConfig withProjectId(String projectId);

    /**
     * Constructs an instance of the policy from this config.
     */
    SecurityPolicy newSecurityPolicy(String repositoryName, String name, List<ValidationViolation> violations);

}
