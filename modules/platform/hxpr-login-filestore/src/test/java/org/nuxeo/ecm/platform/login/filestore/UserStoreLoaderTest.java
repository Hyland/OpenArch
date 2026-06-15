/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.ecm.platform.login.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.platform.login.filestore.config.FileStoreUsersProperties;
import org.nuxeo.ecm.platform.login.filestore.store.UserStore;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Unit tests for {@link UserStoreLoader}. Uses {@code src/test/resources/test-users.yml} as the fixture file.
 */
class UserStoreLoaderTest {

    private UserStoreLoader loader(String fileLocation) {
        FileStoreUsersProperties props = new FileStoreUsersProperties();
        props.setFile(fileLocation);
        UserStoreLoader l = new UserStoreLoader(props, new DefaultResourceLoader());
        l.init();
        return l;
    }

    @Test
    void loadsUsersAndGroupsFromYaml() {
        UserStoreLoader l = loader("classpath:test-users.yml");
        UserStore store = l.getStore();

        assertThat(store.findById("bbbbbbbb-0000-0000-0000-000000000001")).isNotNull();
        assertThat(store.findById("bbbbbbbb-0000-0000-0000-000000000002")).isNotNull();
        assertThat(store.findById("bbbbbbbb-0000-0000-0000-000000000003")).isNotNull();
        assertThat(store.findById("bbbbbbbb-0000-0000-0000-000000000004")).isNotNull();
        assertThat(store.findById("bbbbbbbb-0000-0000-0000-000000000005")).isNotNull();
        assertThat(store.getAllGroups()).hasSize(3);
    }

    @Test
    void loadsUserDetailsForUsersWithUsernameAndPassword() {
        UserStoreLoader l = loader("classpath:test-users.yml");
        List<UserDetails> details = l.getUserDetails();

        // charlie has no username → excluded
        assertThat(details).hasSize(3);
        assertThat(details).extracting(UserDetails::getUsername).containsExactlyInAnyOrder("alice", "bob", "nouserid");
    }

    @Test
    void adminUser_hasAdminAndUserRoles() {
        UserStoreLoader l = loader("classpath:test-users.yml");
        UserDetails alice = l.getUserDetails()
                             .stream()
                             .filter(u -> u.getUsername().equals("alice"))
                             .findFirst()
                             .orElseThrow();

        assertThat(alice.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                                          .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void nonAdminUser_hasOnlyUserRole() {
        UserStoreLoader l = loader("classpath:test-users.yml");
        UserDetails bob = l.getUserDetails()
                           .stream()
                           .filter(u -> u.getUsername().equals("bob"))
                           .findFirst()
                           .orElseThrow();

        assertThat(bob.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void userWithUsernameButNoPassword_isExcludedFromUserDetails() {
        // dave has a username but no password → excluded from Spring Security user details.
        UserStoreLoader l = loader("classpath:test-users.yml");
        assertThat(l.getUserDetails()).extracting(UserDetails::getUsername).isNotEmpty().doesNotContain("dave");
    }

    @Test
    void missingFile_throwsIllegalStateException() {
        assertThatThrownBy(() -> loader("classpath:does-not-exist.yml")).isInstanceOf(IllegalStateException.class)
                                                                        .hasMessageContaining("does-not-exist.yml");
    }

    @Test
    void malformedYaml_throwsIllegalStateException() {
        assertThatThrownBy(() -> loader("classpath:malformed-users.yml")).isInstanceOf(IllegalStateException.class);
    }
}
