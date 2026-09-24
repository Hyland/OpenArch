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

import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.security.config.SecurityPolicyConfig;

/**
 * Security policy.
 */
public interface SecurityPolicy {

    /**
     * Gets the repository to which this security policy applies.
     *
     * @return the repository name
     */
    String getRepositoryName();

    /**
     * Gets the name for this security policy.
     *
     * @return the name
     */
    String getName();

    /**
     * Gets the configuration for this security policy.
     *
     * @return the configuration
     */
    SecurityPolicyConfig getConfig();

    /**
     * Whether this security policy is hidden (not exposed to public APIs).
     *
     * @return {@code true} if the security policy is hidden
     */
    boolean isHidden();

    /**
     * Whether this security policy is global (always applicable to all documents).
     *
     * @return {@code true} if the security policy is always applicable to all documents
     */
    boolean isGlobal();

    /**
     * Gets the set of permissions (flattened) that this security policy can manage.
     *
     * @return the permissions
     */
    Set<String> getPermissions();

    /**
     * Checks whether the policies applies to the document for the given permissions.
     *
     * @param doc the document
     * @param principal the principal
     * @param permissions the permission
     * @return {@code true} if the policy applies
     */
    boolean appliesToDocument(InternalDocument doc, NuxeoPrincipal principal, Set<String> permissions);

    /**
     * Checks if the principal has the given permission on the document.
     *
     * @param doc the document
     * @param principal the principal
     * @param principals the expanded principals
     * @param permission the permission
     * @param permissions the expanded permissions
     * @return {@code true} if the principal has the permission on the document
     */
    boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, Set<String> principals, String permission,
            Set<String> permissions);

    /**
     * Checks if the principal can add/remove a mixin.
     *
     * @param mixin the mixin being added/removed
     * @throws DocumentSecurityException if permission is denied
     */
    void checkAllowedMixinUpdate(String mixin) throws DocumentSecurityException;

    /**
     * Checks if the principal can modify a property.
     *
     * @param xpath the xpath of the property being modified
     * @throws DocumentSecurityException if permission is denied
     */
    void checkAllowedPropertyUpdate(String xpath) throws DocumentSecurityException;

    /**
     * Modifies the passed {@code effectivePermissions} to restrict them based on what this security policy allows.
     *
     * @param effectivePermissions the mutable list of effective permissions being restricted
     * @param doc the document
     * @param principal the principal
     * @param principals the expanded principals
     * @param securityService the security service, for permission expansion
     */
    void restrictEffectivePermissions(List<String> effectivePermissions, InternalDocument doc,
            NuxeoPrincipal principal, Set<String> principals, SecurityService securityService);

    /**
     * Gets the query clause to add for this security policy.
     *
     * @param queryParams the query parameters
     * @param principals the expanded principals
     * @return the query clause to add, or {@code null} if there is nothing to add
     */
    Operand getQueryClause(QueryParameters queryParams, Set<String> principals);

}
