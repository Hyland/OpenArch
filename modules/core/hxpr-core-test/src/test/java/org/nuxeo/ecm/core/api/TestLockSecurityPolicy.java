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

package org.nuxeo.ecm.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ_WRITE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestLockSecurityPolicy {

    protected static String USER1 = "jsmith";

    protected static String USER2 = "jdoe";

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession adminSession;

    public static void checkLockPermissions(CoreSession session, DocumentRef docRef, boolean canWrite) {
        assertEquals(canWrite, session.hasPermission(docRef, WRITE));
        assertTrue(session.hasPermission(docRef, READ));
    }

    @Test
    public void testLockSecurityPolicy() throws Exception {
        DocumentModel root = adminSession.getRootDocument();
        // add read/write to user1 and user2 with admin session
        ACL acl = root.getLocalACL();
        acl.add(ACE.builder().user(USER1).permission(READ_WRITE).build());
        acl.add(ACE.builder().user(USER2).permission(READ_WRITE).build());
        root.setACL(acl, true);
        adminSession.save();

        // create folder
        CoreSession session1 = coreFeature.getCoreSession(USER1);
        checkLockPermissions(session1, root.getRef(), true);
        DocumentModel folder = session1.createDocumentModel(root.getPathAsString(), "folder#1", SYS_FOLDER);
        folder.create();
        DocumentRef folderRef = folder.getRef();

        // write granted to user1 on folder
        checkLockPermissions(session1, folderRef, true);

        CoreSession session2 = coreFeature.getCoreSession(USER2);
        // write granted to user2 on folder
        checkLockPermissions(session2, folderRef, true);

        // set lock on created folder for user2
        folder = session2.getDocument(folderRef);
        folder.setLock();

        // write still granted for user2
        checkLockPermissions(session2, folderRef, true);
        session2.save();
        // even after session save
        checkLockPermissions(session2, folderRef, true);

        // write denied to user1
        checkLockPermissions(session1, folderRef, false);

        // write not denied to admin user
        checkLockPermissions(adminSession, folderRef, true);
    }

}
