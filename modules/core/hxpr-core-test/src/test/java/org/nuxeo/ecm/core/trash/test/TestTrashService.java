/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thierry Delprat
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.trash.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ_WRITE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.trash.TrashService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
public class TestTrashService {

    private static final String MY_FILE = "MyFile";

    @Inject
    protected CoreSession session;

    @Inject
    protected TrashService trashService;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected CoreFeature coreFeature;

    protected DocumentModel fold;

    protected DocumentModel doc1;

    protected DocumentModel doc2;

    protected DocumentModel doc3;

    protected NuxeoPrincipal principal;

    @BeforeEach
    public void setUp() {
        principal = session.getPrincipal();
    }

    public void createDocuments() {
        fold = session.createDocumentModel("/", "fold", SYS_FOLDER);
        // use File as document type as Note is now automatically versioned on each update
        fold.create();
        doc1 = session.createDocumentModel("/fold", "doc1", SYS_FILE);
        doc1.create();
        doc2 = session.createDocumentModel("/fold", "doc2", SYS_FILE);
        doc2.create();
        doc3 = session.createDocumentModel("/", "doc3", MY_FILE);
        doc3.create();
        session.save();
        // commit transaction to make sure the query to fetch the descendants in PropertyTrashService#trashDescendants
        // gets the right documents when the bulk command is processed
        transactionalFeature.nextTransaction();
    }

    @Test
    public void testNameMangling() {
        createDocuments();
        String mangled = trashService.mangleName(doc1);
        assertTrue(mangled.startsWith("doc1._"), mangled);
        assertTrue(mangled.endsWith("_.trashed"), mangled);

        trashService.trashDocument(doc1);
        doc1 = session.getDocument(doc1.getRef());

        mangled = doc1.getName();
        assertTrue(mangled.startsWith("doc1._"), mangled);
        assertTrue(mangled.endsWith("_.trashed"), mangled);

        assertEquals("doc1", trashService.unmangleName(doc1));
    }

    @Test
    public void testBase() {
        createDocuments();
        assertFalse(trashService.canPurgeOrUntrash(Collections.singletonList(fold), principal));
        assertFalse(trashService.canPurgeOrUntrash(Collections.singletonList(doc1), principal));
        assertFalse(trashService.canPurgeOrUntrash(Collections.singletonList(doc2), principal));
    }

    @Test
    public void testTrashPurgeUndelete() {
        createDocuments();
        // file with name from collision
        DocumentModel doc4 = session.createDocumentModel("/", "doc4.1400676936345", SYS_FILE);
        doc4.create();
        String doc4origname = doc4.getName();

        trashService.trashDocuments(Arrays.asList(fold, doc1, doc3, doc4));

        transactionalFeature.nextTransaction();

        fold = session.getDocument(new IdRef(fold.getId()));
        doc1 = session.getDocument(new IdRef(doc1.getId()));
        doc2 = session.getDocument(new IdRef(doc2.getId()));
        doc3 = session.getDocument(new IdRef(doc3.getId()));
        doc4 = session.getDocument(new IdRef(doc4.getId()));
        assertTrue(fold.isTrashed());
        assertTrue(doc1.isTrashed());
        assertTrue(doc3.isTrashed());
        assertTrue(doc4.isTrashed());
        assertTrue(doc2.isTrashed());
        // check names changed
        assertNotEquals("fold", fold.getName());
        assertNotEquals("doc1", doc1.getName());
        String doc3delname = doc3.getName();
        assertNotEquals("doc3", doc3.getName());
        assertNotEquals(doc4origname, doc4.getName());
        assertNotEquals("doc4", doc4.getName());
        // when recursing, don't change name
        assertEquals("doc2", doc2.getName());

        assertTrue(trashService.canPurgeOrUntrash(Arrays.asList(fold, doc1, doc2, doc3, doc4), principal));

        // purge doc1
        trashService.purgeDocuments(session, Collections.singletonList(doc1.getRef()));
        assertFalse(session.exists(doc1.getRef()));

        // untrash doc2 and doc4
        trashService.untrashDocuments(Arrays.asList(doc2, doc4));
        fold = session.getDocument(new IdRef(fold.getId()));
        doc2 = session.getDocument(new IdRef(doc2.getId()));
        doc4 = session.getDocument(new IdRef(doc4.getId()));
        assertFalse(doc2.isTrashed());
        assertFalse(doc4.isTrashed());
        // fold also untrashed
        assertFalse(fold.isTrashed());
        // check name restored
        assertEquals("fold", fold.getName());
        // name still unchanged
        assertEquals("doc2", doc2.getName());
        // doc4 was restored with a pristine name
        assertEquals("doc4", doc4.getName());

        // create a new file with same name as old doc3
        DocumentModel doc3bis = session.createDocumentModel("/", "doc3", SYS_FILE);
        doc3bis.create();
        assertEquals("doc3", doc3bis.getName());
        // untrash doc3
        trashService.untrashDocument(doc3);
        doc3 = session.getDocument(new IdRef(doc3.getId()));
        assertFalse(doc3.isTrashed());
        // check it was renamed again during untrash
        assertNotEquals("doc3", doc3.getName());
        assertNotEquals(doc3delname, doc3.getName());
    }

