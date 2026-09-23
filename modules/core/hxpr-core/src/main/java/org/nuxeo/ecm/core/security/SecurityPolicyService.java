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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.query.QueryParameters;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.query.sql.model.Statement;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import org.nuxeo.ecm.core.storage.dbs.DBSSession;
import org.nuxeo.ecm.core.storage.dbs.StateAccessor;

import com.hyland.openarch.config.Configurable;

/**
 * Security policy service, dealing with the security for one repository.
 */
public interface SecurityPolicyService {

    /**
     * Gets all the security policies.
     *
     * @return the security policies
     */
    List<SecurityPolicy> getSecurityPolicies();

    /**
     * Gets the configuration for all the security policies.
     *
     * @return the configuration
     */
    Map<String, SecurityPolicyConfig> getSecurityPolicyConfigMap();

    /**
     * Gets the result of applying an updated configuration to this security policy service.
     *
     * @param config the config to apply
     * @param projectId the project id, or {@code null}
     * @param violations the violations to be filled
     * @return a new, updated security policy service
     */
    SecurityPolicyService applyUpdate(Map<String, SecurityPolicyConfig> config, String projectId,
            List<ValidationViolation> violations);

    /**
     * Checks if the principal has the given permission on the document.
     */
    boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, String permission);

    /**
     * Checks if the principal can add/remove a mixin on a given document.
     *
     * @param doc the document
     * @param principal the principal
     * @param mixin the mixin being added/removed
     * @throws DocumentSecurityException if permission is denied
     */
    void checkAllowedMixinUpdate(InternalDocument doc, NuxeoPrincipal principal, String mixin)
            throws DocumentSecurityException;

    /**
     * Checks if the principal can modify a property on a given document.
     *
     * @param doc the document
     * @param principal the principal
     * @param xpath the xpath of the property being modified
     * @throws DocumentSecurityException if permission is denied
     */
    void checkAllowedPropertyUpdate(InternalDocument doc, NuxeoPrincipal principal, String xpath)
            throws DocumentSecurityException;

    /**
     * Gets the permissions on a document for a principal.
     * <p>
     * The returned set contains all permissions (including groups of permissions) that the principal has on the
     * document.
     *
     * @param doc the document
     * @param principal the principal
     * @return the permissions on the document for the principal
     */
    Set<String> getEffectivePermissions(InternalDocument doc, NuxeoPrincipal principal);

    /**
     * Sets the initial configured security policies on a document.
     *
     * @param doc the document
     */
    void setSecurityPolicies(DocumentModel doc);

    /**
     * Sets the given security policies on a document.
     *
     * @param doc the document
     * @param securityPolicies the security policies
     */
    void setSecurityPolicies(InternalDocument doc, List<String> securityPolicies);

    /**
     * Adds security policies (including acl) to the query.
     *
     * @param query the query
     * @param queryParams the query parameters
     * @return the new query
     */
    Statement addSecurityPolicies(Statement query, QueryParameters queryParams);

    // these denormalizers will be moved out of the SecurityPolicyService later

    /**
     * Updates the denormalized properties on a document (not recursively).
     */
    void updateDenormalizedProperties(String id, StateAccessor stateAccessor);

    /**
     * Updates the denormalized properties on some documents (not recursively), bypassing caches.
     */
    void updateDenormalizedPropertiesNoCache(Collection<String> ids, StateAccessor stateAccessor);

    /**
     * Updates the denormalized properties on a document recursively.
     */
    void updateDenormalizedPropertiesWithDescendants(String id, StateAccessor stateAccessor, DBSSession session);

    /** INTERNAL: registers suitable {@link Configurable} on instantiation. */
    void registerConfigurables();

    /** INTERNAL: unregisters suitable {@link Configurable} on de-instantiation. */
    void unregisterConfigurables();

}
