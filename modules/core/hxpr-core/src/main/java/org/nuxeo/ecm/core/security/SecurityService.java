/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
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

    /**
     * Sets the security policies for a given repository using optimistic locking.
     * <p>
     * The new configuration is persisted only if the stored version still matches {@code expectedVersion}, and the local
     * policies are updated only after a successful persist; otherwise a concurrent-update exception (HTTP 409) is thrown
     * and the local state is left unchanged. A configuration identical to the stored one is not written at all and keeps
     * its version.
     * <p>
     * The update is merged into, and validated against, the <em>persisted</em> policies the version refers to, not the
     * ones this node currently holds: see {@link #getSecurityPoliciesWithVersion}. A project-scoped update rewrites the
     * whole configuration, so merging into a local copy that lags behind would drop the policies of other projects this
     * node has not seen yet, under a version that matches and therefore raises no conflict.
     *
     * @param repositoryName the repository name
     * @param config the config for security policies to set on the repository
     * @param projectId the project id, or {@code null}
     * @param violations a list of validation errors to be filled
     * @param validateOnly if {@code true} don't actually set the policies, just do validation
     * @param expectedVersion the version the caller last read (see {@link #getSecurityPoliciesWithVersion})
     * @throws ConcurrentUpdateException a concurrent-update exception (HTTP 409) if the persisted version no longer
     *             matches {@code expectedVersion}
     */
    void setSecurityPolicies(String repositoryName, Map<String, SecurityPolicyConfig> config, String projectId,
            List<ValidationViolation> violations, boolean validateOnly, long expectedVersion);

    /**
     * Gets the persisted security policies of a repository together with their optimistic-locking version: the state an
     * optimistic-locking update is computed from, and the version the write is conditioned on.
     * <p>
     * The policies come from the same read as the version, so the two always describe each other. Reading the policies
     * this node holds locally and the version from storage separately would pair a version with content it does not
     * describe, and a client sending that pair back would silently overwrite the difference.
     *
     * @param repositoryName the repository name
     * @return the persisted policies and their version ({@code 0} if never written with one)
     */
    PoliciesWithVersion getSecurityPoliciesWithVersion(String repositoryName);

    /**
     * The persisted security policies of a repository and their optimistic-locking version, as read together.
     *
     * @param policies a detached view of the persisted policies, not the ones registered on this node
     * @param version the version to condition a write of these policies on
     */
    record PoliciesWithVersion(SecurityPolicyService policies, long version) {
    }

}
