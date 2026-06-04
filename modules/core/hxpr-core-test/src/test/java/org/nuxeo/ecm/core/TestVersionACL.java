/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ_VERSION;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.util.Iterator;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.version.test.AbstractTestVersioning;

public class TestVersionACL extends AbstractTestVersioning {

    @Test
    public void testVersionACL() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        ACL acl = new ACLImpl();
        acl.add(ACE.BLOCK);
        acl.add(ACE.builder().user("user1").permission(READ).build());
        session.setACL(folder.getRef(), acl, true);

        DocumentModel file = session.createDocumentModel("/folder", "file", MY_FILE);
        file.create();
        acl = new ACLImpl();
        acl.add(ACE.builder().user("user2").permission(READ).build());
        session.setACL(file.getRef(), acl, true);

        // create a version
        DocumentRef verRef = session.checkIn(file.getRef());
        DocumentModel ver = session.getDocument(verRef);
        String verId = ver.getId();
        // add ACL on version itself
        acl = new ACLImpl();
        acl.add(ACE.builder().user("user3").permission(READ).build());
        session.setACL(verRef, acl, true);

        session.save();
        coreFeature.waitForAsyncCompletion(); // DBS read ACL computation is async

        // check ACLs on the version
        ver.refresh();
        acl = ver.getEffectiveACL();
        assertEquals(3, acl.size());
        Iterator<ACE> aceit = acl.iterator();
        assertEquals(ACE.USER_PREFIX + "user3", aceit.next().getPrincipal());
        assertEquals(ACE.USER_PREFIX + "user2", aceit.next().getPrincipal());
        assertEquals(ACE.USER_PREFIX + "user1", aceit.next().getPrincipal());

        // check Read permission using CoreSession document API
        assertCanRead(false, verRef, "nosuchuser");
        assertCanRead(true, verRef, "user1");
        assertCanRead(true, verRef, "user2");
        assertCanRead(true, verRef, "user3");

        // check Read permission using CoreSession query API
        assertCanQuery(false, verId, "nosuchuser");
        assertCanQuery(true, verId, "user1");
        assertCanQuery(true, verId, "user2");
        assertCanQuery(true, verId, "user3");
    }

    @Test
    public void testReadVersionPermissionOnDocument() {
        doTestReadVersionPermission(true);
    }

    @Test
    public void testReadVersionPermissionOnFolder() {
        doTestReadVersionPermission(false);
    }

    protected void doTestReadVersionPermission(boolean aclOnDocument) {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();

        DocumentModel file = session.createDocumentModel("/folder", "file", MY_FILE);
        file.create();

        DocumentModel aclCarrier = aclOnDocument ? file : folder;
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("user1").permission(READ_VERSION).build());
        acl.add(ACE.BLOCK);
        session.setACL(aclCarrier.getRef(), acl, true);

        // create a version
        DocumentRef verRef = session.checkIn(file.getRef());
        DocumentModel ver = session.getDocument(verRef);
        String verId = ver.getId();

        session.save();
        coreFeature.waitForAsyncCompletion(); // DBS read ACL computation is async
        ver.refresh();

        // check ACLs on the version
        acl = ver.getEffectiveACL();
        assertEquals(2, acl.size());
        assertEquals(ACE.builder().user("user1").permission(READ_VERSION).build(), acl.get(0));
        assertEquals(ACE.builder().user("user1").permission(READ).build(), acl.get(1));

        // check Read permission using CoreSession document API
        assertCanRead(false, verRef, "nosuchuser");
        assertCanRead(true, verRef, "user1");

        // check Read permission using CoreSession query API
        assertCanQuery(false, verId, "nosuchuser");
        assertCanQuery(true, verId, "user1");
    }

    protected void assertCanRead(boolean expected, DocumentRef docRef, String user) {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(),
                DummyLoginFeature.getTestPrincipal(user, false));
        assertEquals(expected, userSession.exists(docRef));
    }

    protected void assertCanQuery(boolean expected, String docId, String user) {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(),
                DummyLoginFeature.getTestPrincipal(user, false));
        DocumentModelList res = userSession.query(String.format("SELECT * FROM SysContent WHERE sys_id = '%s'", docId));
        // first check the pure query, without accessing the document
        int size = res.size();
        assertTrue(size <= 1, String.valueOf(size));
        assertEquals(expected, size == 1);
    }

}
