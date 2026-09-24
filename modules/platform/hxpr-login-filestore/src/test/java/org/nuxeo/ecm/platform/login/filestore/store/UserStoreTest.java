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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.NuxeoGroup;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;

/**
 * Unit tests for {@link UserStore}.
 */
class UserStoreTest {

    private UserStore store;

    private static GroupEntry group(String id, String name) {
        GroupEntry g = new GroupEntry();
        g.setId(id);
        g.setName(name);
        return g;
    }

    // ── findById ────────────────────────────────────────────────────────────

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

    @BeforeEach
    void setUp() {
        GroupEntry adminGroup = group("aaaaaaaa-0000-0000-0000-000000000001", "administrators");
        GroupEntry membersGroup = group("aaaaaaaa-0000-0000-0000-000000000002", "members");
        GroupEntry devGroup = group("aaaaaaaa-0000-0000-0000-000000000003", "developers");

        UserEntry alice = user("bbbbbbbb-0000-0000-0000-000000000001", "alice", "Alice", "Admin", "alice@example.com",
                List.of("administrators", "members"));
        alice.setAttributes(java.util.Map.of("department", "Engineering", "badge", "42"));

        UserEntry bob = user("bbbbbbbb-0000-0000-0000-000000000002", "bob", "Bob", "Builder", "bob@example.com",
                List.of("members", "developers"));

        UserEntry charlie = user("bbbbbbbb-0000-0000-0000-000000000003", "charlie", "Charlie", "Brown",
                "charlie@example.com", List.of("developers"));

        UsersFileModel model = new UsersFileModel();
        model.setAdministratorsGroups(List.of("administrators"));
        model.setGroups(List.of(adminGroup, membersGroup, devGroup));
        model.setUsers(List.of(alice, bob, charlie));

        store = UserStore.from(model);
    }

    // ── findByUsername ───────────────────────────────────────────────────────

    @Test
    void findById_returnsCorrectPrincipal() {
        NuxeoPrincipal p = store.findById("bbbbbbbb-0000-0000-0000-000000000001");
        assertThat(p).isNotNull();
        assertThat(p.getFirstName()).isEqualTo("Alice");
        assertThat(p.getLastName()).isEqualTo("Admin");
        assertThat(p.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findById_returnsNullForUnknownId() {
        assertThat(store.findById("unknown-id")).isNull();
    }

    @Test
    void findByUsername_returnsCorrectPrincipal() {
        NuxeoPrincipal p = store.findByUsername("bob");
        assertThat(p).isNotNull();
        assertThat(p.getId()).isEqualTo("bbbbbbbb-0000-0000-0000-000000000002");
    }

    @Test
    void findByUsername_returnsNullForUnknownUsername() {
        assertThat(store.findByUsername("nobody")).isNull();
    }

    // ── groups ───────────────────────────────────────────────────────────────

    @Test
    void adminGroupMember_isMarkedAsAdministrator() {
        assertThat(store.findById("bbbbbbbb-0000-0000-0000-000000000001").isAdministrator()).isTrue();
    }

    @Test
    void nonAdminGroupMember_isNotMarkedAsAdministrator() {
        assertThat(store.findById("bbbbbbbb-0000-0000-0000-000000000002").isAdministrator()).isFalse();
    }

    @Test
    void principalGroups_containGroupIds() {
        NuxeoPrincipal alice = store.findById("bbbbbbbb-0000-0000-0000-000000000001");
        assertThat(alice.getGroups()).containsExactlyInAnyOrder("aaaaaaaa-0000-0000-0000-000000000001",
                "aaaaaaaa-0000-0000-0000-000000000002");
    }

    // ── attributes ───────────────────────────────────────────────────────────

    @Test
    void findGroupById_returnsCorrectGroup() {
        NuxeoGroup g = store.findGroupById("aaaaaaaa-0000-0000-0000-000000000003");
        assertThat(g).isNotNull();
        assertThat(g.getName()).isEqualTo("developers");
    }

    @Test
    void findGroupById_returnsNullForUnknownGroup() {
        assertThat(store.findGroupById("not-a-group")).isNull();
    }

    // ── administratorsGroups ─────────────────────────────────────────────────

    @Test
    void userAttributes_arePropagatedToPrincipal() {
        NuxeoPrincipal alice = store.findById("bbbbbbbb-0000-0000-0000-000000000001");
        assertThat(alice.getUserAttributes()).containsEntry("department", "Engineering").containsEntry("badge", "42");
    }

    // ── searchPrincipalsByName ───────────────────────────────────────────────

    @Test
    void getAdministratorsGroups_returnsConfiguredNames() {
        assertThat(store.getAdministratorsGroups()).containsExactly("administrators");
    }

    @Test
    void searchByFirstName_matchesPartialCaseInsensitive() {
        List<NuxeoPrincipal> results = store.searchPrincipalsByName("ali");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFirstName()).isEqualTo("Alice");
    }

    @Test
    void searchByLastName_matchesPartialCaseInsensitive() {
        List<NuxeoPrincipal> results = store.searchPrincipalsByName("BUILD");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFirstName()).isEqualTo("Bob");
    }

    @Test
    void searchByUsername_matchesPartialCaseInsensitive() {
        List<NuxeoPrincipal> results = store.searchPrincipalsByName("lic");
        assertThat(results).extracting(NuxeoPrincipal::getFirstName).contains("Alice");
    }

    // ── searchGroupsByName ───────────────────────────────────────────────────

    @Test
    void searchWithDuplicateMatchInFirstAndLastName_deduplicatesResult() {
        // Charlie Nousername has both name fields different — no duplicate risk
        // but a user whose firstName == lastName would appear only once
        UsersFileModel model = new UsersFileModel();
        model.setGroups(List.of());
        model.setUsers(List.of(user("id-1", "tiry", "Tiry", "Tiry", "tiry@example.com", List.of())));
        UserStore s = UserStore.from(model);

        List<NuxeoPrincipal> results = s.searchPrincipalsByName("tiry");
        assertThat(results).hasSize(1);
    }

    @Test
    void searchWithBlankName_returnsAllUsers() {
        List<NuxeoPrincipal> results = store.searchPrincipalsByName("");
        assertThat(results).hasSize(3);
    }

    @Test
    void searchGroupsByName_matchesPartial() {
        List<NuxeoGroup> results = store.searchGroupsByName("dev");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("developers");
    }

    // ── getAllUsers / getAllGroups ────────────────────────────────────────────

    @Test
    void searchGroupsByName_emptyName_returnsAll() {
        assertThat(store.searchGroupsByName("")).hasSize(3);
    }

    @Test
    void searchGroupsByName_nullName_returnsAll() {
        assertThat(store.searchGroupsByName(null)).hasSize(3);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Test
    void getAllUsers_returnsAllThreeUsers() {
        assertThat(store.getAllUsers()).hasSize(3);
    }

    @Test
    void getAllGroups_returnsAllThreeGroups() {
        assertThat(store.getAllGroups()).hasSize(3);
    }
}
