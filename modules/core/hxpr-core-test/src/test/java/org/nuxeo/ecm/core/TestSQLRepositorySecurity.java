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
package org.nuxeo.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_TITLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.security.Access.DENY;
import static org.nuxeo.ecm.core.api.security.Access.GRANT;
import static org.nuxeo.ecm.core.api.security.Access.UNKNOWN;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.CREATE_CHILD;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.CREATE_VERSION;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.DELETE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.DELETE_CHILD;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYONE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.GENERIC_DOCUMENT_SECURITY_ERROR;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.MANAGE_LOCK;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.MANAGE_RETENTION;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ_VERSION;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ_WRITE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.MANAGE_SECURITY;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE_VERSION;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.Access;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-permissions-contrib.xml")
public class TestSQLRepositorySecurity {

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    @Inject
    protected EventService eventService;

    protected CoreSession openSessionAs(String username) {
        return CoreInstance.getCoreSession(session.getRepositoryName(),
                DummyLoginFeature.getTestPrincipal(username, false));
    }

    protected CoreSession openAdminSessionAs(String username) {
        return CoreInstance.getCoreSession(session.getRepositoryName(),
                DummyLoginFeature.getTestPrincipal(username, true));
    }

    // assumes that the global "session" belongs to an Administrator
    protected void setPermissionToAnonymous(String perm) {
        DocumentModel doc = session.getRootDocument();
        ACL acl = doc.getLocalACL();
        acl.add(ACE.builder().user("anonymous").permission(perm).build());
        doc.setACL(acl, true);
        session.save();
    }

    protected void setPermissionToEveryone(String... perms) {
        DocumentModel doc = session.getRootDocument();
        ACL acl = doc.getLocalACL();
        for (String perm : perms) {
            acl.add(ACE.builder().principal(EVERYONE).permission(perm).build());
        }
        doc.setACL(acl, true);
        session.save();
    }

    protected void removePermissionToAnonymous() {
        DocumentModel doc = session.getRootDocument();
        ACL acl = doc.getLocalACL();
        acl.removeByPrincipal(ACE.USER_PREFIX + "anonymous");
        doc.setACL(acl, true);
        session.save();
    }

    protected void removePermissionToEveryone() {
        DocumentModel doc = session.getRootDocument();
        ACL acl = doc.getLocalACL();
        acl.removeByPrincipal(EVERYONE);
        doc.setACL(acl, true);
        session.save();
    }

    private Access getAccess(ACL acl, String principal, String permission) {
        return acl.getAccess(List.of(principal), Set.of(permission));
    }

    @Test
    public void testSecurity() {
        // temporary set an Everything privileges on the root for anonymous
        // so that we can create a folder
        setPermissionToAnonymous(EVERYTHING);

        CoreSession anonSession = openSessionAs("anonymous");
        DocumentModel root = anonSession.getRootDocument();

        DocumentModel folder = anonSession.createDocumentModel(root.getPathAsString(), "folder#1", SYS_FOLDER);
        folder.create();

        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("a").permission("Read").build());
        acl.add(ACE.builder().group("b").permission("Write").build());
        folder.setACL(acl, true);

        acl = folder.getLocalACL();

        assertEquals("u:a", acl.get(0).getPrincipal());
        assertEquals("g:b", acl.get(1).getPrincipal());

        assertSame(GRANT, getAccess(acl, "u:a", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "u:a", "Write"));
        assertSame(GRANT, getAccess(acl, "g:b", "Write"));
        assertSame(UNKNOWN, getAccess(acl, "g:b", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "c", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "c", "Write"));
        assertSame(UNKNOWN, getAccess(acl, "u:c", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "u:c", "Write"));
        assertSame(UNKNOWN, getAccess(acl, "g:c", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "g:c", "Write"));

        // insert a deny Write ACE before the GRANT
        acl.add(0, ACE.builder().group("b").permission("Write").isGranted(false).build());
        // store changes
        folder.setACL(acl, true);
        // refetch acl
        acl = folder.getLocalACL();
        // check perms now
        assertSame(GRANT, getAccess(acl, "u:a", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "u:a", "Write"));
        assertSame(DENY, getAccess(acl, "g:b", "Write"));
        assertSame(UNKNOWN, getAccess(acl, "g:b", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "c", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "c", "Write"));
        assertSame(UNKNOWN, getAccess(acl, "u:c", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "u:c", "Write"));
        assertSame(UNKNOWN, getAccess(acl, "g:c", "Read"));
        assertSame(UNKNOWN, getAccess(acl, "g:c", "Write"));

