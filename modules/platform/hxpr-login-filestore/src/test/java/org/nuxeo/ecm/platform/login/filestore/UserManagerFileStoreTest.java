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
package org.nuxeo.ecm.platform.login.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.login.filestore.store.GroupEntry;
import org.nuxeo.ecm.platform.login.filestore.store.UserEntry;
import org.nuxeo.ecm.platform.login.filestore.store.UserStore;
import org.nuxeo.ecm.platform.login.filestore.store.UsersFileModel;
import org.nuxeo.runtime.springboot.framework.ApplicationContextHelper;
import org.springframework.context.ApplicationContext;

/**
 * Unit tests for {@link UserManagerFileStore}.
 * <p>
 * The {@link UserStoreLoader} is mocked; the {@link UserStore} is built from a small fixture model.
 * {@link ApplicationContextHelper} is seeded with a mock {@link ApplicationContext} that returns the mocked loader.
 * </p>
 */
class UserManagerFileStoreTest {

    private ApplicationContext previousContext;

    private UserManagerFileStore userManager;

    private static GroupEntry group(String id, String name) {
        GroupEntry g = new GroupEntry();
        g.setId(id);
        g.setName(name);
        return g;
    }

    // ── getPrincipal ─────────────────────────────────────────────────────────

    private static UserEntry user(String id, String username, String firstName, String lastName, String email,
            List<String> groups) {
        UserEntry u = new UserEntry();
        u.setId(id);
        u.setUsername(username);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(email);
        u.setGroups(groups);
        return u;
    }

    @AfterEach
    void tearDown() {
        ApplicationContextHelper.setApplicationContext(previousContext);
    }

    @BeforeEach
    void setUp() {
        previousContext = ApplicationContextHelper.getApplicationContext();
        GroupEntry adminGroup = group("group-admin-id", "administrators");
        GroupEntry membersGroup = group("group-members-id", "members");

        UserEntry alice = user("alice-uuid", "alice", "Alice", "Admins", "alice@example.com",
                List.of("administrators", "members"));
        UserEntry bob = user("bob-uuid", "bob", "Bob", "Builder", "bob@example.com", List.of("members"));

        UsersFileModel model = new UsersFileModel();
        model.setAdministratorsGroups(List.of("administrators"));
        model.setGroups(List.of(adminGroup, membersGroup));
        model.setUsers(List.of(alice, bob));

        UserStore store = UserStore.from(model);

        UserStoreLoader loaderMock = mock(UserStoreLoader.class);
        when(loaderMock.getStore()).thenReturn(store);

        ApplicationContext ctxMock = mock(ApplicationContext.class);
        when(ctxMock.getBean(UserStoreLoader.class)).thenReturn(loaderMock);
        ApplicationContextHelper.setApplicationContext(ctxMock);

        userManager = new UserManagerFileStore();
    }

    @Test
    void getPrincipal_byUuid_returnsPrincipal() {
        NuxeoPrincipal p = userManager.getPrincipal("alice-uuid");
        assertThat(p).isNotNull();
        assertThat(p.getFirstName()).isEqualTo("Alice");
    }

    @Test
    void getPrincipal_byUsername_fallback_returnsPrincipal() {
        NuxeoPrincipal p = userManager.getPrincipal("bob");
        assertThat(p).isNotNull();
        assertThat(p.getId()).isEqualTo("bob-uuid");
    }

    @Test
    void getPrincipal_unknownId_returnsNull() {
        assertThat(userManager.getPrincipal("not-a-real-user")).isNull();
    }

    // ── getPrincipals ────────────────────────────────────────────────────────

    @Test
    void getPrincipal_nullId_returnsNull() {
        assertThat(userManager.getPrincipal(null)).isNull();
    }

    // ── searchPrincipalsByName ───────────────────────────────────────────────

    @Test
    void getPrincipal_blankId_returnsNull() {
        assertThat(userManager.getPrincipal("   ")).isNull();
    }

    // ── getGroup ─────────────────────────────────────────────────────────────

    @Test
    void getPrincipals_returnsOnlyKnownIds() {
        List<NuxeoPrincipal> results = userManager.getPrincipals(List.of("alice-uuid", "unknown", "bob-uuid"));
        assertThat(results).hasSize(2);
        assertThat(results).extracting(NuxeoPrincipal::getId).containsExactlyInAnyOrder("alice-uuid", "bob-uuid");
    }

    @Test
    void searchPrincipalsByName_delegatesToStore() {
        List<NuxeoPrincipal> results = userManager.searchPrincipalsByName("ali");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getFirstName()).isEqualTo("Alice");
    }

    @Test
    void getGroup_byId_returnsGroup() {
        NuxeoGroup g = userManager.getGroup("group-admin-id");
        assertThat(g).isNotNull();
        assertThat(g.getName()).isEqualTo("administrators");
    }

    // ── getGroups ────────────────────────────────────────────────────────────

    @Test
    void getGroup_unknownId_returnsNull() {
        assertThat(userManager.getGroup("no-such-group")).isNull();
    }

    // ── searchGroupsByName ───────────────────────────────────────────────────

    @Test
    void getGroup_nullId_returnsNull() {
        assertThat(userManager.getGroup(null)).isNull();
    }

    // ── getAdministratorsGroups ──────────────────────────────────────────────

    @Test
    void getGroups_returnsOnlyKnownIds() {
        List<NuxeoGroup> results = userManager.getGroups(List.of("group-admin-id", "group-members-id", "unknown"));
        assertThat(results).hasSize(2);
    }

    // ── isAdministrator ──────────────────────────────────────────────────────

    @Test
    void searchGroupsByName_delegatesToStore() {
        List<NuxeoGroup> results = userManager.searchGroupsByName("admin");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getName()).isEqualTo("administrators");
    }

    @Test
    void getAdministratorsGroups_returnsConfiguredList() {
        assertThat(userManager.getAdministratorsGroups()).containsExactly("administrators");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Test
    void adminGroupMember_isMarkedAsAdministrator() {
        assertThat(userManager.getPrincipal("alice-uuid").isAdministrator()).isTrue();
    }

    @Test
    void nonAdminGroupMember_isNotMarkedAsAdministrator() {
        assertThat(userManager.getPrincipal("bob-uuid").isAdministrator()).isFalse();
    }
}
