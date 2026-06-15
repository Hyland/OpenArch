/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ecm.platform.usermanager;

import java.io.Serializable;
import java.util.List;

import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.security.AdministratorGroupsProvider;

/**
 * UserManager interface containing methods that are actually used for user and groups management.
 * <p>
 * This interface focuses on core user and group management operations:
 * <ul>
 * <li>Retrieving users/principals and groups by ID or name</li>
 * <li>Searching users and groups</li>
 * </ul>
 *
 */
public interface UserManager extends AdministratorGroupsProvider, Serializable {

    // ==================== PRINCIPAL/USER OPERATIONS ====================

    /**
     * Retrieves the principal with the given userid or null if it does not exist.
     *
     * @param userid the user ID of the entry in the IAM system
     * @return the principal or null if not found
     */
    NuxeoPrincipal getPrincipal(String userid);


    /**
     * Retrieves the principals with the given user IDs.
     *
     * @param userIds the list of user IDs
     * @return list of matching principals or empty list when no match
     */
    List<NuxeoPrincipal> getPrincipals(List<String> userIds);

    /**
     * Retrieves principals whose givenName or familyName contains the given name part.
     *
     * @param name the name part to search for
     * @return list of matching principals
     */
    List<NuxeoPrincipal> searchPrincipalsByName(String name);

    // ==================== GROUP OPERATIONS ====================

    /**
     * Returns the group with the given group ID or null if it does not exist.
     *
     * @param groupId the group ID
     * @return the group or null if not found
     */
    NuxeoGroup getGroup(String groupId);

    /**
     * Returns the groups list with the given group IDs.
     * @param groupIds the list of group IDs
     * @return list of matching groups or empty list when no match
     */
    List<NuxeoGroup> getGroups(List<String> groupIds);

    /**
     * Returns the groups whose name contains the given string.
     *
     * @param groupName the displayName to search for
     * @return list of matching groups or empty list when no match
     */
    List<NuxeoGroup> searchGroupsByName(String groupName);

    // ==================== CONFIGURATION/METADATA ====================

    /**
     * Sets the given configuration on the service.
     *
     * @param descriptor the descriptor as parsed from xml, merged from the previous one if it exists.
     */
    void setConfiguration(UserManagerDescriptor descriptor);

    /**
     * Returns the list of administrator group IDs.
     *
     * @return list of administrator group IDs
     */
    List<String> getAdministratorsGroups();
}
