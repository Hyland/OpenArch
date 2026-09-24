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
package org.nuxeo.rest.acl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYONE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ_WRITE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.NuxeoGroup;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.usermanager.NuxeoGroupImpl;
import org.nuxeo.ecm.platform.usermanager.NuxeoPrincipalImpl;
import org.nuxeo.rest.acl.PermissionsManager;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Features(CoreFeature.class)
@SpringBootTest(classes = { PermissionsManager.class })
public class PermissionsManagerTest extends RuntimeSpringBootTest {

    @Autowired
    PermissionsManager permissionsManager;

    @RuntimeInject
    CoreSession session;

    private static final NuxeoGroup ADMIN_GROUP = new NuxeoGroupImpl("administrators", "administrators");
    private static final NuxeoGroup SALES_GROUP = new NuxeoGroupImpl("sales", "sales");
    private static final NuxeoGroup MEMBERS_GROUP = new NuxeoGroupImpl("members", "members");

    @Test
    public void expandUserPrincipalShouldAddAllGroupsOfCurrentUser() {
        var userPrincipal = new NuxeoPrincipalImpl("user1");
        userPrincipal.setGroups(List.of(ADMIN_GROUP.getId(), SALES_GROUP.getId(), MEMBERS_GROUP.getId()));
        assertThat(permissionsManager.expandUserPrincipal(userPrincipal)).containsExactlyInAnyOrder("u:user1",
                "g:administrators", "g:sales", "g:members", EVERYONE);

        userPrincipal.setGroups(Collections.EMPTY_LIST);
        assertThat(permissionsManager.expandUserPrincipal(userPrincipal)).containsExactlyInAnyOrder("u:user1", EVERYONE);
    }

    @Test
    public void expandUserPrincipalShouldAddAllExternalGroupsOfCurrentUser() {
        var userPrincipal = new NuxeoPrincipalImpl("user1");
        userPrincipal.setExternalGroupMappings(Set.of("ext1_#_extSys1", "ext2_#_extSys2"));
        assertThat(permissionsManager.expandUserPrincipal(userPrincipal)).containsExactlyInAnyOrder("u:user1",
                "g:ext1_#_extSys1", "g:ext2_#_extSys2", EVERYONE);

        userPrincipal.setExternalGroupMappings(Collections.EMPTY_SET);
        assertThat(permissionsManager.expandUserPrincipal(userPrincipal)).containsExactlyInAnyOrder("u:user1", EVERYONE);
    }

    @Test
    public void expandUserPrincipalShouldAddAllExternalUserMappingsOfCurrentUser() {
        var userPrincipal = new NuxeoPrincipalImpl("user1");
        userPrincipal.setExternalUserMappings(Set.of("ext1_#_extSys1", "ext2_#_extSys2"));
        assertThat(permissionsManager.expandUserPrincipal(userPrincipal)).containsExactlyInAnyOrder("u:user1",
                "u:ext1_#_extSys1", "u:ext2_#_extSys2", EVERYONE);

        userPrincipal.setExternalUserMappings(Collections.EMPTY_SET);
        assertThat(permissionsManager.expandUserPrincipal(userPrincipal)).containsExactlyInAnyOrder("u:user1", EVERYONE);
    }

    @Test
    public void fetchFullListACEForUserReturnsAllACLForUserAndInheritedACL() {
        // Update ACL on Root document
        DocumentModel root = session.getRootDocument();
        ACL aclRoot = root.getLocalACL();
        aclRoot.add(ACE.builder().permission(EVERYTHING).group("groupAdmin").build());
        root.setACL(aclRoot, false);
        // Create a parent folder with ACL
        DocumentModel parentDoc = session.createDocumentModel("/", "TestParent", SYS_FOLDER);
        parentDoc.create();
        ACL aclParent = parentDoc.getLocalACL();
        aclParent.add(ACE.builder().group("group1").permission(READ_WRITE).build());
        aclParent.add(ACE.builder().group("group2").permission(READ).build());
        parentDoc.setACL(aclParent, false);
        // Create a child document in the parent folder while keeping the inherited permissions from the parent
        DocumentModel doc = session.createDocumentModel(parentDoc.getPathAsString(), "TestDoc", SYS_FILE);
        doc.create();
        ACL acl = doc.getLocalACL();
        acl.add(ACE.builder().user("user1").permission(READ).build());
        acl.add(ACE.builder().user("user2").permission(READ).build());
        doc.setACL(acl, false);

        // Check the full list of permissions for user user1 on document
        var fullACL = permissionsManager.fetchFullListACEForUser(
                List.of("u:user1", "g:group1", "g:groupAdmin", EVERYONE), session, doc.getRef());
        assertThat(fullACL).hasSize(3);
    }
}
