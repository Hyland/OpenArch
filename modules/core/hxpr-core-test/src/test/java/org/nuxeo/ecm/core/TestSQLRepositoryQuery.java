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

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYONE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_ORDERED_FOLDER;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.trash.TrashService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.StorageConfiguration;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

@ExtendWith(FeaturesExtension.class)
@Features({ CoreFeature.class, LogCaptureFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/testquery-core-types-contrib.xml")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib-2.xml")
public class TestSQLRepositoryQuery {

    private static final String MY_DOC_TYPE = "MyDocType";

    private static final String MY_GOVERNABLE_FILE = "GovernableFile";

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    @Inject
    protected TrashService trashService;

    @Inject
    LogCaptureFeature.Result logCaptureResult;

    private DocumentRef file3ref;

    protected boolean isDBSMem() {
        return coreFeature.getStorageConfiguration().isDBSMem();
    }

    protected boolean isDBSMongoDB() {
        return coreFeature.getStorageConfiguration().isDBSMongoDB();
    }

    protected void waitForFulltextIndexing() {
        nextTransaction();
        coreFeature.getStorageConfiguration().waitForFulltextIndexing();
    }

    protected void maybeSleepToNextSecond() {
        coreFeature.getStorageConfiguration().maybeSleepToNextSecond();
    }

    protected void nextTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    /**
     * Query of NOT (something) matches docs where (something) did not match because the field was null.
     */
    public boolean notMatchesNull() {
        return isDBSMongoDB();
    }

    public boolean supportsScroll() {
        StorageConfiguration conf = coreFeature.getStorageConfiguration();
        // DBS mem is not yet supported
        return conf.isDBSMongoDB();
    }

    protected CoreSession openSessionAs(String username) {
        return CoreInstance.getCoreSession(session.getRepositoryName(),
                DummyLoginFeature.getTestPrincipal(username, false));
    }

    // ---------------------------------------

    /**
     * Creates the following structure of documents:
     *
     * <pre>
     *  root (UUID_0)
     *  |- testfolder1 (UUID_1)
     *  |  |- testfile1 (UUID_2)
     *  |  |- testfile2 (UUID_3)
     *  |  |- testfile3 (UUID_4) (Note)
     *  |     \- _v_{random} (UUID_5) (version)
     *  \- tesfolder2 (UUID_6)
     *     \- testfolder3 (UUID_7)
     *        \- testfile4 (UUID_8)
     * </pre>
     */
    protected void createDocs() {
        DocumentModel folder1 = session.createDocumentModel("/", "testfolder1", SYS_FOLDER);
        folder1.setPropertyValue(SYS_TITLE, "testfolder1_Title");
        folder1.create();

        DocumentModel file1 = session.createDocumentModel("/testfolder1", "testfile1", MY_DOC_TYPE);
        file1.setPropertyValue(SYS_TITLE, "testfile1_Title");
        file1.setPropertyValue(SYS_DESCRIPTION, "testfile1_description");
        String content = "Some caf\u00e9 in a restaurant.\nDrink!.\n";
        String filename = "testfile.txt";
        Blob blob1 = Blobs.createBlob(content);
        blob1.setFilename(filename);
        file1.setPropertyValue(SYSFILE_BLOB, blob1);
        Instant cal1 = Instant.parse("2007-03-01T12:00:00Z");
        file1.setPropertyValue("my2_date", cal1);
        file1.setPropertyValue("mydc_coverage", "football");
        file1.setPropertyValue("mydc_subjects", new String[] { "foo", "gee/moo" });
        file1.create();

        DocumentModel file2 = session.createDocumentModel("/testfolder1", "testfile2", MY_DOC_TYPE);
        file2.setPropertyValue(SYS_TITLE, "testfile2_Title");
        file2.setPropertyValue(SYS_DESCRIPTION, "testfile2_DESCRIPTION2");
        Instant cal2 = Instant.parse("2007-04-01T12:00:00Z");
        file2.setPropertyValue("my2_date", cal2);
        file2.setPropertyValue("mydc_contributors", new String[] { "bob", "pete" });
        file2.setPropertyValue("mydc_coverage", "foo/bar");
        file2.create();

        DocumentModel file3 = session.createDocumentModel("/testfolder1", "testfile3", "Note");
        file3.setPropertyValue(SYS_TITLE, "testfile3_Title");
        file3.setPropertyValue(SYS_DESCRIPTION, "testfile3_desc1 testfile3_desc2,  testfile3_desc3");
        file3.setPropertyValue("mydc_contributors", new String[] { "bob", "john" });
        file3.create();
        file3ref = file3.checkIn();

        DocumentModel folder2 = session.createDocumentModel("/", "testfolder2", SYS_FOLDER);
        folder2.create();

        DocumentModel folder3 = session.createDocumentModel("/testfolder2", "testfolder3", SYS_FOLDER);
        folder3.create();

        // create file 4
        DocumentModel file4 = session.createDocumentModel("/testfolder2/testfolder3", "testfile4", MY_DOC_TYPE);
        // title without space or _ for Oracle fulltext searchability
        file4.setPropertyValue(SYS_TITLE, "testfile4Title");
        file4.setPropertyValue(SYS_DESCRIPTION, "testfile4_DESCRIPTION4");
        file4.create();

        session.save();
    }

    protected DocumentModel checkinDoc() {
        DocumentModel doc = session.getDocument(new PathRef("/testfolder2/testfolder3/testfile4"));
        DocumentRef verRef = session.checkIn(doc.getRef());
        session.save();
        return session.getDocument(verRef);
    }

    // from TestAPI
    @Test
    public void testQueryBasic() {
        DocumentModelList dml;
        createDocs();

        dml = session.query("SELECT * FROM MyDocType");
        assertEquals(3, dml.size());
        for (DocumentModel dm : dml) {
            assertEquals(MY_DOC_TYPE, dm.getType());
        }

        dml = session.query("SELECT * FROM Note WHERE sysver_isVersion = FALSE");
        assertEquals(1, dml.size());
        for (DocumentModel dm : dml) {
            assertEquals("Note", dm.getType());
        }

        dml = session.query("SELECT * FROM SysFolder");
        assertEquals(3, dml.size());
        for (DocumentModel dm : dml) {
            assertEquals(SYS_FOLDER, dm.getType());
        }

        dml = session.query("SELECT * FROM SysContent WHERE sysver_isVersion = FALSE");
        assertEquals(7, dml.size());

        dml = session.query("SELECT * FROM MyDocType WHERE sys_title = 'testfile1_Title'");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM MyDocType WHERE NOT sys_title != 'testfile1_Title'");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM MyDocType WHERE NOT sys_title = 'testfile1_Title'");
        assertEquals(2, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE sys_title = 'testfile1_Title'");
        assertEquals(1, dml.size());

        dml = session.query(
                "SELECT * FROM MyDocType WHERE sys_title = 'testfile1_Title' OR sys_title = 'testfile2_Title'");
        assertEquals(2, dml.size());

        dml = session.query("SELECT * FROM MyDocType WHERE sys_title = 'testfolder1_Title'");
        assertEquals(0, dml.size());

        dml = session.query("SELECT * FROM MyDocType WHERE sysfile_blob/filename = 'testfile.txt'");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM Note WHERE sys_title = 'testfile3_Title' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        // two uses of the same schema
        dml = session.query(
                "SELECT * FROM Note WHERE (sys_title = 'testfile3_Title' OR sys_description = 'hmmm') AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        // this needs an actual LEFT OUTER JOIN
        dml = session.query(
                "SELECT * FROM SysContent WHERE (sysfile_blob/filename = 'testfile.txt' OR sys_title = 'testfile3_Title') AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE (sysfile_blob/filename = 'testfile.txt' OR mydc_contributors = 'bob') AND sysver_isVersion = FALSE");
        assertEquals(3, dml.size());

        // early detection of conflicting types for VCS
        dml = session.query("SELECT * FROM SysContent WHERE sys_primaryType = 'foo'");
        assertEquals(0, dml.size());

        // query complex type
        dml = session.query("SELECT * FROM MyDocType WHERE sysfile_blob/length > 0");
        assertEquals(1, dml.size());
        dml = session.query("SELECT * FROM MyDocType WHERE sysfile_blob/filename = 'testfile.txt'");
        assertEquals(1, dml.size());
    }

    @Test
    public void testQueryBasic2() {
        createDocs();
        DocumentModelList dml;

        dml = session.query("SELECT * FROM SysContent WHERE sys_title ILIKE 'test%' AND sysver_isVersion = FALSE");
        assertEquals(7, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE sys_title ILIKE 'Test%' AND sysver_isVersion = FALSE");
        assertEquals(7, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE sys_title NOT ILIKE 'foo%' AND sysver_isVersion = FALSE");
        assertEquals(notMatchesNull() ? 7 : 5, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE sys_title NOT ILIKE 'Foo%' AND sysver_isVersion = FALSE");
        assertEquals(notMatchesNull() ? 7 : 5, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_subjects ILIKE '%oo' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_subjects NOT ILIKE '%oo' AND sysver_isVersion = FALSE");
        assertEquals(6, dml.size());
    }

    @Test
    public void testQueryMultiple() {
        DocumentModelList dml;
        createDocs();

        dml = session.query("SELECT * FROM MyDocType WHERE mydc_contributors = 'pete' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM MyDocType WHERE mydc_contributors = 'bob' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_contributors = 'bob' AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE mydc_contributors IN ('bob', 'pete') AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE mydc_contributors IN ('bob', 'john') AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE mydc_contributors NOT IN ('bob', 'pete') AND sysver_isVersion = FALSE");
        assertEquals(5, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE mydc_contributors NOT IN ('bob', 'john') AND sysver_isVersion = FALSE");
        assertEquals(5, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_contributors LIKE 'pe%' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_contributors LIKE 'bo%' AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_subjects LIKE '%oo%' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM MyDocType WHERE mydc_subjects NOT LIKE '%oo%' AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());
    }

    @Test
    public void testQueryMultipleNew() {
        DocumentModelList dml;
        createDocs();

        dml = session.query("SELECT * FROM MyDocType WHERE mydc_contributors/* = 'pete' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM MyDocType WHERE mydc_contributors/* = 'bob' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_contributors/* = 'bob' AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE mydc_contributors/* IN ('bob', 'pete') AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE mydc_contributors/* IN ('bob', 'john') AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE mydc_contributors/* NOT IN ('bob', 'pete') AND sysver_isVersion = FALSE");
        assertEquals(notMatchesNull() ? 5 : 1, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE mydc_contributors/* NOT IN ('bob', 'john') AND sysver_isVersion = FALSE");
        assertEquals(notMatchesNull() ? 5 : 1, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_contributors/* LIKE 'pe%' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_contributors/* LIKE 'bo%' AND sysver_isVersion = FALSE");
        assertEquals(2, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE mydc_contributors/* NOT LIKE 'bo%' AND sysver_isVersion = FALSE");
        assertEquals(notMatchesNull() ? 5 : 1, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_subjects/* LIKE '%oo%' AND sysver_isVersion = FALSE");
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM MyDocType WHERE mydc_subjects/* NOT LIKE '%oo%' AND sysver_isVersion = FALSE");
        assertEquals(notMatchesNull() ? 2 : 0, dml.size());

        // with a scalar list defined by a xs:complexType (and not a xs:simpleType)
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_DOC_TYPE);
        doc.setPropertyValue("my2_participants", List.of("foo", "bar"));
        doc.create();
        session.save();
        dml = session.query("SELECT * FROM MyDocType WHERE my2_participants/* = 'foo'");
        assertEquals(1, dml.size());
    }

    @Test
    public void testQueryNegativeMultiple() {
        DocumentModelList dml;
        createDocs();
        dml = session.query("SELECT * FROM SysContent WHERE mydc_contributors <> 'pete' AND sysver_isVersion = FALSE");
        assertEquals(6, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE mydc_contributors <> 'blah' AND sysver_isVersion = FALSE");
        assertEquals(7, dml.size());

        dml = session.query(
                "SELECT * FROM MyDocType WHERE mydc_contributors <> 'blah' AND sysver_isVersion = FALSE");
        assertEquals(3, dml.size());

        dml = session.query(
                "SELECT * FROM SysContent WHERE sys_mixinTypes = 'SysVersionable' AND sys_mixinTypes <> 'Downloadable' AND sysver_isVersion = FALSE");
        assertEquals(4, dml.size());
    }

    @Test
    public void testQueryAfterEdit() {
        DocumentModel root = session.getRootDocument();

        String fname1 = "file1";
        DocumentModel childFile1 = session.createDocumentModel(root.getPathAsString(), fname1, SYS_FILE);
        childFile1.create();

        // add a blob
        String s = "<html><head/><body>La la la!</body></html>";
        Blob blob = Blobs.createBlob(s, "text/html");
        blob.setFilename("f1");
        childFile1.setPropertyValue(SYSFILE_BLOB, blob);

        session.save();

        DocumentModelList list;
        DocumentModel docModel;

        list = session.query("SELECT * FROM SysContent");
        assertEquals(1, list.size());
        docModel = list.get(0);

        // read the properties
        docModel.getPropertyValue(SYS_TITLE);

        Blob blob2 = (Blob) docModel.getPropertyValue(SYSFILE_BLOB);
        assertEquals(s.length(), blob2.getLength()); // only ascii chars
        assertEquals("text/html", blob2.getMimeType());

        // edit the title without touching the blob
        docModel.setPropertyValue(SYS_TITLE, "edited title");
        docModel.setPropertyValue(SYS_DESCRIPTION, "edited description");
        session.save();

        list = session.query("SELECT * FROM SysContent");
        assertEquals(1, list.size());
        docModel = list.get(0);

        session.removeDocument(docModel.getRef());
    }

    @Test
    public void testOrderBy() {
        String sql;
        DocumentModelList dml;
        createDocs();

        sql = "SELECT * FROM SysContent WHERE sys_title LIKE 'testfile%' AND sysver_isVersion = FALSE ORDER BY sys_description";
        dml = session.query(sql);
        assertEquals(4, dml.size());
        assertEquals("testfile1_description", dml.get(0).getPropertyValue(SYS_DESCRIPTION));

        // desc
        sql = "SELECT * FROM SysContent WHERE sys_title LIKE 'testfile%' AND sysver_isVersion = FALSE ORDER BY sys_description DESC";
        dml = session.query(sql);
        assertEquals(4, dml.size());
        assertEquals("testfile4_DESCRIPTION4", dml.get(0).getPropertyValue(SYS_DESCRIPTION));
    }

    @Test
    public void testOrderBySeveralColumns() {
        String sql;
        DocumentModelList dml;
        createDocs();

        // avoid null mydc_coverage, null sort first/last is db-dependent
        sql = "SELECT * FROM MyDocType WHERE sys_title in ('testfile1_Title', 'testfile2_Title')"
                + " ORDER BY sys_title, mydc_coverage";
        dml = session.query(sql);
        assertEquals(2, dml.size());
        assertEquals("testfile1", dml.get(0).getName());
        assertEquals("testfile2", dml.get(1).getName());

        // swap columns
        sql = "SELECT * FROM MyDocType WHERE sys_title in ('testfile1_Title', 'testfile2_Title')"
                + " ORDER BY mydc_coverage, sys_title";
        dml = session.query(sql);
        assertEquals(2, dml.size());
        assertEquals("testfile2", dml.get(0).getName());
        assertEquals("testfile1", dml.get(1).getName());
    }

    @Test
    public void testOrderBySameColumns() {
        String sql;
        DocumentModelList dml;
        createDocs();

        sql = "SELECT * FROM MyDocType WHERE sys_title in ('testfile1_Title', 'testfile2_Title')"
                + " ORDER BY sys_title, sys_title";
        dml = session.query(sql);
        assertEquals(2, dml.size());
        assertEquals("testfile1", dml.get(0).getName());
        assertEquals("testfile2", dml.get(1).getName());

        sql = "SELECT * FROM MyDocType WHERE sys_title in ('testfile1_Title', 'testfile2_Title')"
                + " ORDER BY sys_title DESC, sys_title";
        dml = session.query(sql);
        assertEquals(2, dml.size());
        assertEquals("testfile2", dml.get(0).getName());
        assertEquals("testfile1", dml.get(1).getName());
    }

    @Test
    public void testOrderByPath() {
        String sql;
        DocumentModelList dml;
        createDocs();

        sql = "SELECT * FROM SysContent WHERE sysver_isVersion = FALSE ORDER BY sys_path";
        dml = session.query(sql);
        assertEquals(7, dml.size());
        assertEquals("/testfolder1", dml.get(0).getPathAsString());
        assertEquals("/testfolder1/testfile1", dml.get(1).getPathAsString());
        assertEquals("/testfolder1/testfile2", dml.get(2).getPathAsString());
        assertEquals("/testfolder1/testfile3", dml.get(3).getPathAsString());
        assertEquals("/testfolder2", dml.get(4).getPathAsString());
        assertEquals("/testfolder2/testfolder3", dml.get(5).getPathAsString());
        assertEquals("/testfolder2/testfolder3/testfile4", dml.get(6).getPathAsString());

        sql = "SELECT * FROM SysContent WHERE sysver_isVersion = FALSE ORDER BY sys_path DESC";
        dml = session.query(sql);
        assertEquals(7, dml.size());
        assertEquals("/testfolder2/testfolder3/testfile4", dml.get(0).getPathAsString());
        assertEquals("/testfolder1", dml.get(6).getPathAsString());

        // then with batching

        sql = "SELECT * FROM SysContent WHERE sysver_isVersion = FALSE ORDER BY sys_path";
        dml = session.query(sql, 2, 3, 0);
        assertEquals(2, dml.size());
        assertEquals("/testfolder1/testfile3", dml.get(0).getPathAsString());
        assertEquals("/testfolder2", dml.get(1).getPathAsString());
    }

    @Test
    public void testOrderByPos() {
        DocumentModelList dml;

        DocumentModel ofolder = session.createDocumentModel("/", "ofolder", SYS_ORDERED_FOLDER);
        ofolder.create();
        DocumentModel file1 = session.createDocumentModel("/ofolder", "testfile1", SYS_FILE);
        file1.create();
        DocumentModel file2 = session.createDocumentModel("/ofolder", "testfile2", SYS_FILE);
        file2.create();
        DocumentModel file3 = session.createDocumentModel("/ofolder", "testfile3", SYS_FILE);
        file3.create();
        session.save();

        String sql = String.format("SELECT * FROM SysContent WHERE sys_parentId = '%s' ORDER BY sys_pos",
                ofolder.getId());
        String sqldesc = sql + " DESC";

        dml = session.query(sql);
        assertEquals(3, dml.size());
        assertEquals(file1.getId(), dml.get(0).getId());
        assertEquals(file2.getId(), dml.get(1).getId());
        assertEquals(file3.getId(), dml.get(2).getId());

        dml = session.query(sqldesc);
        assertEquals(file3.getId(), dml.get(0).getId());
        assertEquals(file2.getId(), dml.get(1).getId());
        assertEquals(file1.getId(), dml.get(2).getId());

        session.orderBefore(ofolder.getRef(), "testfile3", "testfile2");
        session.save();

        dml = session.query(sql);
        assertEquals(file1.getId(), dml.get(0).getId());
        assertEquals(file3.getId(), dml.get(1).getId());
        assertEquals(file2.getId(), dml.get(2).getId());

        dml = session.query(sqldesc);
        assertEquals(file2.getId(), dml.get(0).getId());
        assertEquals(file3.getId(), dml.get(1).getId());
        assertEquals(file1.getId(), dml.get(2).getId());

        // test sys_pos as a field
        sql = "SELECT * FROM SysContent WHERE sys_pos = 1";
        dml = session.query(sql);
        assertEquals(1, dml.size());
        assertEquals(file3.getId(), dml.iterator().next().getId());
    }

    @Test
    public void testBatching() {
        doBatching(true);
    }

    public void doBatching(boolean checkNames) {
        DocumentModelList dml;
        createDocs();

        String sql = "SELECT * FROM SysContent WHERE sysver_isVersion = FALSE ORDER BY sys_name";

        dml = session.query(sql);
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());
        if (checkNames) {
            assertEquals("testfile1", dml.get(0).getName());
            assertEquals("testfile2", dml.get(1).getName());
            assertEquals("testfile3", dml.get(2).getName());
            assertEquals("testfile4", dml.get(3).getName());
            assertEquals("testfolder1", dml.get(4).getName());
            assertEquals("testfolder2", dml.get(5).getName());
            assertEquals("testfolder3", dml.get(6).getName());
        }

        dml = session.query(sql, 99, 0, -1);
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());
        if (checkNames) {
            assertEquals("testfile1", dml.get(0).getName());
            assertEquals("testfolder3", dml.get(6).getName());
        }

        dml = session.query(sql, 7, 0, -1);
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());
        if (checkNames) {
            assertEquals("testfile1", dml.get(0).getName());
            assertEquals("testfolder3", dml.get(6).getName());
        }

        dml = session.query(sql, 6, 0, -1);
        assertEquals(6, dml.size());
        assertEquals(7, dml.totalSize());
        if (checkNames) {
            assertEquals("testfile1", dml.get(0).getName());
            assertEquals("testfolder2", dml.get(5).getName());
        }

        dml = session.query(sql, 6, 1, -1);
        assertEquals(6, dml.size());
        assertEquals(7, dml.totalSize());
        if (checkNames) {
            assertEquals("testfile2", dml.get(0).getName());
            assertEquals("testfolder3", dml.get(5).getName());
        }

        dml = session.query(sql, 99, 3, -1);
        assertEquals(4, dml.size());
        assertEquals(7, dml.totalSize());
        if (checkNames) {
            assertEquals("testfile4", dml.get(0).getName());
            assertEquals("testfolder3", dml.get(3).getName());
        }

        dml = session.query(sql, 99, 50, -1);
        assertEquals(0, dml.size());
        assertEquals(7, dml.totalSize());
    }

    @Test
    public void testQueryLimits() {
        DocumentModelList dml;
        createDocs();

        String sql = "SELECT * FROM SysContent WHERE sysver_isVersion = FALSE ORDER BY sys_name";

        dml = session.query(sql);
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());

        // countUpTo = 0 -> no total count, dml set the total size to the list
        // size
        // equivalent to totalCount=false
        dml = session.query(sql, 0, 0, 0);
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());

        dml = session.query(sql, 2, 2, 0);
        assertEquals(2, dml.size());
        assertEquals(2, dml.totalSize());

        dml = session.query(sql, 10, 10, 0);
        assertEquals(0, dml.size());
        assertEquals(0, dml.totalSize());

        // countUpTo = -1 -> ask for exact total size, regardless of
        // offset/limit
        // equivalent to totalCount=true
        dml = session.query(sql, 0, 0, -1);
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());

        // check offset taken into account
        dml = session.query(sql, 0, 2, -1);
        assertEquals(5, dml.size());
        assertEquals(7, dml.totalSize());

        // check offset taken into account even when no results
        dml = session.query(sql, 0, 10, -1);
        assertEquals(0, dml.size());
        assertEquals(7, dml.totalSize());

        dml = session.query(sql, 2, 2, -1);
        assertEquals(2, dml.size());
        assertEquals(7, dml.totalSize());

        dml = session.query(sql, 2, 10, -1);
        assertEquals(0, dml.size());
        assertEquals(7, dml.totalSize());

        dml = session.query(sql, 20, 0, -1);
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());

        // countUpTo = n
        // equivalent to totalCount=true if there are less than n results
        dml = session.query(sql, 0, 0, 10);
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());

        dml = session.query(sql, 0, 0, 7);
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());

        // check offset taken into account
        dml = session.query(sql, 0, 2, 10);
        assertEquals(5, dml.size());
        assertEquals(7, dml.totalSize());

        // check offset taken into account even when no results
        dml = session.query(sql, 0, 9, 10);
        assertEquals(0, dml.size());
        assertEquals(7, dml.totalSize());

        // test totalCount=true with an offset + limit
        dml = session.query(sql, 2, 2, 10);
        assertEquals(2, dml.size());
        assertEquals(7, dml.totalSize());

        // truncate result to 6
        dml = session.query(sql, 0, 0, 6);
        assertTrue(dml.totalSize() < 0);
        // watch out, the size of the list can be countUpTo + 1
        assertEquals(7, dml.size());

        // use limit to have an exact size
        dml = session.query(sql, 6, 0, 6);
        assertTrue(dml.totalSize() < 0);
        assertEquals(6, dml.size());

        // use limit to have an exact size
        dml = session.query(sql, 3, 0, 3);
        assertTrue(dml.totalSize() < 0);
        assertEquals(3, dml.size());

        // limit/offset overrides the countUpTo
        dml = session.query(sql, 5, 0, 2);
        assertTrue(dml.totalSize() < 0);
        assertEquals(5, dml.size());

        dml = session.query(sql, 3, 4, 2);
        assertTrue(dml.totalSize() < 0);
        assertEquals(3, dml.size());
    }

    @Test
    public void testQueryConstantsLeft() {
        assumeTrue(!isDBSMongoDB(), "DBS MongoDB cannot query const = const");

        String sql;
        DocumentModelList dml;
        createDocs();

        sql = "SELECT * FROM SysContent WHERE 1 = 0";
        dml = session.query(sql);
        assertEquals(0, dml.totalSize());

        sql = "SELECT * FROM SysContent WHERE 0 = 0";
        dml = session.query(sql);
        assertEquals(8, dml.totalSize());
    }

    // from TestSQLWithPath
    @Test
    public void testEcmPathEqual() {
        String sql;
        DocumentModelList dml;
        createDocs();

        sql = "SELECT * FROM SysContent WHERE sys_path = '/testfolder1' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        // trailing slash accepted
        sql = "SELECT * FROM SysContent WHERE sys_path = '/testfolder1/' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_path <> '/testfolder1' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(6, dml.size());
    }

    @Test
    public void testStartsWith() {
        String sql;
        DocumentModelList dml;
        createDocs();

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/'";
        dml = session.query(sql);
        assertEquals(8, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(7, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/nothere/'";
        dml = session.query(sql);
        assertEquals(0, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/testfolder1/' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(3, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/testfolder2/'";
        dml = session.query(sql);
        assertEquals(2, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_title='testfile1_Title' AND sys_path STARTSWITH '/' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_title LIKE 'testfile%' AND sys_path STARTSWITH '/' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(4, dml.size());

    }

    @Test
    public void testStartsWithMove() {
        String sql;
        DocumentModelList dml;
        createDocs();

        // move folder2 into folder1
        session.move(new PathRef("/testfolder2/"), new PathRef("/testfolder1/"), null);
        session.save();

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/testfolder1/' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(6, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/testfolder1/testfolder2/' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(2, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/testfolder2/'";
        dml = session.query(sql);
        assertEquals(0, dml.size());
    }

    @Test
    public void testStartsWithCopy() {
        String sql;
        DocumentModelList dml;
        createDocs();

        // copy folder2 into folder1
        session.copy(new PathRef("/testfolder2"), new PathRef("/testfolder1"), null);
        // session.save() not needed, implicit in DBS to follow VCS behavior

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/testfolder1' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(6, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/testfolder1/testfolder2' AND sysver_isVersion = FALSE";
        dml = session.query(sql);
        assertEquals(2, dml.size());

        sql = "SELECT * FROM SysContent WHERE sys_path STARTSWITH '/testfolder2'";
        dml = session.query(sql);
        assertEquals(2, dml.size());
    }

    @Test
    public void testAncestorId() {
        DocumentModelList dml;
        createDocs();

        String query = "SELECT * FROM SysContent WHERE sys_ancestorIds = '%s'";
        dml = session.query(String.format(query, session.getRootDocument().getId()));
        assertEquals(8, dml.size());

        query = "SELECT * FROM SysContent WHERE sys_ancestorIds = '%s' AND sysver_isVersion = FALSE";
        dml = session.query(String.format(query, session.getRootDocument().getId()));
        assertEquals(7, dml.size());

        dml = session.query(String.format(query, "nosuchid"));
        assertEquals(0, dml.size());

        dml = session.query(String.format(query, session.getDocument(new PathRef("/testfolder1")).getId()));
        assertEquals(3, dml.size());

        dml = session.query(String.format(query, session.getDocument(new PathRef("/testfolder2")).getId()));
        assertEquals(2, dml.size());

        // negative query
        dml = session.query(
                String.format("SELECT * FROM SysContent WHERE sys_ancestorIds <> '%s' AND sysver_isVersion = FALSE",
                        session.getDocument(new PathRef("/testfolder1")).getId()));
        assertEquals(4, dml.size());

        dml = session.query(String.format(
                "SELECT * FROM SysContent WHERE sys_title='testfile1_Title' AND sys_ancestorIds = '%s' AND sysver_isVersion = FALSE",
                session.getRootDocument().getId()));
        assertEquals(1, dml.size());

        dml = session.query(String.format(
                "SELECT * FROM SysContent WHERE sys_title LIKE 'testfile%%' AND sys_ancestorIds = '%s' AND sysver_isVersion = FALSE",
                session.getRootDocument().getId()));
        assertEquals(4, dml.size());
    }

    @Test
    public void testStartsWithNonPath() {
        String sql;
        createDocs();

        sql = "SELECT * FROM SysContent WHERE mydc_coverage STARTSWITH 'foo'";
        assertEquals(1, session.query(sql).size());

        sql = "SELECT * FROM SysContent WHERE mydc_coverage STARTSWITH 'foo/bar'";
        assertEquals(1, session.query(sql).size());

        sql = "SELECT * FROM SysContent WHERE mydc_coverage STARTSWITH 'foo/bar/baz'";
        assertEquals(0, session.query(sql).size());

        sql = "SELECT * FROM SysContent WHERE mydc_subjects STARTSWITH 'foo'";
        assertEquals(1, session.query(sql).size());

        sql = "SELECT * FROM SysContent WHERE mydc_subjects STARTSWITH 'gee'";
        assertEquals(1, session.query(sql).size());

        sql = "SELECT * FROM SysContent WHERE mydc_subjects STARTSWITH 'gee/moo'";
        assertEquals(1, session.query(sql).size());

        sql = "SELECT * FROM SysContent WHERE mydc_subjects STARTSWITH 'gee/moo/blah'";
        assertEquals(0, session.query(sql).size());

        // System properties are also supported to have a coherent behavior
        // even if it's useless: (primaryType='SysFolder' OR primaryType LIKE 'SysFolder/%')
        sql = "SELECT * FROM SysContent WHERE sys_primaryType STARTSWITH 'SysFolder'";
        assertTrue(session.query(sql).size() > 0);
    }

    @Test
    public void testReindexEditedDocument() {
        String sql = "SELECT * FROM SysContent WHERE sys_title LIKE 'testfile1_Ti%'";
        DocumentModelList dml;
        createDocs();

        dml = session.query(sql);
        assertEquals(1, dml.size());

        DocumentModel file1 = session.getDocument(new PathRef("/testfolder1/testfile1"));

        // edit file1
        file1.setPropertyValue(SYS_DESCRIPTION, "testfile1_description");
        file1.setPropertyValue(SYSFILE_BLOB, null);
        session.save();

        // rerunning the same query
        dml = session.query(sql);
        assertEquals(1, dml.size());

        // edit the title
        file1.setPropertyValue(SYS_TITLE, "testfile1_ModifiedTitle");
        session.save();

        // rerun the same query
        dml = session.query(sql);
        assertEquals(0, dml.size());

        // editithe title
        file1.setPropertyValue(SYS_DESCRIPTION, "Yet another description");
        session.save();

        // adjust the query to the new title
        sql = "SELECT * FROM SysContent WHERE sys_title LIKE 'testfile1_Mo%'";
        dml = session.query(sql);
        assertEquals(1, dml.size());
    }

    @Test
    public void testTimestamp() {
        String sql;
        DocumentModelList dml;
        createDocs();

        sql = "SELECT * FROM SysContent WHERE my2_date >= TIMESTAMP '2007-03-15 00:00:00'";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        sql = "SELECT * FROM SysContent WHERE my2_date < TIMESTAMP '2100-01-01 01:02:03'";
        dml = session.query(sql);
        assertEquals(2, dml.size());
    }

    // old-style date comparisons (actually using timestamps)
    @Test
    public void testDateOld() {
        String sql;
        DocumentModelList dml;
        createDocs();

        sql = "SELECT * FROM SysContent WHERE my2_date >= DATE '2007-01-01'";
        dml = session.query(sql);
        assertEquals(2, dml.size());

        sql = "SELECT * FROM SysContent WHERE my2_date >= DATE '2007-03-15'";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        sql = "SELECT * FROM SysContent WHERE my2_date >= DATE '2007-05-01'";
        dml = session.query(sql);
        assertEquals(0, dml.size());

        sql = "SELECT * FROM SysContent WHERE my2_date >= DATE '2007-02-15' AND my2_date <= DATE '2007-03-15'";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE my2_date BETWEEN DATE '2007-01-01' AND DATE '2008-01-01'");
        assertEquals(2, dml.size());

        dml = session.query("SELECT * FROM SysContent WHERE my2_date BETWEEN DATE '2007-03-15' AND DATE '2008-01-01'");
        assertEquals(1, dml.size());

        // Documents without creation date don't match any DATE query
        // 2 documents with creation date

        dml = session.query(
                "SELECT * FROM SysContent WHERE my2_date NOT BETWEEN DATE '2007-01-01' AND DATE '2008-01-01'");
        assertEquals(0, dml.size()); // 2 Documents match the BETWEEN query

        dml = session.query(
                "SELECT * FROM SysContent WHERE my2_date NOT BETWEEN DATE '2007-03-15' AND DATE '2008-01-01'");
        assertEquals(1, dml.size()); // 1 Document matches the BETWEEN query

        dml = session.query(
                "SELECT * FROM SysContent WHERE my2_date NOT BETWEEN DATE '2009-03-15' AND DATE '2009-01-01'");
        assertEquals(2, dml.size()); // 0 Document matches the BETWEEN query
    }

    // new-style date comparisons (casting to native DATE type)
    @Test
    public void testDateNew() {
        assumeFalse(isDBSMongoDB(), "MongoDB does not support NXQL DATE casts");

        String sql;
        DocumentModelList dml;
        createDocs();

        // create file 5 (type File2)
        DocumentModel file5 = session.createDocumentModel("/", "testfile5", "File2");
        file5.setPropertyValue(SYS_TITLE, "testfile5Title");
        Instant cal = Instant.parse("2012-03-01T01:02:03Z");
        file5.setPropertyValue("tst2_dates", new Object[] { cal });
        file5.create();
        session.save();

        // same as above but with cast
        sql = "SELECT * FROM SysFile WHERE DATE(my2_date) >= DATE '2007-01-01'";
        dml = session.query(sql);
        assertEquals(2, dml.size());

        sql = "SELECT * FROM SysFile WHERE DATE(my2_date) >= DATE '2007-03-15'";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        sql = "SELECT * FROM SysFile WHERE DATE(my2_date) >= DATE '2007-05-01'";
        dml = session.query(sql);
        assertEquals(0, dml.size());

        // equality testing
        sql = "SELECT * FROM SysFile WHERE DATE(my2_date) = DATE '2007-03-01'";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        // switched order
        sql = "SELECT * FROM SysFile WHERE DATE '2007-01-01' <= DATE(my2_date)";
        dml = session.query(sql);
        assertEquals(2, dml.size());

        // list with subquery
        sql = "SELECT * FROM SysFile WHERE DATE(tst2_dates) = DATE '2012-03-01'";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        // list with join
        sql = "SELECT * FROM SysFile WHERE DATE(tst2_dates/*) = DATE '2012-03-01'";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        // less-than on just date, not timestamp at 00:00:00
        sql = "SELECT * FROM SysFile WHERE DATE(my2_date) <= DATE '2007-03-01'";
        dml = session.query(sql);
        assertEquals(1, dml.size());

        // TODO check bounds for meaningful test
        sql = "SELECT * FROM SysFile WHERE DATE(my2_date) NOT BETWEEN DATE '2007-03-15' AND DATE '2008-01-01'";
        dml = session.query(sql);
        assertEquals(1, dml.size()); // 1 Document matches the BETWEEN query
    }

    @Test
    public void testDateBad() {
        String sql;
        createDocs();

        try {
            sql = "SELECT * FROM SysFile WHERE DATE(sys_title) = DATE '2012-01-01'";
            session.query(sql);
            fail("Should fail due to invalid cast");
        } catch (QueryParseException e) {
            String m = e.getMessage();
            assertTrue(m.contains("Cannot cast to DATE"), m);
        }

        try {
            sql = "SELECT * FROM SysFile WHERE DATE(my2_date) = TIMESTAMP '2012-01-01 00:00:00'";
            session.query(sql);
            fail("Should fail due to invalid cast");
        } catch (QueryParseException e) {
            String m = e.getMessage();
            assertTrue(m.contains("DATE() cast must be used with DATE literal, not TIMESTAMP"), m);
        }

        try {
            sql = "SELECT * FROM SysFile WHERE DATE(my2_date) BETWEEN TIMESTAMP '2012-01-01 00:00:00' AND DATE '2012-02-02'";
            session.query(sql);
            fail("Should fail due to invalid cast");
        } catch (QueryParseException e) {
            String m = e.getMessage();
            assertTrue(m.contains("DATE() cast must be used with DATE literal, not TIMESTAMP"), m);
        }

    }

    // other tests
    @Test
    public void testBoolean() {
        String sql;
        DocumentModelList dml;
        createDocs();

        sql = "SELECT * FROM SysContent WHERE my2_boolean = 1";
        dml = session.query(sql);
        assertEquals(0, dml.size());
        sql = "SELECT * FROM SysContent WHERE my2_boolean = 0";
        dml = session.query(sql);
        assertEquals(0, dml.size());

        DocumentModel doc = session.createDocumentModel("/testfolder1", "mydoc", MY_DOC_TYPE);
        doc.setPropertyValue("my2_boolean", Boolean.TRUE);
        doc.create();
        session.save();

        sql = "SELECT * FROM SysContent WHERE my2_boolean = 1";
        dml = session.query(sql);
        assertEquals(1, dml.size());
        sql = "SELECT * FROM SysContent WHERE my2_boolean = 0";
        dml = session.query(sql);
        assertEquals(0, dml.size());

        doc.setPropertyValue("my2_boolean", Boolean.FALSE);
        session.save();

        sql = "SELECT * FROM SysContent WHERE my2_boolean = 1";
        dml = session.query(sql);
        assertEquals(0, dml.size());
        sql = "SELECT * FROM SysContent WHERE my2_boolean = 0";
        dml = session.query(sql);
        assertEquals(1, dml.size());
    }

    @Test
    public void testDecimal() {
        DocumentModelList dml;

        DocumentModel doc = session.createDocumentModel("/", "mydoc", MY_DOC_TYPE);
        doc.setPropertyValue("my2_decimal", "1234567890");
        doc.create();
        session.save();

        String hxql = "SELECT * FROM MyDocType WHERE my2_decimal = 1234567890";
        dml = session.query(hxql);
        assertEquals(1, dml.size());
    }

    @Test
    public void testQueryWithSecurity() {
        createDocs();
        DocumentModel root = session.getRootDocument();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.builder().user("bob").permission(READ).build());
        root.setACL(acl, true);
        DocumentModel folder1 = session.getDocument(new PathRef("/testfolder1"));
        acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.BLOCK);
        folder1.setACL(acl, true);
        session.save();

        CoreSession bobSession = openSessionAs("bob");
        DocumentModelList dml = bobSession.query("SELECT * FROM SysContent");
        assertEquals(3, dml.size());
    }

    // same with queryAndFetch
    @Test
    public void testQueryWithSecurity2() {
        createDocs();
        DocumentModel root = session.getRootDocument();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.builder().user("bob").permission(READ).build());
        root.setACL(acl, true);
        DocumentModel folder1 = session.getDocument(new PathRef("/testfolder1"));
        acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.BLOCK);
        folder1.setACL(acl, true);
        session.save();

        CoreSession bobSession = openSessionAs("bob");
        IterableQueryResult res = bobSession.queryAndFetch("SELECT * FROM SysContent", "NXQL");
        assertEquals(3, res.size());
        res.close();
    }

    @Test
    public void testWithoutSecurityManager() {
        createDocs();
        DocumentModelList dml;

        dml = session.query("SELECT * FROM SysContent WHERE sysver_isVersion = FALSE");
        assertEquals(7, dml.size());
        assertEquals(7, dml.totalSize());
        dml = session.query("SELECT * FROM SysContent WHERE sysver_isVersion = FALSE", 2, 0, -1);
        assertEquals(2, dml.size());
        assertEquals(7, dml.totalSize());
        dml = session.query("SELECT * FROM SysContent WHERE sysver_isVersion = FALSE", 2, 5, -1);
        assertEquals(2, dml.size());
        assertEquals(7, dml.totalSize());
        dml = session.query("SELECT * FROM SysContent WHERE sysver_isVersion = FALSE", 2, 6, -1);
        assertEquals(1, dml.size());
        assertEquals(7, dml.totalSize());
    }

    private static void assertIdSet(DocumentModelList dml, String... ids) {
        Collection<String> expected = new HashSet<>(Arrays.asList(ids));
        Collection<String> actual = new HashSet<>();
        for (DocumentModel d : dml) {
            actual.add(d.getId());
        }
        assertEquals(expected, actual);
    }

    @Test
    public void testQueryACL() {
        createDocs();
        DocumentModel folder1 = session.getDocument(new PathRef("/testfolder1"));
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.builder().user("bob").permission(READ).build());
        acl.add(ACE.builder().user("steve").permission(READ).build());
        Instant begin = Instant.now().minus(5, DAYS);
        Instant end = Instant.now().plus(5, DAYS);
        acl.add(ACE.builder().user("leela").permission("Write").creator(TEST_ADMIN_USER).begin(begin).end(end).build());

        acl.add(ACE.BLOCK);

        acl.add(ACE.builder().user("pete").permission("Write").build());

        folder1.setACL(acl, true);
        session.save();

        String queryBase = "SELECT * FROM SysContent WHERE ";

        // simple query
        checkQueryACL(1, queryBase + "sys_acl/*/principal = 'u:bob'");

        // documents with both bob and steve
        checkQueryACL(1, queryBase + "sys_acl/*/principal = 'u:bob' AND sys_acl/*/principal = 'u:steve'");

        // bob cannot be steve, no match
        checkQueryACL(0, queryBase + "sys_acl/*1/principal = 'u:bob' AND sys_acl/*1/principal = 'u:steve'");

        // bob with Read
        checkQueryACL(1, queryBase + "sys_acl/*1/principal = 'u:bob' AND sys_acl/*1/permission = 'Read'");

        // bob with Read granted
        checkQueryACL(1, queryBase
                + "sys_acl/*1/principal = 'u:bob' AND sys_acl/*1/permission = 'Read' AND sys_acl/*1/grant = 1");

        // bob with Read denied, no match
        checkQueryACL(0, queryBase
                + "sys_acl/*1/principal = 'u:bob' AND sys_acl/*1/permission = 'Read' AND sys_acl/*1/grant = 0");

        // a bob and a Read
        checkQueryACL(1, queryBase + "sys_acl/*/principal = 'u:bob' AND sys_acl/*/permission = 'Read'");

        // creator is TestAdministrator
        checkQueryACL(1, queryBase + "sys_acl/*/creator = '%s'".formatted(TEST_ADMIN_USER));

        // document for leela with a begin date after 2007-01-01
        checkQueryACL(1, queryBase
                + "sys_acl/*1/principal = 'u:leela' AND sys_acl/*1/begin >= DATE '2007-01-01' AND sys_acl/*1/end <= DATE '2100-01-01'");

        // document for leela with an end date after 2100-01-01, no match
        checkQueryACL(0, queryBase + "sys_acl/*1/principal = 'u:leela' AND sys_acl/*1/end >= DATE '2100-01-01'");

        // document with valid begin date but not end date, no match
        checkQueryACL(0, queryBase + "sys_acl/*1/begin >= DATE '2007-01-01' AND sys_acl/*1/end >= DATE '2100-01-01'");

        // document with effective acl
        checkQueryACL(1, queryBase + "sys_acl/*1/status = 1");

        if (!notMatchesNull()) {
            // document with pending or archived acl, no match
            checkQueryACL(0, queryBase + "sys_acl/*1/status <> 1");
        }

        // block
        checkQueryACL(1, queryBase
                + "sys_acl/*1/principal = '%s' AND sys_acl/*1/permission = 'Everything' AND sys_acl/*1/grant = 0".formatted(
                        EVERYONE));

        // explicit array index not possible on DBS
        // checkQueryACL(1, queryBase + "sys_acl/0/principal = 'pete'")
        // checkQueryACL(1, queryBase + "sys_acl/1/principal = 'Administrator'")
        // checkQueryACL(1, queryBase + "sys_acl/2/principal = 'bob'")
        // checkQueryACL(1, queryBase + "sys_acl/3/principal = 'steve'")
    }

    /**
     * @since 9.3
     */
    protected void checkQueryACL(CoreSession session, int expected, String query) {
        DocumentModelList dml = session.query(query);
        assertEquals(expected, dml.size());

        IterableQueryResult res = session.queryAndFetch(query, "NXQL");
        long size = res.size();
        res.close();
        assertEquals(expected, size);
    }

    protected void checkQueryACL(int expected, String query) {
        checkQueryACL(session, expected, query);
    }

    @Test
    public void testQueryACLReturnedValue() {
        createDocs();
        DocumentModel folder1 = session.getDocument(new PathRef("/testfolder1"));
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.builder().user("bob").permission(READ).build());
        acl.add(ACE.builder().user("steve").permission(READ).build());
        // avoid DB rounding for timestamp on milliseconds
        Instant begin = Instant.now().minus(5, DAYS).truncatedTo(SECONDS);
        Instant end = Instant.now().plus(5, DAYS).truncatedTo(SECONDS);
        acl.add(ACE.builder().user("leela").permission("Write").creator(TEST_ADMIN_USER).begin(begin).end(end).build());
        acl.add(ACE.BLOCK);
        folder1.setACL(acl, true);
        session.save();

        // update the begin and end dates from the one being stored in the DB to correctly match them after
        ACL updatedACL = folder1.getLocalACL();
        for (ACE ace : updatedACL) {
            if ("leela".equals(ace.getPrincipal())) {
                begin = ace.getBegin();
                end = ace.getEnd();
                break;
            }
        }

        IterableQueryResult res;
        // simple query
        res = session.queryAndFetch(
                "SELECT sys_id, sys_acl/*1/principal, sys_acl/*1/permission FROM SysContent WHERE "
                        + "sys_acl/*1/permission = 'Read' AND sys_acl/*1/grant = 1",
                "NXQL");
        assertEquals(2, res.size());
        Set<String> set = new HashSet<>();
        for (Map<String, Object> map : res) {
            set.add(map.get("sys_acl/*1/principal") + ":" + map.get("sys_acl/*1/permission"));
        }
        res.close();
        assertEquals(Set.of("u:bob:Read", "u:steve:Read"), set);

        // read full ACL
        // sys_pos in VCS-specific so not checked
        res = session.queryAndFetch( //
                "SELECT sys_id, sys_acl/*1/principal, sys_acl/*1/permission, sys_acl/*1/grant, " //
                        + "sys_acl/*1/creator, sys_acl/*1/begin, sys_acl/*1/end " //
                        + "FROM SysContent WHERE " //
                        + "sys_acl/*/principal = 'u:bob'",
                "NXQL");
        assertEquals(5, res.size());
        set = new HashSet<>();
        for (Map<String, Object> map : res) {
            String ace = map.get("sys_acl/*1/principal") + ":" + map.get("sys_acl/*1/permission") + ":"
                    + map.get("sys_acl/*1/grant") + ":" + map.get("sys_acl/*1/creator");
            Instant cal = (Instant) map.get("sys_acl/*1/begin");
            ace += ":" + (cal != null ? cal.toEpochMilli() : null);
            cal = (Instant) map.get("sys_acl/*1/end");
            ace += ":" + (cal != null ? cal.toEpochMilli() : null);
            set.add(ace);
        }
        res.close();
        assertEquals(Set.of( //
                "u:%s:%s:true:null:null:null".formatted(TEST_ADMIN_USER, EVERYTHING), //
                "u:bob:%s:true:null:null:null".formatted(READ), //
                "u:steve:%s:true:null:null:null".formatted(READ), //
                "u:leela:%s:true:%s:%s:%s".formatted(WRITE, TEST_ADMIN_USER, begin.toEpochMilli(),
                        end.toEpochMilli()), //
                "%s:%s:false:null:null:null".formatted(EVERYONE, EVERYTHING) //
        ), set);
    }

    @Test
    public void testQueryWithDateBasedACLs() {
        createDocs();

        DocumentModel folder1 = session.getDocument(new PathRef("/testfolder1"));
        Instant begin = Instant.now().plus(5, DAYS);
        Instant end = Instant.now().plus(10, DAYS);

        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("leela").permission(READ).creator(TEST_ADMIN_USER).begin(begin).end(end).build());

        folder1.setACL(acl, true);
        session.save();

        String query = String.format("SELECT * FROM SysContent WHERE sys_id = '%s'", folder1.getId());
        checkQueryACL(1, query);

        CoreSession leelaSession = coreFeature.getCoreSession("leela");
        checkQueryACL(leelaSession, 0, query);

    }

    @Test
    public void testQueryWithVersions() {
        createDocs();
        String noteVersionId = session.getDocument(file3ref).getId();
        DocumentModel ver = checkinDoc();

        DocumentModel doc = session.getDocument(new PathRef("/testfolder2/testfolder3/testfile4"));
        String docId = doc.getId();
        String versionId = ver.getId();
        assertNotNull(versionId);
        assertNotSame(docId, versionId);

        DocumentModelList dml;

        // queries must return also versions
        dml = session.query("SELECT * FROM SysContent WHERE sys_title = 'testfile4Title'");
        assertIdSet(dml, docId, versionId);

        dml = session.query("SELECT * FROM SysContent");
        assertEquals(9, dml.size()); // 7 folder/docs, 2 versions

        dml = session.query("SELECT * FROM SysContent WHERE sysver_isVersion = TRUE");
        assertIdSet(dml, versionId, noteVersionId);

        dml = session.query("SELECT * FROM SysContent WHERE sysver_isVersion = FALSE");
        assertEquals(7, dml.size()); // 7 folder/docs

        // only keep versions
        dml = session.query("SELECT * FROM SysContent WHERE sys_title = 'testfile4Title' AND sysver_isVersion = TRUE");
        assertIdSet(dml, versionId);

        // query with order by
        dml = session.query("SELECT * FROM SysContent WHERE sys_title = 'testfile4Title' ORDER BY sys_title");
        assertIdSet(dml, docId, versionId);
        dml = session.query("SELECT * FROM MyDocType WHERE sys_title = 'testfile4Title' ORDER BY sys_description");
        assertIdSet(dml, docId, versionId);
    }

    @Test
    public void testQueryPaging() {
        createDocs();
        DocumentModelList whole = session.query("SELECT * FROM SysContent ORDER BY sys_modified, sys_id");
        assertTrue(whole.size() >= 2);
        DocumentModelList firstPage = session.query("SELECT * FROM SysContent ORDER BY sys_modified, sys_id", 1, 0, 0);
        assertEquals(1, firstPage.size());
        assertEquals(whole.get(0).getId(), firstPage.get(0).getId());
        DocumentModelList secondPage = session.query("SELECT * FROM SysContent ORDER BY sys_modified, sys_id", 1, 1, 0);
        assertEquals(1, secondPage.size());
        assertEquals(whole.get(1).getId(), secondPage.get(0).getId());
    }

    @Test
    public void testQueryProjectionPaging() {
        createDocs();
        PartialList<Map<String, Object>> whole = session.queryProjection(
                "SELECT * FROM SysContent ORDER BY sys_modified, sys_id", false, 5, 0, -1);
        assertTrue(whole.totalSize() >= 5);
        PartialList<Map<String, Object>> firstPage = session.queryProjection(
                "SELECT * FROM SysContent ORDER BY sys_modified, sys_id", false, 1, 0, 0);
        assertEquals(1, firstPage.size());
        assertEquals(-1, firstPage.totalSize());
        assertEquals(whole.get(0).get(SYS_ID), firstPage.get(0).get(SYS_ID));
        PartialList<Map<String, Object>> secondPage = session.queryProjection(
                "SELECT * FROM SysContent ORDER BY sys_modified, sys_id", false, 1, 1, 0);
        assertEquals(1, secondPage.size());
        assertEquals(-1, secondPage.totalSize());
        assertEquals(whole.get(1).get(SYS_ID), secondPage.get(0).get(SYS_ID));
    }

    @Test
    public void testQueryPrimaryTypeOptimization() { // NOSONAR (no need to assert anything)
        // check these queries in the logs

        // Folder
        session.query("SELECT * FROM SysContent WHERE sys_primaryType = 'SysFolder'");
        // empty
        session.query(
                "SELECT * FROM SysContent WHERE sys_primaryType = 'SysFolder'" + " AND sys_primaryType = 'SysFile'");
        // empty
        session.query("SELECT * FROM SysFolder WHERE sys_primaryType = 'Note'");
        // Folder
        session.query("SELECT * FROM SysContent WHERE sys_primaryType IN ('SysFolder', 'Note')"
                + " AND sys_primaryType = 'SysFolder'");

        // just folderish
        session.query("SELECT * FROM SysContent WHERE sys_mixinTypes = 'SysFolderish'");
        // no folderish
        session.query("SELECT * FROM SysContent WHERE sys_mixinTypes <> 'SysFolderish'");
        // sys_isfolderish
        session.query("SELECT * FROM SysContent WHERE sys_isFolderish = TRUE");

        // empty
        session.query("SELECT * FROM Note WHERE sys_mixinTypes = 'SysFolderish'");
    }

    @Test
    public void testQueryMixinTypeNotPerDocument() {
        createDocs();
        DocumentModel file1 = session.getDocument(new PathRef("/testfolder1/testfile1"));
        file1.addMixin("NotPerDocFacet2");
        session.save();

        // doc has mixin but not found by search
        DocumentModelList dml = session.query("SELECT * FROM SysContent WHERE sys_mixinTypes = 'NotPerDocFacet2'");
        assertEquals(0, dml.size());
    }

    @Test
    public void testQuerySpecialFields() {
        // sysver_isVersion is already tested in testQueryWithVersions

        // sys_path already tested in testStartsWith

        createDocs();
        DocumentModel ver = checkinDoc();

        DocumentModelList dml;
        DocumentModel folder1 = session.getDocument(new PathRef("/testfolder1"));
        DocumentModel file1 = session.getDocument(new PathRef("/testfolder1/testfile1"));
        DocumentModel file2 = session.getDocument(new PathRef("/testfolder1/testfile2"));
        DocumentModel file3 = session.getDocument(new PathRef("/testfolder1/testfile3"));
        session.getDocument(new PathRef("/testfolder2/testfolder3/testfile4"));
        file1.setLock();
        session.save();

        /*
         * sys_id
         */
        dml = session.query(String.format("SELECT * FROM SysContent WHERE sys_id = '%s'", file1.getId()));
        assertIdSet(dml, file1.getId());
        dml = session.query(String.format("SELECT * FROM SysContent WHERE sys_id = '%s'", ver.getId()));
        assertIdSet(dml, ver.getId());

        /*
         * sys_name
         */
        dml = session.query(String.format("SELECT * FROM SysContent WHERE sys_name = '%s'", file1.getName()));
        assertIdSet(dml, file1.getId());

        /*
         * sys_parentId
         */
        dml = session.query(String.format("SELECT * FROM SysContent WHERE sys_parentId = '%s'", folder1.getId()));
        assertIdSet(dml, file1.getId(), file2.getId(), file3.getId());

        /*
         * sys_primaryType
         */
        dml = session.query("SELECT * FROM SysContent WHERE sys_primaryType = 'SysFolder'");
        assertEquals(3, dml.size());
        dml = session.query("SELECT * FROM SysContent WHERE sys_primaryType <> 'SysFolder'");
        assertEquals(6, dml.size()); // 3 files, 1 note, 2 versions
        dml = session.query("SELECT * FROM SysContent WHERE sys_primaryType = 'Note'");
        assertEquals(2, dml.size()); // 1 note, 1 version
        dml = session.query("SELECT * FROM SysContent WHERE sys_primaryType = 'MyDocType'");
        assertEquals(4, dml.size()); // 3 files, 1 version
        dml = session.query("SELECT * FROM SysContent WHERE sys_primaryType IN ('SysFolder', 'Note')");
        assertEquals(5, dml.size()); // 3 folders, 1 note, 1 version
        dml = session.query("SELECT * FROM SysContent WHERE sys_primaryType NOT IN ('SysFolder', 'Note')");
        assertEquals(4, dml.size()); // 3 files, 1 version

        /*
         * sys_mixinType
         */

        dml = session.query("SELECT * FROM SysContent WHERE sys_mixinTypes = 'SysFolderish'");
        assertEquals(3, dml.size());
        dml = session.query("SELECT * FROM SysContent WHERE sys_mixinTypes = 'SysVersionable'");
        assertEquals(6, dml.size());
        dml = session.query("SELECT * FROM SysContent WHERE sys_mixinTypes IN ('SysFolderish', 'Downloadable')");
        assertEquals(3, dml.size());
        dml = session.query("SELECT * FROM SysContent WHERE sys_mixinTypes NOT IN ('SysFolderish', 'Downloadable')");
        assertEquals(6, dml.size()); // 1 note, 3 files, 2 versions

        // to observe locks, which have been set from another transaction
        // we may need to actually commit and re-open a transaction (MySQL)
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        /*
         * sys_lockOwner
         */
        // don't use a '' here for Oracle, for which '' IS NULL
        dml = session.query("SELECT * FROM SysContent WHERE sys_lockOwner <> '_'");
        if (notMatchesNull()) {
            // check that we find the doc in the list
            assertTrue(dml.stream().map(DocumentModel::getId).collect(Collectors.toSet()).contains(file1.getId()));
        } else {
            assertIdSet(dml, file1.getId());
        }
        dml = session.query("SELECT * FROM SysContent ORDER BY sys_lockOwner");
        assertEquals(9, dml.size());

        /*
         * sys_lockCreated
         */
        dml = session.query("SELECT * FROM SysContent ORDER BY sys_lockCreated");
        assertEquals(9, dml.size());
    }

    @Test
    public void testQuerySpecialFieldsVersioning() {
        createDocs();
        String noteVersionId = session.getDocument(file3ref).getId();
        DocumentModel doc = session.getDocument(new PathRef("/testfolder2/testfolder3/testfile4"));
        DocumentModel version = checkinDoc(); // testfile4 version
        DocumentModel file1 = session.getDocument(new PathRef("/testfolder1/testfile1"));
        DocumentRef v1 = session.checkIn(file1.getRef());
        session.checkOut(file1.getRef());
        maybeSleepToNextSecond();
        DocumentRef v2 = session.checkIn(file1.getRef());
        session.save();
        // note is automatically versioned
        DocumentModel note = session.getDocument(new PathRef("/testfolder1/testfile3"));

        DocumentModelList dml;

        /*
         * sysver_isCheckedIn
         */
        dml = session.query("SELECT * FROM SysContent WHERE sysver_isCheckedIn = TRUE");
        assertIdSet(dml, note.getId(), doc.getId(), file1.getId());
        dml = session.query("SELECT * FROM SysContent WHERE sysver_isCheckedIn = FALSE");
        assertEquals(8, dml.size());
        // checkout and make sure we find it in correct state
        session.checkOut(file1.getRef());
        session.save();
        dml = session.query("SELECT * FROM SysContent WHERE sysver_isCheckedIn = TRUE");
        assertIdSet(dml, doc.getId(), note.getId());
        dml = session.query("SELECT * FROM SysContent WHERE sysver_isCheckedIn = FALSE");
        assertEquals(9, dml.size());

        /*
         * sysver_isVersion
         */
        dml = session.query("SELECT * FROM SysContent WHERE sysver_isVersion = TRUE");
        assertIdSet(dml, version.getId(), noteVersionId, v1.toString(), v2.toString());
        dml = session.query("SELECT * FROM SysContent WHERE sysver_isVersion = FALSE");
        assertEquals(7, dml.size()); // 7 folder/docs

        /*
         * sysver_created
         */
        dml = session.query("SELECT * FROM SysContent WHERE sysver_created IS NOT NULL");
        assertIdSet(dml, version.getId(), noteVersionId, v1.toString(), v2.toString());

        /*
         * sysver_creator
         */
        dml = session.query("SELECT * FROM SysContent WHERE sysver_creator = '" + TEST_ADMIN_USER + "'");
        assertIdSet(dml, version.getId(), noteVersionId, v1.toString(), v2.toString());
    }

    @Test
    public void testIsTrashedWithProperty() {
        String sqlNotTrashed = "SELECT * FROM SysContent WHERE sys_isTrashed = FALSE";
        String sqlTrashed = "SELECT * FROM SysContent WHERE sys_isTrashed = TRUE";
        doTestTrashed(sqlNotTrashed, sqlTrashed);
    }

    protected void doTestTrashed(String sqlNotTrashed, String sqlTrashed) {
        DocumentModelList dml;
        createDocs();

        dml = session.query(sqlNotTrashed);
        assertEquals(8, dml.size());
        dml = session.query(sqlTrashed);
        assertEquals(0, dml.size());

        // put a doc in the trash
        DocumentModel file1 = session.getDocument(new PathRef("/testfolder1/testfile1"));
        trashService.trashDocument(file1);

        dml = session.query(sqlNotTrashed);
        assertEquals(7, dml.size());
        dml = session.query(sqlTrashed);
        assertEquals(1, dml.size());
    }

    @Test
    public void testOrderByAndDistinct() {
        createDocs();

        String query;
        DocumentModelList dml;

        DocumentModel file1 = session.getDocument(new PathRef("/testfolder1/testfile1"));
        file1.setPropertyValue(SYS_TITLE, "hello world 1");

        session.save();

        waitForFulltextIndexing();

        query = "SELECT * FROM MyDocType Where sys_title = 'hello world 1' ORDER BY sysver_version";

        dml = session.query(query);
        assertIdSet(dml, file1.getId());
    }

    @Test
    public void testQueryIterable() {
        createDocs();

        IterableQueryResult res = session.queryAndFetch("SELECT * FROM MyDocType", "NXQL");
        List<Map<String, Object>> l = new LinkedList<>();
        for (Map<String, Object> x : res) {
            l.add(x);
        }
        assertEquals(3, l.size());
        res.close();

        // cursor behavior
        res = session.queryAndFetch("SELECT * FROM MyDocType", "NXQL");
        Iterator<Map<String, Object>> it = res.iterator();
        assertEquals(0, res.pos());
        it.next();
        assertEquals(1, res.pos());
        assertEquals(3, res.size());
        assertEquals(1, res.pos());
        assertTrue(it.hasNext());
        assertEquals(1, res.pos());
        it.next();
        assertEquals(2, res.pos());
        assertTrue(it.hasNext());
        assertEquals(2, res.pos());
        it.next();
        assertEquals(3, res.pos());
        assertFalse(it.hasNext());
        assertEquals(3, res.pos());

        res.skipTo(1);
        assertEquals(3, res.size());
        assertTrue(it.hasNext());
        assertEquals(1, res.pos());
        it.next();
        assertEquals(2, res.pos());
        res.close();

        // checking size when at end
        res = session.queryAndFetch("SELECT * FROM MyDocType", "NXQL");
        it = res.iterator();
        it.next();
        it.next();
        it.next();
        assertFalse(it.hasNext());
        assertEquals(3, res.size());
        res.close();

        // size when query returns nothing
        res = session.queryAndFetch("SELECT * FROM MyDocType WHERE sys_title = 'zzz'", "NXQL");
        it = res.iterator();
        assertFalse(it.hasNext());
        assertEquals(0, res.size());
        res.close();
    }

    @Test
    public void testQueryComplexTypeFiles() {
        DocumentModel doc = session.createDocumentModel("/", "myfile", SYS_FILE);
        List<Object> files = new LinkedList<>();
        Map<String, Object> f = new HashMap<>();
        f.put("blob", Blobs.createBlob("b1", "text/plain", "UTF-8", "f1"));
        files.add(f);
        doc.setPropertyValue("sysfiles_blobs", files);
        doc.create();
        session.save();

        DocumentModelList dml = session.query("SELECT * FROM SysFile");
        assertEquals(1, dml.size());
        // Case insensitive databases may fail with:
        // ERROR Unknown document type: file
        // due to its case-insensitivity in = and IN tests...
        // and returning an empty query, cf SQLQueryResult.getDocumentModels
    }

    @Test
    public void testSelectColumns() {
        String query;
        IterableQueryResult res;
        Iterator<Map<String, Object>> it;
        Map<String, Object> map;

        createDocs();

        // check proper tables are joined even if not in FROM
        query = "SELECT sys_id, sys_title FROM MyDocType";
        res = session.queryAndFetch(query, "NXQL");
        assertEquals(3, res.size());
        map = res.iterator().next();
        assertTrue(map.containsKey(SYS_TITLE));
        assertTrue(map.containsKey(SYS_ID));
        res.close();

        // check content
        query = "SELECT sys_id, sys_title FROM MyDocType ORDER BY sys_title";
        res = session.queryAndFetch(query, "NXQL");
        assertEquals(3, res.size());
        it = res.iterator();
        map = it.next();
        assertEquals("testfile1_Title", map.get(SYS_TITLE));
        map = it.next();
        assertEquals("testfile2_Title", map.get(SYS_TITLE));
        map = it.next();
        assertEquals("testfile4Title", map.get(SYS_TITLE));
        res.close();
    }

    @Test
    public void testSelectColumnsSameName() {
        String query;
        IterableQueryResult res;
        Map<String, Object> map;

        // two fields with same key
        DocumentModel file = session.createDocumentModel("/", "testfile", "File2");
        file.setPropertyValue(SYS_TITLE, "title1");
        file.setPropertyValue("tst2_title", "title2");
        file.create();
        session.save();

        query = "SELECT tst2_title, sys_title FROM SysFile WHERE sys_title = 'title1'";
        res = session.queryAndFetch(query, "NXQL");
        assertEquals(1, res.size());
        map = res.iterator().next();
        assertEquals("title1", map.get(SYS_TITLE));
        assertEquals("title2", map.get("tst2_title"));
        res.close();

        // now with ORDER BY
        query = "SELECT tst2_title, sys_title FROM SysFile WHERE sys_title = 'title1' ORDER BY sys_id";
        res = session.queryAndFetch(query, "NXQL");
        assertEquals(1, res.size());
        map = res.iterator().next();
        assertEquals("title1", map.get(SYS_TITLE));
        assertEquals("title2", map.get("tst2_title"));
        res.close();
    }

    /**
     * @since 9.3
     */
    @Test
    public void testSelectColumnMappings() {
        String query;

        DocumentModel file = session.createDocumentModel("/", "testfileForColumns", MY_DOC_TYPE);
        file.setPropertyValue(SYS_TITLE, "special1");
        String content = "I am feeling elastic.";
        String filename = "rucontent.txt";
        Blob blob1 = Blobs.createBlob(content);
        blob1.setFilename(filename);
        file.setPropertyValue(SYSFILE_BLOB, blob1);
        file.create();
        file.checkIn();
        session.save();

        query = "SELECT sys_title FROM MyDocType WHERE sys_title = 'special1' AND sysver_isVersion = TRUE";
        try (IterableQueryResult res = session.queryAndFetch(query, "NXQL")) {
            assertEquals(1, res.size());
            Map<String, Object> map = res.iterator().next();
            assertEquals("special1", map.get(SYS_TITLE));
        }

        query = "SELECT sysver_version FROM MyDocType WHERE sys_title = 'special1' AND sysver_isVersion = TRUE";
        try (IterableQueryResult res = session.queryAndFetch(query, "NXQL")) {
            assertEquals(1, res.size());
            Map<String, Object> map = res.iterator().next();
            assertEquals(1L, map.get(SYSVER_VERSION));
        }

        query = "SELECT sysfile_blob/filename, sysfile_blob/length FROM MyDocType WHERE sys_title = 'special1' AND sysver_isVersion = TRUE";
        try (IterableQueryResult res = session.queryAndFetch(query, "NXQL")) {
            assertEquals(1, res.size());
            Map<String, Object> map = res.iterator().next();
            assertEquals("rucontent.txt", map.get("sysfile_blob/filename"));
            assertEquals(21L, map.get("sysfile_blob/length"));
        }
    }

    @Test
    public void testQueryDistinctId() {
        DocumentModelList dml;
        createDocs();

        dml = session.query("SELECT sys_id FROM MyDocType");
        assertEquals(3, dml.size());
    }

    @Test
    public void testQueryAndFetchDistinctId() {
        makeComplexDoc();

        String query = "SELECT sys_id FROM TestDoc WHERE tst_friends/*/firstname = 'John'";
        IterableQueryResult res = session.queryAndFetch(query, "NXQL");
        assertEquals(1, res.size());
        res.close();
    }

    @Test
    public void testQueryProjectionDistinctId() {
        makeComplexDoc();

        String query = "SELECT sys_id FROM TestDoc WHERE tst_friends/*/firstname = 'John'";
        PartialList<Map<String, Object>> res = session.queryProjection(query, false, 10, 0, 0);
        assertEquals(1, res.size());
    }

    @Test
    public void testQueryAnyTypeSubProperty() {
        makeComplexDoc();

        String query = "SELECT * FROM TestDoc WHERE testschema3_whatever/key1 = 'val1' AND testschema3_whatever/key2/nested = 42";
        DocumentModelList res = session.query(query);
        assertEquals(1, res.size());
    }

    // ----- timestamp tests -----

    protected Instant setupDocTest() {
        Instant currentDate = Instant.now();
        DocumentModel testDocument = session.createDocumentModel("/", "testfolder1", MY_DOC_TYPE);
        testDocument.setPropertyValue(SYS_TITLE, "test");
        testDocument.setPropertyValue("my2_date", currentDate);
        testDocument.create();
        session.save();
        return (Instant) testDocument.getPropertyValue("my2_date");
    }

    @Test
    public void testEqualsTimeWithMilliseconds() {
        Instant currentDate = setupDocTest();
        String testQuery = String.format(
                "SELECT * FROM MyDocType WHERE sys_title = 'test' AND my2_date = TIMESTAMP '%s'", currentDate);
        DocumentModelList docs = session.query(testQuery);
        assertEquals(1, docs.size());
    }

    @Test
    public void testLTTimeWithMilliseconds() {
        // add a second to be sure that the document is found
        Instant currentDate = setupDocTest().plus(1, SECONDS);
        String testQuery = String.format(
                "SELECT * FROM MyDocType WHERE sys_title = 'test' AND my2_date < TIMESTAMP '%s'", currentDate);
        DocumentModelList docs = session.query(testQuery);
        assertEquals(1, docs.size());
    }

    @Test
    public void testQueryNow() throws Exception {
        setupDocTest();
        // sleep a second to be sure that the document is found
        nextTransaction();
        Thread.sleep(1000); // 1s

        String testQuery = "SELECT * FROM MyDocType WHERE sys_title = 'test' AND my2_date < NOW()";
        DocumentModelList docs = session.query(testQuery);
        assertEquals(1, docs.size());
        // test with a period
        testQuery = "SELECT * FROM MyDocType WHERE sys_title = 'test' AND my2_date < NOW('PT1H')";
        docs = session.query(testQuery);
        assertEquals(1, docs.size());
        // negative period
        testQuery = "SELECT * FROM MyDocType WHERE sys_title = 'test' AND my2_date > NOW('-PT1H')";
        docs = session.query(testQuery);
        assertEquals(1, docs.size());
    }

    @Test
    public void testQueryIsNull() {
        DocumentModelList dml;
        createDocs();

        dml = session.query("SELECT * FROM MyDocType WHERE sys_title IS NOT NULL");
        assertEquals(3, dml.size());
        dml = session.query("SELECT * FROM MyDocType WHERE sys_title IS NULL");
        assertEquals(0, dml.size());

        DocumentModel file1 = session.getDocument(new PathRef("/testfolder1/testfile1"));
        file1.setPropertyValue(SYS_TITLE, null);
        session.save();

        dml = session.query("SELECT * FROM MyDocType WHERE sys_title IS NOT NULL");
        assertEquals(2, dml.size());
        dml = session.query("SELECT * FROM MyDocType WHERE sys_title IS NULL");
        assertEquals(1, dml.size());
    }

    @Test
    public void testMultilineQuery() {
        DocumentModelList dml;
        createDocs();

        String query = "SELECT * \n            FROM MyDocType \n      WHERE sys_title IS NOT NULL \n       ORDER BY sys_path";
        dml = session.query(query);
        assertEquals(3, dml.size());

        query = "SELECT * \r\n        FROM MyDocType \r\n      WHERE sys_title IS NULL \r\n       ORDER BY sys_path DESC";
        dml = session.query(query);
        assertEquals(0, dml.size());
    }

    /**
     * Make sure that even when we use a sequence, the id is a String, for compat with the rest of the framework.
     */
    @Test
    public void testIdType() {
        createDocs();
        IterableQueryResult res = session.queryAndFetch("SELECT sys_id, sys_parentId FROM MyDocType", NXQL.NXQL);
        assertEquals(3, res.size());
        for (Map<String, Object> map : res) {
            Object id = map.get(SYS_ID);
            assertTrue(id instanceof String, id.getClass().getName());
            Object parentId = map.get(SYS_PARENT_ID);
            assertTrue(parentId instanceof String, parentId.getClass().getName());
        }
        res.close();
    }

    protected DocumentModel makeComplexDoc() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "TestDoc");

        // tst_title = 'hello world'
        doc.setPropertyValue("tst_title", "hello world");

        // tst_subjects = ['foo', 'bar', 'moo']
        // tst_subjects/item[0] = 'foo'
        // tst_subjects/0 = 'foo'
        doc.setPropertyValue("tst_subjects", new String[] { "foo", "bar", "moo" });

        Map<String, Object> owner = new HashMap<>();
        // tst_owner/firstname = 'Bruce'
        owner.put("firstname", "Bruce");
        // tst_owner/lastname = 'Willis'
        owner.put("lastname", "Willis");
        doc.setPropertyValue("tst_owner", owner);

        Map<String, Object> first = new HashMap<>();
        // tst_couple/first/firstname = 'Steve'
        first.put("firstname", "Steve");
        // tst_couple/first/lastname = 'Jobs'
        first.put("lastname", "Jobs");
        Map<String, Object> second = new HashMap<>();
        // tst_couple/second/firstname = 'Steve'
        second.put("firstname", "Steve");
        // tst_couple/second/lastname = 'McQueen'
        second.put("lastname", "McQueen");
        Map<String, Object> couple = new HashMap<>();
        couple.put("first", first);
        couple.put("second", second);
        doc.setPropertyValue("tst_couple", couple);

        Map<String, Object> friend0 = new HashMap<>();
        // tst_friends/0/firstname = 'John'
        friend0.put("firstname", "John");
        // tst_friends/0/lastname = 'Lennon'
        friend0.put("lastname", "Lennon");
        Map<String, Object> friend1 = new HashMap<>();
        // tst_friends/1/firstname = 'John'
        friend1.put("firstname", "John");
        // tst_friends/1/lastname = 'Smith'
        friend1.put("lastname", "Smith");
        List<Map<String, Object>> friends = Arrays.asList(friend0, friend1);
        doc.setPropertyValue("tst_friends", friends);

        // this one doesn't have a schema prefix
        Map<String, Object> animal = new HashMap<>();
        // animal/race = 'dog'
        animal.put("race", "dog");
        // animal/name = 'Scooby'
        animal.put("name", "Scooby");
        doc.setPropertyValue("testschema3_animal", animal);

        Map<String, Object> whatever = new HashMap<>();
        whatever.put("key1", "val1");
        whatever.put("key2", Map.of("nested", 42));
        doc.setPropertyValue("testschema3_whatever", whatever);

        doc.create();
        session.save();
        return doc;
    }

    protected List<String> getIds(DocumentModelList list) {
        List<String> ids = new ArrayList<>(list.size());
        for (DocumentModel doc : list) {
            ids.add(doc.getId());
        }
        return ids;
    }

    private static final String FROM_WHERE = " FROM TestDoc WHERE ";

    private static final String SELECT_WHERE = "SELECT *" + FROM_WHERE;

    private static final String SELECT_TITLE_WHERE = "SELECT tst_title" + FROM_WHERE;

    @Test
    public void testQueryComplexWhere() {
        DocumentModel doc = makeComplexDoc();
        String docId = doc.getId();

        String clause;
        DocumentModelList res;
        IterableQueryResult it;

        // hierarchy h
        // JOIN hierarchy h2 ON h2.parentid = h.id
        // LEFT JOIN person p ON p.id = h2.id
        // WHERE h2.name = 'tst_owner'
        // AND p.firstname = 'Bruce'
        clause = "tst_owner/firstname = 'Bruce'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_title, tst_owner/lastname" + FROM_WHERE + clause, "NXQL");
        assertEquals(1, it.size());
        assertEquals("Willis", it.iterator().next().get("tst_owner/lastname"));
        it.close();

        // check other operators

        clause = "tst_owner/firstname LIKE 'B%'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        clause = "tst_owner/firstname IS NOT NULL";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        clause = "tst_owner/firstname IN ('Bruce', 'Bilbo')";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        // hierarchy h
        // JOIN hierarchy h2 ON h2.parentid = h.id
        // JOIN hierarchy h3 ON h3.parentid = h2.id
        // LEFT JOIN person p ON p.id = h3.id
        // WHERE h2.name = 'tst_couple'
        // AND h3.name = 'first'
        // AND p.firstname = 'Steve'
        clause = "tst_couple/first/firstname = 'Steve'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_title, tst_couple/first/lastname" + FROM_WHERE + clause, "NXQL");
        assertEquals(1, it.size());
        assertEquals("Jobs", it.iterator().next().get("tst_couple/first/lastname"));
        it.close();

        // hierarchy h
        // JOIN hierarchy h2 ON h2.parentid = h.id
        // LEFT JOIN person p ON p.id = h2.id
        // WHERE h2.name = 'tst_friends' AND h2.pos = 0
        // AND p.firstname = 'John'
        clause = "tst_friends/0/firstname = 'John'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_title, tst_friends/0/lastname" + FROM_WHERE + clause, "NXQL");
        assertEquals(1, it.size());
        assertEquals("Lennon", it.iterator().next().get("tst_friends/0/lastname"));
        it.close();

        // hierarchy h
        // JOIN hierarchy h2 ON h2.parentid = h.id
        // LEFT JOIN person p ON p.id = h2.id
        // WHERE h2.name = 'tst_friends'
        // AND p.firstname = 'John'
        clause = "tst_friends/*/firstname = 'John'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch(SELECT_TITLE_WHERE + clause, "NXQL");
        // MongoDB query projecting on a non-wildcard values doesn't repeat matches
        // as this would entail re-evaluating the projection from the full state
        // just to get duplicated identical rows
        assertEquals(isDBSMongoDB() ? 1 : 2, it.size()); // two uncorrelated stars
        it.close();

        // hierarchy h
        // JOIN hierarchy h2 ON h2.parentid = h.id
        // LEFT JOIN person p ON p.id = h2.id
        // WHERE h2.name = 'tst_friends'
        // AND p.firstname = 'John'
        // AND p.lastname = 'Smith'
        clause = "tst_friends/*1/firstname = 'John'" + " AND tst_friends/*1/lastname = 'Smith'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_title, tst_friends/*1/lastname" + FROM_WHERE + clause, "NXQL");
        assertEquals(1, it.size()); // correlated stars
        assertEquals("Smith", it.iterator().next().get("tst_friends/*1/lastname"));
        it.close();
    }

    @Test
    public void testQueryComplexCorrelation() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "TestDoc");
        doc.setPropertyValue("tst_title", "title");
        Map<String, Object> friend0 = new HashMap<>();
        friend0.put("firstname", "John");
        friend0.put("lastname", "Lennon");
        Map<String, Object> friend1 = new HashMap<>();
        friend1.put("firstname", "Adam");
        friend1.put("lastname", "Smith");
        doc.setPropertyValue("tst_friends", List.of(friend0, friend1));
        doc.create();
        session.save();

        String clause;
        DocumentModelList res;

        // query with correlation, and no single element of the map matches the query
        // the backend must interpret the correlation correctly

        clause = "tst_friends/*1/firstname = 'John' AND " //
                + "tst_friends/*1/lastname = 'Smith'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(0, res.size());

        // same one with interleaved unrelated clause

        clause = "tst_friends/*1/firstname = 'John' AND " //
                + "tst_title = 'foo' AND " //
                + "tst_friends/*1/lastname = 'Smith'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(0, res.size());

        // more complex correlation with sub-clauses

        clause = "(tst_friends/*1/firstname = 'John' OR tst_friends/*1/firstname = 'XYZ') AND " //
                + "(tst_friends/*1/lastname = 'Smith' OR tst_friends/*1/lastname = 'XYZ')";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(0, res.size());

        // same one with interleaved unrelated clause

        clause = "(tst_friends/*1/firstname = 'John' OR tst_friends/*1/firstname = 'XYZ') AND " //
                + "tst_title = 'foo' AND" //
                + "(tst_friends/*1/lastname = 'Smith' OR tst_friends/*1/lastname = 'XYZ')";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(0, res.size());

        // test that such complex queries can match documents too

        clause = "(tst_friends/*1/firstname = 'John' OR tst_friends/*1/firstname = 'XYZ') AND " //
                + "tst_title = 'title' AND" //
                + "(tst_friends/*1/lastname = 'Lennon' OR tst_friends/*1/lastname = 'XYZ')";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(1, res.size());
    }

    @Test
    public void testQueryComplexPrefix() {
        DocumentModel doc = makeComplexDoc();
        String docId = doc.getId();

        String clause;
        DocumentModelList res;

        // schema with a prefix
        clause = "tst_owner/firstname = 'Bruce'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        // use of prefix is mandatory
        try {
            clause = "owner/firstname = 'Bruce'";
            session.query(SELECT_WHERE + clause);
            fail("Should fail on missing prefix");
        } catch (QueryParseException e) {
            assertEquals("Failed to execute query: "
                    + "SELECT * FROM TestDoc WHERE owner/firstname = 'Bruce'" + ", "
                    + "No such property: owner/firstname", e.getMessage());
        }

        // allow use with schema-name-as-prefix
        clause = "testschema3_animal/race = 'dog'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
    }

    @Test
    public void testQueryComplexReturned() {
        DocumentModel doc = makeComplexDoc();
        String docId = doc.getId();

        String clause;
        DocumentModelList res;
        IterableQueryResult it;
        Set<String> set;

        // SELECT p.lastname
        // FROM hierarchy h
        // JOIN hierarchy h2 ON h2.parentid = h.id
        // LEFT JOIN person p ON p.id = h2.id
        // WHERE h2.name = 'tst_friends'
        clause = "tst_title = 'hello world'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_friends/*/lastname" + FROM_WHERE + clause, "NXQL");
        assertEquals(2, it.size());
        set = new HashSet<>();
        for (Map<String, Object> map : it) {
            set.add((String) map.get("tst_friends/*/lastname"));
        }
        assertEquals(new HashSet<>(Arrays.asList("Lennon", "Smith")), set);
        it.close();

        // SELECT p.firstname, p.lastname
        // FROM hierarchy h
        // JOIN hierarchy h2 ON h2.parentid = h.id
        // LEFT JOIN person p ON p.id = h2.id
        // WHERE h2.name = 'tst_friends'
        clause = "tst_title = 'hello world'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_friends/*1/firstname, tst_friends/*1/lastname" + FROM_WHERE + clause,
                "NXQL");
        assertEquals(2, it.size());
        Set<String> fn = new HashSet<>();
        Set<String> ln = new HashSet<>();
        for (Map<String, Object> map : it) {
            fn.add((String) map.get("tst_friends/*1/firstname"));
            ln.add((String) map.get("tst_friends/*1/lastname"));
        }
        assertEquals(Collections.singleton("John"), fn);
        assertEquals(new HashSet<>(Arrays.asList("Lennon", "Smith")), ln);
        it.close();

    }

    @Test
    public void testQueryComplexListElement() {
        DocumentModel doc = makeComplexDoc();
        String docId = doc.getId();

        String clause;
        DocumentModelList res;
        IterableQueryResult it;
        Set<String> set;

        // hierarchy h
        // JOIN tst_subjects s ON h.id = s.id // not LEFT JOIN
        // WHERE s.pos = 0
        // AND s.item = 'foo'
        clause = "tst_subjects/0 = 'foo'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        clause = "tst_subjects/0 = 'bar'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(0, res.size());

        // SELECT s.item
        // FROM hierarchy h
        // JOIN tst_subjects s ON h.id = s.id // not LEFT JOIN
        // WHERE s.pos = 0
        // AND s.item = 'bar'
        clause = "tst_subjects/0 = 'foo'";
        it = session.queryAndFetch("SELECT tst_subjects/0" + FROM_WHERE + clause, "NXQL");
        assertEquals(1, it.size());
        assertEquals("foo", it.iterator().next().get("tst_subjects/0"));
        it.close();

        // SELECT s1.item
        // FROM hierarchy h
        // JOIN tst_subjects s0 ON h.id = s0.id // not LEFT JOIN
        // JOIN tst_subjects s1 ON h.id = s1.id // not LEFT JOIN
        // WHERE s0.pos = 0 AND s1.pos = 1
        // AND s0.item LIKE 'foo%'
        clause = "tst_subjects/0 LIKE 'foo%'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_subjects/1" + FROM_WHERE + clause, "NXQL");
        assertEquals(1, it.size());
        assertEquals("bar", it.iterator().next().get("tst_subjects/1"));
        it.close();

        // SELECT s.item
        // FROM hierarchy h
        // LEFT JOIN tst_subjects s ON h.id = s.id
        // WHERE s.item LIKE '%oo'
        clause = "tst_subjects/*1 LIKE '%oo'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_subjects/*1" + FROM_WHERE + clause, "NXQL");
        assertEquals(2, it.size());
        set = new HashSet<>();
        for (Map<String, Object> map : it) {
            set.add((String) map.get("tst_subjects/*1"));
        }
        assertEquals(new HashSet<>(Arrays.asList("foo", "moo")), set);
        it.close();

        // projection only
        clause = "tst_title = 'hello world'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_subjects/*1" + FROM_WHERE + clause, "NXQL");
        assertEquals(3, it.size());
        set = new HashSet<>();
        for (Map<String, Object> map : it) {
            set.add((String) map.get("tst_subjects/*1"));
        }
        assertEquals(new HashSet<>(Arrays.asList("foo", "moo", "bar")), set);
        it.close();

        // projection only on non-matching doc
        clause = "tst_title = 'nosuchtitle'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.emptyList(), getIds(res));
        it = session.queryAndFetch("SELECT tst_subjects/*1" + FROM_WHERE + clause, "NXQL");
        assertEquals(0, it.size());
        it.close();

        // projection on uncorrelated wildcard
        clause = "tst_subjects/* LIKE '%oo'";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        it = session.queryAndFetch("SELECT tst_subjects/*" + FROM_WHERE + clause, "NXQL");
        // two uncorrelated stars, resulting in a cross join
        assertEquals(6, it.size());
        set = new HashSet<>();
        for (Map<String, Object> map : it) {
            set.add((String) map.get("tst_subjects/*"));
        }
        assertEquals(new HashSet<>(Arrays.asList("foo", "moo", "bar")), set);
        it.close();

        // WHAT
        clause = "tst_title = 'hello world'";
        it = session.queryAndFetch("SELECT tst_subjects/*" + FROM_WHERE + clause, "NXQL");
        assertEquals(3, it.size());
        set = new HashSet<>();
        for (Map<String, Object> map : it) {
            set.add((String) map.get("tst_subjects/*"));
        }
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar", "moo")), set);
        it.close();
    }

    @Test
    public void testQueryComplexOrderBy() {
        DocumentModel doc = makeComplexDoc();
        String docId = doc.getId();

        String clause;
        DocumentModelList res;
        IterableQueryResult it;

        clause = "tst_title LIKE '%' ORDER BY tst_owner/firstname";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        clause = "tst_owner/firstname = 'Bruce' ORDER BY tst_title";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        clause = "tst_owner/firstname = 'Bruce' ORDER BY tst_owner/firstname";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        // this produces a DISTINCT and adds tst_title to the select list
        clause = "tst_subjects/* = 'foo' ORDER BY tst_title";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        clause = "tst_friends/*/firstname = 'John' ORDER BY tst_title";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        // no wildcard index so no DISTINCT needed
        clause = "tst_title LIKE '%' ORDER BY tst_friends/0/lastname";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
        clause = "tst_title LIKE '%' ORDER BY tst_subjects/0";
        res = session.query(SELECT_WHERE + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));

        // SELECT * statement cannot ORDER BY array or complex list element
        clause = "tst_subjects/*1 = 'foo' ORDER BY tst_subjects/*1";
        try {
            session.query(SELECT_WHERE + clause);
            // ORDER BY tst_subjects works on MongoDB
        } catch (QueryParseException e) {
            String expected = "Failed to execute query: "
                    + "SELECT * FROM TestDoc WHERE tst_subjects/*1 = 'foo' ORDER BY tst_subjects/*1"
                    + ", " + "For SELECT * the ORDER BY columns cannot use wildcard indexes";
            assertEquals(expected, e.getMessage());
        }

        clause = "tst_title = 'hello world' ORDER BY tst_subjects/*1";
        it = session.queryAndFetch("SELECT tst_title" + FROM_WHERE + clause, "NXQL");
        // MongoDB query projecting on a non-wildcard values doesn't repeat matches
        // as this would entail re-evaluating the projection from the full state
        // just to get duplicated identical rows
        assertEquals(isDBSMongoDB() ? 1 : 3, it.size());
        it.close();
    }

    @Test
    public void testQueryComplexTwoWildcards() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File2");
        Map<String, Object> map1 = new HashMap<>();
        map1.put("name", "bob");
        map1.put("subscribers", new String[] { "sub1", "sub2" });
        Map<String, Object> map2 = new HashMap<>();
        map2.put("name", "pete");
        map2.put("subscribers", new String[] { "sub1" });
        doc.setPropertyValue("tst2_notifs", List.of(map1, map2));
        doc.create();
        session.save();

        String docId = doc.getId();

        String query = "SELECT * FROM File2 WHERE tst2_notifs/*/subscribers/* = 'sub1'";
        DocumentModelList res = session.query(query);
        assertEquals(Collections.singletonList(docId), getIds(res));
    }

    @Test
    public void testQueryComplexBoolean() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File2");
        Map<String, Object> map1 = new HashMap<>();
        map1.put("name", "bob");
        map1.put("enabled", Long.valueOf(1));
        map1.put("subscribers", new String[] { "sub1", "sub2" });
        doc.setPropertyValue("tst2_notifs", List.of(map1));
        doc.create();
        session.save();

        String docId = doc.getId();

        String query = "SELECT * FROM File2 WHERE tst2_notifs/*/enabled = 1";
        DocumentModelList res = session.query(query);
        assertEquals(Collections.singletonList(docId), getIds(res));
    }

    @Test
    public void testQueryComplexOrderBy2() {
        DocumentModel doc = makeComplexDoc();
        String docId = doc.getId();

        String clause;
        DocumentModelList res;

        clause = "tst_friends/*/firstname = 'John' ORDER BY tst_title";
        res = session.query("SELECT * FROM TestDoc WHERE " + clause);
        assertEquals(Collections.singletonList(docId), getIds(res));
    }

    @Test
    public void testQueryComplexOr() {
        // doc1 tst_title = 'hello world'
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", "TestDoc");
        doc1.setPropertyValue("tst_title", "hello world");
        doc1.create();

        // doc2 tst_owner/firstname = 'Bruce'
        DocumentModel doc2 = session.createDocumentModel("/", "doc2", "TestDoc");
        doc2.setPropertyValue("tst_owner", Map.of("firstname", "Bruce"));
        doc2.create();

        // doc3 tst_friends/0/firstname = 'John'
        DocumentModel doc3 = session.createDocumentModel("/", "doc3", "TestDoc");
        doc3.setPropertyValue("tst_friends", List.of(Map.of("firstname", "John")));
        doc3.create();

        // doc4 tst_subjects/0 = 'foo'
        DocumentModel doc4 = session.createDocumentModel("/", "doc4", "TestDoc");
        doc4.setPropertyValue("tst_subjects", new String[] { "foo" });
        doc4.create();

        session.save();

        String s1 = "SELECT * FROM TestDoc WHERE (";
        String s2 = ")";
        String o = " OR ";
        String c1 = "tst_title = 'hello world'";
        String c2 = "tst_owner/firstname = 'Bruce'";
        String c3 = "tst_friends/0/firstname = 'John'";
        String c4 = "tst_subjects/0 = 'foo'";
        DocumentModelList res;

        res = session.query(s1 + c1 + s2);
        assertEquals(Collections.singletonList(doc1.getId()), getIds(res));

        res = session.query(s1 + c2 + s2);
        assertEquals(Collections.singletonList(doc2.getId()), getIds(res));

        res = session.query(s1 + c3 + s2);
        assertEquals(Collections.singletonList(doc3.getId()), getIds(res));

        res = session.query(s1 + c4 + s2);
        assertEquals(Collections.singletonList(doc4.getId()), getIds(res));

        res = session.query(s1 + c1 + o + c2 + s2);
        assertEquals(2, res.size());

        res = session.query(s1 + c1 + o + c3 + s2);
        assertEquals(2, res.size());

        res = session.query(s1 + c1 + o + c4 + s2);
        assertEquals(2, res.size());

        res = session.query(s1 + c2 + o + c3 + s2);
        assertEquals(2, res.size());

        res = session.query(s1 + c2 + o + c4 + s2);
        assertEquals(2, res.size());

        res = session.query(s1 + c3 + o + c4 + s2);
        assertEquals(2, res.size());

        res = session.query(s1 + c1 + o + c2 + o + c3 + s2);
        assertEquals(3, res.size());

        res = session.query(s1 + c1 + o + c2 + o + c4 + s2);
        assertEquals(3, res.size());

        res = session.query(s1 + c1 + o + c3 + o + c4 + s2);
        assertEquals(3, res.size());

        res = session.query(s1 + c2 + o + c3 + o + c4 + s2);
        assertEquals(3, res.size());

        res = session.query(s1 + c1 + o + c2 + o + c3 + o + c4 + s2);
        assertEquals(4, res.size());
    }

    @Test
    public void testQueryLikeWildcard() {
        DocumentModelList dml;
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foo");
        doc.setPropertyValue(SYS_DESCRIPTION, "fo%");
        doc.setPropertyValue("mydc_rights", "fo_");
        doc.setPropertyValue("mydc_source", "fo\\");
        doc.create();
        session.save();

        // regular % wildcard
        dml = session.query("SELECT * FROM SysFile WHERE sys_title LIKE 'f%'");
        assertEquals(1, dml.size());
        // regular _ wildcard
        dml = session.query("SELECT * FROM SysFile WHERE sys_title LIKE 'fo_'");
        assertEquals(1, dml.size());

        // escaped % wildcard
        dml = session.query("SELECT * FROM SysFile WHERE sys_title LIKE 'fo\\\\%'");
        assertEquals(0, dml.size());
        dml = session.query("SELECT * FROM SysFile WHERE sys_description LIKE 'fo\\\\%'");
        assertEquals(1, dml.size());

        // escaped _ wildcard
        dml = session.query("SELECT * FROM SysFile WHERE sys_title LIKE 'fo\\\\_'");
        assertEquals(0, dml.size());
        dml = session.query("SELECT * FROM SysFile WHERE mydc_rights LIKE 'fo\\\\_'");
        assertEquals(1, dml.size());

        // explicit \
        // because \ is already an escape char in NXQL strings, we have to double-double it
        // doubled for NXQL string escaping, and doubled for LIKE escaping
        dml = session.query("SELECT * FROM SysFile WHERE sys_title LIKE 'fo\\\\\\\\'");
        assertEquals(0, dml.size());
        dml = session.query("SELECT * FROM SysFile WHERE mydc_source LIKE 'fo\\\\\\\\'");
        assertEquals(1, dml.size());
    }

    @Test
    public void testQueryLikeAnchoredDefault() {
        dotTestQueryLikeAnchored(true);
    }

    protected void dotTestQueryLikeAnchored(boolean anchored) {
        assumeTrue(anchored || isDBSMongoDB(), "LIKE anchoring can only be disabled on MongoDB");

        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foobarbaz");
        doc.create();
        session.save();

        // should not match
        checkLikeMatch(false, "gee");
        checkLikeMatch(false, "foogee");
        checkLikeMatch(false, "geebar");
        // should match if not anchored
        checkLikeMatch(!anchored, "");
        checkLikeMatch(!anchored, "bar");
        checkLikeMatch(!anchored, "foobar");
        checkLikeMatch(!anchored, "barbaz");
        // should match
        checkLikeMatch(true, "foobarbaz");
        checkLikeMatch(true, "%foobarbaz");
        checkLikeMatch(true, "foobarbaz%");
        checkLikeMatch(true, "%foobarbaz%");
        checkLikeMatch(true, "%barbaz");
        checkLikeMatch(true, "foobar%");
        checkLikeMatch(true, "foo%baz");
        checkLikeMatch(true, "%");
        checkLikeMatch(true, "%bar%");
        checkLikeMatch(true, "%foo%baz%");
    }

    protected void checkLikeMatch(boolean shouldMatch, String string) {
        DocumentModelList dml = session.query("SELECT * FROM SysFile WHERE sys_title LIKE '" + string + "'");
        assertEquals(shouldMatch ? 1 : 0, dml.size(), "Should " + (shouldMatch ? "match" : "not match") + ": '" + string + "'");
    }

    @Test
    public void testQueryIdNotFromUuid() {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();

        DocumentModel doc2 = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc2.setPropertyValue("mydc_source", doc1.getId());
        doc2.create();
        session.save();

        DocumentModelList dml = session.query("SELECT mydc_source FROM SysFile WHERE sys_name = 'doc2'");
        assertEquals(1, dml.size());
        DocumentModel doc = dml.get(0);
        assertEquals(doc1.getId(), doc.getId());
    }

    @Test
    public void testQueryIdNotFromUuidReturnedTwice() {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();
        String doc1Id = doc1.getId();

        DocumentModel doc2 = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc2.setPropertyValue("mydc_source", doc1Id);
        doc2.create();

        DocumentModel doc3 = session.createDocumentModel("/", "doc3", SYS_FILE);
        doc3.setPropertyValue("mydc_source", doc1Id);
        doc3.create();

        session.save();
        nextTransaction();

        DocumentModelList dml = session.query("SELECT mydc_source FROM SysFile WHERE sys_name IN ('doc2', 'doc3')");
        assertEquals(2, dml.size());
        assertEquals(doc1Id, dml.get(0).getId());
        assertEquals(doc1Id, dml.get(1).getId());
    }

    @Test
    public void testQueryIdNotFromUuidWithMissingDoc() {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();

        DocumentModel doc2 = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc2.setPropertyValue("mydc_source", "not-a-doc-id");
        doc2.create();
        session.save();

        String nxql = "SELECT mydc_source FROM SysFile WHERE sys_name = 'doc2'";
        DocumentModelList dml = session.query(nxql);
        assertEquals(0, dml.size());
        assertEquals(0, dml.totalSize());

        IterableQueryResult res = session.queryAndFetch(nxql, NXQL.NXQL);
        assertEquals(1, res.size());
        Map<String, Object> map = res.iterator().next();
        assertEquals("not-a-doc-id", map.get("mydc_source"));
        res.close();

        PartialList<Map<String, Object>> projection = session.queryProjection(nxql, false, 0, 0, -1);
        assertEquals(1, projection.size());
        assertEquals(1, projection.totalSize());
        map = projection.get(0);
        assertEquals("not-a-doc-id", map.get("mydc_source"));
    }

    // SELECT mydc_subjects/* ... must not return NULLs (due to LEFT JOIN) if there is no match
    @Test
    public void testQueryIdListNotFromUuidButFromArray() {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();
        // don't set any mydc_subjects at all
        session.save();

        String nxql = "SELECT mydc_subjects/* FROM SysFile WHERE sys_name = 'doc1'";
        DocumentModelList dml = session.query(nxql, 10, 0, -1);
        assertEquals(0, dml.size());
        assertEquals(0, dml.totalSize());

        IterableQueryResult res = session.queryAndFetch(nxql, NXQL.NXQL);
        assertEquals(0, res.size());
        res.close();

        PartialList<Map<String, Object>> projection = session.queryProjection(nxql, false, 10, 0, -1);
        assertEquals(0, projection.size());
        if (!isDBSMem()) {
            assertEquals(-1, projection.totalSize());
        } else {
            assertEquals(0, projection.totalSize());
        }
    }

    // SELECT mydc_subjects/* ... cannot compute total size on DBS due to manual projection
    @Test
    public void testQueryIdListFromArrayTotalSize() {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.setPropertyValue("mydc_subjects", List.of("a", "b", "c"));
        doc1.create();
        session.save();

        String nxql = "SELECT mydc_subjects/* FROM SysFile WHERE sys_name = 'doc1'";
        PartialList<Map<String, Object>> projection;

        int expectedTotal = 3;
        int expectedUnknown = -1;
        int expectedUnknownForDBS = !isDBSMem() ? expectedUnknown : expectedTotal;

        // no limit, countUpTo -1 (countTotal true)
        projection = session.queryProjection(nxql, false, 0, 0, -1);
        assertEquals(expectedTotal, projection.size());
        assertEquals(expectedTotal, projection.totalSize());

        // no limit, countUpTo 0 (countTotal false)
        projection = session.queryProjection(nxql, false, 0, 0, 0);
        assertEquals(expectedTotal, projection.size());
        assertEquals(expectedUnknown, projection.totalSize());

        // no limit, countUpTo 5
        projection = session.queryProjection(nxql, false, 0, 0, 5);
        assertEquals(expectedTotal, projection.size());
        assertEquals(expectedTotal, projection.totalSize());

        // no limit, countUpTo 15
        projection = session.queryProjection(nxql, false, 0, 0, 15);
        assertEquals(expectedTotal, projection.size());
        assertEquals(expectedTotal, projection.totalSize());

        // limit, countUpTo -1 (countTotal true)
        projection = session.queryProjection(nxql, false, 10, 0, -1);
        assertEquals(expectedTotal, projection.size());
        assertEquals(expectedUnknownForDBS, projection.totalSize());

        // limit, countUpTo 0 (countTotal false)
        projection = session.queryProjection(nxql, false, 10, 0, 0);
        assertEquals(expectedTotal, projection.size());
        assertEquals(expectedUnknown, projection.totalSize());

        // limit, countUpTo 5
        projection = session.queryProjection(nxql, false, 10, 0, 5);
        assertEquals(expectedTotal, projection.size());
        assertEquals(expectedUnknownForDBS, projection.totalSize());
    }

    // SELECT mydc_subjects/*1 ... WHERE mydc_subjects/*1 ... may return NULLs
    @Test
    public void testQueryIdListNotFromUuidButFromArrayMentionedInWhereClause() {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();
        // don't set any mydc_subjects at all
        session.save();

        String nxql = "SELECT mydc_subjects/*1 FROM SysFile WHERE sys_name = 'doc1' AND mydc_subjects/*1 IS NULL";
        DocumentModelList dml = session.query(nxql, 10, 0, -1);
        assertEquals(0, dml.size()); // null docs filtered out
        assertEquals(0, dml.totalSize());

        IterableQueryResult res = session.queryAndFetch(nxql, NXQL.NXQL);
        assertEquals(1, res.size());
        Map<String, Object> map = res.iterator().next();
        assertNull(map.get("mydc_subjects/*1"));
        res.close();

        PartialList<Map<String, Object>> projection = session.queryProjection(nxql, false, 10, 0, -1);
        assertEquals(1, projection.size());
        if (!isDBSMem()) {
            assertEquals(-1, projection.totalSize());
        } else {
            assertEquals(1, projection.totalSize());
        }
        map = projection.get(0);
        assertNull(map.get("mydc_subjects/*1"));
    }

    @Test
    public void testQueryIdListNotFromUuid() {
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();
        DocumentModel doc2 = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc2.create();
        DocumentModel doc3 = session.createDocumentModel("/", "doc3", SYS_FILE);
        doc3.create();

        // test both orders
        for (Pair<DocumentModel, DocumentModel> pair : Arrays.asList(Pair.of(doc1, doc2), Pair.of(doc2, doc1))) {
            DocumentModel doca = pair.getLeft();
            DocumentModel docb = pair.getRight();
            String[] prop = new String[] { "not-a-valid-id", doca.getId(), doca.getId(), docb.getId() };
            List<String> expected = Arrays.asList(prop[1], prop[2], prop[3]);
            doc3.setPropertyValue("mydc_subjects", prop);
            session.save();

            DocumentModelList dml;
            List<String> actual;
            String query = "SELECT mydc_subjects/* FROM SysFile WHERE sys_name = 'doc3'";

            // expect a specific order (NXP-19484)
            dml = session.query(query);
            assertEquals(3, dml.size());
            actual = Arrays.asList(dml.get(0).getId(), dml.get(1).getId(), dml.get(2).getId());
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testScrollApi() {
        final int nbDocs = 127;
        final int batchSize = 13;
        DocumentModel doc;
        for (int i = 0; i < nbDocs; i++) {
            doc = session.createDocumentModel("/", "doc" + i, SYS_FILE);
            doc.create();
        }
        session.save();
        DocumentModelList dml;
        dml = session.query("SELECT * FROM SysContent");
        assertEquals(nbDocs, dml.size());

        ScrollResult<String> ret = session.scroll("SELECT * FROM SysContent", batchSize, 10);
        int total = 0;
        while (ret.hasResults()) {
            List<String> ids = ret.getResults();
            ids.forEach(id -> assertFalse(id.isEmpty()));
            total += ids.size();
            ret = session.scroll(ret.getScrollId());
        }
        assertEquals(nbDocs, total);

        // the scroll id is now closed
        ScrollResult<String> res = ret; // effectively final
        NuxeoException e = assertThrows(NuxeoException.class, () -> session.scroll(res.getScrollId()));
        assertEquals("Unknown or timed out scrollId", e.getMessage());
    }

    @Disabled("The test has been disabled because it's failing randomly and often. It's failing because of another " +
            "thread logging a WARN log due to AWS errors (none related to the test itself)")
    @Test
    @LogCaptureFeature.FilterOn(logLevel = "WARN")
    public void testScrollApiEmtpy() throws Exception {
        // do a scroll that return nothing
        ScrollResult<String> ret = session.scroll("SELECT * FROM SysFile", 10, 1);
        assertFalse(ret.hasResults());
        // wait for the scroll timeout
        Thread.sleep(1100);
        // A new scroll call will warn about timed out scroll
        ret = session.scroll("SELECT * FROM SysFile", 10, 1);
        assertFalse(ret.hasResults());
        // we expect to have no warn because empty scroll should not maintain any cursor
        List<String> events = logCaptureResult.getCaughtEventMessages();
        assertTrue(events.isEmpty());
    }

    @Test
    public void testScrollApiDoesNotRequiresAdminRights() {
        DocumentModel root = session.getRootDocument();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("bob").permission(READ).build());
        root.setACL(acl, true);

        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();
        session.save();

        CoreSession bobSession = openSessionAs("bob");
        ScrollResult<String> ret = bobSession.scroll("SELECT * FROM SysContent", 1, 10);
        assertTrue(ret.hasResults());
        assertEquals(1, ret.getResults().size());
    }

    @Test
    public void testScrollTimeout() throws Exception {
        assumeTrue(supportsScroll(), "Backend must support true scrolling");

        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();
        DocumentModel doc2 = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc2.create();
        session.save();

        ScrollResult<String> ret = session.scroll("SELECT * FROM SysContent", 1, 1);
        assertTrue(ret.hasResults());
        assertEquals(1, ret.getResults().size());
        // wait for scroll timeout
        Thread.sleep(1100);

        NuxeoException e = assertThrows(NuxeoException.class, () -> session.scroll(ret.getScrollId()));
        assertEquals("Timed out scrollId", e.getMessage());
    }

    @Test
    public void testScrollBadUsageInvalidScrollId() {
        NuxeoException e = assertThrows(NuxeoException.class, () -> session.scroll("foo"));
        assertEquals("Unknown or timed out scrollId", e.getMessage());
    }

    @Test
    public void testScrollBadUsage() throws Exception {
        assumeTrue(supportsScroll(), "Backend must support true scrolling");

        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.create();
        DocumentModel doc2 = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc2.create();
        session.save();

        ScrollResult<String> ret1 = session.scroll("SELECT * FROM SysContent", 1, 1);
        ScrollResult<String> ret2 = session.scroll("SELECT * FROM SysContent", 1, 1);
        session.scroll("SELECT * FROM SysContent", 1, 1);
        assertTrue(ret1.hasResults());
        assertEquals(1, ret1.getResults().size());

        Thread.sleep(1100);
        // normal timeout on ret1
        try {
            session.scroll(ret1.getScrollId());
        } catch (NuxeoException e) {
            assertEquals("Timed out scrollId", e.getMessage());
        }
        // This new call will clean leaked scroll
        ScrollResult<String> ret4 = session.scroll("SELECT * FROM SysContent", 1, 1);
        assertTrue(ret4.hasResults());

        // ret2 is now unknown because it has been cleaned
        NuxeoException e = assertThrows(NuxeoException.class, () -> session.scroll(ret2.getScrollId()));
        assertEquals("Unknown or timed out scrollId", e.getMessage());
    }

    @Test
    public void testScrollApiConcurrency() throws Exception {
        final int nbDocs = 127;
        final int batchSize = 13;
        final int nbThread = nbDocs / batchSize + 1;
        // System.out.println("nbDocs: " + nbDocs + ", batch: " + batchSize + ", thread: " + nbThread);
        DocumentModel doc;
        for (int i = 0; i < nbDocs; i++) {
            doc = session.createDocumentModel("/", "doc" + i, SYS_FILE);
            doc.create();
        }
        session.save();

        DocumentModelList dml;
        dml = session.query("SELECT * FROM SysContent");
        assertEquals(nbDocs, dml.size());

        ScrollResult<String> ret = session.scroll("SELECT * FROM SysContent", batchSize, 10);
        List<String> ids = ret.getResults();
        int total = ids.size();
        String scrollId = ret.getScrollId();
        // System.out.println("first call: " + total);
        List<CompletableFuture<Integer>> futures = new ArrayList<>(nbThread);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(nbThread);
        final CountDownLatch latch = new CountDownLatch(nbThread);
        for (int n = 0; n < nbThread; n++) {
            CompletableFuture<Integer> completableFuture = CompletableFuture.supplyAsync(() -> {
                TransactionHelper.startTransaction();
                try {
                    // make sure all threads ask to scroll at the same time
                    latch.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new NuxeoException(e);
                    }
                    int nb = session.scroll(scrollId).getResults().size();
                    // System.out.println(Thread.currentThread().getName() + ": return: " + nb);
                    return nb;
                } finally {
                    TransactionHelper.commitOrRollbackTransaction();
                }
            }, executor);
            futures.add(completableFuture);
        }
        for (int n = 0; n < nbThread; n++) {
            int count = futures.get(n).get();
            total += count;
        }
        assertEquals(nbDocs, total);
    }

    @Test
    public void testScrollCleaningConcurrency() throws Exception {
        final int NB_TRHEADS = 15;
        final int NB_SCROLLS = 100;

        assumeTrue(supportsScroll(), "Backend must support true scrolling");

        DocumentModel doc = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc.create();
        doc = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc.create();
        session.save();
        for (int i = 0; i < NB_SCROLLS; i++) {
            session.scroll("SELECT * FROM SysContent", 1, 1).getScrollId();
        }
        // wait for timeout
        Thread.sleep(1100);

        List<CompletableFuture<Integer>> futures = new ArrayList<>(NB_TRHEADS);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(NB_TRHEADS);
        final CountDownLatch latch = new CountDownLatch(NB_TRHEADS);
        for (int n = 0; n < NB_TRHEADS; n++) {
            CompletableFuture<Integer> completableFuture = CompletableFuture.supplyAsync(() -> {
                TransactionHelper.startTransaction();
                try {
                    // make sure all threads ask to scroll at the same time
                    latch.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new NuxeoException(e);
                    }
                    session.scroll("SELECT * FROM SysContent", 1, 1).getResults().size();
                    return 1;
                } finally {
                    TransactionHelper.commitOrRollbackTransaction();
                }
            }, executor);
            futures.add(completableFuture);
        }
        int total = 0;
        for (int n = 0; n < NB_TRHEADS; n++) {
            int count = futures.get(n).get();
            total += count;
        }
        assertEquals(NB_TRHEADS, total);
    }

    @Test
    public void testScrollCheckUserPermission() {
        assumeTrue(supportsScroll(), "Backend must support true scrolling");

        createDocs();
        DocumentModel root = session.getRootDocument();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.builder().user("bob").permission(READ).build());
        root.setACL(acl, true);
        DocumentModel folder1 = session.getDocument(new PathRef("/testfolder1"));
        acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.BLOCK);
        folder1.setACL(acl, true);
        session.save();

        CoreSession bobSession = openSessionAs("bob");
        ScrollResult<String> ret = bobSession.scroll("SELECT * FROM SysContent", 1, 10);
        int total = 0;
        while (ret.hasResults()) {
            List<String> ids = ret.getResults();
            total += ids.size();
            ret = bobSession.scroll(ret.getScrollId());
        }
        assertEquals(3, total);
    }

    @Disabled("TODO DISABLED reimplement default values later")
    @Test
    public void testQueryDefaultValue() {
        // create a document with no metadata
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_DOC_TYPE);
        doc.create();
        session.save();
        // try to query default values
        DocumentModelList dml = session.query("SELECT * FROM MyDocType WHERE my_testDefault = 'the default value'");
        assertEquals(1, dml.size());
    }

    @Disabled("TODO DISABLED reimplement default values later")
    @Test
    public void testQueryDefaultValueInComplex() {
        // create a document with no metadata
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_DOC_TYPE);
        doc.create();
        session.save();
        // try to query default values
        DocumentModelList dml = session.query(
                "SELECT * FROM MyDocType WHERE my_complex/testDefault = 'the default value'");
        assertEquals(1, dml.size());
    }

    @Test
    public void testIsRecord() {
        String queryRecords = "SELECT * FROM GovernableFile WHERE sysgov_isRecord = TRUE";
        String queryNotRecord = "SELECT * FROM GovernableFile WHERE sysgov_isRecord = FALSE";
        DocumentModelList dml;

        // no record present initially
        dml = session.query(queryRecords);
        assertEquals(0, dml.size());
        dml = session.query(queryNotRecord);
        assertEquals(0, dml.size());

        // create a document
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_GOVERNABLE_FILE);
        doc.create();
        session.save();

        // still no record
        dml = session.query(queryRecords);
        assertEquals(0, dml.size());
        dml = session.query(queryNotRecord);
        assertEquals(1, dml.size()); // 1 doc that isn't a record

        // make a record
        session.setRecord(doc.getRef(), true);
        session.save();

        // check that we can find the record
        dml = session.query(queryRecords);
        assertEquals(1, dml.size());
        assertEquals(doc.getId(), dml.get(0).getId());
        dml = session.query(queryNotRecord);
        assertEquals(0, dml.size());
    }

    @Test
    public void testRetainUntil() {
        Instant fiveSeconds = Instant.now().plus(5, SECONDS);
        String query = String.format("SELECT * FROM GovernableFile WHERE sysgov_retainUntil > TIMESTAMP '%s'", fiveSeconds);
        DocumentModelList dml = session.query(query);
        assertEquals(0, dml.size());

        // create a record
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_GOVERNABLE_FILE);
        doc.create();
        session.setRecord(doc.getRef(), true);
        // retain one hour
        Instant oneHour = Instant.now().plus(1, HOURS);
        session.setRetainUntil(doc.getRef(), oneHour, null);
        session.save();

        // check that we can find the record by its retention date
        dml = session.query(query);
        assertEquals(1, dml.size());
        assertEquals(doc.getId(), dml.get(0).getId());
    }

    @Test
    public void testHasLegalHold() {
        String queryHolds = "SELECT * FROM GovernableFile WHERE sysgov_hasLegalHold = TRUE";
        String queryNoHold = "SELECT * FROM GovernableFile WHERE sysgov_hasLegalHold = FALSE";
        DocumentModelList dml;

        // no hold present initially
        dml = session.query(queryHolds);
        assertEquals(0, dml.size());
        dml = session.query(queryNoHold);
        assertEquals(0, dml.size());

        // create a document
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_GOVERNABLE_FILE);
        doc.create();
        session.setRecord(doc.getRef(), true);
        session.save();

        // still no hold
        dml = session.query(queryHolds);
        assertEquals(0, dml.size());
        dml = session.query(queryNoHold);
        assertEquals(1, dml.size()); // 1 doc that has no hold

        // add hold
        session.setLegalHold(doc.getRef(), true, null);
        session.save();

        // check that we can find the hold
        dml = session.query(queryHolds);
        assertEquals(1, dml.size());
        assertEquals(doc.getId(), dml.get(0).getId());
        dml = session.query(queryNoHold);
        assertEquals(0, dml.size());
    }

    @Test
    public void testBlobKeys() {
        DocumentModel doc = session.createDocumentModel("/", "testfile", SYS_FILE);
        Blob blob1 = Blobs.createBlob("foo");
        doc.setPropertyValue(SYSFILE_BLOB, blob1);
        Blob blob2 = Blobs.createBlob("bar");
        doc.setPropertyValue("sysfiles_blobs", List.of(Map.of("blob", blob2)));
        doc.create();
        session.save();

        blob1 = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        blob2 = (Blob) doc.getPropertyValue("sysfiles_blobs/0/blob");
        String format = "SELECT * FROM SysFile WHERE sys_blobKeys = '%s'";

        String q = String.format(format, ((ManagedBlob) blob1).getKey());
        DocumentModelList dml = session.query(q);
        assertEquals(1, dml.size());
        assertEquals(doc.getId(), dml.get(0).getId());

        q = String.format(format, ((ManagedBlob) blob2).getKey());
        dml = session.query(q);
        assertEquals(1, dml.size());
        assertEquals(doc.getId(), dml.get(0).getId());
    }

    // NXP-30434
    @Test
    public void testQueryCollection() {
        DocumentModel file1 = session.createDocumentModel("/", "testfile1", SYS_FILE);
        file1.create();
        ACL acl = file1.getLocalACL();
        acl.add(ACE.builder().user("bob").permission(READ).build());
        file1.setACL(acl, true);
        DocumentModel file2 = session.createDocumentModel("/", "testfile2", SYS_FILE);
        file2.create();
        DocumentModel collection = session.createDocumentModel("/", "doc", SYS_FILE);
        collection.setPropertyValue("mydc_subjects", List.of(file1.getId(), file2.getId()));
        collection.create();
        acl = collection.getLocalACL();
        acl.add(ACE.builder().user("bob").permission(READ).build());
        collection.setACL(acl, true);
        session.save();

        CoreSession bobSession = coreFeature.getCoreSession("bob");
        assertTrue(bobSession.exists(file1.getRef()));
        assertFalse(bobSession.exists(file2.getRef()));
        assertTrue(bobSession.exists(collection.getRef()));

        List<DocumentModel> docs = bobSession.query(
                String.format("SELECT mydc_subjects/* FROM SysContent WHERE sys_id = '%s'", collection.getId()));
        assertEquals(1, docs.size());
        assertEquals(file1.getId(), docs.get(0).getId());
    }

}