    @Test
    public void testTrashPurgeDocumentsUnder() {
        createDocuments();
        trashService.trashDocument(doc1);

        transactionalFeature.nextTransaction();

        assertFalse(session.isTrashed(fold.getRef()));
        assertTrue(session.isTrashed(doc1.getRef()));
        assertFalse(session.isTrashed(doc2.getRef()));
        assertFalse(session.isTrashed(doc3.getRef()));

        // doc1 is now trashed but not doc2 - purgeDocumentsUnder of fold should only purge doc1
        trashService.purgeDocumentsUnder(fold);

        transactionalFeature.nextTransaction();

        assertFalse(session.isTrashed(fold.getRef()));
        assertFalse(session.exists(doc1.getRef()));
        assertFalse(session.isTrashed(doc2.getRef()));
        assertFalse(session.isTrashed(doc3.getRef()));

    }

    // NXP-30417
    @Test
    public void testTrashPurgeDocumentsUnderWithPermission() {
        DocumentModel fold1 = session.createDocumentModel("/", "fold1", SYS_FOLDER);
        fold1.create();
        DocumentModel fold2 = session.createDocumentModel("/fold1", "fold2", SYS_FOLDER);
        fold2.create();
        ACL acl = fold2.getLocalACL();
        acl.add(ACE.builder().user("bob").permission(READ_WRITE).build());
        fold2.setACL(acl, true);
        DocumentModel doc1 = session.createDocumentModel("/fold1/fold2", "doc1", SYS_FILE);
        doc1.create();
        session.save();
        transactionalFeature.nextTransaction();

        CoreSession bobSession = coreFeature.getCoreSession("bob");
        assertFalse(bobSession.exists(fold1.getRef()));
        assertTrue(bobSession.exists(fold2.getRef()));
        assertTrue(bobSession.exists(doc1.getRef()));

        trashService.trashDocument(doc1);
        session.save();
        transactionalFeature.nextTransaction();

        assertTrue(trashService.isTrashed(bobSession, doc1.getRef()));

        // fetch and purge fold2 with bob
        fold2 = bobSession.getDocument(fold2.getRef());
        trashService.purgeDocumentsUnder(fold2);
        session.save();
        transactionalFeature.nextTransaction();

        assertFalse(session.exists(doc1.getRef()));
    }

    @Test
    public void testUntrashChildren() {
        createDocuments();
        // trashing /fold/doc1 to mangle its name specifically
        trashService.trashDocument(doc1);
        // trashing /fold and recursively /fold/doc2
        trashService.trashDocument(fold);

        transactionalFeature.nextTransaction();

        fold = session.getDocument(new IdRef(fold.getId()));
        doc1 = session.getDocument(new IdRef(doc1.getId()));
        doc2 = session.getDocument(new IdRef(doc2.getId()));
        assertTrue(fold.isTrashed());
        // doc1 trashed explicitly
        assertTrue(doc1.isTrashed());
        // doc2 trashed recursively
        assertTrue(doc2.isTrashed());
        // fold is mangled as it was directly trashed
        assertTrue(trashService.isMangledName(fold.getName()));
        // doc1 is mangled as it was directly trashed
        assertTrue(trashService.isMangledName(doc1.getName()));
        // doc2 is not mangled as it was indirectly trashed
        assertFalse(trashService.isMangledName(doc2.getName()));

        // untrash fold
        trashService.untrashDocument(fold);

        transactionalFeature.nextTransaction();

        fold = session.getDocument(new IdRef(fold.getId()));
        doc1 = session.getDocument(new IdRef(doc1.getId()));
        doc2 = session.getDocument(new IdRef(doc2.getId()));
        assertFalse(fold.isTrashed());
        // children recursively untrashed
        assertFalse(doc1.isTrashed());
        assertFalse(doc2.isTrashed());
        // fold is unmangled
        assertFalse(trashService.isMangledName(fold.getName()));
        // doc1 is recursively unmangled
        assertFalse(trashService.isMangledName(doc1.getName()));
        // doc2 stays unmangled
        assertFalse(trashService.isMangledName(doc2.getName()));
    }

