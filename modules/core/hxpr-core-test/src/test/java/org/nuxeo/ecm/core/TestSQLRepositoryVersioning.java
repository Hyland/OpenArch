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

import static jakarta.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_TEMP;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_MINOR_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_TITLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.api.model.VersionNotModifiableException;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;

import jakarta.inject.Inject;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
public class TestSQLRepositoryVersioning {

    private static final String MY_FILE = "MyFile";

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    protected CoreSession openSessionAs(NuxeoPrincipal pal) {
        return CoreInstance.getCoreSession(session.getRepositoryName(), pal);
    }

    /**
     * Sleep 1s, useful for stupid databases (like MySQL) that don't have subsecond resolution in TIMESTAMP fields.
     */
    protected void maybeSleepToNextSecond() {
        coreFeature.getStorageConfiguration().maybeSleepToNextSecond();
    }

    protected void waitForFulltextIndexing() {
        nextTransaction();
        coreFeature.getStorageConfiguration().waitForFulltextIndexing();
    }

    protected void nextTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    private DocumentModelList getVersions(DocumentModel doc) {
        return session.query(
                "SELECT * FROM SysContent WHERE sys_parentId = '%s' AND sysver_isVersion = TRUE ORDER BY sysver_version".formatted(
                        doc.getId()));
    }

    @Test
    public void testCreateVersionsManyTimes() {
        for (int i = 0; i < 10; i++) {
            createVersions(i);
        }
    }

    protected void createVersions(int i) {
        DocumentModel folder = session.createDocumentModel("/", "fold" + i, SYS_FOLDER);
        folder.create();
        DocumentModel file = session.createDocumentModel("/fold" + i, "file", MY_FILE);
        file.create();
        createTrioVersions(file);
    }

    @Test
    public void testRemoveSingleDocVersion() {
        DocumentModel folder = session.createDocumentModel("/", "folder#1", SYS_FOLDER);
        folder.create();

        DocumentModel file = session.createDocumentModel(folder.getPathAsString(), "file#1", MY_FILE);
        file.create();

        checkVersions(file);

        file.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("B", "text/plain", "UTF-8", "A"));
        file.checkIn();
        file.checkOut(); // to allow deleting last version

        checkVersions(file, 1);

        DocumentModel lastversion = getVersions(file).get(0);
        assertNotNull(lastversion);

        assertTrue(lastversion.isVersion());
        session.removeDocument(lastversion.getRef());

