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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CONTRIBUTORS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LAST_CONTRIBUTOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.StorageConfiguration;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
public class TestSysCreatedModified {

    private static final String MY_FILE = "MyFile";

    @Inject
    protected CoreFeature feature;

    @Inject
    protected CoreSession session;

    protected StorageConfiguration storageConfiguration;

    @BeforeEach
    public void before() {
        storageConfiguration = feature.getStorageConfiguration();
    }

    protected CoreSession getCoreSession(String username) {
        return CoreInstance.getCoreSession(null, DummyLoginFeature.getTestPrincipal(username, false));
    }

    private DocumentModelList getVersions(DocumentModel doc) {
        return session.query(
                "SELECT * FROM SysContent WHERE sys_parentId = '%s' AND sysver_isVersion = TRUE ORDER BY sysver_version".formatted(
                        doc.getId()));
    }

    @Test
    public void testCreationDateAndCreator() {
        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();
        session.save();
        doc.refresh();
        assertNotNull(doc.getPropertyValue(SYS_CREATED));
        assertEquals(TEST_ADMIN_USER, doc.getPropertyValue(SYS_CREATOR));
    }

    @Test
    public void testModificationDate() {
        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();
        session.save();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        doc.setPropertyValue(SYS_TITLE, "toto");

        session.save();
        doc.refresh();

        Instant created = (Instant) doc.getPropertyValue(SYS_CREATED);
        assertNotNull(created);

        Instant modified = (Instant) doc.getPropertyValue(SYS_MODIFIED);
        assertNotNull(modified);

        assertTrue(modified.isAfter(created), modified + " !> " + created);
    }

    // Wait until we can have a real list management
    @Test
    public void testContributors() {
        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();
        session.save();
        doc.refresh();

        String author = (String) doc.getPropertyValue(SYS_CREATOR);
        assertEquals(TEST_ADMIN_USER, author);

        List<String> contributorsList = (List<String>) doc.getPropertyValue(SYS_CONTRIBUTORS);
        assertTrue(contributorsList.contains(TEST_ADMIN_USER));

        // modify security to test with a new user

        DocumentModel root = session.getRootDocument();
        ACL acl = root.getLocalACL();
        acl.add(ACE.builder().user("Jacky").permission(SecurityConstants.EVERYTHING).build());
        root.setACL(acl, true);

        // create a new session
        session.save();

        CoreSession session2 = getCoreSession("Jacky");
        DocumentModel doc2 = session2.getDocument(doc.getRef());
        doc2.setPropertyValue(SYS_TITLE, "testing");
        session2.save();
        doc2.refresh();

        contributorsList = (List<String>) doc2.getPropertyValue(SYS_CONTRIBUTORS);
        assertTrue(contributorsList.contains("Jacky"));
        assertEquals(TEST_ADMIN_USER, doc2.getPropertyValue(SYS_CREATOR));
    }

    @Test
    public void testLastContributor() {
        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();
        session.save();
        doc.refresh();

        String lastContributor = (String) doc.getPropertyValue(SYS_LAST_CONTRIBUTOR);
        assertEquals(TEST_ADMIN_USER, lastContributor);

        List<String> contributorsList = (List<String>) doc.getPropertyValue(SYS_CONTRIBUTORS);
        assertTrue(contributorsList.contains(TEST_ADMIN_USER));

        // modify security to test with tow new user
        DocumentModel root = session.getRootDocument();
        ACL acl = root.getLocalACL();
        acl.add(ACE.builder().user("Jacky").permission(SecurityConstants.EVERYTHING).build());
        acl.add(ACE.builder().user("Fredo").permission(SecurityConstants.EVERYTHING).build());
        root.setACL(acl, true);
        session.save();

        // create a new session
        CoreSession session2 = getCoreSession("Jacky");
        DocumentModel doc2 = session2.getDocument(doc.getRef());
        doc2.setPropertyValue(SYS_TITLE, "testing");
        session2.save();
        doc2.refresh();

        contributorsList = (List<String>) doc2.getPropertyValue(SYS_CONTRIBUTORS);
        assertTrue(contributorsList.contains("Jacky"));
        assertEquals(1, contributorsList.indexOf("Jacky"));
        assertEquals("Jacky", doc2.getPropertyValue(SYS_LAST_CONTRIBUTOR));
        session2.save();

        // Test if a new contributor will be at the end of the list
        CoreSession session3 = getCoreSession("Fredo");
        doc2 = session3.getDocument(doc.getRef());
        doc2.setPropertyValue(SYS_TITLE, "testing2"); // make a change
        session3.save();
        doc2.refresh();

        contributorsList = (List<String>) doc2.getPropertyValue(SYS_CONTRIBUTORS);
        assertTrue(contributorsList.contains("Fredo"));
        assertEquals("Fredo", doc2.getPropertyValue(SYS_LAST_CONTRIBUTOR));
        session3.save();

        // Test if a previously contributor will be moved to the end of the list
        CoreSession session4 = CoreInstance.getCoreSession(session.getRepositoryName());
        doc2 = session4.getDocument(doc.getRef());
        doc2.setPropertyValue(SYS_TITLE, "testing");
        session4.save();
        doc2.refresh();

        contributorsList = (List<String>) doc2.getPropertyValue(SYS_CONTRIBUTORS);
        assertTrue(contributorsList.contains(TEST_ADMIN_USER));
        assertEquals(TEST_ADMIN_USER, doc2.getPropertyValue(SYS_LAST_CONTRIBUTOR));
    }

