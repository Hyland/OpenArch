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
package org.nuxeo.ecm.core.api;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hyland.openarch.runtime.api.login.LoginContext;
import com.hyland.openarch.runtime.api.login.LoginServiceImpl.SystemID;

/**
 * Class to represent a principal in Nuxeo. This class holds the list of roles and groups for this principal.
 */
public interface NuxeoPrincipal extends Principal, Serializable {

    String PREFIX = "user:";

    /**
     * Inherited from {@link Principal}, use {@link #getId} instead.
     *
     * @see #getId()
     */
    @Override
    @Deprecated
    default String getName() {
        return getId();
    }

    /**
     * Gets the unique identifier for this principal.
     */
    String getId();

    void setId(String id);

    /**
     * Gets the first name of this principal.
     *
     * @return the first name of this principal
     */
    String getFirstName();

    /**
     * Gets the last name of this principal.
     *
     * @return the last name of this principal
     */
    String getLastName();

    /**
     * Gets the password of this principal.
     * <p>
     * Note: Some APIs that return principals from the database intentionally do not fill this field
     *
     * @return the password of this principal
     */
    String getPassword();

    /**
     * Get the user email if any. Return null if not email was specified
     *
     * @return the user email or null if none
     */
    String getEmail();

    /**
     * Gets the groups this principal is directly member of.
     *
     * @return the list of the groups
     */
    List<String> getGroups();

    /**
     * Gets the groups this principal directly or undirectly is member of.
     *
     * @return the list of the groups
     */
    List<String> getAllGroups();

    /**
     * Gets the external user mapping IDs (from Nucleus System Integration) for this principal.
     * Values are formatted as {@code externalUserId_#_systemId}.
     *
     * @return the set of external user mappings, never {@code null}
     */
    Set<String> getExternalUserMappings();

    /**
     * Sets the external user mapping IDs (from Nucleus System Integration) for this principal.
     * Values are formatted as {@code externalUserId_#_systemId}.
     */
    void setExternalUserMappings(Set<String> externalUserMappings);

    /**
     * Gets the external group mapping IDs (from Nucleus System Integration) for this principal.
     * Values are formatted as {@code externalGroupId_#_systemId}.
     *
     * @return the set of external group mappings, never {@code null}
     */
    Set<String> getExternalGroupMappings();

    /**
     * Sets the external group mapping IDs (from Nucleus System Integration) for this principal.
     * Values are formatted as {@code externalGroupId_#_systemId}.
     */
    void setExternalGroupMappings(Set<String> externalGroupMappings);

    Map<String, Serializable> getUserAttributes();

    /**
     * Recursively test if the user is member of this group.
     *
     * @param group The name of the group
     */
    boolean isMemberOf(String group);

    void setFirstName(String firstName);

    void setLastName(String lastName);

    void setGroups(List<String> groups);

    void setPassword(String password);

    void setEmail(String email);

    /**
     * Gets the login username of this principal (human-readable identifier,
     * distinct from the opaque UUID returned by {@link #getId()}).
     * May be {@code null} if not available.
     */
    default String getUsername() {
        return null;
    }

    /**
     * Sets the login username of this principal (human-readable identifier,
     * distinct from the opaque UUID returned by {@link #getId()}).
     */
    default void setUsername(String username) {
        throw new UnsupportedOperationException();
    }

    void setUserAttributes(Map<String, Serializable> userAttributes);

    Map<String, Object> getProperties();

    void setProperties(Map<String, Object> properties);

    /**
     * Returns true if the principal is an administrator.
     * <p>
     * Security checks still apply on the repository for administrator user. If user is a system user, this method will
     * return true.
     *
     * @return true if the principal is an administrator.
     */
    boolean isAdministrator();

    /**
     * Gets the id from which this principal was created, or {@code null} if this principal was not created from another
     * one.
     *
     * @return the originating principal id, or {@code null}
     */
    String getOriginatingId();

    /**
     * Sets the originating principal id.
     */
    void setOriginatingPrincipal(String id);

    /**
     * Gets the acting id for this principal.
     * <p>
     * This is the originating principal id (usually when this principal is a system user), or if there is none this
     * principal's id.
     *
     * @since 6.0
     */
    String getActingId();

    /**
     * Returns the current logged in {@link NuxeoPrincipal}.
     *
     * @return the current logged in {@link NuxeoPrincipal}, or {@code null} if there is none
     * @since 11.1
     */
    static NuxeoPrincipal getCurrent() {
        Principal principal = LoginContext.getCurrentPrincipal();
        if (principal instanceof NuxeoPrincipal nuxeoPrincipal) {
            return nuxeoPrincipal;
        } else if (principal instanceof SystemID system) {
            return new SystemPrincipal(system.getName());
        } else {
            return null;
        }
    }

    /**
     * Checks if the current logged in {@link NuxeoPrincipal} is an Administrator.
     *
     * @return {@code true} if the current logged in {@link NuxeoPrincipal} is an Administrator
     * @since 11.1
     */
    static boolean isCurrentAdministrator() {
        NuxeoPrincipal principal = getCurrent();
        return principal != null && principal.isAdministrator();
    }

}