        // create a child document and grant on it the write for b

        // remove anonymous Everything privileges on the root
        // so that it not influence test results
        removePermissionToAnonymous();
        anonSession.save(); // process invalidations

        try {
            DocumentModel folder2 = anonSession.createDocumentModel(folder.getPathAsString(), "folder#2", SYS_FOLDER);
            folder2.create();
            fail("privilege is granted but should not be");
        } catch (DocumentSecurityException e) {
            // ok
        }

        setPermissionToAnonymous(EVERYTHING);
        anonSession.save(); // process invalidations

        root = anonSession.getRootDocument();

        // and try again - this time it should work
        DocumentModel folder2 = anonSession.createDocumentModel(folder.getPathAsString(), "folder#2", SYS_FOLDER);
        folder2.create();

        ACL acl2 = new ACLImpl();
        acl2.add(ACE.builder().group("b").permission("Write").build());

        folder2.setACL(acl2, true);
        acl2 = folder2.getEffectiveACL();

        assertSame(GRANT, getAccess(acl2, "u:a", "Read"));
        assertSame(UNKNOWN, getAccess(acl2, "u:a", "Write"));
        assertSame(GRANT, getAccess(acl2, "g:b", "Write"));
        assertSame(UNKNOWN, getAccess(acl2, "g:b", "Read"));
        assertSame(UNKNOWN, getAccess(acl2, "c", "Read"));
        assertSame(UNKNOWN, getAccess(acl2, "c", "Write"));
        assertSame(UNKNOWN, getAccess(acl2, "u:c", "Read"));
        assertSame(UNKNOWN, getAccess(acl2, "u:c", "Write"));
        assertSame(UNKNOWN, getAccess(acl2, "g:c", "Read"));
        assertSame(UNKNOWN, getAccess(acl2, "g:c", "Write"));

        // remove anonymous Everything privileges on the root
        // so that it not influence test results
        removePermissionToAnonymous();
        anonSession.save(); // process invalidations

        setPermissionToEveryone(WRITE, DELETE, CREATE_CHILD, DELETE_CHILD, READ);
        root = anonSession.getRootDocument();

        DocumentModel folder3 = anonSession.createDocumentModel(folder.getPathAsString(), "folder#3", SYS_FOLDER);
        folder3.create();

        anonSession.removeDocument(folder3.getRef());

        removePermissionToEveryone();
        setPermissionToEveryone(DELETE);
        anonSession.save(); // process invalidations

        try {
            folder3 = anonSession.createDocumentModel(folder.getPathAsString(), "folder#3", SYS_FOLDER);
            folder3.create();
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void testACLEscaping() {
        // temporary set an Everything privileges on the root for anonymous
        // so that we can create a folder
        setPermissionToAnonymous(EVERYTHING);

        DocumentModel root = session.getRootDocument();

        DocumentModel folder = session.createDocumentModel(root.getPathAsString(), "folder1", SYS_FOLDER);
        folder.create();

        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("xyz").permission("Read").build());
        acl.add(ACE.builder().user("x:y:z").permission("Read").build());
        acl.add(ACE.builder().user("abc@def<&>/ ").permission("Read").build());
        acl.add(ACE.builder().user("caf\u00e9").permission("Read").build());
        acl.add(ACE.builder().user("o'hara").permission("Read").build()); // name to quote
        acl.add(ACE.builder().user("A_x1234_").permission("Read").build()); // name to quote
        folder.setACL(acl, true);

        // check what we read
        acl = folder.getLocalACL();
        assertNotNull(acl);
        assertEquals("u:xyz", acl.get(0).getPrincipal());
        assertEquals("u:x:y:z", acl.get(1).getPrincipal());
        assertEquals("u:abc@def<&>/ ", acl.get(2).getPrincipal());
        assertEquals("u:caf\u00e9", acl.get(3).getPrincipal());
        assertEquals("u:o'hara", acl.get(4).getPrincipal());
        assertEquals("u:A_x1234_", acl.get(5).getPrincipal());
    }