        checkVersions(file);
    }

    // Creates 3 versions and removes the first.
    @Test
    public void testRemoveFirstDocVersion() {
        DocumentModel folder = session.createDocumentModel("/", "folder#1", SYS_FOLDER);
        folder.create();

        DocumentModel file = session.createDocumentModel(folder.getPathAsString(), "file#1", MY_FILE);
        file.create();

        createTrioVersions(file);

        DocumentModel firstversion = getVersions(file).get(0);
        assertNotNull(firstversion);

        assertTrue(firstversion.isVersion());
        session.removeDocument(firstversion.getRef());

        checkVersions(file, 2, 3);
    }

    // Creates 3 versions and removes the second.
    @Test
    public void testRemoveMiddleDocVersion() {
        DocumentModel folder = session.createDocumentModel("/", "folder#1", SYS_FOLDER);
        folder.create();

        DocumentModel file = session.createDocumentModel(folder.getPathAsString(), "file#1", MY_FILE);
        file.create();

        createTrioVersions(file);

        DocumentModel version = getVersions(file).get(1);
        assertNotNull(version);

        assertTrue(version.isVersion());
        session.removeDocument(version.getRef());

        checkVersions(file, 1, 3);
    }

    // Creates 3 versions and removes the last.
    @Test
    public void testRemoveLastDocVersion() {
        DocumentModel folder = session.createDocumentModel("/", "folder#1", SYS_FOLDER);
        folder.create();

        DocumentModel file = session.createDocumentModel(folder.getPathAsString(), "file#1", MY_FILE);
        file.create();

        createTrioVersions(file);

        DocumentModel lastversion = getVersions(file).get(2);
        assertNotNull(lastversion);

        assertTrue(lastversion.isVersion());
        session.removeDocument(lastversion.getRef());

        checkVersions(file, 1, 2);
    }

    private void createTrioVersions(DocumentModel file) {
        // create a first version
        file.setPropertyValue(SYSFILE_BLOB, new StringBlob("A"));
        file.checkIn();

        checkVersions(file, 1);

        // create a second version
        // make it dirty so it will be saved
        file.setPropertyValue(SYSFILE_BLOB, new StringBlob("B"));
        maybeSleepToNextSecond();
        file.checkIn();

        checkVersions(file, 1, 2);

        // create a third version
        file.setPropertyValue(SYSFILE_BLOB, new StringBlob("C"));
        maybeSleepToNextSecond();
        file.checkIn();
        file.checkOut(); // to allow deleting last version

        checkVersions(file, 1, 2, 3);
    }

    private void checkVersions(DocumentModel doc, long... versions) {
        List<Long> actual = new ArrayList<>();
        for (DocumentModel ver : getVersions(doc)) {
            assertTrue(ver.isVersion());
            actual.add(ver.getVersion());
        }
        assertEquals(List.of(ArrayUtils.toObject(versions)), actual);
    }

    @Test
    public void testCheckInCheckOut() {
        DocumentModel doc = session.createDocumentModel("/", "file#789", MY_FILE);
        assertTrue(doc.isCheckedOut());
        doc.create();
        assertTrue(session.isCheckedOut(doc.getRef()));
        assertTrue(doc.isCheckedOut());
        session.save();
        assertTrue(session.isCheckedOut(doc.getRef()));
        assertTrue(doc.isCheckedOut());

        DocumentRef verRef = session.checkIn(doc.getRef());
        DocumentModel ver = session.getDocument(verRef);
        assertTrue(ver.isVersion());
        doc.refresh();
        assertFalse(session.isCheckedOut(doc.getRef()));
        assertFalse(doc.isCheckedOut());

        session.checkOut(doc.getRef());
        assertTrue(session.isCheckedOut(doc.getRef()));

        // using DocumentModel API
        DocumentRef verRef2 = doc.checkIn();
        DocumentModel ver2 = session.getDocument(verRef2);
        assertTrue(ver2.isVersion());
        assertFalse(doc.isCheckedOut());
        doc.checkOut();
        assertTrue(doc.isCheckedOut());
    }

    @Test
    public void testAutoCheckOut() {
        DocumentModel doc = session.createDocumentModel("/", "file", MY_FILE);
        doc.setPropertyValue(SYS_TITLE, "t0");
        doc.create();
        assertTrue(doc.isCheckedOut());
        session.checkIn(doc.getRef());
        doc.refresh();
        assertFalse(doc.isCheckedOut());

        // an ignored system-owned write does not auto-checkout
        doc.putContextData(CoreSession.USER_CHANGE, Boolean.TRUE);
        doc.setPropertyValue(SYS_NAME, "ignored");
        assertEquals("file", doc.getName());
        assertFalse(doc.isCheckedOut());

        // repository metadata updates do not auto-checkout
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("bob").permission("Read").build());
        doc.setPropertyValue(SYS_ACL, acl);
        assertFalse(doc.isCheckedOut());

        // auto-checkout
        doc.setPropertyValue(SYS_TITLE, "t1");
        assertTrue(doc.isCheckedOut());

        session.checkIn(doc.getRef());
        doc.refresh();
        assertFalse(doc.isCheckedOut());

        // disable auto-checkout
        doc.setPropertyValue(SYS_TITLE, "t2");
        doc.checkIn();
        assertFalse(doc.isCheckedOut());
        assertEquals("t2", doc.getPropertyValue(SYS_TITLE));

        // can still be checked out normally afterwards
        doc.checkOut();
        assertTrue(doc.isCheckedOut());
        assertEquals("t2", doc.getPropertyValue(SYS_TITLE));
    }

    @Test
    public void testRestoreToVersion() {
        String name2 = "file#456";
        DocumentModel doc = session.createDocumentModel("/", name2, MY_FILE);
        doc.create();
        DocumentRef docRef = doc.getRef();

        session.save();
        DocumentRef v1Ref = session.checkIn(docRef);
        assertFalse(session.isCheckedOut(docRef));
        session.checkOut(docRef);
        assertTrue(session.isCheckedOut(docRef));

        doc.setPropertyValue(SYS_TITLE, "f1");
        doc.setPropertyValue(SYS_DESCRIPTION, "desc 1");
        session.save();

        maybeSleepToNextSecond();
        DocumentRef v2Ref = session.checkIn(docRef);
        session.checkOut(docRef);

        DocumentModel newDoc = session.getDocument(docRef);
        assertNotNull(newDoc);
        assertNotNull(newDoc.getRef());
        assertEquals("desc 1", newDoc.getPropertyValue(SYS_DESCRIPTION));

        session.save();
        waitForFulltextIndexing();
        maybeSleepToNextSecond();
        DocumentModel restoredDoc = session.restoreVersion(v1Ref);

        assertNotNull(restoredDoc);
        assertNotNull(restoredDoc.getRef());
        assertNull(restoredDoc.getPropertyValue(SYS_DESCRIPTION));

        session.save();
        waitForFulltextIndexing();
        maybeSleepToNextSecond();
        restoredDoc = session.restoreVersion(v2Ref);

        assertNotNull(restoredDoc);
        assertNotNull(restoredDoc.getRef());
        assertEquals("desc 1", restoredDoc.getPropertyValue(SYS_DESCRIPTION));
    }

    @Test
    public void testRestoreInvalidations() throws Exception {
        final NuxeoPrincipal pal = session.getPrincipal();
        DocumentModel doc = session.createDocumentModel("/", "myfile", MY_FILE);
        doc.setPropertyValue(SYS_TITLE, "t1");
        doc.create();
        final DocumentRef docRef = doc.getRef();
        DocumentRef v1 = session.checkIn(docRef);
        session.checkOut(docRef);
        doc.setPropertyValue(SYS_TITLE, "t2");
        session.save();

        waitForFulltextIndexing();

        // we need 2 threads to get 2 different sessions that send each other invalidations
        final CyclicBarrier barrier = new CyclicBarrier(2);
        Throwable[] throwables = new Throwable[2];
        Thread t1 = new Thread(() -> {
            TransactionHelper.startTransaction();
            try {
                CoreSession session = openSessionAs(pal);
                DocumentModel doc1 = session.getDocument(docRef);
                assertEquals("t2", doc1.getPropertyValue(SYS_TITLE));
                // 1. sync
                barrier.await(30, TimeUnit.SECONDS); // (throws on timeout)
                // 2. restore and next tx to send invalidations
                DocumentModel restored = session.restoreVersion(v1);
                assertEquals("t1", restored.getPropertyValue(SYS_TITLE));
                session.save();
                nextTransaction();
                // 3. sync
                barrier.await(30, TimeUnit.SECONDS); // (throws on timeout)
                // 4. wait
            } catch (InterruptedException t) {
                Thread.currentThread().interrupt();
                throwables[0] = t;
            } catch (Exception | AssertionError t) {
                throwables[0] = t;
            } finally {
                TransactionHelper.commitOrRollbackTransaction();
            }
        });
        Thread t2 = new Thread(() -> {
            TransactionHelper.startTransaction();
            try {
                CoreSession session = openSessionAs(pal);
                DocumentModel doc12 = session.getDocument(docRef);
                assertEquals("t2", doc12.getPropertyValue(SYS_TITLE));
                // 1. sync
                barrier.await(30, TimeUnit.SECONDS); // (throws on timeout)
                // 2. nop
                // 3. sync
                barrier.await(30, TimeUnit.SECONDS); // (throws on timeout)
                // 4. next tx to get invalidations and check
                nextTransaction();
                DocumentModel restored = session.getDocument(docRef);
                assertEquals("t1", restored.getPropertyValue(SYS_TITLE));
            } catch (InterruptedException t) {
                Thread.currentThread().interrupt();
                throwables[1] = t;
            } catch (Exception | AssertionError t) {
                throwables[1] = t;
            } finally {
                TransactionHelper.commitOrRollbackTransaction();
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        AssertionError assertionError = null;
        for (Throwable t : throwables) {
            if (t != null) {
                if (assertionError == null) {
                    assertionError = new AssertionError("Exceptions in threads");
                }
                assertionError.addSuppressed(t);
            }
        }
        if (assertionError != null) {
            throw assertionError;
        }
    }

    @Test
    public void testGetDocumentWithVersion() {
        String name2 = "file#248";
        DocumentModel childFile = session.createDocumentModel("/", name2, MY_FILE);
        childFile.create();
        session.save();
        DocumentRef v1Ref = session.checkIn(childFile.getRef());
        session.checkOut(childFile.getRef());

        childFile.setPropertyValue(SYS_TITLE, "f1");
        childFile.setPropertyValue(SYS_DESCRIPTION, "desc 1");
        session.save();
        maybeSleepToNextSecond();
        DocumentRef v2Ref = session.checkIn(childFile.getRef());

        DocumentModel newDoc = session.getDocument(childFile.getRef());
        assertNotNull(newDoc);
        assertNotNull(newDoc.getRef());
        assertEquals("desc 1", newDoc.getPropertyValue(SYS_DESCRIPTION));

        // restore, no snapshot as already pristine
        waitForFulltextIndexing();
        maybeSleepToNextSecond();
        DocumentModel restoredDoc = session.restoreVersion(v1Ref);

        assertNotNull(restoredDoc);
        assertNotNull(restoredDoc.getRef());
        assertNull(restoredDoc.getPropertyValue(SYS_DESCRIPTION));
    }

    @Test
    public void testVersionRemoval() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel file = session.createDocumentModel("/folder", "file", MY_FILE);
        file.create();
        DocumentRef verRef = session.checkIn(file.getRef());
        session.save();

        // we can remove the version
        session.removeDocument(verRef);
        session.save();
    }

    @Test
    public void testCopyCheckedIn() {
        DocumentModel doc = session.createDocumentModel("/", "file", MY_FILE);
        doc.create();
        doc.checkIn();
        session.save();
        assertFalse(doc.isCheckedOut());
        assertEquals(1, (long) doc.getVersion());

        // copy
        DocumentModel copy = session.copy(doc.getRef(), session.getRootDocument().getRef(), "fileCopied");

        assertTrue(copy.isCheckedOut());
        assertEquals(0, (long) copy.getVersion());
    }

    @Test
    public void testVersionLabelOfCopy() {
        DocumentModel doc = session.createDocumentModel("/", "file", MY_FILE);
        doc.create();
        session.save();
        DocumentModel copy = session.copy(doc.getRef(), new PathRef("/"), "copy");
        DocumentRef versionRef = session.checkIn(copy.getRef());
        DocumentModel version = session.getDocument(versionRef);

        // check version
        assertEquals(1, (long) version.getVersion());
    }

    @Test
    public void testVersionNotNullOnCopy() {
        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();
        session.save();
        DocumentModel copy = session.copy(doc.getRef(), new PathRef("/"), "copy");
        assertEquals(0, (long) copy.getVersion());
    }

    @Test
    public void testCmisProperties() throws Exception {

        /*
         * checked out doc (live; private working copy)
         */

        DocumentModel doc = session.createDocumentModel("/", "myfile", MY_FILE);
        doc.create();

        assertTrue(doc.isCheckedOut()); // nuxeo prop, false only on live
        assertFalse(doc.isVersion());
        assertEquals(0, (long) doc.getVersion());

        /*
         * checked in doc
         */

        DocumentRef verRef = doc.checkIn();
        session.save();
        DocumentModel ver = session.getDocument(verRef);

        assertFalse(doc.isCheckedOut());
        assertFalse(doc.isVersion());
        // assertTrue(doc.isImmutable()); // debatable
        assertEquals(1, (long) doc.getVersion());

        /*
         * version
         */

        // assertFalse(ver.isCheckedOut()); // TODO
        assertTrue(ver.isVersion());
        assertEquals(1, (long) ver.getVersion());
        assertEquals(doc.getId(), session.getWorkingCopy(ver.getRef()).getId());

        /*
         * re-checked out doc
         */

        doc.checkOut();
        ver.refresh();

        assertTrue(doc.isCheckedOut());
        assertFalse(doc.isVersion());
        assertEquals(1, (long) doc.getVersion());

        /*
         * checkin
         */

        Thread.sleep(2); // make sure the second version doesn't have the same "created" as the first
        DocumentRef majRef = doc.checkIn();
        DocumentModel maj = session.getDocument(majRef);
        ver.refresh();

        assertEquals(doc.getId(), session.getWorkingCopy(maj.getRef()).getId());
        // previous ver
        assertEquals(doc.getId(), session.getWorkingCopy(ver.getRef()).getId());
    }

    @Test
    public void testSaveRestoredVersionWithVersionAutoIncrement() {
        // check-in version 1, 2 and restore version 1
        DocumentModel doc = session.createDocumentModel("/", "myfile", MY_FILE);
        doc.create();
        DocumentRef co = doc.getRef();
        DocumentRef ci1 = session.checkIn(co);
        session.checkOut(co);
        maybeSleepToNextSecond();
        session.checkIn(co);
        waitForFulltextIndexing();
        maybeSleepToNextSecond();
        session.restoreVersion(ci1);

        // after restore checked out and at version 2
        doc = session.getDocument(co);
        assertTrue(doc.isCheckedOut());
        assertEquals(2, (long) doc.getVersion());

        // next checkin should produce version 3
        doc.checkIn();
        assertEquals(3, (long) doc.getVersion());
    }

    @Test
    public void testVersionWriteRejected() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "MyDocType");
        doc.setPropertyValue("my_string", "string1");
        doc.create();
        DocumentRef verRef = session.checkIn(doc.getRef());

        // regular version cannot be written
        DocumentModel ver = session.getDocument(verRef);
        try {
            ver.setPropertyValue("my_string", "string2");
            fail("Should not allow version write");
        } catch (PropertyException e) {
            assertTrue(e.getMessage().contains("Cannot set property on a version"), e.getMessage());
        }

    }

    @Test
    public void testAllowVersionWriteOnTitleAndDescription() {
        // set up a document with a version
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        DocumentRef verRef = session.checkIn(doc.getRef());
        DocumentModel ver = session.getDocument(verRef);

        // should not allow setting version number
        ver.setPropertyValue(SYSVER_VERSION, 42L);
        assertNotEquals(42L, ver.getPropertyValue(SYSVER_VERSION));

        // should allow setting version title
        String title = "custom version title";
        ver.setPropertyValue(SYSVER_TITLE, title);
        assertEquals(title, ver.getPropertyValue(SYSVER_TITLE));

        // should allow setting version description
        String description = "custom version description";
        ver.setPropertyValue(SYSVER_DESCRIPTION, description);
        assertEquals(description, ver.getPropertyValue(SYSVER_DESCRIPTION));
    }

    @Test
    public void testAllowVersionWriteACL() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        DocumentRef verRef = session.checkIn(doc.getRef());
        DocumentModel ver = session.getDocument(verRef);
        ACL acl = new ACLImpl();
        ACE ace = ACE.builder().user("princ1").permission("perm1").build();
        acl.add(ace);
        // check that ACL can be set
        ver.setACL(acl, true);
    }

    @Test
    public void testDirtyStateBehaviours() {
        // given a created doc with a given version
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        session.checkIn(doc.getRef());
        doc = session.getDocument(doc.getRef());
        Long v1 = doc.getVersion();

        // when I update the doc with changes
        doc.setPropertyValue(SYS_TITLE, "coucou");
        // then the doc is checked out
        doc = session.getDocument(doc.getRef());
        assertTrue(doc.isCheckedOut());

        // reset the version
        session.checkIn(doc.getRef());
        doc = session.getDocument(doc.getRef());
    }

    // NXP-30075
    @Test
    public void testVersionNotModifiableException() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        session.save();
        DocumentRef vRef = session.checkIn(doc.getRef());

        try {
            session.checkIn(vRef);
            fail("should have thrown VersionNotModifiableException");
        } catch (VersionNotModifiableException e) {
            assertEquals(422, e.getStatusCode());
        }
    }

    @Test
    public void testVersionNameAndTitle() {
        String name = "myfile";
        String title = "My File";
        DocumentModel doc = session.createDocumentModel("/", name, MY_FILE);
        doc.setPropertyValue(SYS_TITLE, title);
        doc.create();
        session.save();

        DocumentRef verRef = session.checkIn(doc.getRef());
        DocumentModel ver = session.getDocument(verRef);
        assertTrue(ver.getName().startsWith("_v_"), ver.getName());
        assertEquals(title, ver.getPropertyValue(SYS_TITLE));
    }

    @Test
    public void testVersioningNotAllowedIfNotSysVersionable() {
        DocumentModel doc = session.createDocumentModel("/", "foo", SYS_FILE);
        doc.create();
        assertFalse(doc.isVersionable());

        try {
            doc.checkIn();
            fail("Should not be able to check in non-versionable document");
        } catch (NuxeoException e) {
            assertEquals(SC_PRECONDITION_FAILED, e.getStatusCode());
            assertEquals("Not versionable: " + doc.getId(), e.getMessage());
        }

        try {
            doc.checkOut();
            fail("Should not be able to check out non-versionable document");
        } catch (NuxeoException e) {
            assertEquals(SC_PRECONDITION_FAILED, e.getStatusCode());
            assertEquals("Not versionable: " + doc.getId(), e.getMessage());
        }
    }

    @Test
    public void testConstraintViolationIfDuplicateVersions() {
        DocumentModel doc = session.createDocumentModel("/", "foo", MY_FILE);
        doc.create();

        // create version 1.0
        DocumentRef v10ref = doc.checkIn();
        DocumentModel v10 = session.getDocument(v10ref);
        assertEquals(Long.valueOf(1), v10.getVersion());
        assertEquals(Long.valueOf(0), v10.getMinorVersion());

        // create version 1.1
        doc.checkOut();
        DocumentRef v11ref = doc.checkIn(true); // minor
        DocumentModel v11 = session.getDocument(v11ref);
        assertEquals(Long.valueOf(1), v11.getVersion());
        assertEquals(Long.valueOf(1), v11.getMinorVersion());

        // create version 2.0
        doc.checkOut();
        DocumentRef v20ref = doc.checkIn();
        DocumentModel v20 = session.getDocument(v20ref);
        assertEquals(Long.valueOf(2), v20.getVersion());
        assertEquals(Long.valueOf(0), v20.getMinorVersion());

        // trying to artificially reset working copy version to 1.0
        doc.checkOut();
        doc.setPropertyValue(SYSVER_VERSION, 1L);
        doc.setPropertyValue(SYSVER_MINOR_VERSION, 0L);
        session.save();
        nextTransaction();
        doc.refresh();

        // check doc version was not modified
        assertEquals(2L, doc.getVersion());
        assertEquals(0L, doc.getMinorVersion());
    }

    @Test
    public void testEditingVersion() {
        DocumentModel doc = session.createDocumentModel("/", "foo", MY_FILE);
        doc.create();

        DocumentRef verRef = doc.checkIn();
        DocumentModel ver = doc.getCoreSession().getDocument(verRef);
        ver.setPropertyValue(SYSVER_TITLE, "title easy peasy lemon squeezy");
        ver.setPropertyValue(SYSVER_DESCRIPTION, "version Bingo bango bongo pish pash push");

        // read-only fields should not appear on the output
        ver.setPropertyValue(SYSVER_MINOR_VERSION, 222);
        ver.setPropertyValue(SYSVER_VERSION, 333);
        ver.setPropertyValue(SYSVER_IS_TEMP, true);
        ver.setPropertyValue(SYSVER_CREATOR, "TestViewer");

        var sysCreated = Instant.now().toString();
        ver.setPropertyValue(SYSVER_CREATED, sysCreated);

        assertEquals("title easy peasy lemon squeezy", ver.getPropertyValue(SYSVER_TITLE));
        assertEquals("version Bingo bango bongo pish pash push", ver.getPropertyValue(SYSVER_DESCRIPTION));

        // check that the read-only fields are not saved
        assertEquals(0L, ver.getPropertyValue(SYSVER_MINOR_VERSION));
        assertEquals(1L, ver.getPropertyValue(SYSVER_VERSION));
        assertNull(ver.getPropertyValue(SYSVER_IS_TEMP));
        assertEquals(TEST_ADMIN_USER, ver.getPropertyValue(SYSVER_CREATOR));
        assertNotEquals(sysCreated, ver.getPropertyValue(SYSVER_CREATED));
    }

    @Test
    public void testEditingWorkingCopyVersion() throws Exception {
        DocumentModel workingCopy = session.createDocumentModel("/", "foo", MY_FILE);
        workingCopy.setPropertyValue(SYSVER_TITLE, "version title");
        workingCopy.setPropertyValue(SYSVER_DESCRIPTION, "version descr");

        // read-only fields should not appear on the output
        workingCopy.setPropertyValue(SYSVER_MINOR_VERSION, 222);
        workingCopy.setPropertyValue(SYSVER_VERSION, 333);
        workingCopy.setPropertyValue(SYSVER_IS_TEMP, true);
        workingCopy.setPropertyValue(SYSVER_CREATOR, "TestAdministrator");

        var sysCreated = Instant.now().toString();
        workingCopy.setPropertyValue(SYSVER_CREATED, sysCreated);

        assertEquals("version title", workingCopy.getPropertyValue(SYSVER_TITLE));
        assertEquals("version descr", workingCopy.getPropertyValue(SYSVER_DESCRIPTION));

        // check that the read-only fields are not saved
        assertEquals(workingCopy.getMinorVersion(), workingCopy.getPropertyValue(SYSVER_MINOR_VERSION));
        assertEquals(workingCopy.getVersion(), workingCopy.getPropertyValue(SYSVER_VERSION));
        assertNull(workingCopy.getPropertyValue(SYSVER_IS_TEMP));
        assertNull(workingCopy.getPropertyValue(SYSVER_CREATOR));
        assertNull(workingCopy.getPropertyValue(SYSVER_CREATED));
    }

}
