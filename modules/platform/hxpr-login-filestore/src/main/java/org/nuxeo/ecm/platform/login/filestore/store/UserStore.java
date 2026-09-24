/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.platform.login.filestore.store;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import com.hyland.openarch.ecm.core.api.RepositoryGroup;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import org.nuxeo.ecm.platform.usermanager.RepositoryGroupImpl;
import org.nuxeo.ecm.platform.usermanager.RepositoryPrincipalImpl;

/**
 * Immutable in-memory snapshot of all users and groups loaded from the YAML user-store file.
 * <p>
 * Built once at startup via {@link #from(UsersFileModel)} and then shared across all callers. Thread-safe after
 * construction.
 * </p>
 * <p>
 * Maintains two lookup indexes for users:
 * <ul>
 *   <li>{@code byId} — UUID-based lookup used by the {@code UserManager} interface</li>
 *   <li>{@code byUsername} — username-based lookup used as fallback during the HTTP Basic Auth
 *       flow, where the principal name coming from Spring Security is the login username rather
 *       than the UUID</li>
 * </ul>
 * </p>
 */
public class UserStore {

    private final Map<String, RepositoryPrincipal> byId;

    private final Map<String, RepositoryPrincipal> byUsername;

    private final Map<String, RepositoryGroup> groupsById;

    private final List<String> administratorsGroups;

    private UserStore(Map<String, RepositoryPrincipal> byId, Map<String, RepositoryPrincipal> byUsername,
                      Map<String, RepositoryGroup> groupsById, List<String> administratorsGroups) {
        this.byId = Collections.unmodifiableMap(byId);
        this.byUsername = Collections.unmodifiableMap(byUsername);
        this.groupsById = Collections.unmodifiableMap(groupsById);
        this.administratorsGroups = Collections.unmodifiableList(administratorsGroups);
    }

    /**
     * Builds an immutable {@link UserStore} from the given file model.
     *
     * @param model the deserialized YAML model
     * @return an immutable user store
     */
    public static UserStore from(UsersFileModel model) {
        Map<String, String> groupNameToId = new HashMap<>();
        Map<String, RepositoryGroup> groupsById = new HashMap<>();
        for (GroupEntry ge : model.getGroups()) {
            if (groupsById.containsKey(ge.getId())) {
                throw new IllegalArgumentException("Duplicate group ID in user-store: '" + ge.getId() + "'");
            }
            if (groupNameToId.containsKey(ge.getName())) {
                throw new IllegalArgumentException("Duplicate group name in user-store: '" + ge.getName() + "'");
            }
            groupsById.put(ge.getId(), new RepositoryGroupImpl(ge.getId(), ge.getName()));
            groupNameToId.put(ge.getName(), ge.getId());
        }

        Set<String> adminGroupNames = new HashSet<>(model.getAdministratorsGroups());

        Map<String, RepositoryPrincipal> byId = new HashMap<>();
        Map<String, RepositoryPrincipal> byUsername = new HashMap<>();

        for (UserEntry ue : model.getUsers()) {
            if (byId.containsKey(ue.getId())) {
                throw new IllegalArgumentException("Duplicate user ID in user-store: '" + ue.getId() + "'");
            }
            // allow empty username (cannot login, but available for document metadata resolution: sys_creator, etc.)
            if (StringUtils.isNotBlank(ue.getUsername()) && byUsername.containsKey(ue.getUsername())) {
                throw new IllegalArgumentException("Duplicate username in user-store: '" + ue.getUsername() + "'");
            }
            boolean isAdmin = ue.getGroups().stream().anyMatch(adminGroupNames::contains);
            RepositoryPrincipal principal = new RepositoryPrincipalImpl(ue.getId(), isAdmin);
            principal.setFirstName(ue.getFirstName());
            principal.setLastName(ue.getLastName());
            principal.setEmail(ue.getEmail());
            principal.setUsername(ue.getUsername());

            // Resolve group names to IDs for storage on the principal
            List<String> groupIds = ue.getGroups()
                                      .stream()
                                      .map(name -> groupNameToId.getOrDefault(name, name))
                                      .toList();
            principal.setGroups(groupIds);

            Map<String, Serializable> attrs = new HashMap<>(ue.getAttributes());
            if (!attrs.isEmpty()) {
                principal.setUserAttributes(attrs);
            }

            byId.put(ue.getId(), principal);
            if (StringUtils.isNotBlank(ue.getUsername())) {
                byUsername.put(ue.getUsername(), principal);
            }
        }

        return new UserStore(byId, byUsername, groupsById, new ArrayList<>(model.getAdministratorsGroups()));
    }

    /**
     * Returns the principal matching the given UUID, or {@code null} if not found.
     */
    public RepositoryPrincipal findById(String id) {
        return byId.get(id);
    }

    /**
     * Returns the principal matching the given username, or {@code null} if not found. Used as a fallback during Basic
     * Auth where the principal name is the login username.
     */
    public RepositoryPrincipal findByUsername(String username) {
        return byUsername.get(username);
    }

    /**
     * Returns the group matching the given UUID, or {@code null} if not found.
     */
    public RepositoryGroup findGroupById(String groupId) {
        return groupsById.get(groupId);
    }

    /**
     * Returns all principals whose username, {@code firstName}, or {@code lastName} contains the given name fragment
     * (case-insensitive).
     */
    public List<RepositoryPrincipal> searchPrincipalsByName(String name) {
        if (StringUtils.isBlank(name)) {
            return new ArrayList<>(byUsername.values());
        }
        return byUsername.entrySet()
                         .stream()
                         .filter(e -> Strings.CI.contains(e.getKey(), name) || Strings.CI.contains(
                                 e.getValue().getFirstName(), name) || Strings.CI.contains(e.getValue().getLastName(),
                                 name))
                         .map(Map.Entry::getValue)
                         .distinct()
                         .toList();
    }

    /**
     * Returns all groups whose name contains the given name fragment (case-insensitive). Returns all groups if the
     * fragment is {@code null} or empty.
     */
    public List<RepositoryGroup> searchGroupsByName(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return new ArrayList<>(groupsById.values());
        }
        return groupsById.values()
                         .stream()
                         .filter(g -> Strings.CI.contains(g.getName(), groupName))
                         .toList();
    }

    /**
     * Returns all principals.
     */
    public Collection<RepositoryPrincipal> getAllUsers() {
        return byId.values();
    }

    /**
     * Returns all groups.
     */
    public Collection<RepositoryGroup> getAllGroups() {
        return groupsById.values();
    }

    /**
     * Returns the list of group names that confer administrator privileges.
     */
    public List<String> getAdministratorsGroups() {
        return administratorsGroups;
    }
}