    @Test
    public void testEffectiveAclStopAtBlock() {
        DocumentModel root = session.createDocumentModel("/", "testACLInheritance", SYS_FOLDER);
        root.create();
        DocumentModel folder = session.createDocumentModel("/testACLInheritance", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/testACLInheritance/folder", "doc", SYS_FILE);
        doc.create();

        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("alice").permission(READ).build());
        root.setACL(acl, true);

        acl = new ACLImpl();
        acl.add(ACE.BLOCK);
        folder.setACL(acl, true);

        acl = new ACLImpl();
        ACE bob = ACE.builder().user("bob").permission(READ).build();
        acl.add(bob);
        doc.setACL(acl, true);

        acl = doc.getEffectiveACL();
        assertEquals(List.of(bob), acl); // stop at block
    }

    @Test
    public void testEffectiveAclDedupe() {
        DocumentModel root = session.createDocumentModel("/", "folder", SYS_FOLDER);
        root.create();
        DocumentModel doc = session.createDocumentModel("/folder", "doc", SYS_FILE);
        doc.create();

        ACE alice = ACE.builder().user("alice").permission(READ).build();
        ACE bob = ACE.builder().user("bob").permission(READ).build();

        ACL acl = new ACLImpl();
        acl.add(alice);
        acl.add(bob);
        root.setACL(acl, true);

        acl = new ACLImpl();
        acl.add(bob);
        doc.setACL(acl, true);

        acl = doc.getEffectiveACL();
        assertEquals(List.of(bob, alice), acl); // ace has been deduped of bob
    }

    @Test
    public void testPermissionChecks() throws Throwable {
        DocumentRef ref = createDocumentModelWithSamplePermissions("docWithPerms");

        CoreSession joeReaderSession = openSessionAs("joe_reader");
        // reader only has the right to consult the document
        DocumentModel joeReaderDoc = joeReaderSession.getDocument(ref);
        try {
            DocumentModel doc = joeReaderSession.createDocumentModel(joeReaderDoc.getPathAsString(), "child", SYS_FILE);
            doc.create();
            fail("should have raised a security exception");
        } catch (DocumentSecurityException e) {
        }

        try {
            joeReaderSession.removeDocument(ref);
            fail("should have raised a security exception");
        } catch (DocumentSecurityException e) {
        }
        joeReaderSession.save();

        // contributor only has the right to write the properties of
        // document
        CoreSession joeContributorSession = openSessionAs("joe_contributor");
        DocumentModel joeContributorDoc = joeContributorSession.getDocument(ref);

        DocumentModel child = joeContributorSession.createDocumentModel(joeContributorDoc.getPathAsString(), "child", SYS_FILE);
        child.create();
        DocumentRef childRef = child.getRef();
        joeContributorSession.save();

        // joe contributor can copy the newly created doc
        joeContributorSession.copy(childRef, ref, "child_copy");

        // joe contributor cannot move the doc
        try {
            joeContributorSession.move(childRef, ref, "child_move");
            fail("should have raised a security exception");
        } catch (DocumentSecurityException e) {
        }

        // joe contributor cannot remove the folder either
        try {
            joeContributorSession.removeDocument(ref);
            fail("should have raised a security exception");
        } catch (DocumentSecurityException e) {
        }
        joeContributorSession.save();

        // local manager can read, write, create and remove
        CoreSession joeLocalManagerSession = openSessionAs("joe_localmanager");
        DocumentModel joeLocalManagerDoc = joeLocalManagerSession.getDocument(ref);

        child = joeLocalManagerSession.createDocumentModel(joeLocalManagerDoc.getPathAsString(), "child2", SYS_FILE);
        child.create();
        childRef = child.getRef();
        joeLocalManagerSession.save();

        // joe local manager can copy the newly created doc
        joeLocalManagerSession.copy(childRef, ref, "child2_copy");

        // joe local manager cannot move the doc
        joeLocalManagerSession.move(childRef, ref, "child2_move");

        joeLocalManagerSession.removeDocument(ref);
        joeLocalManagerSession.save();

    }