    @Test
    public void testLastContributorForSystemSession() {
        // use a system session with no originating user name
        CoreSession session2 = CoreInstance.getCoreSessionSystem(session.getRepositoryName(), null);
        DocumentModel file = session2.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        session2.save();
        file.refresh();
        // check we haven't inserted a null in last contributors
        assertEquals(Collections.singletonList(SecurityConstants.SYSTEM_USERNAME),
                file.getPropertyValue(SYS_CONTRIBUTORS));
    }

    @Test
    public void testContributorsAndModifiedDoesNotChangeIfThereAreNoChanges() {
        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();
        // backup the data to check
        Instant modified = (Instant) doc.getPropertyValue(SYS_MODIFIED);
        String lastContributor = (String) doc.getPropertyValue(SYS_LAST_CONTRIBUTOR);
        @SuppressWarnings("unchecked")
        List<String> contributors = (List<String>) doc.getPropertyValue(SYS_CONTRIBUTORS);
        // get the data to check
        Instant modified2 = (Instant) doc.getPropertyValue(SYS_MODIFIED);
        String lastContributor2 = (String) doc.getPropertyValue(SYS_LAST_CONTRIBUTOR);
        @SuppressWarnings("unchecked")
        List<String> contributors2 = (List<String>) doc.getPropertyValue(SYS_CONTRIBUTORS);
        assertEquals(modified, modified2);
        assertEquals(lastContributor, lastContributor2);
        assertEquals(contributors, contributors2);
    }

    @Test
    public void testCreatorForUnrestrictedSessionCreatedDoc() {
        CoreSession session2 = getCoreSession("Jacky");
        CreateDocumentUnrestricted runner = new CreateDocumentUnrestricted(session2);
        runner.runUnrestricted();
        String creator = runner.getCreator();
        assertEquals("Jacky", creator);
    }

    public class CreateDocumentUnrestricted extends UnrestrictedSessionRunner {

        private String creator;

        public CreateDocumentUnrestricted(CoreSession session) {
            super(session);
        }

        @Override
        public void run() {
            DocumentModel folder = session.createDocumentModel("/", "testfolder1", SYS_FOLDER);
            folder.create();
            session.save();
            folder.refresh();
            creator = (String) folder.getPropertyValue(SYS_CREATOR);
        }

        public String getCreator() {
            return creator;
        }

    }

    @Test
    public void testCopyDocument() {
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.create();
        storageConfiguration.maybeSleepToNextSecond();
        DocumentModel copy = session.copy(file.getRef(), file.getParentRef(), "filecopy");

        waitForAsyncCompletion();

        assertNotNull(copy);
        // with the new system-based create/modified update, all documents have new values
        // this is a change in behavior from previous code
        assertNotEquals(file.getPropertyValue(SYS_CREATED), copy.getPropertyValue(SYS_CREATED));
        assertNotEquals(file.getPropertyValue(SYS_MODIFIED), copy.getPropertyValue(SYS_MODIFIED));
    }

    @Test
    public void testCopyDocumentWithResetCoreMetadata() {
        DocumentModel file = session.createDocumentModel("/", "file-007", SYS_FILE);
        file.create();
        storageConfiguration.maybeSleepToNextSecond();
        DocumentModel copy = session.copy(file.getRef(), file.getParentRef(), "file-008");
        session.save();

        waitForAsyncCompletion();

        assertNotNull(copy);
        assertNotEquals(file.getPropertyValue(SYS_CREATED), copy.getPropertyValue(SYS_CREATED));
        assertNotEquals(file.getPropertyValue(SYS_MODIFIED), copy.getPropertyValue(SYS_MODIFIED));
    }

    @Test
    public void testListenerRunsAtCreationBeforeVersioning() {
        DocumentModel note = session.createDocumentModel("/", "file", MY_FILE);
        note.create();
        note.checkIn();
        session.save();
        // note is checked in after auto-versioning
        assertFalse(note.isCheckedOut());
        // note's created/modified info was correctly filled in
        Instant created = (Instant) note.getPropertyValue(SYS_CREATED);
        assertNotNull(created);
        assertEquals(TEST_ADMIN_USER, note.getPropertyValue(SYS_LAST_CONTRIBUTOR));
        // one version exists
        List<DocumentModel> versions = getVersions(note);
        assertEquals(1, versions.size());
        DocumentModel version = versions.get(0);
        assertEquals(1, (long) version.getVersion());
        // version's created/modified info was correctly filled in
        Instant versionCreated = (Instant) version.getPropertyValue(SYS_CREATED);
        assertNotNull(versionCreated);
        assertEquals(created, versionCreated);
        assertEquals(TEST_ADMIN_USER, version.getPropertyValue(SYS_LAST_CONTRIBUTOR));
    }

    protected void waitForAsyncCompletion() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

}
