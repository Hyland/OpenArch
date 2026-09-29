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
package com.hyland.openarch.ecm.platform.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import com.hyland.openarch.ecm.core.api.RepositoryGroup;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.platform.usermanager.RepositoryGroupImpl;
import com.hyland.openarch.ecm.platform.usermanager.RepositoryPrincipalImpl;
import com.hyland.openarch.ecm.platform.usermanager.UserManager;
import com.hyland.openarch.ecm.platform.usermanager.UserManagerDescriptor;

/**
 * In-memory {@link UserManager} implementation for use in tests.
 * <p>
 * Pre-populated with a fixed set of test users and groups. See {@link UserManagerMockFeature}.
 */
public class UserManagerMock implements UserManager {

    // UUID that corresponds to the default test user (same value as {@code UserManagerFeature.TEST_USER_ID}).
    public static final String TEST_USER_ID = "ff7b0360-3594-416c-b587-2fa4dd0839ce";

    // AbstractUserAPITest.TEST_USER_2_ID
    public static final String TEST_USER_2_ID = "850edbd1-33c3-4653-b7c9-15e594570f28";

    // AbstractUserAPITest.TEST_USER_3_ID
    public static final String TEST_USER_3_ID = "850edbd1-33c3-4653-b7c9-15e594570f29";

    // UUID of the groups (mirrors the IdP identity-resources.json values)
    public static final String TEST_GROUP_1_ID = "e41b8531-41ec-42c2-bd4d-91e5bcbf5e49";
    public static final String TEST_GROUP_1_NAME = "myGroupName";

    public static final String TEST_GROUP_2_ID = "983c5bf9-5c98-4118-a289-af562fa36d5a";
    public static final String TEST_GROUP_2_NAME = "myGroupNameTwo";

    // UUID of the "members" group used in ACL tests (mirrors the IdP identity-resources.json value).
    public static final String MEMBERS_GROUP_ID = "8d6542c9-50e9-4027-aa10-2cfb2179413f";
    public static final String MEMBERS_GROUP_NAME = "members";

    // UUID of the alternative test user "jchan", referenced by {@code AbstractAPITest.ALT_USER_ID}.
    public static final String ALT_USER_ID = "ced9ee59-c4d5-4b58-866a-b84cd6cc6fae";
    public static final String ALT_USER_NAME = "jchan";

    // LinkedHashMap: insertion order is stable so any test that iterates values gets a predictable sequence
    private final Map<String, RepositoryPrincipalImpl> users = new LinkedHashMap<>();
    private final Map<String, RepositoryGroupImpl> groups = new LinkedHashMap<>();

    public UserManagerMock() {
        addUser(TEST_USER_ID, "Test", "User", "test.user@hyland.com", null);
        users.get(TEST_USER_ID).setUserAttributes(
                Map.of("YOJ", "2013", "empId", "E443336-3675", "favouriteColor", "beige"));
        addUser(TEST_USER_2_ID, "MyFirstOne", "MyFamilyOne", "firstOne.lastnameOne@hyland.com", "myUserNameOne");
        addUser(TEST_USER_3_ID, "MyFirstTwo", "MyFamilyTwo", "firstTwo.lastnameTwo@hyland.com", "myUserNameTwo");
        // User with identical first and last name to exercise deduplication in searchPrincipalsByName
        addUser("dd94b0c1-bbb0-4e22-b0e6-8f07b93dcf01", "Tiry", "Tiry", "tiry.delprat@hyland.com", null);
        // Alternative test user referenced by AbstractAPITest.ALT_USER / ALT_USER_ID
        addUser(ALT_USER_ID, "Jacky", "Chan", "jacky.chan@hyland.com", ALT_USER_NAME);

        addGroup(TEST_GROUP_1_ID, TEST_GROUP_1_NAME);
        addGroup(TEST_GROUP_2_ID, TEST_GROUP_2_NAME);
        addGroup(MEMBERS_GROUP_ID, MEMBERS_GROUP_NAME);
        for (int i = 1; i <= 50; i++) {
            addGroup("group-id-" + i, "generated-group-name-" + i);
        }
    }

    private void addUser(String id, String firstName, String lastName, String email, String username) {
        RepositoryPrincipalImpl p = new RepositoryPrincipalImpl(id);
        p.setFirstName(firstName);
        p.setLastName(lastName);
        p.setEmail(email);
        if (username != null) {
            p.setUsername(username);
        }
        users.put(id, p);
    }

    private void addGroup(String id, String name) {
        groups.put(id, new RepositoryGroupImpl(id, name));
    }

    @Override
    public RepositoryPrincipal getPrincipal(String userid) {
        return users.get(userid);
    }

    @Override
    public List<RepositoryPrincipal> getPrincipals(List<String> userIds) {
        return userIds.stream()
                      .filter(users::containsKey)
                      .map(users::get)
                      .collect(Collectors.toList());
    }

    /**
     * Returns principals whose firstName, lastName, or username contains the given name part
     * (case-insensitive). Deduplicates results so a user whose firstName and lastName both
     * match still appears only once.
     */
    @Override
    public List<RepositoryPrincipal> searchPrincipalsByName(String name) {
        if (StringUtils.isBlank(name)) {
            return new ArrayList<>(users.values());
        }
        return users.values().stream()
                    .filter(p -> Strings.CI.contains(p.getFirstName(), name)
                              || Strings.CI.contains(p.getLastName(), name)
                              || Strings.CI.contains(p.getUsername(), name))
                    .distinct()
                    .collect(Collectors.toList());
    }

    @Override
    public RepositoryGroup getGroup(String groupId) {
        return groups.get(groupId);
    }

    @Override
    public List<RepositoryGroup> getGroups(List<String> groupIds) {
        return groupIds.stream()
                       .filter(groups::containsKey)
                       .map(groups::get)
                       .collect(Collectors.toList());
    }

    @Override
    public List<RepositoryGroup> searchGroupsByName(String groupName) {
        if (StringUtils.isBlank(groupName)) {
            return new ArrayList<>(groups.values());
        }
        return groups.values().stream()
                     .filter(g -> Strings.CI.contains(g.getName(), groupName))
                     .collect(Collectors.toList());
    }

    @Override
    public void setConfiguration(UserManagerDescriptor descriptor) {
        // no-op for mock
    }

    @Override
    public List<String> getAdministratorsGroups() {
        return List.of("administrators");
    }

}
