/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.acl;

import static org.nuxeo.ecm.core.api.security.ACE.EXTERNAL_SYSTEM_SEPARATOR;
import static org.nuxeo.ecm.core.api.security.ACE.GROUP_PREFIX;
import static org.nuxeo.ecm.core.api.security.ACE.USER_PREFIX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.rest.user.GroupDTO;
import org.nuxeo.rest.user.UserDTO;
import org.nuxeo.runtime.api.Framework;
import org.springframework.stereotype.Component;

/**
 * Helper methods to translate {@link ACE} from/to {@link ACEDTO}.
 */
@Component
public class ACEMapper {

    private UserManager getUserManager() {
        return Framework.getService(UserManager.class);
    }

    /**
     * Converts a list of ACEs to ACEDTOs using batch principal fetching.
     * This method extracts all user and group IDs from the ACE list and fetches them
     * in two batch calls instead of 2N individual calls, eliminating the N+1 query problem.
     *
     * @param aces the list of ACEs to convert
     * @return the list of ACEDTOs with enriched user and group information
     */
    public List<ACEDTO> acesToAceDTOs(List<ACE> aces) {
        if (aces == null || aces.isEmpty()) {
            return List.of();
        }

        // Extract all user and group IDs from the ACE principals
        // Use Sets to automatically handle duplicate principals (same principal with different permissions)
        Set<String> userIds = new HashSet<>();
        Set<String> groupIds = new HashSet<>();

        for (ACE ace : aces) {
            String principal = ace.getPrincipal();
            if (isExternalPrincipal(principal)) {
                // External principals (containing _#_) don't exist in IAM - skip batch fetching
                continue;
            }
            if (isPrincipalWithoutPrefix(principal)) {
                userIds.add(principal);
            } else if (principal != null && principal.startsWith(USER_PREFIX)) {
                String userId = getPrincipalWithoutPrefix(principal);
                if (userId != null) {
                    userIds.add(userId);
                }
            } else if (principal != null && principal.startsWith(GROUP_PREFIX)) {
                String groupId = getPrincipalWithoutPrefix(principal);
                if (groupId != null) {
                    groupIds.add(groupId);
                }
            }
        }

        // Batch fetch users and groups
        Map<String, NuxeoPrincipal> userMap = new HashMap<>();
        Map<String, NuxeoGroup> groupMap = new HashMap<>();

        if (!userIds.isEmpty()) {
            List<NuxeoPrincipal> users = getUserManager().getPrincipals(new ArrayList<>(userIds));
            userMap = users.stream()
                           .collect(Collectors.toMap(NuxeoPrincipal::getId, u -> u,
                                   (existing, replacement) -> existing));
        }

        if (!groupIds.isEmpty()) {
            List<NuxeoGroup> groups = getUserManager().getGroups(new ArrayList<>(groupIds));
            groupMap = groups.stream()
                             .collect(Collectors.toMap(NuxeoGroup::getId, g -> g,
                                     (existing, replacement) -> existing));
        }

        // Map each ACE using the pre-fetched data
        List<ACEDTO> result = new ArrayList<>(aces.size());
        for (ACE ace : aces) {
            UserDTO userDTO = getUserPrincipalFromMap(ace.getPrincipal(), userMap);
            GroupDTO groupDTO = getGroupPrincipalFromMap(ace.getPrincipal(), groupMap);
            result.add(new ACEDTO(
                ace.getCreator(),
                ace.getPermission(),
                ace.isGranted(),
                userDTO,
                groupDTO,
                ace.getEnd(),
                ace.getBegin(),
                ace.getStatus()
            ));
        }

        return result;
    }

    private UserDTO getUserPrincipalFromMap(String principal, Map<String, NuxeoPrincipal> userMap) {
        if (principal != null && principal.startsWith(USER_PREFIX) && isExternalPrincipal(principal)) {
            // External user principal - return directly without UserManager lookup
            return new UserDTO(getPrincipalWithoutPrefix(principal));
        }
        if (isPrincipalWithoutPrefix(principal)) {
            NuxeoPrincipal userPrincipal = userMap.get(principal);
            if (userPrincipal != null) {
                return new UserDTO(userPrincipal);
            }
            return new UserDTO(principal);
        }
        if (principal != null && principal.startsWith(USER_PREFIX)) {
            String userName = getPrincipalWithoutPrefix(principal);
            if (userName == null) {
                return null;
            }
            NuxeoPrincipal userPrincipal = userMap.get(userName);
            if (userPrincipal != null) {
                return new UserDTO(userPrincipal);
            }
            return new UserDTO(userName);
        }
        return null;
    }

    private GroupDTO getGroupPrincipalFromMap(String principal, Map<String, NuxeoGroup> groupMap) {
        if (principal != null && principal.startsWith(GROUP_PREFIX)) {
            String groupId = getPrincipalWithoutPrefix(principal);
            if (groupId == null) {
                return null;
            }
            if (isExternalPrincipal(principal)) {
                // External group principal - return directly without UserManager lookup
                return new GroupDTO(groupId);
            }
            NuxeoGroup group = groupMap.get(groupId);
            if (group != null) {
                return new GroupDTO(group);
            }
            return new GroupDTO(groupId);
        }
        return null;
    }

    public ACE aceDTOToAce(ACEDTO acedto) {
        return ACE.builder()
                  .begin(acedto.begin())
                  .end(acedto.end())
                  .isGranted(acedto.granted())
                  .creator(acedto.creator())
                  .permission(acedto.permission())
                  .principal(getPrincipalString(acedto.user(), acedto.group()))
                  .build();
    }

    private void verifyPrincipalString(UserDTO user, GroupDTO group) {
        if (user != null && group != null) {
            throw new IllegalArgumentException("Ace contains both user and group principals which is not allowed");
        }
        if (user == null && group == null) {
            throw new IllegalArgumentException("Ace contains neither user nor group principal which is not allowed");
        }
    }

    private String getPrincipalString(UserDTO user, GroupDTO group) {
        verifyPrincipalString(user, group);
        if (user != null) {
            if (user.id().equals(SecurityConstants.EVERYONE)) {
                return SecurityConstants.EVERYONE;
            }
            return USER_PREFIX + user.id();
        } else {
            return GROUP_PREFIX + group.id();
        }
    }

    private boolean isExternalPrincipal(String principal) {
        return principal != null && principal.contains(EXTERNAL_SYSTEM_SEPARATOR);
    }

    private boolean isPrincipalWithoutPrefix(String principal) {
        if (principal == null) {
            return false;
        }
        return !principal.startsWith(USER_PREFIX) && !principal.startsWith(GROUP_PREFIX);
    }

    private String getPrincipalWithoutPrefix(String principal) {
        if (principal.contains(":")) {
            return principal.split(":", 2)[1];
        }
        return null;
    }
}
