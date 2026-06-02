/*
 * (C) Copyright 2006-2021 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *     Bogdan Stefanescu
 *     Olivier Grisel
 *     Anahide Tchertchian
 */

package org.nuxeo.ecm.core.security;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;

/**
 * Security service.
 */
public interface SecurityService {

    /**
     * Gets all the permissions defined.
     *
     * @return the permissions
     */
    Set<String> getAllPermissions();

    /**
     * Gets the permission and all the permission groups that include it (recursively), including {@code Everything}.
     */
    Set<String> getAllPermissionsContaining(String permission);

    /**
     * Gets the permission and all the permissions that are included in it (recursively).
     */
    Set<String> getAllPermissionsContainedIn(String permission);

    /**
     * Gets the principal and all the groups that include it (recursively), including {@code Everyone}.
     */
    Set<String> expandPrincipal(NuxeoPrincipal principal);

    /**
     * Gets the security policy service for the given repository.
     *
     * @param repositoryName the repository
     * @return the security policy service for the given repository
     */
    SecurityPolicyService getSecurityPolicyService(String repositoryName);

    // ----- admin -----

    /**
     * Sets the security policies for a given repository.
     *
     * @param repositoryName the repository name
     * @param config the config for security policies to set on the repository
     * @throws SecurityPolicyValidationException if there are violation errors
     */
    void setSecurityPolicies(String repositoryName, Map<String, SecurityPolicyConfig> config)
            throws SecurityPolicyValidationException;

    /**
     * Sets the security policies for a given repository.
     *
     * @param repositoryName the repository name
     * @param config the config for security policies to set on the repository
     * @param projectId the project id, or {@code null}
     * @param violations a list of validation errors to be filled
     * @param validateOnly if {@code true} don't actually set the policies, just do validation
     */
    void setSecurityPolicies(String repositoryName, Map<String, SecurityPolicyConfig> config,
            String projectId, List<ValidationViolation> violations, boolean validateOnly);

}