    protected DocumentRef createDocumentModelWithSamplePermissions(String name) {
        DocumentModel root = session.getRootDocument();
        DocumentModel doc = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        doc.create();

        ACL localACL = doc.getLocalACL();

        localACL.add(ACE.builder().user("joe_reader").permission(READ).build());

        localACL.add(ACE.builder().user("joe_contributor").permission(READ).build());
        localACL.add(ACE.builder().user("joe_contributor").permission(WRITE).build());
        localACL.add(ACE.builder().user("joe_contributor").permission(CREATE_CHILD).build());

        localACL.add(ACE.builder().user("joe_localmanager").permission(READ).build());
        localACL.add(ACE.builder().user("joe_localmanager").permission(WRITE).build());
        localACL.add(ACE.builder().user("joe_localmanager").permission(CREATE_CHILD).build());
        localACL.add(ACE.builder().user("joe_localmanager").permission(DELETE).build());
        localACL.add(ACE.builder().user("joe_localmanager").permission(MANAGE_SECURITY).build());

        doc.setACL(localACL, true);

        // add the permission to remove children on the root
        ACL rootACL = root.getLocalACL();
        rootACL.add(ACE.builder().user("joe_localmanager").permission(DELETE_CHILD).build());
        root.setACL(rootACL, true);

        // make it visible for others
        session.save();
        return doc.getRef();
    }

    @Test
    public void testPermissionChecksBeforeCreate() {
        // create a document as an admin under root
        DocumentModel folder = session.createDocumentModel("/", "Test Folder", SYS_FOLDER);
        folder.create();
        session.save();

        // create a session for anotherUser user
        CoreSession anotherUserSession = openSessionAs("anotherUser");

        // anotherUser user cannot create document without Read on parent
        try {
            anotherUserSession.createDocumentModel(folder.getPathAsString(), "Test File", SYS_FILE);
            fail("should fail to create document model due to missing access to parent");
        } catch (DocumentSecurityException e) {
            assertEquals("Privilege 'Read' is not granted to 'anotherUser' on document '" + folder.getId() + "'", e.getMessage());
        }

        // grant anotherUser user permission Read on parent
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("anotherUser").permission(READ).build());
        folder.setACL(acl, false);
        session.save();

        // anotherUser still cannot cannot create as it doesn't have CreateChild
        try {
            DocumentModel file = anotherUserSession.createDocumentModel(folder.getPathAsString(), "Test File", SYS_FILE);
            file.create();
            fail("should fail to create document due to missing CreateChild permission");
        } catch (DocumentSecurityException e) {
            assertEquals(GENERIC_DOCUMENT_SECURITY_ERROR.formatted(CREATE_CHILD, "anotherUser", folder.getRef()), e.getMessage());
        }

        // grant anotherUser user permission CREATE_CHILD
        acl = new ACLImpl();
        acl.add(ACE.builder().user("anotherUser").permission(CREATE_CHILD).build());
        folder.setACL(acl, false);
        session.save();