    /**
     * @since 7.3
     */
    @Test
    public void testDeleteTwice() {
        createDocuments();
        List<DocumentModel> dd = new ArrayList<>();
        dd.add(doc1);
        trashService.trashDocuments(dd);
        trashService.trashDocuments(dd);
        assertTrue(session.exists(doc1.getRef()));
    }

    @Test
    public void testPlacelessDocument() {
        DocumentModel doc4 = session.createDocumentModel(null, "doc4", SYS_FILE);
        doc4.create();
        session.save();
        DocumentModel above = trashService.getAboveDocument(doc4, principal);
        assertNull(above);
        trashService.trashDocument(doc4);
        assertFalse(session.exists(doc4.getRef()));
    }

    @Test
    public void testTrashCheckedInDocumentDefault() {
        doTestTrashCheckedInDocument(true);
    }

    protected void doTestTrashCheckedInDocument(boolean expectCheckedIn) {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/folder", "doc", MY_FILE);
        doc.create();

        // check in
        doc.checkIn();
        assertFalse(doc.isCheckedOut());
        session.save();

        // trash
        trashService.trashDocument(doc);

        // make sure it's still checked in (or not if compat)
        doc = session.getDocument(new IdRef(doc.getId()));
        assertTrue(doc.isTrashed());
        if (expectCheckedIn) {
            assertFalse(doc.isCheckedOut());
        } else {
            assertTrue(doc.isCheckedOut());
        }

        // untrash
        trashService.untrashDocument(doc);

        // make sure it's still checked in (or not if compat)
        doc = session.getDocument(new IdRef(doc.getId()));
        assertFalse(doc.isTrashed());
        if (expectCheckedIn) {
            assertFalse(doc.isCheckedOut());
        } else {
            assertTrue(doc.isCheckedOut());
        }

        // another checked in doc
        DocumentModel doc2 = session.createDocumentModel("/folder", "doc2", MY_FILE);
        doc2.create();
        doc2.checkIn();
        session.save();
    }

    /*
     * NXP-28935
     */
    @Test
    public void testUntrashThreeLevels() {
        var folder1 = session.createDocumentModel("/", "folder1", SYS_FOLDER);
        folder1.create();
        var folder2 = session.createDocumentModel("/folder1", "folder2", SYS_FOLDER);
        folder2.create();
        var folder3 = session.createDocumentModel("/folder1/folder2", "folder3", SYS_FOLDER);
        folder3.create();
        var doc = session.createDocumentModel("/folder1/folder2/folder3", "doc", SYS_FILE);
        doc.create();
        session.save();
        // commit transaction to make sure the query to fetch the descendants in PropertyTrashService#trashDescendants
        // gets the right documents when the bulk command is processed
        transactionalFeature.nextTransaction();

        // trashing /folder1
        trashService.trashDocument(folder1);
        session.save();

        // children recursion is async
        transactionalFeature.nextTransaction();

        folder1.refresh();
        folder2.refresh();
        folder3.refresh();
        doc.refresh();
        assertTrue(folder1.isTrashed());
        assertTrue(folder2.isTrashed());
        assertTrue(folder3.isTrashed());
        assertTrue(doc.isTrashed());

        // untrashing /folder1
        trashService.untrashDocument(folder1);
        session.save();

        // children recursion is async
        transactionalFeature.nextTransaction();

        folder1.refresh();
        folder2.refresh();
        folder3.refresh();
        doc.refresh();
        assertFalse(folder1.isTrashed());
        assertFalse(folder2.isTrashed());
        assertFalse(folder3.isTrashed());
        assertFalse(doc.isTrashed());
    }
}