        // now creation is possible
        // and we can call various write-like APIs on the uncreated file before the actual create
        DocumentModel file = anotherUserSession.createDocumentModel(folder.getPathAsString(), "Test File", SYS_FILE);
        // this requires Write
        file.setPropertyValue(SYS_TITLE, "hello");
        // this requires Write
        file.setPrimaryTypeAndMixinTypes(SYS_FILE, List.of());
        // this requires ManageSecurity
        acl = new ACLImpl();
        acl.add(ACE.builder().user("anotherUser").permission(WRITE).build());
        file.setACL(acl, false);
        // now create
        file.create();
    }

    @Test
    public void testPermissionChecksBeforeCreateBasic() {
        // create folder with user having permission Read and CreateChild
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("user").permission(READ).build());
        acl.add(ACE.builder().user("user").permission(CREATE_CHILD).build());
        folder.setACL(acl, true);
        folder.create();
        session.save();

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // creation is not possible because we miss Write
        CoreSession userSession = openSessionAs("user");
        DocumentModel file = userSession.createDocumentModel("/folder", "file", SYS_FILE);
        try {
            file.create();
            fail("should fail to create document due to missing Write permission");
        } catch (DocumentSecurityException e) {
            assertEquals(GENERIC_DOCUMENT_SECURITY_ERROR.formatted(WRITE, "user", file.getRef()), e.getMessage());
            // file and tx are now in an inconsistent state due to exception, reset
            TransactionHelper.setTransactionRollbackOnly();
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
            folder.refresh();
        }

        // now try with Write too
        acl = new ACLImpl();
        acl.add(ACE.builder().user("user").permission(WRITE).build());
        folder.setACL(acl, false);
        session.save();

        // now we can create
        file = userSession.createDocumentModel("/folder", "file", SYS_FILE);
        file.create();
    }

    @Test
    public void testReadAclSecurity() {
        // Check that all permissions that contain Read enable to list a
        // document using aclOptimization
        SecurityService securityService = Framework.getService(SecurityService.class);
        Set<String> readPermissions = securityService.getAllPermissionsContaining(READ);
        // Check for test permission contribution
        assertTrue(readPermissions.contains("ViewTest"));
        List<String> docNames = new ArrayList<>();
        DocumentModel root = session.getRootDocument();
        for (String permission : readPermissions) {
            // Create a folder with only the Read permission
            String name = "joe-has-" + permission + "-permission";
            docNames.add(name);
            DocumentModel folder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
            folder.create();
            ACL acl = new ACLImpl();
            acl.add(ACE.builder().user("joe").permission(permission).build());
            folder.setACL(acl, true);
        }
        session.save();

        CoreSession joeSession = openSessionAs("joe");
        DocumentModelList list;
        list = joeSession.query("SELECT * FROM SysFolder");
        List<String> names = new ArrayList<>();
        for (DocumentModel doc : list) {
            names.add(doc.getName());
        }
        assertEquals(readPermissions.size(), list.size(), "Expecting " + docNames + " got " + names);

        list = joeSession.query("SELECT * FROM SysFolder");
        names.clear();
        for (DocumentModel doc : list) {
            names.add(doc.getName());
        }
        assertEquals(readPermissions.size(), list.size(), "Expecting " + docNames + " got " + names);

        // Add a new folder to update the read acls
        DocumentModel folder = session.createDocumentModel(root.getPathAsString(), "new-folder", SYS_FOLDER);
        folder.create();
        ACL acl = folder.getLocalACL();
        acl.add(ACE.builder().user("joe").permission(READ).build());
        acl.add(ACE.builder().user("bob").permission(READ).build());
        folder.setACL(acl, true);
        session.save();

        list = joeSession.query("SELECT * FROM SysFolder");
        assertEquals(readPermissions.size() + 1, list.size());
    }

    @Test
    public void testReadAclSecurityUpdate() {
        // check that aclOptimization update the user aclr cache
        // NXP-13109
        DocumentModel root = session.getRootDocument();
        // Create a doc and set a new ACLR on it
        DocumentModel doc = session.createDocumentModel(root.getPathAsString(), "foo", SYS_FOLDER);
        doc.create();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().principal(EVERYONE).permission("Read").build());
        doc.setACL(acl, true);
        session.save();

        CoreSession joeSession = openSessionAs("joe");
        DocumentModelList list;
        list = joeSession.query("SELECT * FROM SysFolder");
        assertEquals(1, list.size());
        // Remove the document, so the ACLR created is not anymore assigned
        session.removeDocument(doc.getRef());
        session.save();
        list = joeSession.query("SELECT * FROM SysFolder");
        assertEquals(0, list.size());

        CoreSession bobSession = openSessionAs("bob");
        // Perform a query to init the ACLR cache
        list = bobSession.query("SELECT * FROM SysFolder");
        assertEquals(0, list.size());
        // Create a new doc with the same ACLR
        doc = session.createDocumentModel(root.getPathAsString(), "bar", SYS_FOLDER);
        doc.create();
        doc.setACL(acl, true);
        session.save();
        // Check that the ACLR has been added to the user cache
        list = bobSession.query("SELECT * FROM SysFolder");
        assertEquals(1, list.size());
    }

    @Test
    public void testReadAclAfterSetACL() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/folder", "doc", SYS_FILE);
        doc.create();
        session.save();

        // nothing can be seen by joe
        CoreSession joeSession = openSessionAs("joe");
        DocumentModelList list = joeSession.query("SELECT * FROM SysFolder");
        assertEquals(0, list.size());
        list = joeSession.query("SELECT * FROM SysFile");
        assertEquals(0, list.size());

        // set ACL on folder
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().principal(EVERYONE).permission("Read").build());
        folder.setACL(acl, true);
        session.save();

        // now joe sees things
        list = joeSession.query("SELECT * FROM SysFolder");
        assertEquals(1, list.size());
        assertEquals(folder.getId(), list.get(0).getId());
        list = joeSession.query("SELECT * FROM SysFile");
        assertEquals(1, list.size());
        assertEquals(doc.getId(), list.get(0).getId());
    }

    @Test
    public void testReadAclAfterCreate() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        session.save();

        // folder cannot be seen
        CoreSession joeSession = openSessionAs("joe");
        DocumentModelList list = joeSession.query("SELECT * FROM SysFolder");
        assertEquals(0, list.size());

        // set ACL on folder
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().principal(EVERYONE).permission("Read").build());
        folder.setACL(acl, true);
        session.save();

        // the folder can be seen by joe
        list = joeSession.query("SELECT * FROM SysFolder");
        assertEquals(1, list.size());
        assertEquals(folder.getId(), list.get(0).getId());

        // create a doc under the folder
        DocumentModel doc = session.createDocumentModel("/folder", "doc", SYS_FILE);
        doc.create();
        session.save();

        // the doc can be seen by joe
        list = joeSession.query("SELECT * FROM SysFile");
        assertEquals(1, list.size());
        assertEquals(doc.getId(), list.get(0).getId());
    }

    @Test
    public void testReadAclAfterMove() {
        DocumentModel folder1 = session.createDocumentModel("/", "folder1", SYS_FOLDER);
        folder1.create();
        DocumentModel folder2 = session.createDocumentModel("/", "folder2", SYS_FOLDER);
        folder2.create();
        DocumentModel doc = session.createDocumentModel("/folder1", "doc", SYS_FILE);
        doc.create();

        // set ACL on folder2
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().principal(EVERYONE).permission("Read").build());
        folder2.setACL(acl, true);
        session.save();

        // doc under folder1 cannot be read by joe
        CoreSession joeSession = openSessionAs("joe");
        DocumentModelList list = joeSession.query("SELECT * FROM SysFile");
        assertEquals(0, list.size());

        // move doc under folder2
        session.move(doc.getRef(), folder2.getRef(), null);
        session.save();

        // check doc now readable by joe
        list = joeSession.query("SELECT * FROM SysFile");
        assertEquals(1, list.size());
    }

    @Test
    public void testReadAclAfterCopy() {
        DocumentModel folder1 = session.createDocumentModel("/", "folder1", SYS_FOLDER);
        folder1.create();
        DocumentModel folder2 = session.createDocumentModel("/", "folder2", SYS_FOLDER);
        folder2.create();
        DocumentModel doc = session.createDocumentModel("/folder1", "doc", SYS_FILE);
        doc.create();

        // set ACL on folder2
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().principal(EVERYONE).permission("Read").build());
        folder2.setACL(acl, true);
        session.save();

        // doc under folder1 cannot be read by joe
        CoreSession joeSession = openSessionAs("joe");
        DocumentModelList list = joeSession.query("SELECT * FROM SysFile");
        assertEquals(0, list.size());

        // copy doc under folder2
        session.copy(doc.getRef(), folder2.getRef(), "doccopy");
        session.save();

        // check doc copy now readable by joe
        list = joeSession.query("SELECT * FROM SysFile");
        assertEquals(1, list.size());
        assertEquals("doccopy", list.get(0).getName());
    }

    @Test
    public void testEmptyLocalACL() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "folder", SYS_FOLDER);
        doc.create();
        ACL acl = doc.getLocalACL();
        // don't add anything
        doc.setACL(acl, true);
        session.save();

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        session = coreFeature.reopenCoreSession();

        doc = session.getDocument(doc.getRef());
        acl = doc.getLocalACL();
        assertTrue(acl.isEmpty());
    }

    @Test
    public void shouldRemoveDuplicateACE() throws Exception {
        // Using helper prevent adding duplicates
        ACL acl = new ACLImpl();
        ACE ace = ACE.builder().user("leela").permission("Read").build();
        ACE ace2 = ACE.builder().user("bob").permission("Read").build();
        assertTrue(acl.add(ace));
        assertTrue(acl.add(ace2));
        assertFalse(acl.add(ace));
        assertFalse(acl.add(ace2));
        assertFalse(acl.add(ace2));
        assertEquals(2, acl.size());
    }

    @Test
    public void testReadAclOnLargeTree() {
        String enabledProp = "nuxeo.core.readacl.async.enabled";
        String thresholdProp = "nuxeo.core.readacl.async.threshold";
        Framework.getProperties().put(enabledProp, "true");
        Framework.getProperties().put(thresholdProp, "10");
        try {
            doTestReadAclOnLargeTree();
        } finally {
            Framework.getProperties().remove(enabledProp);
            Framework.getProperties().remove(thresholdProp);
        }
    }

    protected void doTestReadAclOnLargeTree() {
        DocumentModel rootFolder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        rootFolder.create();
        String firstUser = "mickey";
        String secondUser = "minnie";

        // set ACL for first user on root folder
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user(firstUser).permission(READ).build());
        rootFolder.setACL(acl, true);

        int nbLevels = 10;
        int nbPerLevel = 10;
        DocumentModel parent = rootFolder;
        for (int level = 0; level < nbLevels; level++) {
            DocumentModel folder = session.createDocumentModel(parent.getPathAsString(), "folder-" + level, SYS_FOLDER);
            folder.create();
            for (int i = 0; i < nbPerLevel; i++) {
                DocumentModel doc = session.createDocumentModel(folder.getPathAsString(), "doc-" + level + "-" + i,
                        SYS_FILE);
                doc.create();
            }
            parent = folder;
        }
        session.save();
        int nbDocs = 1 + nbLevels * (nbPerLevel + 1);

        // check that only first user has access to everything, but not second user
        assertEquals(nbDocs, numberOfReadableDocuments(firstUser));
        assertEquals(0, numberOfReadableDocuments(secondUser));

        // set ACL for user on root folder
        acl.add(ACE.builder().user(secondUser).permission(READ).build());
        rootFolder.setACL(acl, true);
        session.save();

        // wait for asynchronous stuff to finish
        coreFeature.waitForAsyncCompletion();

        // check that both users now have access to everything
        assertEquals(nbDocs, numberOfReadableDocuments(firstUser));
        assertEquals(nbDocs, numberOfReadableDocuments(secondUser));
    }

    protected int numberOfReadableDocuments(String username) {
        CoreSession userSession = openSessionAs(username);
        String nxql = "SELECT sys_id FROM SysContent";
        PartialList<Map<String, Object>> pl = userSession.queryProjection(nxql, false, 0, 0, 0);
        return pl.size();
    }

    @Test
    public void testInvalidACL() {
        DocumentModel root = session.getRootDocument();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().principal("foo").permission("Read").build());
        try {
            root.setACL(acl, true);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("ACE principal 'foo' should be prefixed by 'u:' (user) or 'g:' (group) in foo:Read:true:::",
                    e.getMessage());
        }
    }

    @Test
    public void testGetEffectivePermissions() throws Exception {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/folder", "doc", SYS_FOLDER);
        doc.create();

        ACL folderAcl = folder.getLocalACL();
        folderAcl.add(ACE.builder().user("joe").permission(READ).build());
        folderAcl.add(ACE.builder().user("bob").permission(READ).build());
        folder.setACL(folderAcl, true);

        ACL docAcl = doc.getLocalACL();
        docAcl.add(ACE.builder().user("bob").permission(READ_WRITE).build());
        doc.setACL(docAcl, true);

        session.save();

        Set<String> allPerms = Set.of( //
                CREATE_CHILD, //
                CREATE_VERSION, //
                DELETE, //
                DELETE_CHILD, //
                MANAGE_LOCK, //
                MANAGE_RETENTION, //
                READ, //
                READ_VERSION, //
                READ_WRITE, //
                "ViewTest", //
                WRITE, //
                WRITE_VERSION, //
                MANAGE_SECURITY //
        );
        Set<String> readPerms = Set.of( //
                READ //
        );
        Set<String> readWritePerms = Set.of( //
                CREATE_CHILD, //
                CREATE_VERSION, //
                DELETE, //
                DELETE_CHILD, //
                READ, //
                READ_VERSION, //
                READ_WRITE, //
                WRITE, //
                WRITE_VERSION //
        );

        // for admin
        CoreSession adminSession = openAdminSessionAs("admin");
        assertEquals(allPerms, adminSession.getDocument(folder.getRef()).getEffectivePermissions());
        assertEquals(allPerms, adminSession.getDocument(doc.getRef()).getEffectivePermissions());

        // for joe
        CoreSession joeSession = openSessionAs("joe");
        assertEquals(readPerms, joeSession.getDocument(folder.getRef()).getEffectivePermissions());
        assertEquals(readPerms, joeSession.getDocument(doc.getRef()).getEffectivePermissions());

        // for bob
        CoreSession bobSession = openSessionAs("bob");
        assertEquals(readPerms, bobSession.getDocument(folder.getRef()).getEffectivePermissions());
        assertEquals(readWritePerms, bobSession.getDocument(doc.getRef()).getEffectivePermissions());

        // for someone else
        CoreSession otherSession = openSessionAs("some_random_dude");
        assertFalse(otherSession.exists(folder.getRef()));
        assertFalse(otherSession.exists(doc.getRef()));
    }

    @Test
    public void testGetEffectivePermissionsWithDeny() throws Exception {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/folder", "doc", SYS_FOLDER);
        doc.create();

        ACL folderAcl = folder.getLocalACL();
        folderAcl.add(ACE.builder().user("bob").permission(READ_WRITE).build());
        folder.setACL(folderAcl, true);

        ACL docAcl = doc.getLocalACL();
        docAcl.add(ACE.builder().user("bob").permission(WRITE).isGranted(false).build());
        doc.setACL(docAcl, true);

        session.save();

        Set<String> allPerms = Set.of( //
                CREATE_CHILD, //
                CREATE_VERSION, //
                DELETE, //
                DELETE_CHILD, //
                MANAGE_LOCK, //
                MANAGE_RETENTION, //
                MANAGE_SECURITY, //
                READ, //
                READ_VERSION, //
                READ_WRITE, //
                "ViewTest", //
                WRITE, //
                WRITE_VERSION //
        );
        Set<String> readWritePerms = Set.of( //
                CREATE_CHILD, //
                CREATE_VERSION, //
                DELETE, //
                DELETE_CHILD, //
                READ, //
                READ_VERSION, //
                READ_WRITE, //
                WRITE, //
                WRITE_VERSION //
        );
        Set<String> readWriteWithoutWritePerms = Set.of( //
                CREATE_CHILD, //
                CREATE_VERSION, //
                DELETE, //
                DELETE_CHILD, //
                READ, //
                READ_VERSION, //
                // no READ_WRITE
                // no WRITE
                WRITE_VERSION //
        );

        // for admin
        CoreSession adminSession = openAdminSessionAs("admin");
        assertEquals(allPerms, adminSession.getDocument(folder.getRef()).getEffectivePermissions());
        assertEquals(allPerms, adminSession.getDocument(doc.getRef()).getEffectivePermissions());

        // for bob
        CoreSession bobSession = openSessionAs("bob");
        assertEquals(readWritePerms, bobSession.getDocument(folder.getRef()).getEffectivePermissions());
        assertEquals(readWriteWithoutWritePerms, bobSession.getDocument(doc.getRef()).getEffectivePermissions());

        // for someone else
        CoreSession otherSession = openSessionAs("some_random_dude");
        assertFalse(otherSession.exists(folder.getRef()));
        assertFalse(otherSession.exists(doc.getRef()));
    }

    @Test
    public void testWriteVersionPermission() {
        // set up a document with a version
        DocumentModel doc = session.createDocumentModel("/", "doc", "MyDocType");
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().principal(EVERYONE).permission(READ).build());
        acl.add(ACE.builder().principal(EVERYONE).permission(WRITE).build());
        doc.setACL(acl, true);
        doc.create();
        DocumentRef verRef = session.checkIn(doc.getRef());
        session.save();

        // check that joe cannot set the version title or description
        CoreSession joeSession = openSessionAs("joe");
        DocumentModel ver = joeSession.getDocument(verRef);
        String title = "custom version title";
        try {
            ver.setPropertyValue(SYSVER_TITLE, title);
            fail("should fail to set title due to missing WriteVersion permission");
        } catch (DocumentSecurityException e) {
            assertEquals(GENERIC_DOCUMENT_SECURITY_ERROR.formatted(WRITE_VERSION, "joe", ver.getRef()), e.getMessage());
        }
        String description = "custom version description";
        try {
            ver.setPropertyValue(SYSVER_DESCRIPTION, description);
            fail("should fail to set title due to missing WriteVersion permission");
        } catch (DocumentSecurityException e) {
            assertEquals(GENERIC_DOCUMENT_SECURITY_ERROR.formatted(WRITE_VERSION, "joe", ver.getRef()), e.getMessage());
        }

        // grant joe the WriteVersion permission
        acl.add(ACE.builder().user("joe").permission(WRITE_VERSION).build());
        doc.setACL(acl, true);
        session.save();

        // check that joe can now set the version title and description
        joeSession = openSessionAs("joe");
        ver = joeSession.getDocument(verRef);
        ver.setPropertyValue(SYSVER_TITLE, title);
        ver.setPropertyValue(SYSVER_DESCRIPTION, description);
        joeSession.save();

        // check that the version title and description were set
        ver = session.getDocument(verRef);
        assertEquals(title, ver.getPropertyValue(SYSVER_TITLE));
        assertEquals(description, ver.getPropertyValue(SYSVER_DESCRIPTION));
    }

}
