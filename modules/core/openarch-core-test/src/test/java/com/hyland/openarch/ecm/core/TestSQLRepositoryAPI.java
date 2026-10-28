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
package com.hyland.openarch.ecm.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSTEM;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_MINOR_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_GENERATION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_EFFECTIVE_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_EFFECTIVE_PERMISSIONS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH_DEPTH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_USER_GENERATION;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.DIRTY_BLOB_XPATHS;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.DIRTY_XPATHS;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.RETAIN_UNTIL;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_EXTEND_RETENTION;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_MAKE_RECORD;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_REMOVE_LEGAL_HOLD;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_SET_LEGAL_HOLD;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_SET_RETENTION;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_BLOB_MODIFIED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_LOCKED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_SECURITY_UPDATED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UNLOCKED;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.DELETE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.GENERIC_DOCUMENT_SECURITY_ERROR;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ_WRITE;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_VERSIONABLE;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_GOVERNABLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_ORDERED_FOLDER;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import jakarta.inject.Inject;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.hyland.openarch.common.function.ThrowableRunnable;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.api.ConditionFailedException;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.LockException;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.PropertyException;
import com.hyland.openarch.ecm.core.api.UpdateCondition;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.api.model.PropertyNotFoundException;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.api.security.impl.ACLImpl;
import com.hyland.openarch.ecm.core.async.tasks.RetentionExpiredBatchTaskProcessor;
import com.hyland.openarch.ecm.core.blob.AbstractBlobGarbageCollector;
import com.hyland.openarch.ecm.core.blob.BlobProvider;
import com.hyland.openarch.ecm.core.blob.BlobService;
import com.hyland.openarch.ecm.core.blob.ManagedBlob;
import com.hyland.openarch.ecm.core.blob.binary.BinaryGarbageCollector;
import com.hyland.openarch.ecm.core.blob.binary.BinaryManagerStatus;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.ecm.core.event.test.CapturingEventListener;
import com.hyland.openarch.ecm.core.model.InternalSession;
import com.hyland.openarch.ecm.core.model.Repository;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.repository.RepositoryService;
import com.hyland.openarch.ecm.core.schema.DocumentTypeImpl;
import com.hyland.openarch.ecm.core.schema.MixinNames;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.SchemaManagerImpl;
import com.hyland.openarch.ecm.core.security.RetentionExpiredFinderScheduledJob;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.dbs.DBSConnection;
import com.hyland.openarch.ecm.core.storage.dbs.DBSRepository;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import com.hyland.openarch.async.tasks.TaskService;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/disable-schedulers.xml")
public class TestSQLRepositoryAPI {

    private long previousWriteTTL;

    @BeforeEach
    public void disableWriteGracePeriod() {
        previousWriteTTL = AbstractBlobGarbageCollector.GC_WRITE_TTL_SECONDS;
        AbstractBlobGarbageCollector.GC_WRITE_TTL_SECONDS = 0;
    }

    @AfterEach
    public void restoreWriteGracePeriod() {
        AbstractBlobGarbageCollector.GC_WRITE_TTL_SECONDS = previousWriteTTL;
    }

    private static final String CANNOT_REMOVE_ROOT_DIRECTORY = "Cannot remove %s, it is the root directory";

    private static final String MY_FILE = "MyFile";

    private static final String GOVERNABLE_FILE = "GovernableFile";

    private static final String VERSIONABLE_AND_GOVERNABLE_FILE = "VersionableAndGovernableFile";

    private static final String AGED = "Aged";

    private static final String AGE_AGE = "age_age";

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected CoreSession session;

    protected SchemaManager schemaManager;

    @Inject
    protected BlobService blobService;

    @Inject
    protected RepositoryService repositoryService;

    @Inject
    protected TaskService asyncTasksService;

    @BeforeEach
    public void setUp() {
        schemaManager = session.getSchemaManager();
    }

    protected CoreSession openSessionAs(String username) {
        return CoreInstance.getCoreSession(session.getRepositoryName(),
                DummyLoginFeature.getTestPrincipal(username, false));
    }

    protected CoreSession openSessionAs(RepositoryPrincipal principal) {
        return CoreInstance.getCoreSession(session.getRepositoryName(), principal);
    }

    protected void reopenSession() {
        session = coreFeature.reopenCoreSession();
    }

    protected void waitForAsyncCompletion() {
        nextTransaction();
    }

    protected void nextTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    /**
     * Sleep 1s, useful for stupid databases (like MySQL) that don't have subsecond resolution in TIMESTAMP fields.
     */
    protected void maybeSleepToNextSecond() {
        coreFeature.getStorageConfiguration().maybeSleepToNextSecond();
    }

    private DocumentModelList getVersions(DocumentModel doc) {
        return session.query(
                "SELECT * FROM SysContent WHERE sys_parentId = '%s' AND sysver_isVersion = TRUE ORDER BY sysver_version".formatted(
                        doc.getId()));
    }

    /**
     * @since 8.4
     */
    @Test
    public void testIsVersionWritable() {
        DocumentModel doc = session.createDocumentModel("/", "foo", MY_FILE);
        doc.create();
        session.save();

        doc.addMixin(AGED);
        assertTrue(doc.hasMixin(AGED));

        DocumentRef refVersion = doc.checkIn();

        DocumentModel version = session.getDocument(refVersion);

        // TODO DISABLED reimplement per-schema writable version later
        //        version.setPropertyValue(AGE_AGE, "123");
    }

    @Test
    public void testBasics() {
        DocumentModel root = session.getRootDocument();
        DocumentModel child = session.createDocumentModel("/", "domain", "MyDocType");
        child.create();
        session.save();

        child.setPropertyValue(SYS_TITLE, "The title");
        session.save();

        // ----- new session -----
        reopenSession();
        // root = session.getRootDocument();
        child = session.getChild(root.getRef(), "domain");

        String title = (String) child.getPropertyValue(SYS_TITLE);
        assertEquals("The title", title);
        String description = (String) child.getPropertyValue(SYS_DESCRIPTION);
        assertNull(description);
    }

    /*
     * NXP-28727
     */
    @Test
    public void testCreateDocumentsHierarchy() {
        DocumentModel root = session.getRootDocument();

        // create the parent doc under the root
        DocumentModel parent = session.newDocumentModel(new PathRef("/"), "domain", "MyDocType");
        parent.setPropertyValue(SYS_TITLE, "Title of parent");
        parent.create();

        // create the child doc under the parent
        DocumentModel child = session.newDocumentModel(parent.getRef(), "anyFile", SYS_FILE);
        child.setPropertyValue(SYS_TITLE, "Title of child");
        child.setPropertyValue(SYS_DESCRIPTION, "Description of child");
        child.create();

        session.save();

        // open a new session to make the check
//        reopenSession();

        assertEquals(root.getRef(), parent.getParentRef());
        assertEquals("Title of parent", parent.getPropertyValue(SYS_TITLE));
        assertNull(parent.getPropertyValue(SYS_DESCRIPTION));

        assertEquals(parent.getRef(), child.getParentRef());
        assertEquals("Title of child", child.getPropertyValue(SYS_TITLE));
        assertEquals("Description of child", child.getPropertyValue(SYS_DESCRIPTION));

        // remove the parent
        session.removeDocument(parent.getRef());
        transactionalFeature.nextTransaction();

        assertFalse(session.exists(parent.getRef()));
        assertFalse(session.exists(child.getRef()));
    }

    @Test
    public void testSanitizeNameByDefault() {
        // create file with name that has to be sanitized
        DocumentModel file = session.createDocumentModel("/", " foo\\bare\u0301 ", SYS_FILE);
        file.create();
        // spaces trimmed, backslash replaced
        // decomposed chars normalized: E + COMBINING ACUTE ACCENT -> E WITH ACUTE
        assertEquals("foo-bar\u00e9", file.getName());
    }

    @Test
    public void testDuplicateNameIsRenamedByDefault() {
        DocumentModel file = session.createDocumentModel("/", "foo", SYS_FILE);
        file.create();

        // create another with same name
        file = session.createDocumentModel("/", "foo", SYS_FILE);
        file.create();
        assertThat(file.getName(), Matchers.startsWith("foo."));
    }

    @Test
    public void testDuplicateNameIsPreventedWithOption() {
        DocumentModel file = session.createDocumentModel("/", "foo", SYS_FILE);
        file.create();

        // try to create another with same name, but with ENFORCE_SYS_NAME option
        DocumentModel file2 = session.createDocumentModel("/", "foo", SYS_FILE);
        file2.putContextData(CoreSession.ENFORCE_SYS_NAME, true);
        // can't create, concurrent update exception
        assertThrows(ConcurrentUpdateException.class, () -> file2.create());
    }

    @Test
    public void testSanitizeNameIsPreventedWithOption() {
        // tested with spaces, but sanitization checks other things too
        DocumentModel file = session.createDocumentModel("/", "foo ", SYS_FILE);
        file.create();
        // thanks to sanitization spaces have been trimmed (among other things)
        assertEquals("foo", file.getName());

        // but with ENFORCE_SYS_NAME option this is prevented
        DocumentModel file2 = session.createDocumentModel("/", "bar ", SYS_FILE);
        file2.putContextData(CoreSession.ENFORCE_SYS_NAME, true);
        // can't create, property exception
        assertThrows(PropertyException.class, () -> file2.create());
    }

    @Test
    public void testLists() {
        DocumentModel root = session.getRootDocument();
        DocumentModel child = session.createDocumentModel("/", "domain", "MyDocType");
        child.create();
        session.save();

        // simple list as array
        child.setPropertyValue("mydc_subjects", new String[] { "a", "b" });
        // simple list as List
        child.setPropertyValue("testList_simplelist", new ArrayList<>(Arrays.asList("c", "d")));
        // simple list as generic array
        child.setPropertyValue("testList_strings", new Object[] { "e", "f" });
        // complex list as List
        child.setPropertyValue("testList_participants", new ArrayList<>(Arrays.asList("c", "d")));
        session.save();

        child = session.getDocument(child.getRef());
        Object s = child.getPropertyValue("testList_strings");
        assertTrue(s instanceof List, s.getClass().getName());

        // ----- new session -----
        reopenSession();
        root = session.getRootDocument();
        child = session.getChild(root.getRef(), "domain");

        Object subjects = child.getPropertyValue("mydc_subjects");
        assertEquals(Arrays.asList("a", "b"), subjects);
        Object simples = child.getPropertyValue("testList_simplelist");
        assertEquals(Arrays.asList("c", "d"), simples);
        Object strings = child.getPropertyValue("testList_strings");
        assertEquals(Arrays.asList("e", "f"), strings);
        Object participants = child.getPropertyValue("testList_participants");
        assertEquals(Arrays.asList("c", "d"), participants);
    }

    @Test
    public void testPathWithExtraSlash() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "MyDocType");
        doc.create();
        session.save();
        DocumentModelList children = session.getChildren(new PathRef("/"));
        assertEquals(1, children.size());
        children = session.getChildren(new PathRef("/doc"));
        assertEquals(0, children.size());
        children = session.getChildren(new PathRef("/doc/"));
        assertEquals(0, children.size());
    }

    @Test
    public void testComplexType() {
        // boiler plate to handle the asynchronous full-text indexing of blob
        // content in a deterministic way

        DocumentModel doc = session.createDocumentModel("/", "complex-doc", "ComplexDoc");
        doc.create();
        DocumentRef docRef = doc.getRef();
        session.save();
        waitForAsyncCompletion();

        // test setting and reading a map with an empty list
        reopenSession();
        doc = session.getDocument(docRef);
        Map<String, Object> attachedFile = new HashMap<>();
        List<Map<String, Object>> vignettes = new ArrayList<>();
        attachedFile.put("name", "somename");
        attachedFile.put("vignettes", vignettes);
        doc.setPropertyValue("cmpf_attachedFile", attachedFile);
        session.save();

        doc = session.getDocument(docRef);
        assertEquals(attachedFile, doc.getPropertyValue("cmpf_attachedFile"));
        assertEquals(attachedFile.get("vignettes"), doc.getPropertyValue("cmpf_attachedFile/vignettes"));

        // test setting and reading a list of maps without a complex type in the
        // maps
        Map<String, Object> vignette = new HashMap<>();
        vignette.put("width", Long.valueOf(0));
        vignette.put("height", Long.valueOf(0));
        vignette.put("content", Blobs.createBlob("textblob content"));
        vignette.put("label", "vignettelabel");
        vignettes.add(vignette);
        doc.setPropertyValue("cmpf_attachedFile", attachedFile);
        session.save();

        doc = session.getDocument(docRef);
        assertEquals("text/plain",
                doc.getPropertyValue("cmpf_attachedFile/vignettes/0/content/mimeType"));
        assertEquals(Long.valueOf(0), doc.getPropertyValue("cmpf_attachedFile/vignettes/0/height"));
        assertEquals("vignettelabel", doc.getPropertyValue("cmpf_attachedFile/vignettes/0/label"));

        // test setting and reading a list of maps with a blob inside the map
        Blob blob = Blobs.createBlob("01AB", "application/octet-stream", null, "file.bin");
        vignette.put("content", blob);
        doc.setPropertyValue("cmpf_attachedFile", attachedFile);
        session.save();

        doc = session.getDocument(docRef);
        assertEquals(Long.valueOf(0), doc.getPropertyValue("cmpf_attachedFile/vignettes/0/height"));
        assertEquals("vignettelabel", doc.getPropertyValue("cmpf_attachedFile/vignettes/0/label"));

        // this doesn't work due to core restrictions (BlobProperty):
        // assertEquals(blob.getFilename(), doc.getProperty(
        // "cmpf_attachedFile/vignettes/0/content/filename").getValue());
        Blob b = (Blob) doc.getPropertyValue("cmpf_attachedFile/vignettes/0/content");
        assertEquals("file.bin", b.getFilename());
    }

    @Test
    public void testComplexTypeOrdering() throws Exception {
        // test case to reproduce an ordering content related Heisenbug on
        // postgresql: NXP-2810: Preserve creation order of children of a
        // complex type property in SQL storage with PostgreSQL

        // create documents with a list of ordered vignettes
        createComplexDocs(0, 5);

        // check that the created docs hold their complex content in the
        // creation order
        checkComplexDocs(0, 5);

        // add some more docs
        createComplexDocs(5, 10);

        // check that both the old and new document still hold their complex
        // content in the same creation order
        checkComplexDocs(0, 10);
    }

    protected void createComplexDocs(int iMin, int iMax) {
        for (int i = iMin; i < iMax; i++) {
            DocumentModel doc = session.createDocumentModel("/", "doc" + i, "ComplexDoc");

            Map<String, Object> attachedFile = new HashMap<>();
            List<Map<String, Object>> vignettes = new ArrayList<>();
            attachedFile.put("name", "some name");
            attachedFile.put("vignettes", vignettes);

            for (int j = 0; j < 3; j++) {
                Map<String, Object> vignette = new HashMap<>();
                vignette.put("width", Long.valueOf(j));
                vignette.put("height", Long.valueOf(j));
                vignette.put("content", Blobs.createBlob(String.format("document %d, vignette %d", i, j)));
                vignettes.add(vignette);
            }
            doc.setPropertyValue("cmpf_attachedFile", attachedFile);
            doc.create();

            session.save();
        }
    }

    protected void checkComplexDocs(int iMin, int iMax) throws IOException {
        for (int i = iMin; i < iMax; i++) {
            DocumentModel doc = session.getDocument(new PathRef("/doc" + i));

            for (int j = 0; j < 3; j++) {
                String propertyPath = String.format("cmpf_attachedFile/vignettes/%d/", j);
                assertEquals(Long.valueOf(j), doc.getPropertyValue(propertyPath + "height"));
                assertEquals(Long.valueOf(j), doc.getPropertyValue(propertyPath + "width"));
                assertEquals(String.format("document %d, vignette %d", i, j),
                        ((Blob) doc.getPropertyValue(propertyPath + "content")).getString());
            }
        }
    }

    @Test
    public void testMarkDirty() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "MyDocType");
        doc.create();
        session.save();

        doc.setPropertyValue(SYS_TITLE, "title1");
        doc.setPropertyValue("testList_participants", new ArrayList<>(Arrays.asList("a", "b")));
        session.save();

        doc.setPropertyValue(SYS_TITLE, "title2");
        doc.setPropertyValue("testList_participants", new ArrayList<>(Arrays.asList("c", "d")));
        session.save();

        // ----- new session -----
        reopenSession();
        // root = session.getRootDocument();
        doc = session.getDocument(new PathRef("/doc"));
        String title = (String) doc.getPropertyValue(SYS_TITLE);
        assertEquals("title2", title);
        Object participants = doc.getPropertyValue("testList_participants");
        assertEquals(List.of("c", "d"), participants);
    }

    @Test
    public void testMarkDirtyForList() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "ComplexDoc");
        Map<String, Object> attachedFile = new HashMap<>();
        List<Map<String, Object>> vignettes = new ArrayList<>();
        attachedFile.put("vignettes", vignettes);
        Map<String, Object> vignette = new HashMap<>();
        vignette.put("width", 111L);
        vignettes.add(vignette);
        doc.setPropertyValue("cmpf_attachedFile", attachedFile);
        doc.create();
        session.save();

        doc.setPropertyValue("cmpf_attachedFile/vignettes/0/width", 222L);
        session.save();

        doc.setPropertyValue("cmpf_attachedFile/vignettes/0/width", 333L);
        session.save();

        // ----- new session -----
        reopenSession();
        doc = session.getDocument(new PathRef("/doc"));
        assertEquals(333L, doc.getPropertyValue("cmpf_attachedFile/vignettes/0/width"));
    }

    protected final Random random = new Random(System.currentTimeMillis());

    protected String generateUnique() {
        return String.valueOf(random.nextLong());
    }

    @Test
    public void testGetRootDocument() {
        DocumentModel root = session.getRootDocument();
        assertNotNull(root);
        assertNotNull(root.getId());
        assertNotNull(root.getRef());
        assertNotNull(root.getPathAsString());
    }

    @Test
    public void testShouldNotRemoveRootDocument() {
        DocumentModel root = session.getRootDocument();
        RuntimeException exception = assertThrows(DocumentSecurityException.class, () -> {
            session.removeDocument(root.getRef());
        });
        assertEquals(CANNOT_REMOVE_ROOT_DIRECTORY.formatted(root.getRef().reference()),
                exception.getMessage());
    }

    @Test
    public void testShouldNotRemoveRootDocuments() {
        DocumentModel root = session.getRootDocument();
        RuntimeException exception = assertThrows(DocumentSecurityException.class, () -> {
            session.removeDocuments(new DocumentRef[] { root.getRef() });
        });
        assertEquals(CANNOT_REMOVE_ROOT_DIRECTORY.formatted(root.getRef().reference()),
                exception.getMessage());
    }

    @Test
    public void testShouldNotRemoveRootDocumentById() {

        DocumentModel root = session.getRootDocument();
        InternalSession internalSession = ((SessionImpl) session).getSession();

        RuntimeException exception = assertThrows(DocumentSecurityException.class, () -> {
            internalSession.removeDocument(root.getId(), session.getPrincipal());
        });
        assertEquals(CANNOT_REMOVE_ROOT_DIRECTORY.formatted(root.getRef().reference()),
                exception.getMessage());
    }

    @SuppressWarnings({ "SimplifiableJUnitAssertion" })
    @Test
    public void testDocumentReferenceEqualitySameInstance() {
        DocumentModel root = session.getRootDocument();

        assertEquals(root.getRef(), root.getRef());
    }

    @Test
    public void testCreateFolderDocumentRefDocumentModel() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        assertEquals(SYS_FOLDER, childFolder.getType());
        assertEquals(name, childFolder.getName());
    }

    @Test
    public void testCreateFileDocumentRefDocumentModel() {
        DocumentModel root = session.getRootDocument();

        String name = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name, SYS_FILE);

        childFile.create();

        assertEquals(SYS_FILE, childFile.getType());
        assertEquals(name, childFile.getName());
    }

    @Test
    public void testCreateFolderDocumentRefDocumentModelArray() { // NOSONAR (no need to assert anything)
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "folder#" + generateUnique();
        DocumentModel childFolder2 = session.createDocumentModel(root.getPathAsString(), name2, SYS_FOLDER);
        childFolder2.create();
    }

    @Test
    public void testCreateFileDocumentRefDocumentModelArray() { // NOSONAR (no need to assert anything)
        DocumentModel root = session.getRootDocument();

        String name = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name, SYS_FILE);
        childFile.create();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile2 = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile2.create();
    }

    @Test
    public void testExists() {
        DocumentModel root = session.getRootDocument();

        assertTrue(session.exists(root.getRef()));
    }

    @Test
    public void testGetChild() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        assertFalse(session.hasChild(root.getRef(), name));
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "file#" + generateUnique();
        assertFalse(session.hasChild(root.getRef(), name2));
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        assertTrue(session.hasChild(root.getRef(), name));
        assertTrue(session.hasChild(root.getRef(), name2));

        DocumentModel retrievedChild = session.getChild(root.getRef(), name);

        assertNotNull(retrievedChild);
        assertNotNull(retrievedChild.getId());
        assertNotNull(retrievedChild.getPathAsString());
        assertNotNull(retrievedChild.getName());
        assertNotNull(retrievedChild.getRef());

        assertEquals(name, retrievedChild.getName());

        retrievedChild = session.getChild(root.getRef(), name2);

        assertNotNull(retrievedChild);
        assertNotNull(retrievedChild.getId());
        assertNotNull(retrievedChild.getPathAsString());
        assertNotNull(retrievedChild.getName());
        assertNotNull(retrievedChild.getRef());

        assertEquals(name2, retrievedChild.getName());

        try {
            session.getChild(root.getRef(), "nosuchchild");
            fail("Should fail with DocumentNotFoundException");
        } catch (DocumentNotFoundException e) {
            assertEquals("nosuchchild", e.getMessage());
        }
    }

    @Test
    public void testGetChildrenDocumentRef() {
        DocumentModel root = session.getRootDocument();

        List<DocumentModel> docs = session.getChildren(root.getRef());

        assertEquals(0, docs.size());
    }

    /**
     * Test for NXP-741: Search based getChildren.
     */
    @Test
    public void testGetChildrenInFolderWithSearch() { // NOSONAR (no need to assert anything)
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel folder = session.createDocumentModel(root.getPathAsString(), name, "FolderWithSearch");

        folder.create();

        // create more children
        for (int i = 0; i < 5; i++) {
            name = "File_" + i;
            DocumentModel childFile = session.createDocumentModel(folder.getPathAsString(), name, SYS_FILE);
            childFile.create();
        }
    }

    @Test
    public void testGetDocumentDocumentRef() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        DocumentModel doc = session.getDocument(childFolder.getRef());

        assertNotNull(doc);
        assertNotNull(doc.getRef());
        assertNotNull(doc.getName());
        assertNotNull(doc.getId());
        assertNotNull(doc.getPathAsString());

        assertEquals(name, doc.getName());
        assertEquals(SYS_FOLDER, doc.getType());
    }

    // TODO NXP-2514: fix this test.
    @Test
    @Disabled
    public void testGetDocumentDocumentRefStringArray() {
        DocumentModel root = session.getRootDocument();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        session.save();

        childFile.setPropertyValue(SYS_TITLE, "f1");
        childFile.setPropertyValue(SYS_DESCRIPTION, "desc 1");

        session.save();

        DocumentModel returnedDocument = session.getDocument(childFile.getRef());

        assertNotNull(returnedDocument);
        assertNotNull(returnedDocument.getRef());
        assertNotNull(returnedDocument.getId());
        assertNotNull(returnedDocument.getName());
        assertNotNull(returnedDocument.getPathAsString());
        assertNotNull(returnedDocument.getType());
        assertNotNull(returnedDocument.getSchemas());

        // TODO NXP-2514: should it contain 3 or 1 schemas? not sure about that.
        Set<String> schemas = returnedDocument.getSchemas();
        assertEquals(2, schemas.size());
        assertTrue(schemas.contains(SYSFILE));
        assertTrue(schemas.contains(SYSTEM));

        assertEquals("f1", returnedDocument.getPropertyValue(SYS_TITLE));
        assertEquals("desc 1", returnedDocument.getPropertyValue(SYS_DESCRIPTION));

        returnedDocument = session.getDocument(childFile.getRef());

        assertNotNull(returnedDocument);
        assertNotNull(returnedDocument.getRef());
        assertNotNull(returnedDocument.getId());
        assertNotNull(returnedDocument.getName());
        assertNotNull(returnedDocument.getPathAsString());
        assertNotNull(returnedDocument.getType());
        assertNotNull(returnedDocument.getSchemas());

        schemas = returnedDocument.getSchemas();
        assertEquals(2, schemas.size());
        assertTrue(schemas.contains(SYSFILE));
        assertTrue(schemas.contains(SYSTEM));

        assertEquals("f1", returnedDocument.getPropertyValue(SYS_TITLE));
        assertEquals("desc 1", returnedDocument.getPropertyValue(SYS_DESCRIPTION));
    }

    @Test
    public void testGetParentDocument() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        DocumentModel shouldBeRoot = session.getParentDocument(childFolder.getRef());

        assertEquals(root.getPathAsString(), shouldBeRoot.getPathAsString());
    }

    @Test
    public void testGetParentDocumentFromPathReference() {
        DocumentRef rootRef = new PathRef("/");
        assertNull(session.getParentDocument(rootRef));

        String name = "folder#" + generateUnique();
        DocumentModel parentFolder = session.createDocumentModel("/", name, SYS_FOLDER);
        DocumentModel shouldBeRoot = session.getParentDocument(parentFolder.getRef());
        assertEquals("/", shouldBeRoot.getPathAsString());

        parentFolder.create();
        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(parentFolder.getPathAsString(), name2, SYS_FILE);
        DocumentModel shouldBeFolder = session.getParentDocument(childFile.getRef());
        assertEquals(parentFolder.getPathAsString(), shouldBeFolder.getPathAsString());
    }

    @Test
    public void testGetDocumentAncestors() {
        DocumentModel folder = session.createDocumentModel("/", "Folder#" + generateUnique(), SYS_FOLDER);
        folder.create();

        DocumentModel file = session.createDocumentModel(folder.getPathAsString(), "File#" + generateUnique(), SYS_FILE);
        file.create();

        List<DocumentModel> ancestors = session.getAncestorDocuments(file.getRef());
        assertEquals(2, ancestors.size());
        assertEquals(folder, ancestors.get(0));
        assertEquals(file, ancestors.get(1));
    }

    @Test
    public void testGetDocumentAncestorsWithPermissions() {
        // create document structure: FolderA > FolderB > FolderC > File
        DocumentModel folderA = session.createDocumentModel("/", "FolderA#" + generateUnique(), SYS_FOLDER);
        folderA.create();
        String folderAPath = folderA.getPathAsString();
        DocumentModel folderB = session.createDocumentModel(folderAPath, "FolderB#" + generateUnique(), SYS_FOLDER);
        folderB.create();
        String folderBPath = folderB.getPathAsString();
        DocumentModel folderC = session.createDocumentModel(folderBPath, "FolderC#" + generateUnique(), SYS_FOLDER);
        folderC.create();
        String folderCPath = folderC.getPathAsString();
        DocumentModel file = session.createDocumentModel(folderCPath, "File#" + generateUnique(), SYS_FILE);
        file.create();

        // user shouldn't have access to any document
        String user = "user";
        CoreSession userSession = openSessionAs(user);
        List<DocumentModel> ancestors = userSession.getAncestorDocuments(file.getRef());
        assertEquals(0, ancestors.size());

        // set the ACL of FolderA, granting user READ access
        ACL aclFolderA = new ACLImpl();
        aclFolderA.add(ACE.builder().user(user).permission(READ).build());
        folderA.setACL(aclFolderA, true);
        // set the ACL of FolderB, removing all permissions from everyone
        ACL aclFolderB = new ACLImpl();
        aclFolderB.add(ACE.BLOCK);
        folderB.setACL(aclFolderB, true);
        // set the ACL of File, granting user READ access
        ACL aclFile = new ACLImpl();
        aclFile.add(ACE.builder().user(user).permission(READ).build());
        file.setACL(aclFile, true);

        // user should only have access to FolderA and File
        ancestors = userSession.getAncestorDocuments(file.getRef());
        assertEquals(4, ancestors.size());
        assertEquals(folderA, ancestors.get(0));
        assertNull(ancestors.get(1));
        assertNull(ancestors.get(2));
        assertEquals(file, ancestors.get(3));
    }

    @Test
    public void testHasChildren() {
        DocumentModel root = session.getRootDocument();

        // the root document at the moment has no children
        assertFalse(session.hasChildren(root.getRef()));
    }

    @Test
    public void testRemoveChildren() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        session.removeChildren(root.getRef());

        assertFalse(session.exists(childFolder.getRef()));
        assertFalse(session.exists(childFile.getRef()));
    }

    @Test
    public void testRemoveChildren2() { // NOSONAR (no need to assert anything)
        DocumentModel folder1 = session.createDocumentModel("/", "folder1", SYS_FOLDER);
        folder1.create();
        DocumentModel folder2 = session.createDocumentModel("/folder1", "folder2", SYS_FOLDER);
        folder2.create();
        DocumentModel doc1 = session.createDocumentModel("/folder1/folder2", "doc1", SYS_FILE);
        doc1.create();
        session.save();
        // now remove
        session.removeDocument(folder2.getRef());
        // don't save
        // check that we don't get phantom docs when getting children
        // for instance when updating ACLs recursively
        folder1.setACL(new ACLImpl(), true);
        session.save();
    }

    protected void dumpAllDocuments(CoreSession session) {
        DocumentModelList docs = session.query("select * FROM SysContent");
        System.out.println("List all documents");
        for (DocumentModel doc : docs) {
            System.out.println(String.format("- type: %s, version: %s %s", doc.getType(), doc.isVersion(), doc));
        }
    }

    @Test
    @Disabled("NXP-14686")
    public void testRemoveChildrenWithVersion() {
        DocumentModel root = session.getRootDocument();

        DocumentModel doc = session.createDocumentModel(root.getPathAsString(), "somefile", SYS_FILE);
        doc.create();

        DocumentModel folder = session.createDocumentModel(root.getPathAsString(), "somefolder", SYS_FOLDER);
        folder.create();

        DocumentModel file = session.getDocument(doc.getRef());
        session.checkIn(file.getRef());
        session.save();
        // TransactionHelper.commitOrRollbackTransaction();
        // TransactionHelper.startTransaction();

        dumpAllDocuments(session);

        session.removeChildren(root.getRef());
        session.save();
        // TransactionHelper.commitOrRollbackTransaction();
        // TransactionHelper.startTransaction();
        dumpAllDocuments(session);

        assertTrue(session.query("SELECT * FROM SysContent").isEmpty(), "NXP-14686 removeChildren fails to delete orphan version");
    }

    @Test
    public void testRemoveDocument() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        session.removeDocument(childFolder.getRef());

        assertFalse(session.exists(childFolder.getRef()));
    }

    @Test
    public void testRemoveVersion() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        // create version
        DocumentRef version = doc.checkIn();

        // create version 2
        doc.checkOut();
        doc.checkIn();
        // restore doc to version 1
        session.restoreVersion(version);

        // CAN remove version even if last and working copy is checked in
        session.removeDocument(version);
    }

    @Test
    public void testRemoveDocuments() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        DocumentRef[] refs = { childFolder.getRef(), childFile.getRef() };
        session.removeDocuments(refs);

        assertFalse(session.exists(childFolder.getRef()));
        assertFalse(session.exists(childFile.getRef()));

    }

    @Test
    public void testRemoveDocumentsTree() {
        DocumentModel rootFolder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        rootFolder.create();
        String firstUser = "mickey";

        // set EVERYTHING ACL for first user on root folder
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user(firstUser).permission(EVERYTHING).build());
        rootFolder.setACL(acl, true);

        // Create a hierarchy
        int nbLevels = 2;
        DocumentModel[] docs = new DocumentModel[nbLevels];
        DocumentModel parent = rootFolder;
        for (int level = 0; level < nbLevels; level++) {
            docs[level] = session.createDocumentModel(parent.getPathAsString(), "folder-" + level, SYS_FOLDER);
            docs[level].create();
            parent = docs[level];
        }
        session.save();

        // Block permission on deepest doc for first user
        ACL blockedACL = new ACLImpl();
        blockedACL.add(ACE.builder().user(SecurityConstants.SYSTEM_USERNAME).permission(EVERYTHING).build());
        blockedACL.add(ACE.BLOCK);
        docs[nbLevels - 1].setACL(blockedACL, true);
        session.save();

        CoreSession firstUserSession = openSessionAs(firstUser);
        // remove tree
        firstUserSession.removeDocument(docs[0].getRef());

        // wait for asynchronous stuff to finish
        coreFeature.waitForAsyncCompletion();

        // Make sure the deepest document was removed despite permission was blocked
        assertTrue(session.query("SELECT * FROM SysContent WHERE sys_id = '%s'".formatted(docs[nbLevels - 1].getRef()))
                          .isEmpty());
    }

    /*
     * case where some documents are actually children of other ones from the list
     */
    @Test
    public void testRemoveDocumentsWithDeps() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        // careless removing this one after the folder would fail
        String name2 = "file#" + generateUnique();
        DocumentModel folderChildFile = session.createDocumentModel(childFolder.getPathAsString(), name2, SYS_FILE);
        folderChildFile.create();

        // one more File object, whose path is greater than the folder's
        String name3 = "file#" + generateUnique();
        DocumentModel folderChildFile2 = session.createDocumentModel(childFolder.getPathAsString(), name3, SYS_FILE);
        folderChildFile2.create();

        // one more File object at the root,
        // whose path is greater than the folder's
        String name4 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name4, SYS_FILE);
        childFile.create();

        // one more File object at the root, whose path is greater than the
        // folder's and with name conflict resolved by core directly, see
        // NXP-3240
        DocumentModel childFile2 = session.createDocumentModel(root.getPathAsString(), name4, SYS_FILE);
        childFile2.create();

        assertEquals(name, childFolder.getName());
        assertEquals(name2, folderChildFile.getName());
        assertEquals(name3, folderChildFile2.getName());
        assertEquals(name4, childFile.getName());
        // not the same here: conflict resolved by core session
        String name5 = childFile2.getName();
        assertNotSame(name4, name5);
        assertTrue(name5.startsWith(name4));

        DocumentRef[] refs = { childFolder.getRef(), folderChildFile.getRef(), folderChildFile2.getRef(),
                childFile.getRef(), childFile2.getRef() };
        session.removeDocuments(refs);
        transactionalFeature.nextTransaction();

        assertFalse(session.exists(childFolder.getRef()));
        assertFalse(session.exists(folderChildFile.getRef()));
        assertFalse(session.exists(folderChildFile2.getRef()));
        assertFalse(session.exists(childFile.getRef()));
        assertFalse(session.exists(childFile2.getRef()));
    }

    /*
     * Same as testRemoveDocumentWithDeps with a different given ordering of documents to delete
     */
    @Test
    public void testRemoveDocumentsWithDeps2() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        // careless removing this one after the folder would fail
        String name2 = "file#" + generateUnique();
        DocumentModel folderChildFile = session.createDocumentModel(childFolder.getPathAsString(), name2, SYS_FILE);
        folderChildFile.create();

        // one more File object, whose path is greater than the folder's
        String name3 = "file#" + generateUnique();
        DocumentModel folderChildFile2 = session.createDocumentModel(childFolder.getPathAsString(), name3, SYS_FILE);
        folderChildFile2.create();

        // one more File object at the root,
        // whose path is greater than the folder's
        String name4 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name4, SYS_FILE);
        childFile.create();

        // one more File object at the root, whose path is greater than the
        // folder's and with name conflict resolved by core directly, see
        // NXP-3240
        DocumentModel childFile2 = session.createDocumentModel(root.getPathAsString(), name4, SYS_FILE);
        childFile2.create();

        assertEquals(name, childFolder.getName());
        assertEquals(name2, folderChildFile.getName());
        assertEquals(name3, folderChildFile2.getName());
        assertEquals(name4, childFile.getName());
        // not the same here: conflict resolved by core session
        String name5 = childFile2.getName();
        assertNotSame(name4, name5);
        assertTrue(name5.startsWith(name4));

        // here's the different ordering
        DocumentRef[] refs = { folderChildFile.getRef(), childFolder.getRef(), childFile2.getRef(), childFile.getRef(),
                folderChildFile2.getRef() };
        session.removeDocuments(refs);
        transactionalFeature.nextTransaction();

        assertFalse(session.exists(childFolder.getRef()));
        assertFalse(session.exists(folderChildFile.getRef()));
        assertFalse(session.exists(folderChildFile2.getRef()));
        assertFalse(session.exists(childFile.getRef()));
        assertFalse(session.exists(childFile2.getRef()));
    }

    @Test
    public void testRemoveDocumentTreeWithSecurity() {
        ACL acl;
        DocumentModelList dml;

        DocumentModel root = session.getRootDocument();
        DocumentModel f1 = session.createDocumentModel("/", "f1", SYS_FOLDER);
        f1.create();
        DocumentModel doc1 = session.createDocumentModel("/f1", "doc1", SYS_FILE);
        doc1.create();
        DocumentModel doc2 = session.createDocumentModel("/f1", "doc2", SYS_FILE);
        doc2.create();
        // set ACL on root
        acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.builder().user("bob").permission(EVERYTHING).build());
        root.setACL(acl, true);
        // set ACL on doc1 to block bob
        acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.BLOCK);
        doc1.setACL(acl, true);
        session.save();

        // check admin sees doc1 and doc2
        dml = session.query("SELECT * FROM SysContent WHERE sys_path STARTSWITH '/f1'");
        assertEquals(2, dml.size());

        // as bob
        CoreSession bobSession = openSessionAs("bob");

        // check bob doesn't see doc1
        dml = bobSession.query("SELECT * FROM SysContent WHERE sys_path STARTSWITH '/f1'");
        assertEquals(1, dml.size());

        // do copy
        bobSession.copy(f1.getRef(), root.getRef(), "f2");

        // save is mandatory to propagate read acls after a copy
        bobSession.save();

        // check bob doesn't see doc1's copy
        dml = bobSession.query("SELECT * FROM SysContent WHERE sys_path STARTSWITH '/f2'");
        assertEquals(1, dml.size());
    }

    @Test
    public void testSave() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        session.save();

        // TODO NXP-2514: this should be tested across sessions - when it can be done
        assertTrue(session.exists(childFolder.getRef()));
        assertTrue(session.exists(childFile.getRef()));
    }

    @Test
    public void testSaveFolder() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();

        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        childFolder.setPropertyValue(SYS_TITLE, "f1");
        childFolder.setPropertyValue(SYS_DESCRIPTION, "desc 1");

        // TODO NXP-2514: this should be tested across sessions - when it can be done
        assertTrue(session.exists(childFolder.getRef()));

        assertEquals("f1", childFolder.getPropertyValue(SYS_TITLE));
        assertEquals("desc 1", childFolder.getPropertyValue(SYS_DESCRIPTION));
    }

    @Test
    public void testSaveFile() {
        DocumentModel root = session.getRootDocument();

        String name = "file#" + generateUnique();

        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name, SYS_FILE);
        childFile.setPropertyValue(SYS_TITLE, "f1");
        childFile.setPropertyValue(SYS_DESCRIPTION, "desc 1");

        childFile.create();

        assertTrue(session.exists(childFile.getRef()));

        DocumentModel retrievedFile = session.getDocument(childFile.getRef());

        assertEquals("f1", retrievedFile.getPropertyValue(SYS_TITLE));
        assertEquals("desc 1", retrievedFile.getPropertyValue(SYS_DESCRIPTION));
    }

    @Test
    public void testDocumentReferenceEqualityDifferentInstances() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        DocumentModel retrievedChild = session.getChild(root.getRef(), name);

        assertNotNull(retrievedChild);
        assertNotNull(retrievedChild.getId());
        assertNotNull(retrievedChild.getPathAsString());
        assertNotNull(retrievedChild.getName());
        assertNotNull(retrievedChild.getRef());
        assertEquals(name, retrievedChild.getName());

        assertEquals(root.getRef(), retrievedChild.getParentRef());

        retrievedChild = session.getChild(root.getRef(), name2);

        assertNotNull(retrievedChild);
        assertNotNull(retrievedChild.getId());
        assertNotNull(retrievedChild.getPathAsString());
        assertNotNull(retrievedChild.getName());
        assertNotNull(retrievedChild.getRef());

        assertEquals(name2, retrievedChild.getName());
        assertEquals(root.getRef(), retrievedChild.getParentRef());
    }

    @Test
    public void testDocumentReferenceNonEqualityDifferentInstances() {
        DocumentModel root = session.getRootDocument();

        String name = "folder#" + generateUnique();
        DocumentModel childFolder = session.createDocumentModel(root.getPathAsString(), name, SYS_FOLDER);
        childFolder.create();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        DocumentModel retrievedChild = session.getChild(root.getRef(), name);

        assertNotNull(retrievedChild);
        assertNotNull(retrievedChild.getId());
        assertNotNull(retrievedChild.getPathAsString());
        assertNotNull(retrievedChild.getName());
        assertNotNull(retrievedChild.getRef());
        assertEquals(name, retrievedChild.getName());

        assertNotEquals(root.getRef(), retrievedChild.getRef());
        assertNotEquals(retrievedChild.getRef(), retrievedChild.getParentRef());
    }

    @Test
    public void testFolderMixin() {
        DocumentModel child1 = session.createDocumentModel("/", "file1", SYS_FILE);
        child1.create();
        DocumentModel child2 = session.createDocumentModel("/", "fold1", SYS_FOLDER);
        child2.create();
        DocumentModel child3 = session.createDocumentModel("/", "ws1", SYS_FOLDER);
        child3.create();
        assertFalse(child1.isFolder());
        assertTrue(child2.isFolder());
        assertTrue(child3.isFolder());
    }

    @Test
    public void testMixinAPI() {
        DocumentModel doc = session.createDocumentModel("/", "foo", SYS_FILE);
        doc.create();
        session.save();
        DocumentModelList dml;

        // mixin not yet present
        assertFalse(doc.hasMixin(AGED));
        Set<String> baseMixins = Set.of(MixinNames.SYS_FILISH);
        assertEquals(baseMixins, doc.getEffectiveMixins());
        try {
            doc.setPropertyValue(AGE_AGE, "123");
            fail();
        } catch (PropertyNotFoundException e) {
            // ok
        }
        dml = session.query("SELECT * FROM SysFile WHERE sys_mixinTypes = 'Aged'");
        assertEquals(0, dml.size());
        dml = session.query("SELECT * FROM SysContent WHERE sys_mixinTypes <> 'Aged'");
        assertEquals(1, dml.size());

        // add mixin
        doc.setMixins(Set.of(AGED));
        assertTrue(doc.hasMixin(AGED));
        assertEquals(baseMixins.size() + 1, doc.getEffectiveMixins().size());
        doc.setPropertyValue(AGE_AGE, "123");
        assertTrue(doc.hasMixin(AGED));
        assertEquals(baseMixins.size() + 1, doc.getEffectiveMixins().size());
        assertEquals("123", doc.getPropertyValue(AGE_AGE));
        session.save();

        reopenSession();
        doc = session.getDocument(doc.getRef());
        assertTrue(doc.hasMixin(AGED));
        assertEquals(baseMixins.size() + 1, doc.getEffectiveMixins().size());
        assertEquals("123", doc.getPropertyValue(AGE_AGE));
        dml = session.query("SELECT * FROM SysFile WHERE sys_mixinTypes = 'Aged'");
        assertEquals(1, dml.size());
        dml = session.query("SELECT * FROM SysContent WHERE sys_mixinTypes <> 'Aged'");
        assertEquals(0, dml.size());

        // add other mixin with no schema
        doc.setMixins(Set.of(AGED, MixinNames.SYS_ORDERABLE));
        assertTrue(doc.hasMixin(AGED));
        assertTrue(doc.hasMixin(MixinNames.SYS_ORDERABLE));
        assertEquals(baseMixins.size() + 2, doc.getEffectiveMixins().size());

        // remove first mixin
        doc.setMixins(Set.of(MixinNames.SYS_ORDERABLE));
        assertFalse(doc.hasMixin(AGED));
        assertTrue(doc.hasMixin(MixinNames.SYS_ORDERABLE));
        assertEquals(baseMixins.size() + 1, doc.getEffectiveMixins().size());
        try {
            doc.getPropertyValue(AGE_AGE);
            fail();
        } catch (PropertyNotFoundException e) {
            // ok
        }
        assertFalse(doc.hasMixin(AGED));
        assertTrue(doc.hasMixin(MixinNames.SYS_ORDERABLE));
        assertEquals(baseMixins.size() + 1, doc.getEffectiveMixins().size());
        try {
            doc.getPropertyValue(AGE_AGE);
            fail();
        } catch (PropertyNotFoundException e) {
            // ok
        }
        session.save();

        reopenSession();
        doc = session.getDocument(doc.getRef());
        assertFalse(doc.hasMixin(AGED));
        assertTrue(doc.hasMixin(MixinNames.SYS_ORDERABLE));
        assertEquals(baseMixins.size() + 1, doc.getEffectiveMixins().size());
        try {
            doc.getPropertyValue(AGE_AGE);
            fail();
        } catch (PropertyNotFoundException e) {
            // ok
        }
        dml = session.query("SELECT * FROM SysFile WHERE sys_mixinTypes = 'Aged'");
        assertEquals(0, dml.size());
        dml = session.query("SELECT * FROM SysContent WHERE sys_mixinTypes <> 'Aged'");
        assertEquals(1, dml.size());

        // remove other mixin
        doc.setMixins(Set.of());
        assertFalse(doc.hasMixin(AGED));
        assertFalse(doc.hasMixin(MixinNames.SYS_ORDERABLE));
        assertEquals(baseMixins, doc.getEffectiveMixins());
    }

    @Test
    public void testMixinIncludedInPrimaryType() {
        DocumentModel doc = session.createDocumentModel("/", "foo", "DocWithAge");
        doc.setPropertyValue(AGE_AGE, "123");
        doc.create();
        session.save();

        // new session
        reopenSession();
        doc = session.getDocument(doc.getRef());
        assertEquals("123", doc.getPropertyValue(AGE_AGE));

        // API on doc whose type has a mixin
        assertEquals(Collections.singleton(AGED), doc.getEffectiveMixins());
        assertTrue(doc.hasMixin(AGED));
    }

    @Test
    public void testSetPrimaryTypeAndMixinTypes() {
        DocumentModel doc = session.createDocumentModel("/", "foo", "DocWithAge");
        doc.setPropertyValue(AGE_AGE, "123");
        doc.create();
        session.save();

        // change primary type
        doc.setPrimaryTypeAndMixinTypes(SYS_FILE, Set.of());
        session.save();

        // property is gone
        assertEquals(SYS_FILE, doc.getType());
        try {
            doc.getPropertyValue(AGE_AGE);
            fail();
        } catch (PropertyNotFoundException e) {
            // ok
        }

        // change back primary type
        doc.setPrimaryTypeAndMixinTypes("DocWithAge", Set.of());
        session.save();

        // property value has been lost
        assertEquals("DocWithAge", doc.getType());
        assertNull(doc.getPropertyValue(AGE_AGE));
    }

    @Test
    public void testSetPrimaryAndTypeMixinTypes2() {
        // create a doc with an instance mixin
        DocumentModel doc = session.createDocumentModel("/", "foo", SYS_FILE);
        doc.addMixin(AGED);
        doc.setPropertyValue(AGE_AGE, "123");
        doc.create();
        session.save();

        // change primary type
        doc.setPrimaryTypeAndMixinTypes("DocWithAge", Set.of());
        session.save();

        // property is still there, moved from instance mixin to type's
        assertEquals("DocWithAge", doc.getType());
        assertEquals("123", doc.getPropertyValue(AGE_AGE));

        // change back primary type but re-add instance mixin
        doc.setPrimaryTypeAndMixinTypes(SYS_FILE, Set.of(AGED));
        session.save();

        // property is still there, moved back to instance mixin
        assertEquals(SYS_FILE, doc.getType());
        assertEquals(Set.of(MixinNames.SYS_FILISH, AGED), doc.getEffectiveMixins());
        assertEquals("123", doc.getPropertyValue(AGE_AGE));

        // change to remove instance mixin
        doc.setPrimaryTypeAndMixinTypes(SYS_FILE, Set.of());
        session.save();

        // property value has been lost and there is no more mixin
        assertEquals(SYS_FILE, doc.getType());
        assertEquals(Set.of(MixinNames.SYS_FILISH), doc.getEffectiveMixins());
        try {
            doc.getPropertyValue(AGE_AGE);
            fail();
        } catch (PropertyNotFoundException e) {
            // ok
        }
    }

    @Test
    public void testSetPrimaryAndTypeMixinTypesInvalid() {
        DocumentModel doc = session.createDocumentModel("/", "foo", SYS_FILE);
        doc.create();
        session.save();

        // invalid type
        try {
            doc.setPrimaryTypeAndMixinTypes("NoSuchPrimaryType", Set.of());
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("NoSuchPrimaryType", e.getMessage());
        }

        // invalid mixin
        try {
            doc.setPrimaryTypeAndMixinTypes(SYS_FILE, Set.of("NoSuchMixin"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("NoSuchMixin", e.getMessage());
        }
    }

    @Test
    public void testMixinAddRemove() {
        DocumentModel doc = session.createDocumentModel("/", "foo", SYS_FILE);
        doc.create();
        session.save();

        // mixin not there
        try {
            doc.getPropertyValue(AGE_AGE);
            fail();
        } catch (PropertyNotFoundException e) {
            // ok
        }

        // add
        doc.addMixin(AGED);
        doc.setPropertyValue(AGE_AGE, "123");
        session.save();

        // remove
        doc.setMixins(Set.of());
        session.save();

        // mixin not there anymore
        try {
            doc.getPropertyValue(AGE_AGE);
            fail();
        } catch (PropertyNotFoundException e) {
            // ok
        }
    }

    // mixin on doc with same schema in primary type does no harm
    @Test
    public void testMixinAddRemove2() {
        DocumentModel doc = session.createDocumentModel("/", "foo", "DocWithAge");
        doc.setPropertyValue(AGE_AGE, "123");
        doc.create();
        session.save();

        doc.addMixin(AGED);
        assertEquals("123", doc.getPropertyValue(AGE_AGE));

        doc.setMixins(Set.of());
        assertEquals("123", doc.getPropertyValue(AGE_AGE));
    }

    @Test
    public void testMixinAddRemoveWhenSchemaAlreadyPresent() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "foo", SYS_FILE);
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("hello"));
        doc.create();
        session.save();

        doc = session.getDocument(doc.getRef());
        Blob blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals("hello", blob.getString());

        // add WithFile mixin, where file schema is already present in doc
        doc.addMixin("WithFile");
        session.save();
        reopenSession();

        // check getting the blob works (complex child "sysfile_blob" not re-added)
        doc = session.getDocument(doc.getRef());
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertNotNull(blob);
        assertEquals("hello", blob.getString());

        // now remove mixin
        doc.setMixins(Set.of());
        session.save();
        reopenSession();

        // schema still present because of primary type
        doc = session.getDocument(doc.getRef());
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertNotNull(blob);
        assertEquals("hello", blob.getString());

        /*
         * two mixins with the same schema
         */

        doc = session.createDocumentModel("/", "bar", SYS_FOLDER);
        doc.setMixins(Set.of("WithFile2"));
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("hello"));
        doc.create();
        session.save();

        doc = session.getDocument(doc.getRef());
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertEquals("hello", blob.getString());

        // add WithFile mixin, where file schema is already present in doc
        doc.setMixins(Set.of("WithFile", "WithFile2"));
        session.save();
        reopenSession();

        // check getting the blob works (complex child "sysfile_blob" not re-added)
        doc = session.getDocument(doc.getRef());
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertNotNull(blob);
        assertEquals("hello", blob.getString());

        // now remove mixin, but other mixin WithFile2 is still present
        doc.setMixins(Set.of("WithFile2"));
        session.save();
        reopenSession();

        // schema still present because of other mixin
        doc = session.getDocument(doc.getRef());
        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertNotNull(blob);
        assertEquals("hello", blob.getString());
    }

    @Test
    public void testMixinWithSamePropertyName() {
        DocumentModel doc = session.createDocumentModel("/", "foo", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "bar");
        doc.create();
        session.save();

        doc.addMixin(AGED);
        doc.setPropertyValue("age_title", "gee");
        session.save();

        assertEquals("bar", doc.getPropertyValue(SYS_TITLE));
        assertEquals("gee", doc.getPropertyValue("age_title"));

        // refetch
        doc = session.getDocument(doc.getRef());
        assertEquals("bar", doc.getPropertyValue(SYS_TITLE));
        assertEquals("gee", doc.getPropertyValue("age_title"));
    }

    @Test
    public void testMixinCopy() {
        DocumentModel doc = session.createDocumentModel("/", "foo", SYS_FILE);
        doc.addMixin(AGED);
        doc.setPropertyValue(AGE_AGE, "123");
        doc.create();
        session.save();

        // copy the doc
        DocumentModel copy = session.copy(doc.getRef(), session.getRootDocument().getRef(), "bar");
        assertTrue(copy.hasMixin(AGED));
        assertEquals("123", copy.getPropertyValue(AGE_AGE));
    }

    @Test
    public void testMixinQueryContent() {
        DocumentModel doc = session.createDocumentModel("/", "foo", SYS_FILE);
        doc.addMixin(AGED);
        doc.setPropertyValue(AGE_AGE, "barbar");
        doc.create();
        session.save();

        DocumentModelList list = session.query("SELECT * FROM SysFile WHERE age_age = 'barbar'");
        assertEquals(1, list.size());
    }

    @Test
    public void testCopy() throws Exception {
        DocumentModel root = session.getRootDocument();
        DocumentModel folder1 = session.createDocumentModel(root.getPathAsString(), "folder1", SYS_FOLDER);
        folder1.create();
        DocumentModel folder2 = session.createDocumentModel(root.getPathAsString(), "folder2", SYS_FOLDER);
        folder2.create();
        DocumentModel file = session.createDocumentModel(folder1.getPathAsString(), "file", SYS_FILE);
        file.create();
        session.save();

        assertTrue(session.exists(new PathRef("/folder1/file")));
        assertFalse(session.exists(new PathRef("/folder2/file")));

        // copy using orig name
        DocumentModel copy1 = session.copy(file.getRef(), folder2.getRef(), null);

        assertTrue(session.exists(new PathRef("/folder1/file")));
        assertTrue(session.exists(new PathRef("/folder2/file")));
        assertFalse(session.exists(new PathRef("/folder2/fileCopy")));
        assertTrue(session.getChildren(folder2.getRef()).contains(copy1));

        // copy using another name
        DocumentModel copy2 = session.copy(file.getRef(), folder2.getRef(), "fileCopy");

        assertTrue(session.exists(new PathRef("/folder1/file")));
        assertTrue(session.exists(new PathRef("/folder2/file")));
        assertTrue(session.exists(new PathRef("/folder2/fileCopy")));
        assertTrue(session.getChildren(folder2.getRef()).contains(copy2));

        // copy again to same space
        DocumentModel copy3 = session.copy(file.getRef(), folder2.getRef(), null);

        assertTrue(session.exists(new PathRef("/folder1/file")));
        assertTrue(session.exists(new PathRef("/folder2/file")));
        assertTrue(session.getChildren(folder2.getRef()).contains(copy3));
        assertNotSame(copy1.getName(), copy3.getName());

        // copy again again to same space
        if (SystemUtils.IS_OS_WINDOWS) {
            // windows has too coarse time granularity for SQLSession.findFreeName
            Thread.sleep(1000);
        }
        DocumentModel copy4 = session.copy(file.getRef(), folder2.getRef(), null);

        assertTrue(session.exists(new PathRef("/folder1/file")));
        assertTrue(session.exists(new PathRef("/folder2/file")));
        assertTrue(session.getChildren(folder2.getRef()).contains(copy4));
        assertNotSame(copy1.getName(), copy4.getName());
        assertNotSame(copy3.getName(), copy4.getName());

        // copy inplace
        DocumentModel copy5 = session.copy(file.getRef(), folder1.getRef(), null);

        assertTrue(session.exists(new PathRef("/folder1/file")));
        assertTrue(session.exists(new PathRef("/folder2/file")));
        assertTrue(session.getChildren(folder1.getRef()).contains(copy5));
        assertNotSame(copy1.getName(), copy5.getName());

        session.cancel();
    }

    @Test
    public void testCopyNameAndTitle() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        String name = "file";
        DocumentModel file = session.createDocumentModel("/", name, SYS_FILE);
        // file has no title for now
        file.create();

        // copy file with no title and no requested name
        DocumentModel copy1 = session.copy(file.getRef(), folder.getRef(), null);
        assertEquals(name, copy1.getName());
        assertEquals(name, copy1.getPropertyValue(SYS_TITLE));
        session.removeDocument(copy1.getRef());

        // copy file with no title and specified name
        String newname = "newname";
        DocumentModel copy2 = session.copy(file.getRef(), folder.getRef(), newname);
        assertEquals(newname, copy2.getName());
        assertEquals(name, copy2.getPropertyValue(SYS_TITLE));
        session.removeDocument(copy2.getRef());

        // now give the file a title
        String title = "My File";
        file.setPropertyValue(SYS_TITLE, title);

        // copy file with title and no requested name
        DocumentModel copy3 = session.copy(file.getRef(), folder.getRef(), null);
        assertEquals(name, copy3.getName());
        assertEquals(title, copy3.getPropertyValue(SYS_TITLE));
        session.removeDocument(copy3.getRef());

        // copy file with title and specified name
        DocumentModel copy4 = session.copy(file.getRef(), folder.getRef(), newname);
        assertEquals(newname, copy4.getName());
        assertEquals(title, copy4.getPropertyValue(SYS_TITLE));
        session.removeDocument(copy4.getRef());

        // copy file with name collision (file is already itself in /)
        DocumentModel copy5 = session.copy(file.getRef(), new PathRef("/"), name);
        String copy5Name = copy5.getName();
        assertNotEquals(name, copy5Name);
        assertTrue(copy5Name.startsWith(name + "."), copy5Name);
        assertEquals(title, copy5.getPropertyValue(SYS_TITLE));
    }

    @Test
    public void testCopySanitizesName() {
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.create();

        // copy file with name that has to be sanitized
        DocumentModel copy1 = session.copy(file.getRef(), new PathRef("/"), " foo\\bar ");
        assertEquals("foo-bar", copy1.getName());
    }

    @Test
    public void testCopyVersionable() {
        DocumentModel note = session.createDocumentModel("/", "note", "Note");
        note.create();
        note.checkIn();
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        session.save();

        assertTrue(session.exists(new PathRef("/note")));
        assertTrue(session.exists(new PathRef("/folder")));

        // only one version at first
        List<DocumentModel> versions = getVersions(note);
        assertEquals(1, versions.size());

        // version the note
        note.setPropertyValue(SYS_TITLE, "blah");
        note.checkIn();
        session.save();

        // check versions
        versions = getVersions(note);
        assertEquals(2, versions.size());

        // copy
        DocumentModel copy = session.copy(note.getRef(), folder.getRef(), null);

        // check no versions on copy
        versions = getVersions(copy);
        assertEquals(0, versions.size());

        session.cancel();
    }

    @Test
    public void testCopyFolderOfVersionable() {
        DocumentModel root = session.getRootDocument();
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel note = session.createDocumentModel("/folder", "note", "Note");
        note.create();
        note.checkIn();
        session.save();

        assertTrue(session.exists(new PathRef("/folder")));
        assertTrue(session.exists(new PathRef("/folder/note")));

        // only one version at first
        List<DocumentModel> versions = getVersions(note);
        assertEquals(1, versions.size());

        // version the note
        note.setPropertyValue(SYS_TITLE, "blah");
        note.checkIn();
        session.save();

        // check versions
        versions = getVersions(note);
        assertEquals(2, versions.size());

        // copy folder, use an all-digit name to test for xpath escaping
        DocumentModel copy = session.copy(folder.getRef(), root.getRef(), "123");

        // check no versions on copied note
        DocumentModel note2 = session.getChild(copy.getRef(), "note");
        versions = getVersions(note2);
        assertEquals(0, versions.size());

        session.cancel();
    }

    @Test
    public void testMove() {
        DocumentModel root = session.getRootDocument();
        DocumentModel folder1 = session.createDocumentModel(root.getPathAsString(), "folder1", SYS_FOLDER);
        folder1.create();
        DocumentModel folder2 = session.createDocumentModel(root.getPathAsString(), "folder2", SYS_FOLDER);
        folder2.create();
        DocumentModel file = session.createDocumentModel(folder1.getPathAsString(), "file", SYS_FILE);
        file.create();

        assertTrue(session.exists(new PathRef("/folder1/file")));
        assertFalse(session.exists(new PathRef("/folder2/file")));
        assertFalse(session.exists(new PathRef("/folder1/fileMove")));

        // move using orig name
        session.move(file.getRef(), folder2.getRef(), null);

        assertFalse(session.exists(new PathRef("/folder1/file")));
        assertTrue(session.exists(new PathRef("/folder2/file")));

        file = session.getChild(folder2.getRef(), "file");
        session.move(file.getRef(), folder1.getRef(), "fileMove");

        assertTrue(session.exists(new PathRef("/folder1/fileMove")));

        DocumentModel file2 = session.createDocumentModel(folder2.getPathAsString(), "file2", SYS_FILE);
        file2.create();
        assertTrue(session.exists(new PathRef("/folder2/file2")));
        DocumentModel newFile2 = session.move(file.getRef(), folder2.getRef(), "file2"); // collision
        String newName = newFile2.getName();
        assertNotEquals("file2", newName);
        assertTrue(session.exists(new PathRef("/folder2/file2")));
        assertTrue(session.exists(new PathRef("/folder2/" + newName)));

        // move with null dest (rename)
        DocumentModel newFile3 = session.move(file.getRef(), null, "file3");
        assertEquals("file3", newFile3.getName());
    }

    @Test
    public void testMoveSanitizesName() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.create();

        // move file with name that has to be sanitized
        DocumentModel move1 = session.move(file.getRef(), folder.getRef(), " foo\\bar ");
        assertEquals("foo-bar", move1.getName());
    }

    @Test
    public void testMoveConcurrentWithGetChild() throws Exception {
        prepareDocsForMoveConcurrentWithGetChildren();
        try {
            session.getChild(new PathRef("/folder"), "doc");
            fail("should not find child moved under /folder in another transaction");
        } catch (DocumentNotFoundException e) {
            assertEquals("doc", e.getMessage());
        }
    }

    @Test
    public void testMoveConcurrentWithGetChildren() throws Exception {
        prepareDocsForMoveConcurrentWithGetChildren();
        // should not find child moved under /folder in another transaction
        DocumentModelList children = session.getChildren(new PathRef("/folder"));
        assertEquals(0, children.size());
    }

    @Test
    public void testMoveConcurrentWithGetChildrenRefs() throws Exception {
        prepareDocsForMoveConcurrentWithGetChildren();
        // should not find child moved under /folder in another transaction
        List<DocumentModel> children = session.getChildren(new PathRef("/folder"));
        assertEquals(0, children.size());
    }

    @Test
    public void testNotAllowedToMoveVersions() {
        DocumentModel root = session.getRootDocument();
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();

        DocumentModel file = session.createDocumentModel("/folder", "file", SYS_FILE);
        file.addMixin(SYS_VERSIONABLE);
        file.create();
        var verRef = file.checkIn();

        session.save();

        assertTrue(session.exists(new PathRef("/folder")));
        assertTrue(session.exists(new PathRef("/folder/file")));

        // move the version
        try {
            session.move(verRef, root.getRef(), null);
            fail("Should not be allowed to move a version");
        } catch (RepositoryException e) {
            assertTrue(e.getMessage().startsWith("Cannot move a version: "));
        }
    }


    protected void prepareDocsForMoveConcurrentWithGetChildren() throws Exception {
        // create folder
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        // create doc outside of folder
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();
        nextTransaction();
        // load doc as DBS transient
        session.getDocument(doc.getRef());

        // in other thread, move doc into folder
        MutableObject<RuntimeException> me = new MutableObject<>();
        Thread thread = new Thread(() -> {
            TransactionHelper.runInTransaction(() -> {
                try {
                    CoreSession session2 = CoreInstance.getCoreSession(coreFeature.getRepositoryName());
                    session2.move(new PathRef("/doc"), new PathRef("/folder"), null);
                    session2.save();
                } catch (RuntimeException e) {
                    me.setValue(e);
                }
            });
        });
        thread.start();
        thread.join();
        if (me.getValue() != null) {
            throw new RuntimeException(me.getValue());
        }
    }

    // TODO NXP-2514: fix this test
    @Test
    @Disabled
    public void testScalarList() {
        DocumentModel root = session.getRootDocument();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        String[] str = { "a", "b", "c" };
        childFile.setPropertyValue("dc_participants", str);

        childFile = session.getChild(root.getRef(), childFile.getName());

        str = (String[]) childFile.getPropertyValue("dc_participants");

        assertNotNull(str);
        List<String> list = Arrays.asList(str);
        assertTrue(list.contains("a"));
        assertTrue(list.contains("b"));
        assertTrue(list.contains("c"));

        // modify the array

        str = new String[] { "a", "b" };
        childFile.setPropertyValue("dc_participants", str);

        str = (String[]) childFile.getPropertyValue("dc_participants");

        childFile = session.getChild(root.getRef(), childFile.getName());
        str = (String[]) childFile.getPropertyValue("dc_participants");

        assertNotNull(str);
        list = Arrays.asList(str);
        assertTrue(list.contains("a"));
        assertTrue(list.contains("b"));
    }

    @Test
    public void testBlob() throws Exception {
        DocumentModel root = session.getRootDocument();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();

        session.save();

        byte[] bytes = "hello".getBytes(UTF_8);
        Blob blob = Blobs.createBlob(bytes, "java/class", "UTF8");
        blob.setFilename("blob.txt");
        blob.setDigest("XYZ-123"); // include "-" to denote temporary digest
        long length = blob.getLength();
        byte[] content = blob.getByteArray();

        childFile.setPropertyValue(SYS_TITLE, "Blob test");
        childFile.setPropertyValue(SYS_DESCRIPTION, "this is a test");
        childFile.setPropertyValue(SYSFILE_BLOB, blob);

        childFile = session.getDocument(childFile.getRef());
        blob = (Blob) childFile.getPropertyValue(SYSFILE_BLOB);

        // digest and digest algorithm have been fixed up on save
        assertEquals("SHA-256", blob.getDigestAlgorithm());
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", blob.getDigest());
        assertEquals("blob.txt", blob.getFilename());
        assertEquals(length, blob.getLength());
        assertEquals("UTF8", blob.getEncoding());
        assertEquals("java/class", blob.getMimeType());
        assertTrue(Arrays.equals(content, blob.getByteArray()));
    }

    @Test
    public void testGetRepositoryName() {
        DocumentModel root = session.getRootDocument();

        String name2 = "file#" + generateUnique();
        DocumentModel childFile = session.createDocumentModel(root.getPathAsString(), name2, SYS_FILE);
        childFile.create();
        assertNotNull(childFile.getRepositoryName());
        assertEquals("test", childFile.getRepositoryName());
    }

    @Test
    public void testCreateDocumentModel() {
        // first method: only the typename
        DocumentModel docModel = session.createDocumentModel(null, null, SYS_FILE);
        assertEquals(SYS_FILE, docModel.getType());

        // bad type should fail
        try {
            session.createDocumentModel(null, null, "NotAValidTypeName");
            fail();
        } catch (IllegalArgumentException e) {
        }

        // same as previously with path info
        docModel = session.createDocumentModel("/", "some-id", SYS_FILE);
        assertEquals(SYS_FILE, docModel.getType());
        assertEquals("/some-id", docModel.getPathAsString());
    }

    @Test
    public void testOrdering() {
        DocumentModel root = session.getRootDocument();
        DocumentModel parent = session.createDocumentModel(root.getPathAsString(), "theParent", SYS_ORDERED_FOLDER);

        parent.create();

        DocumentModel doc1 = session.createDocumentModel(parent.getPathAsString(), "the1", SYS_FILE);
        doc1.create();
        DocumentModel doc2 = session.createDocumentModel(parent.getPathAsString(), "the2", SYS_FILE);
        doc2.create();
        session.save(); // XXX

        String name1 = doc1.getName();
        String name2 = doc2.getName();

        DocumentModelList children = session.getChildren(parent.getRef());
        assertEquals(2, children.size());
        assertEquals(name1, children.get(0).getName());
        assertEquals(name2, children.get(1).getName());
        assertEquals(Long.valueOf(0), children.get(0).getPos());
        assertEquals(Long.valueOf(1), children.get(1).getPos());

        session.orderBefore(parent.getRef(), name2, name1);

        children = session.getChildren(parent.getRef());
        assertEquals(2, children.size());
        assertEquals(name2, children.get(0).getName());
        assertEquals(name1, children.get(1).getName());
        assertEquals(Long.valueOf(0), children.get(0).getPos());
        assertEquals(Long.valueOf(1), children.get(1).getPos());

        session.orderBefore(parent.getRef(), name2, null);

        children = session.getChildren(parent.getRef());
        assertEquals(2, children.size());
        assertEquals(name1, children.get(0).getName());
        assertEquals(name2, children.get(1).getName());
        assertEquals(Long.valueOf(0), children.get(0).getPos());
        assertEquals(Long.valueOf(1), children.get(1).getPos());

        // check in a non-ordered folder
        DocumentModel parent2 = session.createDocumentModel("/", "folder", SYS_FOLDER);
        parent2.create();
        DocumentModel doc3 = session.createDocumentModel("/folder", "doc3", "MyDocType");
        doc3.create();
        session.save();
        doc3 = session.getDocument(doc3.getRef());
        assertNull(doc3.getPos());
    }

    @Test
    public void testOrderingAfterCopy() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_ORDERED_FOLDER);
        folder.create();
        DocumentModel doc1 = session.createDocumentModel("/folder", "doc1", SYS_FILE);
        doc1.create();
        DocumentModel doc2 = session.createDocumentModel("/folder", "doc2", SYS_FILE);
        doc2.create();

        // copy doc1 as doc3, should be positioned last
        session.copy(doc1.getRef(), folder.getRef(), "doc3");

        DocumentModelList children = session.getChildren(folder.getRef());
        assertEquals(3, children.size());
        assertEquals("doc1", children.get(0).getName());
        assertEquals("doc2", children.get(1).getName());
        assertEquals("doc3", children.get(2).getName());
    }

    @Test
    public void testPropertyXPath() {
        DocumentModel root = session.getRootDocument();
        DocumentModel parent = session.createDocumentModel(root.getPathAsString(), "theParent", SYS_ORDERED_FOLDER);

        parent.create();

        DocumentModel doc = session.createDocumentModel(parent.getPathAsString(), "theDoc", SYS_FILE);

        doc.setPropertyValue(SYS_TITLE, "my title");
        assertEquals("my title", doc.getPropertyValue(SYS_TITLE));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testComplexList() {
        DocumentModel root = session.getRootDocument();
        DocumentModel doc = session.createDocumentModel(root.getPathAsString(), "mydoc", "MyDocType");

        doc.create();

        List list = (List) doc.getPropertyValue("testList_attachments");
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    public static byte[] createBytes(int size, byte val) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, val);
        return bytes;
    }

    @Test
    public void testBlob2() throws Exception {
        DocumentModel root = session.getRootDocument();
        DocumentModel doc = session.createDocumentModel(root.getPathAsString(), "mydoc", SYS_FILE);

        doc.create();
        byte[] bytes = createBytes(1024 * 1024, (byte) 24);

        Blob blob = Blobs.createBlob(bytes);
        doc.setPropertyValue(SYSFILE_BLOB, blob);

        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertTrue(Arrays.equals(bytes, blob.getByteArray()));

        // reset not implemented (not needed) for StorageBlob's Binary
        // XXX blob.getStream().reset();

        blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertTrue(Arrays.equals(bytes, blob.getByteArray()));
    }

    @Test
    public void testVersionWriteDublinCoreDefault() {
        doTestVersionWriteDublinCore(false);
    }

    protected void doTestVersionWriteDublinCore(boolean expectWritable) {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        DocumentRef verRef = session.checkIn(doc.getRef());
        DocumentModel ver = session.getDocument(verRef);
        try {
            ver.setPropertyValue(SYS_TITLE, "ver title");
            if (!expectWritable && ver.getPropertyValue(SYS_TITLE).equals("ver title")) {
                fail("should fail to save");
            }
        } catch (PropertyException e) {
            if (expectWritable) {
                throw e;
            } else {
                assertEquals("Cannot set property on a version: sys_title", e.getMessage(), e.getMessage());
            }
        }
    }

    // we must be careful as documentModified events from a single save (same time) are sent in random order
    public static void assertEvents(CapturingEventListener listener, String... expectedEventNames) {
        List<String> actualEventNames = new ArrayList<>();
        long accTime = 0;
        List<String> accNames = new ArrayList<>();
        for (Event event : listener.getCapturedEvents()) {
            String name = event.getName();
            long time = event.getTime();
            String detailedName = getDetailedEventName(event);
            // should we flush acc?
            if ((name.equals(DOCUMENT_UPDATED) && accTime != 0 && accTime != time) //
                    || !name.equals(DOCUMENT_UPDATED)) {
                // flush acc
                if (!accNames.isEmpty()) {
                    addSorted(actualEventNames, accNames);
                    accTime = 0;
                    accNames.clear();
                }
            }
            if (name.equals(DOCUMENT_UPDATED)) {
                // accumulate
                accTime = time;
                accNames.add(detailedName);
            } else {
                actualEventNames.add(detailedName);
            }
        }
        // flush acc
        if (!accNames.isEmpty()) {
            addSorted(actualEventNames, accNames);
        }
        assertEquals(Arrays.asList(expectedEventNames), actualEventNames);
    }

    private static void addSorted(List<String> actualEventNames, List<String> accNames) {
        accNames.sort(null); // sort all consecutive documentModified events with same time
        actualEventNames.addAll(accNames);
    }

    public static String getDetailedEventName(Event event) {
        String name = event.getName();
        if (event.getContext() instanceof DocumentEventContext) {
            DocumentModel doc = ((DocumentEventContext) event.getContext()).getSourceDocument();
            if (doc != null) {
                if (doc.isVersion()) {
                    name += "/v";
                } else if (doc.isFolder()) {
                    name += "/f";
                }
            }
        }
        return name;
    }

    @Test
    public void testDocumentCreatedEvent() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.setPropertyValue(SYS_TITLE, "My Title");
        doc.create();

        try (CapturingEventListener listener = new CapturingEventListener("documentCreated")) {
            session.save();
            List<Event> events = listener.getCapturedEvents();
            Assertions.assertEquals(1, events.size());
            Event docCreatedEvent = events.getFirst();
            Assertions.assertInstanceOf(DocumentEventContext.class, docCreatedEvent.getContext());
            DocumentEventContext docCreatedContext = (DocumentEventContext) docCreatedEvent.getContext();
            Assertions.assertEquals(doc.getRef(), docCreatedContext.getSourceDocument().getRef());
            Assertions.assertEquals(
                    List.of("sys_ancestorIds", "sys_ancestorIds/*", "sys_contributors", "sys_contributors/*",
                            "sys_created", "sys_creator", "sys_gen", "sys_id", "sys_lastContributor", "sys_modified", "sys_name",
                            "sys_parentId", "sys_primaryType", "sys_title", "sys_ugen"),
                    docCreatedContext.getProperty(DIRTY_XPATHS));
        }
    }

    @Test
    public void testDocumentCreatedEventWithBlob() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("some blob"));
        doc.create();

        try (CapturingEventListener listener = new CapturingEventListener("documentCreated")) {
            session.save();
            List<Event> events = listener.getCapturedEvents();
            Assertions.assertEquals(1, events.size());
            Event docCreatedEvent = events.getFirst();
            Assertions.assertInstanceOf(DocumentEventContext.class, docCreatedEvent.getContext());
            DocumentEventContext docCreatedContext = (DocumentEventContext) docCreatedEvent.getContext();
            Assertions.assertEquals(doc.getRef(), docCreatedContext.getSourceDocument().getRef());
            Assertions.assertEquals(List.of("sys_ancestorIds", "sys_ancestorIds/*", "sys_blobKeys", "sys_blobKeys/*",
                    "sys_contributors", "sys_contributors/*", "sys_created", "sys_creator", "sys_gen", "sys_id",
                    "sys_lastContributor", "sys_modified", "sys_name", "sys_parentId", "sys_primaryType",
                    "sys_title", "sys_ugen", "sysfile_blob", "sysfile_blob/digest",
                    "sysfile_blob/encoding", "sysfile_blob/key", "sysfile_blob/length", "sysfile_blob/mimeType"),
                    docCreatedContext.getProperty(DIRTY_XPATHS));
        }
    }

    @Test
    public void testDocumentCreatedEventWithComplexAndList() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "ComplexDoc");
        doc.setPropertyValue("cmpf_attachedFile", Map.of("name", "someVignette", "vignettes",
                List.of(Map.of("content", Blobs.createBlob("some vignette content")))));
        doc.create();

        try (CapturingEventListener listener = new CapturingEventListener("documentCreated")) {
            session.save();
            List<Event> events = listener.getCapturedEvents();
            Assertions.assertEquals(1, events.size());
            Event docCreatedEvent = events.getFirst();
            Assertions.assertInstanceOf(DocumentEventContext.class, docCreatedEvent.getContext());
            DocumentEventContext docCreatedContext = (DocumentEventContext) docCreatedEvent.getContext();
            Assertions.assertEquals(doc.getRef(), docCreatedContext.getSourceDocument().getRef());
            Assertions.assertEquals(List.of("cmpf_attachedFile", "cmpf_attachedFile/name",
                    "cmpf_attachedFile/vignettes/*/content/digest", "cmpf_attachedFile/vignettes/*/content/encoding",
                    "cmpf_attachedFile/vignettes/*/content/key", "cmpf_attachedFile/vignettes/*/content/length",
                    "cmpf_attachedFile/vignettes/*/content/mimeType", "sys_ancestorIds", "sys_ancestorIds/*",
                    "sys_blobKeys", "sys_blobKeys/*", "sys_contributors", "sys_contributors/*", "sys_created",
                    "sys_creator", "sys_gen", "sys_id", "sys_lastContributor", "sys_modified", "sys_name", "sys_parentId",
                    "sys_primaryType", "sys_title", "sys_ugen"),
                    docCreatedContext.getProperty(DIRTY_XPATHS));
        }
    }

    @Test
    public void testDocumentModifiedEvent() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        session.save();

        try (CapturingEventListener listener = new CapturingEventListener("documentModified")) {
            doc.setPropertyValue(SYS_TITLE, "title2");
            session.save();
            List<Event> events = listener.getCapturedEvents();
            Assertions.assertEquals(1, events.size());
            Event docModifiedEvent = events.getFirst();
            Assertions.assertInstanceOf(DocumentEventContext.class, docModifiedEvent.getContext());
            DocumentEventContext docModifiedContext = (DocumentEventContext) docModifiedEvent.getContext();
            Assertions.assertEquals(doc.getRef(), docModifiedContext.getSourceDocument().getRef());
            Assertions.assertEquals(List.of(SYS_MODIFIED, SYS_TITLE), docModifiedContext.getProperty(DIRTY_XPATHS));
        }
    }

    @Test
    public void testDocumentModifiedEventWithBlob() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        session.save();

        try (CapturingEventListener listener = new CapturingEventListener(DOCUMENT_UPDATED)) {
            doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("some blob"));
            session.save();
            Event docModifiedEvent = listener.getCapturedEvents().getFirst();
            DocumentEventContext docModifiedContext = (DocumentEventContext) docModifiedEvent.getContext();
            Assertions.assertEquals(List.of("sys_blobKeys", "sys_blobKeys/*", "sys_modified", "sysfile_blob",
                    "sysfile_blob/digest", "sysfile_blob/encoding", "sysfile_blob/key", "sysfile_blob/length",
                    "sysfile_blob/mimeType"), docModifiedContext.getProperty(DIRTY_XPATHS));
        }
    }

    @Test
    public void testDocumentModifiedEventWithComplexAndList() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "ComplexDoc");
        doc.create();
        session.save();

        try (CapturingEventListener listener = new CapturingEventListener(DOCUMENT_UPDATED)) {
            doc.setPropertyValue("cmpf_attachedFile", Map.of("name", "someVignette", "vignettes",
                    List.of(Map.of("content", Blobs.createBlob("some vignette content")))));
            session.save();
            Event docModifiedEvent = listener.getCapturedEvents().getFirst();
            DocumentEventContext docModifiedContext = (DocumentEventContext) docModifiedEvent.getContext();
            Assertions.assertEquals(List.of("cmpf_attachedFile", "cmpf_attachedFile/name",
                    "cmpf_attachedFile/vignettes/*/content/digest", "cmpf_attachedFile/vignettes/*/content/encoding",
                    "cmpf_attachedFile/vignettes/*/content/key", "cmpf_attachedFile/vignettes/*/content/length",
                    "cmpf_attachedFile/vignettes/*/content/mimeType", "sys_blobKeys", "sys_blobKeys/*", "sys_modified"),
                    docModifiedContext.getProperty(DIRTY_XPATHS));
        }
    }

    @Test
    public void testDocumentBlobModifiedEvent() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("some blob"));
        doc.create();
        session.save();
        doc.refresh();

        try (CapturingEventListener listener = new CapturingEventListener(DOCUMENT_BLOB_MODIFIED)) {
            doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("some other blob"));
            session.save();
            List<Event> events = listener.getCapturedEvents();
            Assertions.assertEquals(1, events.size());
            Event docBlobModifiedEvent = events.getFirst();
            Assertions.assertInstanceOf(DocumentEventContext.class, docBlobModifiedEvent.getContext());
            DocumentEventContext docBlobModifiedContext = (DocumentEventContext) docBlobModifiedEvent.getContext();
            Assertions.assertEquals(List.of(SYSFILE_BLOB), docBlobModifiedContext.getProperty(DIRTY_BLOB_XPATHS));
        }
    }

    @Test
    public void testDocumentBlobModifiedEventWithComplexAndList() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "ComplexDoc");
        doc.create();
        session.save();

        String cmpfAttachedFileXpath = "cmpf_attachedFile";
        try (CapturingEventListener listener = new CapturingEventListener(DOCUMENT_BLOB_MODIFIED)) {
            doc.setPropertyValue("cmpf_attachedFile", Map.of("name", "someVignette", "vignettes",
                    List.of(Map.of("content", Blobs.createBlob("some vignette content")))));
            session.save();
            Event docBlobModifiedEvent = listener.getCapturedEvents().getFirst();
            DocumentEventContext docModifiedContext = (DocumentEventContext) docBlobModifiedEvent.getContext();
            String vignetteBlobXpath = cmpfAttachedFileXpath + "/vignettes/0/content";
            Assertions.assertEquals(List.of(vignetteBlobXpath), docModifiedContext.getProperty(DIRTY_BLOB_XPATHS));
        }
    }

    @Test
    public void testVersioningEvents() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        DocumentModel folder = session.createDocumentModel("/", "fold", SYS_FOLDER);
        folder.create();
        session.save();

        try (CapturingEventListener listener = new CapturingEventListener()) {
            DocumentRef verRef = session.checkIn(doc.getRef());
            session.save();
            assertEvents(listener, //
                    "aboutToCheckIn", //
                    "documentModified", //
                    "documentCreated/v", //
                    "documentModified", //
                    "documentCheckedIn");

            listener.clear();
            session.checkOut(doc.getRef());
            session.save();
            assertEvents(listener, //
                    "documentCheckedOut", //
                    "documentModified");

            listener.clear();
            doc.checkIn();
            assertEvents(listener, //
                    "aboutToCheckIn", //
                    "documentModified", //
                    "documentCreated/v", //
                    "documentModified", //
                    "documentCheckedIn");

            // auto-checkout
            listener.clear();
            doc.setPropertyValue(SYS_TITLE, "title2");
            session.save();
            assertEvents(listener, //
                    "documentCheckedOut", //
                    "documentModified");

            // save with versioning
            listener.clear();
            doc.setPropertyValue(SYS_TITLE, "title2");
            doc.checkIn();
            session.save();
            assertEvents(listener, //
                    "aboutToCheckIn", //
                    "documentModified", //
                    "documentCreated/v", //
                    "documentModified", //
                    "documentCheckedIn");

            listener.clear();
            doc.checkOut();
            assertEvents(listener, //
                    "documentCheckedOut");

            // restore to version
            listener.clear();
            session.restoreVersion(verRef);
            session.save();
            assertEvents(listener, //
                    "documentRestored", //
                    "documentModified");
        }
    }

    @Test
    public void testPlacelessDocument() {
        DocumentModel doc = session.createDocumentModel(null, "mydoc", "MyDocType");
        doc.setPropertyValue(SYS_TITLE, "The title");
        doc.create();
        assertNull(doc.getParentRef()); // placeless
        session.save();

        DocumentModel doc2 = session.createDocumentModel(null, "other", "MyDocType");
        doc2.setPropertyValue(SYS_TITLE, "Other");
        doc2.create();
        assertNull(doc2.getParentRef()); // placeless
        session.save();

        // ----- new session -----
        reopenSession();
        doc = session.getDocument(new IdRef(doc.getId()));
        assertNull(doc.getParentRef());

        assertEquals("The title", doc.getPropertyValue(SYS_TITLE));
        assertNull(doc.getPropertyValue(SYS_DESCRIPTION));

        doc2 = session.getDocument(new IdRef(doc2.getId()));
        assertNull(doc2.getParentRef());

        // remove
        session.removeDocument(doc.getRef());
        session.save();
    }

    /*
     * NXP-28727
     */
    @Test
    public void testCreateDocumentUnderPlacelessDoc() {
        DocumentModel parent = session.newDocumentModel(null, "parent", SYS_FILE);
        parent.create();
        assertNull(parent.getParentRef());
        session.save();

        DocumentModel child = session.newDocumentModel(parent.getRef(), "child", SYS_FILE);
        child.create();
        session.save();

        reopenSession();

        // the parent file must be a placeless
        parent = session.getDocument(parent.getRef());
        assertNull(parent.getParentRef());

        // the child file mustn't be a placeless
        child = session.getDocument(child.getRef());
        assertEquals(parent.getRef(), child.getParentRef());

        // remove the parent
        session.removeDocument(parent.getRef());
        transactionalFeature.nextTransaction();

        assertFalse(session.exists(parent.getRef()));
        assertFalse(session.exists(child.getRef()));
    }

    @Test
    public void testRelation() {
        DocumentModel rel = session.createDocumentModel(null, "myrel", "SysRelation");
        rel.setPropertyValue("sysrelation_source", "1234");
        rel.setPropertyValue(SYS_TITLE, "My Rel");
        rel.create();
        assertNull(rel.getParentRef()); // placeless
        session.save();

        // query
        String query = "SELECT * FROM SysRelation WHERE sysrelation_source = '1234'";
        DocumentModelList list = session.query(query);
        assertEquals(1, list.size());

        DocumentModel doc = list.get(0);
        assertNull(doc.getParentRef());
        assertEquals("My Rel", doc.getPropertyValue(SYS_TITLE));

        waitForAsyncCompletion(); // before remove
        // remove
        session.removeDocument(rel.getRef());
        session.save();
        list = session.query(query);
        assertEquals(0, list.size());
    }

    @Test
    public void testObsoleteType() throws Exception {
        DocumentRef rootRef = session.getRootDocument().getRef();
        DocumentModel doc = session.createDocumentModel("/", "doc", "MyDocType");
        doc.create();
        DocumentRef docRef = new IdRef(doc.getId());
        session.save();
        assertEquals(1, session.getChildren(rootRef).size());
        assertNotNull(session.getDocument(docRef));
        assertNotNull(session.getChild(rootRef, "doc"));

        // remove MyDocType from known types
        var documentTypes = ((SchemaManagerImpl) this.schemaManager).documentTypes;
        DocumentTypeImpl dt = documentTypes.remove("MyDocType");
        try {
            // DBS does not do obsolete type check on hasChildren() due to cost
            // assertFalse(session.hasChildren(rootRef))
            assertEquals(0, session.getChildren(rootRef).size());
            try {
                session.getDocument(docRef);
                fail("shouldn't be able to get doc with obsolete type");
            } catch (DocumentNotFoundException e) {
                assertTrue(e.getMessage().contains("Unknown document type: MyDocType"), e.getMessage());
            }
            try {
                session.getChild(rootRef, "doc");
                fail("shouldn't be able to get doc with obsolete type");
            } catch (DocumentNotFoundException e) {
                assertTrue(e.getMessage().contains("Unknown document type: MyDocType"), e.getMessage());
            }
        } finally {
            // put doctype back for other tests
            documentTypes.put("MyDocType", dt);
        }
    }

    @Test
    public void testVersionParent() {
        DocumentModel doc = session.createDocumentModel("/", "file", MY_FILE);
        doc.create();
        DocumentRef verRef = session.checkIn(doc.getRef());
        DocumentModel ver = session.getDocument(verRef);
        assertTrue(ver.getPathAsString().startsWith("/file/_v_"), ver.getPathAsString());
        // version's parent is the working copy
        DocumentRef parentRef = ver.getParentRef();
        assertEquals(doc.getId(), parentRef.toString());
        // now remove working copy
        session.removeDocument(doc.getRef());
        session.save();
        // version has been removed with it
        try {
            ver = session.getDocument(verRef);
            fail("version should have been removed");
        } catch (DocumentNotFoundException e) {
            // ok
        }
    }

    @Test
    public void testRollback() {
        DocumentModel file1 = session.createDocumentModel("/", "file1", SYS_FILE);
        file1.setPropertyValue(SYS_TITLE, "foo");
        file1.create();
        DocumentModel file2 = session.createDocumentModel("/", "file2", SYS_FILE);
        file2.setPropertyValue(SYS_TITLE, "smurf");
        file2.create();
        DocumentModel file3 = session.createDocumentModel("/", "file3", SYS_FILE);
        file3.create();
        session.save();
        // wait for async completion before deletes
        waitForAsyncCompletion();

        // start some changes
        // modify file1 title
        file1.refresh();
        file1.setPropertyValue(SYS_TITLE, "bar");
        // modify file2 title
        file2.setPropertyValue(SYS_TITLE, "zap");
        // remove file3
        session.removeDocument(file3.getRef());
        // create file4
        DocumentModel file4 = session.createDocumentModel("/", "file4", SYS_FILE);
        file4.create();
        // create file5
        DocumentModel file5 = session.createDocumentModel("/", "file5", SYS_FILE);
        file5.create();
        session.save();

        // more changes
        // modify again file1 title
        file1.setPropertyValue(SYS_TITLE, "gee");
        // remove file2
        session.removeDocument(file2.getRef());
        // modify file4
        file4.setPropertyValue(SYS_TITLE, "moo");
        // remove file5
        session.removeDocument(file5.getRef());
        session.save();

        // abort the transaction
        TransactionHelper.setTransactionRollbackOnly();
        nextTransaction();

        // check what we now have
        // file1 title unchanged
        file1 = session.getDocument(file1.getRef());
        assertEquals("foo", file1.getPropertyValue(SYS_TITLE));
        // file2 title unchanged
        file2 = session.getDocument(file2.getRef());
        assertEquals("smurf", file2.getPropertyValue(SYS_TITLE));
        // file3 still present
        assertTrue(session.exists(file3.getRef()));
        // file4 not present
        assertFalse(session.exists(file4.getRef()));
        // file5 not present
        assertFalse(session.exists(file5.getRef()));
    }

    @Test
    public void testRollback2() {
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.setPropertyValue(SYS_TITLE, "foo");
        file.create();
        session.save();
        nextTransaction();

        // start some changes
        file.refresh();
        file.setPropertyValue(SYS_TITLE, "bar");

        // abort the transaction
        TransactionHelper.setTransactionRollbackOnly();
        nextTransaction();

        // the changes have been rolled back
        file.refresh();
        assertEquals("foo", file.getPropertyValue(SYS_TITLE));
    }

    @Test
    public void testRollback3() {
        // create file 1
        DocumentModel file1 = session.createDocumentModel("/", "file1", SYS_FILE);
        file1.create();
        session.save();
        nextTransaction();

        // create file 2 and rollback
        DocumentModel file2 = session.createDocumentModel("/", "file2", SYS_FILE);
        file2.create();
        session.save();
        TransactionHelper.setTransactionRollbackOnly();
        nextTransaction();

        // check just one file is here
        DocumentModelList docs = session.query("SELECT * FROM SysFile");
        assertEquals(1, docs.size());

        // re-try to create file 2 and re-rollback
        file2 = session.createDocumentModel("/", "file2", SYS_FILE);
        file2.create();
        session.save();
        TransactionHelper.setTransactionRollbackOnly();
        nextTransaction();

        // still just one file
        docs = session.query("SELECT * FROM SysFile");
        assertEquals(1, docs.size());
    }

    @Test
    public void testRollback4() {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        // set rollback-only
        TransactionHelper.setTransactionRollbackOnly();
        // then use the session (first use in this transaction, it will be reconnected)
        try {
            session.getDocument(new PathRef("/"));
            fail("should not allow use of session when marked rollback-only");
        } catch (RepositoryException e) {
            assertEquals("Cannot use a session when transaction is marked rollback-only", e.getMessage());
        }
    }

    @Test
    public void testBinaryGC() throws Exception {
        // GC binaries from previous tests
        runBinariesGC(true, false);

        // store some binaries
        for (String str : Arrays.asList("ABC", "DEF", "GHI", "JKL")) {
            addBinary(str, str);
            addBinary(str, str + "2");
        }
        session.save();
        // wait for async completion before deletes
        waitForAsyncCompletion();

        BinaryManagerStatus status = runBinariesGC(true, false);
        assertEquals(4, status.getNumBinaries()); // ABC, DEF, GHI, JKL
        assertEquals(4 * 3, status.getSizeBinaries());
        assertEquals(0, status.getNumBinariesGC());
        assertEquals(0, status.getSizeBinariesGC());

        // remove some binaries
        session.removeDocument(new PathRef("/ABC"));
        session.removeDocument(new PathRef("/ABC2"));
        session.removeDocument(new PathRef("/DEF"));
        session.removeDocument(new PathRef("/DEF2"));
        session.removeDocument(new PathRef("/GHI")); // GHI2 remains
        // JKL and JKL2 remain
        session.save();
        nextTransaction();

        // run GC in non-delete mode
        status = runBinariesGC(false, false);
        assertEquals(2, status.getNumBinaries()); // GHI, JKL
        assertEquals(2 * 3, status.getSizeBinaries());
        assertEquals(2, status.getNumBinariesGC()); // ABC, DEF
        assertEquals(2 * 3, status.getSizeBinariesGC());

        // add a new binary during GC and revive one which was about to die;
        // the write-time mark protects the revived DEF, MNO added during GC isn't in the candidate set so isn't counted
        status = runBinariesGC(true, true);
        assertEquals(3, status.getNumBinaries()); // DEF3, GHI2, JKL (MNO not counted)
        assertEquals(3 * 3, status.getSizeBinaries());
        assertEquals(1, status.getNumBinariesGC()); // ABC
        assertEquals(1 * 3, status.getSizeBinariesGC());

        status = runBinariesGC(true, false);
        assertEquals(4, status.getNumBinaries()); // DEF3, GHI2, JKL, MNO
        assertEquals(4 * 3, status.getSizeBinaries());
        assertEquals(0, status.getNumBinariesGC());
        assertEquals(0, status.getSizeBinariesGC());
    }

    protected void addBinary(String content, String name) {
        Blob blob = Blobs.createBlob(content);
        DocumentModel doc = session.createDocumentModel("/", name, SYS_FILE);
        doc.setPropertyValue(SYSFILE_BLOB, blob);
        doc.create();
    }

    /**
     * Referencing an existing blob key anew doesn't rewrite the blob, so nothing but the write-time mark protects it
     * from a concurrent GC whose repository scan missed the new reference (the scan isn't atomic).
     */
    @Test
    public void testBinaryGCReferenceExistingKeyDuringGC() throws Exception {
        // GC binaries from previous tests
        runBinariesGC(true, false);

        addBinary("STU", "STU");
        session.save();
        waitForAsyncCompletion();

        // keep the managed blob around, then drop the only reference to it
        ManagedBlob blob = (ManagedBlob) session.getDocument(new PathRef("/STU")).getPropertyValue(SYSFILE_BLOB);
        session.removeDocument(new PathRef("/STU"));
        session.save();
        nextTransaction();

        // run a GC whose scan sees no reference to the blob, but reference it anew while the GC is in progress
        BinaryManagerStatus status = runBinariesGC(true, () -> {
            DocumentModel doc = session.createDocumentModel("/", "STU2", SYS_FILE);
            doc.setPropertyValue(SYSFILE_BLOB, blob);
            doc.create();
            session.save();
            nextTransaction();
        });
        // the blob was a deletion candidate but the write-time mark saved it
        assertEquals(0, status.getNumBinariesGC());

        // the reference isn't dangling, the blob can still be read
        Blob reread = (Blob) session.getDocument(new PathRef("/STU2")).getPropertyValue(SYSFILE_BLOB);
        assertEquals("STU", reread.getString());
    }

    protected BinaryManagerStatus runBinariesGC(boolean delete, boolean addDuringGC) {
        return runBinariesGC(delete, addDuringGC ? () -> {
            // while GC is in progress, add a new binary
            addBinary("MNO", "MNO");
            // and revive one that was about to be deleted
            // note that this wouldn't work if we didn't recreate the Binary object from an InputStream
            // and reused an old one
            addBinary("DEF", "DEF3");
            session.save();
            nextTransaction();
        } : null);
    }

    protected BinaryManagerStatus runBinariesGC(boolean delete, Runnable duringGC) {
        BlobProvider blobProvider = blobService.getBlobProvider(session.getRepositoryName());
        BinaryGarbageCollector gc = blobProvider.getBinaryGarbageCollector();
        Repository repository = repositoryService.getRepository(session.getRepositoryName());

        assertFalse(gc.isInProgress());
        gc.start(delete);
        assertTrue(gc.isInProgress());
        repository.markReferencedBlobs((key, repositoryName) -> {
            gc.mark(key);
        }, null);
        if (duringGC != null) {
            duringGC.run();
        }
        gc.stop(delete);
        return gc.getStatus();
    }

    /** Test that stores blobs in attachments (complex list). */
    @Test
    public void testBinaryGC2() throws Exception {
        // GC binaries from previous tests
        runBinariesGC(true, false);

        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        Map<String, Object> abc = Collections.singletonMap("blob", Blobs.createBlob("ABC"));
        Map<String, Object> def = Collections.singletonMap("blob", Blobs.createBlob("DEF"));
        Map<String, Object> ghi = Collections.singletonMap("blob", Blobs.createBlob("GHI"));
        doc.setPropertyValue("sysfiles_blobs", Arrays.asList(abc, def, ghi));
        doc.create();
        session.save();
        // remove GHI
        doc.setPropertyValue("sysfiles_blobs", Arrays.asList(abc, def));
        session.save();
        nextTransaction();

        // run GC in non-delete mode
        BinaryManagerStatus status = runBinariesGC(false, false);
        assertEquals(2, status.getNumBinaries()); // ABC, DEF
        assertEquals(1, status.getNumBinariesGC()); // GHI

        // actual GC
        status = runBinariesGC(true, false);
        assertEquals(2, status.getNumBinaries()); // ABC, DEF
        assertEquals(1, status.getNumBinariesGC()); // GHI

        // again
        status = runBinariesGC(true, false);
        assertEquals(2, status.getNumBinaries()); // ABC, DEF
        assertEquals(0, status.getNumBinariesGC());
    }

    @Test
    public void testSystemPropertyGenericAccessMatchesDedicatedAccessors() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        assertTrue(folder.isFolder());
        assertEquals(Boolean.TRUE, folder.getPropertyValue(SYS_IS_FOLDERISH));

        DocumentModel identified = session.createDocumentModel("/", "identified", SYS_FILE);
        identified.setPropertyValue(SYS_ID, "generic-id");
        identified.create();
        assertEquals("generic-id", identified.getId());

        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        assertThrows(RepositoryException.class, () -> doc.setPropertyValue(SYS_ID, "replacement-id"));
        assertThrows(PropertyException.class, () -> doc.getPropertyValue(SYSGOV_IS_RECORD));
        assertThrows(PropertyException.class, () -> doc.setPropertyValue(SYSGOV_IS_RECORD, true));

        assertEquals(doc.getId(), doc.getPropertyValue(SYS_ID));
        assertEquals(doc.getName(), doc.getPropertyValue(SYS_NAME));
        assertEquals(doc.getPathAsString(), doc.getPropertyValue(SYS_PATH));
        assertEquals(2, doc.getPropertyValue(SYS_PATH_DEPTH));
        assertEquals(doc.getParentRef().toString(), doc.getPropertyValue(SYS_PARENT_ID));
        assertEquals(doc.getAncestorIds(), doc.getPropertyValue(SYS_ANCESTOR_IDS));
        assertEquals(doc.getType(), doc.getPropertyValue(SYS_PRIMARY_TYPE));
        assertEquals(doc.isFolder(), doc.getPropertyValue(SYS_IS_FOLDERISH));
        assertEquals(doc.getEffectiveMixins(), Set.copyOf((List<String>) doc.getPropertyValue(SYS_MIXIN_TYPES)));
        assertEquals(doc.getGeneration(), (long) (Long) doc.getPropertyValue(SYS_GENERATION));
        assertEquals(doc.getUserGeneration(), (long) (Long) doc.getPropertyValue(SYS_USER_GENERATION));
        assertEquals(doc.getLocalACL(), doc.getPropertyValue(SYS_ACL));
        assertEquals(doc.getEffectiveACL(), doc.getPropertyValue(SYS_EFFECTIVE_ACL));
        assertEquals(doc.getEffectivePermissions(),
                Set.copyOf((List<String>) doc.getPropertyValue(SYS_EFFECTIVE_PERMISSIONS)));
        assertNull(doc.getPropertyValue(SYS_SECURITY_POLICIES));

        doc.setPropertyValue(SYS_NAME, "ignored");
        assertEquals("doc", doc.getName());
        doc.setPropertyValue(SYS_TITLE, "Generic title");
        assertEquals("Generic title", doc.getPropertyValue(SYS_TITLE));
        doc.setPropertyValue(SYS_PRIMARY_TYPE, SYS_FILE);
        doc.setPropertyValue(SYS_MIXIN_TYPES, List.of(SYS_GOVERNABLE));
        assertTrue(doc.hasMixin(SYS_GOVERNABLE));
        doc.setPropertyValue(SYS_MIXIN_TYPES, List.of());
        assertFalse(doc.hasMixin(SYS_GOVERNABLE));
        doc.setPropertyValue(SYS_SECURITY_POLICIES, List.of());
        assertNull(doc.getPropertyValue(SYS_SECURITY_POLICIES));

        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("bob").permission(READ).build());
        try (CapturingEventListener listener = new CapturingEventListener(DOCUMENT_SECURITY_UPDATED)) {
            doc.setPropertyValue(SYS_ACL, acl);
            assertEquals(1, listener.getCapturedEvents().size());
        }
        assertEquals(acl, doc.getLocalACL());

        try (CapturingEventListener listener = new CapturingEventListener(DOCUMENT_LOCKED, DOCUMENT_UNLOCKED)) {
            doc.setPropertyValue(SYS_LOCK_OWNER, TEST_ADMIN_USER);
            assertEquals(TEST_ADMIN_USER, doc.getLockInfo().getOwner());
            assertEquals(doc.getLockInfo().getCreated(), doc.getPropertyValue(SYS_LOCK_CREATED));
            doc.setPropertyValue(SYS_LOCK_OWNER, TEST_ADMIN_USER);
            doc.setPropertyValue(SYS_LOCK_OWNER, null);
            assertNull(doc.getLockInfo());
            doc.setPropertyValue(SYS_LOCK_OWNER, null);

            Lock lock = doc.setLock();
            assertEquals(TEST_ADMIN_USER, lock.getOwner());
            Lock relock = doc.setLock();
            assertEquals(lock.getOwner(), relock.getOwner());
            assertEquals(lock.getCreated().truncatedTo(MILLIS), relock.getCreated().truncatedTo(MILLIS));
            Lock removedLock = doc.removeLock();
            assertEquals(lock.getOwner(), removedLock.getOwner());
            assertEquals(lock.getCreated().truncatedTo(MILLIS), removedLock.getCreated().truncatedTo(MILLIS));
            assertNull(doc.removeLock());

            List<Event> events = listener.getCapturedEvents();
            assertEquals(List.of(DOCUMENT_LOCKED, DOCUMENT_UNLOCKED, DOCUMENT_LOCKED, DOCUMENT_UNLOCKED),
                    events.stream().map(Event::getName).toList());
        }
    }

    @Test
    public void testSystemPropertyGenericAccessEnforcesDedicatedPermissions() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("pete").permission(READ_WRITE).build());
        doc.setACL(acl, true);

        DocumentModel peteDoc = openSessionAs("pete").getDocument(doc.getRef());
        assertThrows(DocumentSecurityException.class, () -> peteDoc.getPropertyValue(SYS_ACL));
        assertThrows(DocumentSecurityException.class, () -> peteDoc.getPropertyValue(SYS_EFFECTIVE_ACL));
        assertThrows(DocumentSecurityException.class,
                () -> peteDoc.setPropertyValue(SYS_ACL, new ACLImpl()));
        assertThrows(PropertyException.class,
                () -> peteDoc.setPropertyValue(SYS_ACL + "/0/permission", READ));
        assertThrows(PropertyException.class, () -> peteDoc.getPropertyValue(SYS_ACL + "/0/principal"));
        assertThrows(PropertyException.class, () -> peteDoc.setPropertyValue("sys:secpol", List.of("policy")));
    }

    @Test
    public void testVersionSystemPropertiesMatchDedicatedAccessors() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        DocumentModel version = session.getDocument(doc.checkIn());

        assertTrue(version.isVersion());
        assertEquals(Boolean.TRUE, version.getPropertyValue(SYSVER_IS_VERSION));
        assertFalse(version.isCheckedOut());
        assertEquals(version.getVersion(), version.getPropertyValue(SYSVER_VERSION));
        assertEquals(version.getMinorVersion(), version.getPropertyValue(SYSVER_MINOR_VERSION));
    }

    @Test
    public void testLocking() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        DocumentRef docRef = doc.getRef();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("pete").permission(READ_WRITE).build());
        doc.setACL(acl, true);
        session.save();

        Lock lock = doc.getLockInfo();
        assertNull(lock);

        lock = doc.setLock();
        assertNotNull(lock);
        assertEquals(TEST_ADMIN_USER, lock.getOwner());
        assertNotNull(lock.getCreated());

        Lock lock2 = doc.getLockInfo();
        assertNotNull(lock2);
        assertEquals(TEST_ADMIN_USER, lock2.getOwner());

        // a user can call setLock again on their own lock without error
        Lock relock = doc.setLock();
        assertEquals(lock.getOwner(), relock.getOwner());
        assertEquals(lock.getFailed(), relock.getFailed());
        assertEquals(lock.getCreated().truncatedTo(MILLIS), relock.getCreated().truncatedTo(MILLIS));

        CoreSession peteSession = openSessionAs("pete");
        // cannot relock as pete
        // try to relock as pete
        try {
            peteSession.getDocument(docRef).setLock();
            fail();
        } catch (LockException e) {
            assertEquals(409, e.getStatusCode());
        }

        // cannot remove lock as pete
        // try to remove lock as pete
        try {
            peteSession.getDocument(docRef).removeLock();
            fail();
        } catch (LockException e) {
            assertTrue(e.getMessage().contains("Document already locked"), e.getMessage());
        }

        lock = doc.removeLock();
        assertNotNull(lock);
        assertEquals(TEST_ADMIN_USER, lock.getOwner());

        lock = doc.getLockInfo();
        assertNull(lock);

        // removing lock on non-locked doesn't fail
        lock = doc.removeLock();
        assertNull(lock);

        // set lock as pete
        DocumentModel peteDoc = peteSession.getDocument(docRef);
        lock = peteDoc.setLock();
        assertNotNull(lock);
        assertEquals("pete", lock.getOwner());

        // remove as pete
        lock = peteDoc.removeLock();
        assertNotNull(lock);
    }

    @Test
    public void testLockingBeforeSave() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();

        Lock lock = doc.getLockInfo();
        assertNull(lock);

        lock = doc.setLock();
        assertNotNull(lock);
        assertEquals(TEST_ADMIN_USER, lock.getOwner());

        lock = doc.removeLock();
        assertNotNull(lock);
        assertEquals(TEST_ADMIN_USER, lock.getOwner());

        lock = doc.setLock();
        assertNotNull(lock);
        assertEquals(TEST_ADMIN_USER, lock.getOwner());

        session.save();

        lock = doc.getLockInfo();
        assertNotNull(lock);
        assertEquals(TEST_ADMIN_USER, lock.getOwner());
    }

    @Test
    public void testRecord() {
        DocumentModel doc = session.createDocumentModel("/", "doc", GOVERNABLE_FILE);
        doc.create();
        DocumentRef docRef = doc.getRef();

        assertFalse(session.isRecord(docRef));
        assertFalse(doc.isRecord());
        // try to unset before it is set
        session.setRecord(docRef, false);
        assertFalse(session.isRecord(docRef));
        // set as record
        session.setRecord(docRef, true);
        assertTrue(session.isRecord(docRef));
        doc.refresh();
        assertTrue(doc.isRecord());
        // set as record again
        session.setRecord(docRef, true);
        assertTrue(session.isRecord(docRef));
    }

    @Test
    public void testGovernanceThroughGenericSystemProperties() {
        DocumentModel doc = session.createDocumentModel("/", "doc", GOVERNABLE_FILE);
        doc.create();

        doc.setPropertyValue(SYSGOV_IS_RECORD, true);
        assertTrue(doc.isRecord());
        assertEquals(Boolean.TRUE, doc.getPropertyValue(SYSGOV_IS_RECORD));

        Instant retainUntil = Instant.now().plus(1, HOURS);
        doc.setPropertyValue(SYSGOV_RETAIN_UNTIL, retainUntil);
        assertEquals(retainUntil, doc.getRetainUntil());

        doc.setPropertyValue(SYSGOV_HAS_LEGAL_HOLD, true);
        assertTrue(doc.hasLegalHold());
        doc.setPropertyValue(SYSGOV_HAS_LEGAL_HOLD, false);
        assertFalse(doc.hasLegalHold());

        assertThrows(PropertyException.class, () -> doc.setPropertyValue(SYSGOV_IS_RECORD, false));
    }

    @Test
    public void testGovernanceEventsThroughGenericAndDedicatedSetters() {
        DocumentModel doc = session.createDocumentModel("/", "doc", GOVERNABLE_FILE);
        doc.create();
        Instant retainUntil = Instant.now().plus(1, HOURS);
        Instant extendedRetainUntil = retainUntil.plus(1, HOURS);

        try (CapturingEventListener listener = new CapturingEventListener(AFTER_MAKE_RECORD, AFTER_SET_RETENTION,
                AFTER_EXTEND_RETENTION, AFTER_SET_LEGAL_HOLD, AFTER_REMOVE_LEGAL_HOLD)) {
            doc.setPropertyValue(SYSGOV_IS_RECORD, true);
            session.setRetainUntil(doc.getRef(), retainUntil);
            doc.setPropertyValue(SYSGOV_RETAIN_UNTIL, extendedRetainUntil);
            doc.setPropertyValue(SYSGOV_HAS_LEGAL_HOLD, true);
            session.setLegalHold(doc.getRef(), false);

            session.setRecord(doc.getRef(), true);
            doc.setPropertyValue(SYSGOV_RETAIN_UNTIL, extendedRetainUntil);
            session.setLegalHold(doc.getRef(), false);

            List<Event> events = listener.getCapturedEvents();
            assertEquals(
                    List.of(AFTER_MAKE_RECORD, AFTER_SET_RETENTION, AFTER_EXTEND_RETENTION, AFTER_SET_LEGAL_HOLD,
                            AFTER_REMOVE_LEGAL_HOLD),
                    events.stream().map(Event::getName).toList());
            for (Event event : events) {
                assertFalse(event.isInline());
                DocumentEventContext context = (DocumentEventContext) event.getContext();
                assertEquals(doc.getRef(), context.getSourceDocument().getRef());
            }
            assertEquals(retainUntil, events.get(1).getContext().getProperty(RETAIN_UNTIL));
            assertEquals(extendedRetainUntil, events.get(2).getContext().getProperty(RETAIN_UNTIL));
        }
    }

    @Test
    public void testNullRetentionEventProperty() {
        DocumentModel doc = session.createDocumentModel("/", "doc", GOVERNABLE_FILE);
        doc.create();
        session.setRecord(doc.getRef(), true);

        try (CapturingEventListener listener = new CapturingEventListener(AFTER_SET_RETENTION)) {
            doc.setPropertyValue(SYSGOV_RETAIN_UNTIL, null);

            assertEquals(1, listener.getCapturedEvents().size());
            Event event = listener.getCapturedEvents().getFirst();
            assertTrue(event.getContext().getProperties().containsKey(RETAIN_UNTIL));
            assertNull(event.getContext().getProperty(RETAIN_UNTIL));
        }
    }

    @Test
    public void testUnsetRecord() {
        DocumentModel doc = session.createDocumentModel("/", "doc", GOVERNABLE_FILE);
        doc.create();
        DocumentRef docRef = doc.getRef();

        assertFalse(session.isRecord(docRef));
        assertFalse(doc.isRecord());
        // try to unset before it is set
        session.setRecord(docRef, false);
        assertFalse(session.isRecord(docRef));
        // set as record
        session.setRecord(docRef, true);
        assertTrue(session.isRecord(docRef));
        doc.refresh();
        assertTrue(doc.isRecord());
        PropertyException ex = assertThrows(PropertyException.class, () -> session.setRecord(docRef, false));
        assertEquals("Cannot unmark a record",ex.getMessage());
        assertEquals(ex.getStatusCode(),400);
        assertTrue(doc.isRecord());
    }

    @Test
    public void testRecordVersioning() {
        DocumentModel doc = session.createDocumentModel("/", "doc", VERSIONABLE_AND_GOVERNABLE_FILE);
        doc.create();
        DocumentRef docRef = doc.getRef();

        // create a version
        DocumentRef verRef = session.checkIn(docRef);

        // a version cannot be made a record
        try {
            session.setRecord(verRef, true);
            fail();
        } catch (PropertyException e) {
            assertEquals("Version cannot be made record: " + verRef, e.getMessage());
        }

        // turn the live doc into a record
        session.setRecord(docRef, true);

        // the record can not be unset
        assertThrows(PropertyException.class, () -> session.setRecord(docRef, false));

        // a record cannot be checked in
        try {
            session.checkIn(docRef);
            fail();
        } catch (PropertyException e) {
            assertEquals("Record cannot be checked in: " + docRef, e.getMessage());
            assertEquals(SC_BAD_REQUEST, e.getStatusCode());
        }

        // a record cannot be checked in through auto-versioning
        doc.setPropertyValue(SYS_TITLE, "change");
        try {
            doc.checkIn();
            fail();
        } catch (PropertyException e) {
            assertEquals("Record cannot be checked in: " + docRef, e.getMessage());
            assertEquals(SC_BAD_REQUEST, e.getStatusCode());
        }

        // a version cannot be restored on a record
        try {
            session.restoreVersion(verRef);
            fail();
        } catch (PropertyException e) {
            assertEquals("Version cannot be restored on a record: " + docRef, e.getMessage());
        }
    }

    @Test
    public void testRetainUntil() {
        DocumentModel folder = session.createDocumentModel("/", "fold", SYS_FOLDER);
        folder.create();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("bob").permission(DELETE).build());
        folder.setACL(acl, true);
        DocumentModel subFolder = session.createDocumentModel("/fold", "subfold", SYS_FOLDER);
        subFolder.create();
        DocumentModel doc = session.createDocumentModel("/fold/subfold", "doc", GOVERNABLE_FILE);
        doc.create();

        // must be a record
        try {
            session.setRetainUntil(doc.getRef(), null);
            fail("Should fail because document is not a record");
        } catch (PropertyException e) {
            assertEquals("Document is not a record", e.getMessage());
        }

        // make it a record
        session.setRecord(doc.getRef(), true);

        // bob can delete it
        CoreSession bobSession = openSessionAs("bob");
        assertTrue(bobSession.hasPermission(doc.getRef(), SecurityConstants.DELETE));

        // first, set inderminate retention
        assertNull(session.getRetainUntil(doc.getRef()));
        assertNull(doc.getRetainUntil());
        session.setRetainUntil(doc.getRef(), CoreSession.RETAIN_UNTIL_INDETERMINATE);
        assertEquals(CoreSession.RETAIN_UNTIL_INDETERMINATE, session.getRetainUntil(doc.getRef()));
        doc.refresh();
        assertEquals(CoreSession.RETAIN_UNTIL_INDETERMINATE, doc.getRetainUntil());

        // cannot be deleted
        checkUndeletable(folder, doc);
        // bob cannot delete it
        assertFalse(bobSession.hasPermission(doc.getRef(), SecurityConstants.DELETE));

        // then reduce retention to an actual date in the future
        Instant retainUntil = Instant.now().plus(1, HOURS);
        session.setRetainUntil(doc.getRef(), retainUntil);
        assertEquals(retainUntil, session.getRetainUntil(doc.getRef()));
        doc.refresh();
        assertEquals(retainUntil, doc.getRetainUntil());

        // cannot be deleted
        checkUndeletable(folder, doc);
        // bob cannot delete it
        assertFalse(bobSession.hasPermission(doc.getRef(), SecurityConstants.DELETE));

        // cannot reduce retention
        Instant retainUntilEarlier = Instant.now().plus(5, MINUTES);
        try {
            session.setRetainUntil(doc.getRef(), retainUntilEarlier);
            fail("Should fail because cannot reduce retention time");
        } catch (PropertyException e) {
            assertTrue(e.getMessage().startsWith("Cannot reduce retention time"), e.getMessage());
            assertEquals(e.getStatusCode(),400);
        }
    }

    @Test
    public void testUnsetRetainUntil() throws Exception{
        DocumentModel folder = session.createDocumentModel("/", "fold", SYS_FOLDER);
        folder.create();
        DocumentModel subFolder = session.createDocumentModel("/fold", "subfold", SYS_FOLDER);
        subFolder.create();
        DocumentModel doc = session.createDocumentModel("/fold/subfold", "doc", GOVERNABLE_FILE);
        doc.create();

        // make it a record
        session.setRecord(doc.getRef(), true);

        // set the retention time
        Instant retainUntil = Instant.now().plus(1, SECONDS);
        session.setRetainUntil(doc.getRef(), retainUntil);
        assertEquals(retainUntil, session.getRetainUntil(doc.getRef()));
        doc.refresh();
        assertEquals(retainUntil, doc.getRetainUntil());
        // let us try set it to null
        try {
            session.setRetainUntil(doc.getRef(), null);
            fail("Should fail because cannot unset retention time if retention not expired");
        } catch (PropertyException e) {
            assertTrue(e.getMessage().startsWith("Cannot reduce retention time"), e.getMessage());
            assertEquals(e.getStatusCode(),400);
        }
    }

    @Test
    public void testLegalHold() {
        DocumentModel folder = session.createDocumentModel("/", "fold", SYS_FOLDER);
        folder.create();
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("bob").permission(DELETE).build());
        folder.setACL(acl, true);
        DocumentModel subFolder = session.createDocumentModel("/fold", "subfold", SYS_FOLDER);
        subFolder.create();
        DocumentModel doc = session.createDocumentModel("/fold/subfold", "doc", GOVERNABLE_FILE);
        doc.create();
        assertFalse(doc.hasLegalHold());

        // must be a record
        try {
            session.setRetainUntil(doc.getRef(), null);
            fail("Should fail because document is not a record");
        } catch (PropertyException e) {
            assertEquals("Document is not a record", e.getMessage());
        }

        // make it a record
        session.setRecord(doc.getRef(), true);

        // bob can delete it
        CoreSession bobSession = openSessionAs("bob");
        assertTrue(bobSession.hasPermission(doc.getRef(), SecurityConstants.DELETE));

        // set legal hold
        assertFalse(session.hasLegalHold(doc.getRef()));
        session.setLegalHold(doc.getRef(), true);
        assertTrue(session.hasLegalHold(doc.getRef()));
        doc.refresh();
        assertTrue(doc.hasLegalHold());

        checkUndeletable(folder, doc);
        // bob cannot delete it
        assertFalse(bobSession.hasPermission(doc.getRef(), SecurityConstants.DELETE));

        // unset legal hold, can now delete
        session.setLegalHold(doc.getRef(), false);
        doc.refresh();
        assertFalse(doc.hasLegalHold());
        // bob can delete it
        assertTrue(bobSession.hasPermission(doc.getRef(), SecurityConstants.DELETE));

        session.removeDocument(doc.getRef());
    }

    @Test
    public void testRetentionExpiresOnInvalidDocs() {
        DocumentModel doc = session.createDocumentModel("/", "doc", GOVERNABLE_FILE);
        doc.create();
        session.setRecord(doc.getRef(), true);

        // set retention to current time
        Instant now = Instant.now();
        session.setRetainUntil(doc.getRef(), now);
        session.save();

        // Let's launch a task with a valid and and invalid doc id
        class DummyRetentionExpiredTaskProcessor extends RetentionExpiredBatchTaskProcessor {
            public boolean wentThrough(List<String> ids) {
                try {
                    super.process(null, ids, session);
                    return true;
                } catch (RepositoryException e) {
                    return false;
                }
            }
        }
        DummyRetentionExpiredTaskProcessor computation = new DummyRetentionExpiredTaskProcessor();
        assertTrue(computation.wentThrough(Arrays.asList("foo", doc.getId())));

        // valid doc has no retention anymore and can be deleted
        doc = session.getDocument(doc.getRef());
        assertNull(session.getRetainUntil(doc.getRef()));
        session.removeDocument(doc.getRef());
    }

    @Test
    public void testRetentionExpiresAutomatically() throws Exception {
        DocumentModel folder = session.createDocumentModel("/", "fold", SYS_FOLDER);
        folder.create();
        DocumentModel subFolder = session.createDocumentModel("/fold", "subfold", SYS_FOLDER);
        subFolder.create();
        DocumentModel doc = session.createDocumentModel("/fold/subfold", "doc", GOVERNABLE_FILE);
        doc.create();
        session.setRecord(doc.getRef(), true);
        // set retention to two seconds in the future
        Instant twoSeconds = Instant.now().plus(2, SECONDS);
        session.setRetainUntil(doc.getRef(), twoSeconds);
        session.save();

        checkUndeletable(folder, doc);

        // wait 3s to pass retention expiration date
        nextTransaction();
        Thread.sleep(3_000);
        // trigger manually instead of waiting for scheduler
        new RetentionExpiredFinderScheduledJob().run();
        // wait for all bulk tasks to be executed
        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(60)), "Bulk tasks didn't finish");

        // re-acquire the doc in a new transaction
        nextTransaction();
        doc = session.getDocument(doc.getRef());

        // it has no retention anymore and can be deleted
        assertNull(session.getRetainUntil(doc.getRef()));
        session.removeDocument(doc.getRef());
    }

    protected void checkUndeletable(DocumentModel folder, DocumentModel doc) {
        // check that the document cannot be deleted now, even by system user
        assertTrue(session.getPrincipal().isAdministrator());
        try {
            session.removeDocument(doc.getRef());
            fail("remove should fail");
        } catch (DocumentSecurityException e) {
            assertEquals(GENERIC_DOCUMENT_SECURITY_ERROR.formatted(DELETE, session.getPrincipal().getId(), doc.getId()), e.getMessage());
        }

        // check that the document cannot be deleted through a parent
        assertTrue(session.getPrincipal().isAdministrator());
        try {
            session.removeDocument(folder.getRef());
            fail("remove should fail");
        } catch (DocumentSecurityException e) {
            assertEquals(
                    "Cannot remove " + folder.getId() + ", subdocument " + doc.getId() + " is under retention / hold",
                    e.getMessage());
        }
    }

    @Test
    public void testGeneration() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();
        assertEquals(0L, doc.getGeneration());
        assertEquals(0L, doc.getUserGeneration());

        // now change the doc
        doc.setPropertyValue(SYS_TITLE, "Doc Changed");
        session.save();

        // the system change token has been updated
        // but not the user change token as this was not flagged a user change
        assertEquals(1L, doc.getGeneration());
        assertEquals(0L, doc.getUserGeneration());

        // change the doc again as a user change
        doc.setPropertyValue(SYS_TITLE, "Doc Changed Again");
        doc.markUserChange();
        session.save();

        // the system change token has been updated again
        // and the user change token as well
        assertEquals(2L, doc.getGeneration());
        assertEquals(1L, doc.getUserGeneration());
    }

    @Test
    public void testGenerationSystem() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();
        assertEquals(0L, doc.getGeneration());

        // now change the doc
        doc.setPropertyValue(SYS_TITLE, "Doc Changed");
        session.save();

        // reopen session to not read from caches
        reopenSession();
        doc = session.getDocument(doc.getRef());

        // the system change token has been updated and written
        assertEquals(1L, doc.getGeneration());
    }

    @Test
    public void testGenerationForList() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();

        // do a first change to provoke the initial update VCS side effects (fulltext table)
        doc.setPropertyValue(SYS_TITLE, "Doc Changed");
        session.save();

        // get the token
        assertEquals(1L, doc.getGeneration());

        // change the doc by changing a list
        doc.setPropertyValue("mydc_subjects", Arrays.asList("foo", "bar"));
        session.save();

        // the change token has been updated
        assertEquals(2L, doc.getGeneration());
    }

    @Test
    public void testGenerationForComplex() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "ComplexDoc");
        doc.create();
        session.save();

        // do a first change to provoke the initial update VCS side effects (fulltext table)
        doc.setPropertyValue(SYS_TITLE, "foo");
        session.save();

        // get the token
        assertEquals(1L, doc.getGeneration());

        // change the doc by creating a complex property list
        doc.setPropertyValue("cmpf_attachedFile/vignettes", List.of(Map.of("label", "123")));
        session.save();

        // the change token has been updated
        assertEquals(2L, doc.getGeneration());

        // change the doc by updating a complex property list
        doc.setPropertyValue("cmpf_attachedFile/vignettes", List.of(Map.of("label", "456")));
        session.save();

        // the change token has been updated again
        assertEquals(3L, doc.getGeneration());

        // change the doc by removing a complex property list
        doc.setPropertyValue("cmpf_attachedFile/vignettes", Collections.emptyList());
        session.save();

        // the change token has been updated again
        assertEquals(4L, doc.getGeneration());
    }

    @Test
    public void testOptimisticLockingWithExplicitCondition() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();
        assertEquals(0L, doc.getGeneration());
        assertEquals(0L, doc.getUserGeneration());

        // simulate some background system work
        doc.setPropertyValue(SYS_DESCRIPTION, "background update");
        session.save();
        assertEquals(1L, doc.getGeneration());
        assertEquals(0L, doc.getUserGeneration());

        // now change the doc, using the previously-retrieved change token
        doc = session.getDocument(doc.getRef());
        doc.setUpdateCondition(UpdateCondition.hxql("sys_ugen = 0"));
        doc.setPropertyValue(SYS_TITLE, "foo");
        session.save(); // save succeeds even though there was a background system update
        assertEquals(2L, doc.getGeneration());
        assertEquals(0L, doc.getUserGeneration());
    }

    @Test
    public void testOptimisticLockingWithExplicitConditionCollision() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();
        assertEquals(0L, doc.getGeneration());
        assertEquals(0L, doc.getUserGeneration());

        // simulate some parallel user work
        doc.markUserChange();
        doc.setPropertyValue(SYS_TITLE, "foo");
        session.save();
        assertEquals(1L, doc.getGeneration());
        assertEquals(1L, doc.getUserGeneration());

        // now change the doc, using the originally-retrieved change token
        try {
            doc = session.getDocument(doc.getRef());
            doc.setUpdateCondition(UpdateCondition.hxql("sys_ugen = 0"));
            doc.setPropertyValue(SYS_TITLE, "bar");
            session.save();
            fail("save should fail because the user generation changed");
        } catch (ConditionFailedException e) {
            TransactionHelper.setTransactionRollbackOnly();
            assertEquals(doc.getId(), e.getMessage());
        }
    }

    @Test
    public void testOptimisticLockingWithAsyncWork() { // NOSONAR (no need to assert anything)
        DocumentModel file = session.createDocumentModel("/", "file", SYS_FILE);
        file.setPropertyValue(SYS_TITLE, "foo");
        file.create();
        session.save();
        nextTransaction();
        // here async text indexing modifies file1

        // modify file as a user change
        file.refresh();
        file.setPropertyValue(SYS_TITLE, "bar");
        file.markUserChange();
        session.save();
    }

    @Test
    public void testOptimisticLockingWithParallelChange() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foo");
        doc.create();
        DocumentRef docRef = doc.getRef();
        session.save();

        // re-start a new transaction that hasn't done any writes
        nextTransaction();
        waitForAsyncCompletion();
        reopenSession();

        doc = session.getDocument(docRef);

        // in other thread, update the doc as a user change
        MutableObject<RuntimeException> me = new MutableObject<>();
        Thread thread = new Thread(() -> {
            TransactionHelper.runInTransaction(() -> {
                try {
                    CoreSession session2 = CoreInstance.getCoreSession(coreFeature.getRepositoryName());
                    DocumentModel doc2 = session2.getDocument(docRef);
                    doc2.setPropertyValue(SYS_TITLE, "bar parallel");
                    doc2.markUserChange();
                    session2.save(); // save succeeds
                } catch (RuntimeException e) {
                    me.setValue(e);
                }
            });
        });
        thread.start();
        thread.join();
        if (me.getValue() != null) {
            throw me.getValue();
        }

        // now try to save the doc as a user change as well
        try {
            doc.setPropertyValue(SYS_TITLE, "bar");
            doc.markUserChange();
            session.save();
            fail("save should fail because of concurrent update in other transaction");
        } catch (ConcurrentUpdateException e) {
            // ok
            TransactionHelper.setTransactionRollbackOnly();
        }
    }

    protected static class SavingJob implements Runnable {

        protected final List<DocumentRef> docRefs;

        protected final CyclicBarrier barrier;

        protected Exception exc;

        public SavingJob(List<DocumentModel> docs, CyclicBarrier barrier) {
            docRefs = docs.stream().map(DocumentModel::getRef).toList();
            this.barrier = barrier;
        }

        @Override
        public void run() {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                TransactionHelper.runInTransaction(() -> {
                    try {
                        CoreSession session = CoreInstance.getCoreSession(null);
                        for (DocumentRef docRef : docRefs) {
                            DocumentModel doc = session.getDocument(docRef);
                            doc.setPropertyValue(SYS_TITLE, "foo" + System.nanoTime());
                        }
                        barrier.await(5, TimeUnit.SECONDS);
                        session.save();
                    } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                        exc = e;
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                if (exc == null) {
                    exc = new Exception(docRefs.toString(), e);
                }
            }
        }
    }

    /**
     * Test that two concurrent save of sets of documents that have commonalities don't produce a deadlock because of
     * the order of the writes.
     * <p>
     * The test saves in parallel random subsets of the same document list, in a random order.
     */
    @Test
    public void testConcurrentSavesNoDeadlock() throws Exception { // NOSONAR (no need to assert anything)
        final int DOCS = 10;
        final int CONCUR = 2;
        final int TRIES = 100;
        List<DocumentModel> docs = new ArrayList<>(DOCS);
        for (int i = 0; i < DOCS; i++) {
            DocumentModel doc = session.createDocumentModel("/", "doc" + i, SYS_FILE);
            doc.setPropertyValue(SYS_TITLE, "doc" + i);
            doc.create();
            docs.add(doc);
        }
        session.save();
        for (int tries = 0; tries < TRIES; tries++) {
            waitForAsyncCompletion();

            CyclicBarrier barrier = new CyclicBarrier(CONCUR);
            List<SavingJob> jobs = new ArrayList<>(CONCUR);
            List<Thread> threads = new ArrayList<>(CONCUR);
            for (int i = 0; i < CONCUR; i++) {
                List<DocumentModel> list = new ArrayList<>(docs);
                // randomize list
                Collections.shuffle(list);
                // truncate to a random number of elements (at least 2)
                int s = random.nextInt(DOCS - 1) + 2;
                list = list.subList(0, s);
                SavingJob job = new SavingJob(list, barrier);
                jobs.add(job);
                Thread thread = new Thread(job);
                threads.add(thread);
                thread.start();
            }
            for (int i = 0; i < CONCUR; i++) {
                threads.get(i).join();
            }
            Exception err = new Exception("");
            for (int i = 0; i < CONCUR; i++) {
                Exception e = jobs.get(i).exc;
                if (e != null) {
                    err.addSuppressed(e);
                }
            }
            if (err.getSuppressed().length != 0) {
                throw err;
            }
        }
    }

    /*
    @Test
    public void testConcurrentArrayUpdateAndRemove() {
        DocumentModel doc = session.createDocumentModel("/", "document", "MyDocType2");
        doc.setPropertyValue("cpxl:complexList",
                List.of(Map.of("foo", "value-foo", "bar", "value-bar")));
        doc.create();
        DocumentRef docRef = doc.getRef();
        nextTransaction();

        // run concurrently an array complex element update and an array removal
        // but with removal executed first
        try {
            var barrier = new CyclicBarrier(2);
            runConcurrently(() -> {
                TransactionHelper.startTransaction();
                DocumentModel document = session.getDocument(docRef);
                // sync the two threads
                barrier.await(5, TimeUnit.SECONDS);
                document.setPropertyValue("cpxl:complexList", List.of());
                // the removal should occur before the update
                session.save();
                TransactionHelper.commitOrRollbackTransaction();
                barrier.await(50, TimeUnit.SECONDS);
            }, () -> {
                TransactionHelper.startTransaction();
                DocumentModel document = session.getDocument(docRef);
                // sync the two threads
                barrier.await(5, TimeUnit.SECONDS);
                document.setPropertyValue("cpxl:complexList/0/foo", "updated");
                // the update should occur after the removal
                barrier.await(5, TimeUnit.SECONDS);
                session.save();
                TransactionHelper.commitOrRollbackTransaction();
            });
        } catch (RepositoryException e) {
            if (isDBS()) {
                assertEquals("Exceptions in threads", e.getMessage());
                Throwable[] s = e.getSuppressed();
                assertEquals(1, s.length);
                Throwable s0 = s[0];
                assertTrue(s0 instanceof ConcurrentUpdateException);
                assertTrue(s0.getMessage().startsWith("Failed to save session"), s0.getMessage());
                return;
            } else {
                throw e;
            }
        }
        if (isDBS()) {
            fail("expected ConcurrentUpdateException");
        } else {
            // VCS doesn't detect this kind of concurrent update
            // the update to the complex element will just fail to do anything
            // as the element is now gone
            doc.refresh();
            assertEquals(List.of(), doc.getPropertyValue("cpxl:complexList"));
        }
    }
    */

    @SafeVarargs
    protected final void runConcurrently(ThrowableRunnable<Exception>... runnables) {
        ExecutorService executor = Executors.newFixedThreadPool(runnables.length);
        var exceptions = Collections.synchronizedList(new ArrayList<Exception>());

        // convert throwable runnables to regular runnable
        // and submit the to the executor
        Arrays.stream(runnables).map(r -> (Runnable) () -> {
            try {
                r.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RepositoryException("Interrupted", e);
            } catch (Exception e) {
                exceptions.add(e);
            }
        }).forEach(executor::submit);

        // shutdown executor and wait for termination
        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(50, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("Interrupted", e);
        }

        // rethrow any exception that occurred during thread execution
        if (!exceptions.isEmpty()) {
            var e = new RepositoryException("Exceptions in threads");
            exceptions.forEach(e::addSuppressed);
            throw e;
        }
    }

    @Test
    public void testCopyWhichChildren() {
        // Create a file document
        DocumentModel file = session.createDocumentModel("/", "SourceDoc", MY_FILE);
        file.create();

        // Add different types of children (regular, versioned with parent) on this created file

        // versioned with parent (called "special" in this test)
        DocumentModel specialChildDoc = session.createDocumentModel(file.getPathAsString(), "specialChildDoc",
                "SpecialFolder");
        specialChildDoc.create();
        DocumentModel regularUnderSpecialDoc = session.createDocumentModel(specialChildDoc.getPathAsString(),
                "regularUnderSpecialChildDoc", SYS_FILE);
        regularUnderSpecialDoc.create();

        // regular
        DocumentModel regularChildDoc = session.createDocumentModel(file.getPathAsString(), "regularChildDoc", SYS_FILE);
        regularChildDoc.create();

        session.save();

        assertEquals(2, session.getChildren(file.getRef()).size());

        // Test nominal copy
        DocumentModel copiedDocument = session.copy(file.getRef(), new PathRef("/"), "CopyDoc");
        copiedDocument = session.getDocument(copiedDocument.getRef());
        assertNotEquals(file.getId(), copiedDocument.getId());
        assertEquals("CopyDoc", copiedDocument.getName());
        DocumentModelList children = session.getChildren(copiedDocument.getRef());
        assertEquals(2, children.size());
        assertEquals(Set.of("regularChildDoc", "specialChildDoc"),
                children.stream().map(DocumentModel::getName).collect(toSet()));

        // Test checkin copy, only special children (versioned with parent) shall be copied
        DocumentRef checkedIn = file.checkIn();
        assertEquals(3, session.getChildren(file.getRef()).size()); // version is a new child
        children = session.getChildren(checkedIn);
        assertEquals(1, children.size());
        DocumentModel versionedChild = children.get(0);
        assertEquals("SpecialFolder", versionedChild.getType());
        assertEquals("specialChildDoc", versionedChild.getName());
        assertNotEquals(regularChildDoc.getRef(), versionedChild.getRef());

        // Regular documents under the special children shall be copied as well
        children = session.getChildren(versionedChild.getRef());
        assertEquals(1, children.size());
        DocumentModel versionedSubChild = children.get(0);
        assertEquals(SYS_FILE, versionedSubChild.getType());
        assertEquals("regularUnderSpecialChildDoc", versionedSubChild.getName());
        assertNotEquals(regularUnderSpecialDoc.getRef(), versionedSubChild.getRef());

        // Test restore copy, working copy document shall keep both special and regular children.
        // No version children shall be added during restore
        DocumentModel restored = session.restoreVersion(checkedIn);
        children = session.getChildren(restored.getRef());
        assertEquals(3, children.size());
    }

    // NXP-30250
    @Test
    public void testResetVersionCopyHierarchy() {
        DocumentModel root = session.getRootDocument();
        DocumentModel folder1 = session.createDocumentModel(root.getPathAsString(), "folder1", SYS_FOLDER);
        folder1.create();
        DocumentModel folder2 = session.createDocumentModel(root.getPathAsString(), "folder2", SYS_FOLDER);
        folder2.create();
        DocumentModel file = session.createDocumentModel(folder1.getPathAsString(), "file", SYS_FILE);
        file.create();

        session.save();

        assertTrue(session.exists(new PathRef("/folder1/file")));
        assertFalse(session.exists(new PathRef("/folder2/folder1")));
        assertEquals(0, (long) file.getVersion());

        session.copy(folder1.getRef(), folder2.getRef(), null);

        assertTrue(session.exists(new PathRef("/folder1/file")));
        assertTrue(session.exists(new PathRef("/folder2/folder1")));
        assertTrue(session.exists(new PathRef("/folder2/folder1/file")));

        DocumentModel copyFile = session.getDocument(new PathRef("/folder2/folder1/file"));
        assertEquals(0, (long) copyFile.getVersion());
    }

    @Test
    public void testSetId() {
        String id = "custom-id-123_456.789";
        DocumentModel doc1 = session.createDocumentModel("/", "doc1", SYS_FILE);
        doc1.setId(id);
        doc1.create();
        session.save();

        // id is set as expected
        assertEquals(id, doc1.getId());

        // create another doc with same id (same session so detected immediately in-memory)
        DocumentModel doc2 = session.createDocumentModel("/", "doc2", SYS_FILE);
        doc2.setId(id);

        // fails with concurrent update exception
        assertThrows(ConcurrentUpdateException.class, () -> doc2.create());
        TransactionHelper.setTransactionRollbackOnly();
    }

    @Test
    public void testSetIdDuplicateInOtherThread() throws Exception {
        String id = "custom-id-000";

        var exc = Collections.synchronizedList(new ArrayList<Exception>());
        Runnable run = () -> {
            TransactionHelper.runInTransaction(() -> {
                try {
                    CoreSession s = CoreInstance.getCoreSession(coreFeature.getRepositoryName());
                    String name = "doc" + Thread.currentThread().threadId();
                    DocumentModel doc = s.createDocumentModel("/", name, SYS_FILE);
                    doc.setId(id);
                    doc.create();
                    session.save();
                } catch (RuntimeException e) {
                    exc.add(e);
                    TransactionHelper.setTransactionRollbackOnly();
                }
            });
        };
        Thread thread1 = new Thread(run);
        Thread thread2 = new Thread(run);
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // one of the thread failed with a ConcurrentUpdateException
        assertEquals(1, exc.size());
        Exception e = exc.get(0);
        assertTrue(e instanceof ConcurrentUpdateException, e.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "foo bar", "foo:bar", "foo/bar", "foo%bar", "foo\"bar", "foo'bar" })
    public void testSetIdForbiddenValues(String id) {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);

        assertThrows(PropertyException.class, () -> doc.setId(id));
    }

    @Test
    public void testSetIdMaxLen() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);

        String id500 = "x".repeat(500);
        doc.setId(id500); // should succeed

        String id501 = "x".repeat(501);
        assertThrows(PropertyException.class, () -> doc.setId(id501));
    }

    @Test
    public void testSetIdAfterCreateFails() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();

        var e = assertThrows(RepositoryException.class, () -> doc.setId("custom-id-123"));
        assertEquals("Document already created", e.getMessage());
    }

    /**
     * A document stored before {@code sys_gen} was introduced has no such field in the database. It must still be
     * usable as a regular existing document, in particular as the parent of a newly created document.
     */
    @Test
    public void testCreateChildInFolderWithoutGeneration() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        session.save();
        String folderId = folder.getId();
        nextTransaction();

        // simulate a document written before sys_gen existed, by removing the field from the database
        removeGeneration(folderId);
        reopenSession();

        DocumentModel parent = session.getDocument(new IdRef(folderId));
        assertNull(parent.getPropertyValue(SYS_GENERATION));

        // creating a child must not fail with "Document not yet created"
        DocumentModel child = session.createDocumentModel(parent.getPathAsString(), "child", SYS_FILE);
        child.create();
        session.save();

        assertEquals("/folder/child", child.getPathAsString());
        assertTrue(session.hasChild(parent.getRef(), "child"));
    }

    /** Removes {@code sys_gen} from the stored state, bypassing the session. */
    protected void removeGeneration(String id) {
        StateDiff diff = new StateDiff();
        diff.put(SYS_GENERATION, null); // a null value removes the field
        Repository repository = repositoryService.getRepository(session.getRepositoryName());
        try (DBSConnection connection = ((DBSRepository) repository).getConnection()) {
            connection.updateState(id, diff, null);
        }
    }

    @Test
    public void testConditionalUpdate() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foo");
        doc.create();
        session.save();

        // conditional update that passes; showcase complex HXQL conditions
        String hxql = "sys_title = 'foo' AND (sys_primaryType IN ('SysFile', 'Foo') OR sys_name = 'abcd')";
        doc.setUpdateCondition(UpdateCondition.hxql(hxql));
        doc.setPropertyValue(SYS_TITLE, "bar");
        session.save();
        assertEquals("bar", doc.getPropertyValue(SYS_TITLE));

        // commit transaction
        nextTransaction();
        doc.refresh();

        // conditional update that fails
        doc.setUpdateCondition(UpdateCondition.hxql("sys_title = 'baz'"));
        doc.setPropertyValue(SYS_TITLE, "gee");
        assertThrows(ConditionFailedException.class, () -> session.save());

        // rollback after exception in save
        TransactionHelper.setTransactionRollbackOnly();
        nextTransaction();
        doc.refresh();

        // doc has still old value from previous transaction
        assertEquals("bar", doc.getPropertyValue(SYS_TITLE));
    }

    @Test
    public void testConditionalUpdateInvalid() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foo");
        doc.create();
        session.save();

        // commit transaction
        nextTransaction();
        doc.refresh();

        // conditional update syntax error
        assertThrows(QueryParseException.class, () -> doc.setUpdateCondition(UpdateCondition.hxql("this-is-not-hxql")));

        // conditional update on sys_path is forbidden
        doc.setUpdateCondition(UpdateCondition.hxql("sys_path = 'foo'"));
        doc.setPropertyValue(SYS_TITLE, "bar");
        var e = assertThrows(QueryParseException.class, () -> session.save());
        assertEquals("sys_path not allowed in condition", e.getMessage());

        // rollback after exception in save
        TransactionHelper.setTransactionRollbackOnly();
        nextTransaction();
        doc.refresh();

        // conditional update on sys_path STARTSWITH is forbidden too
        doc.setUpdateCondition(UpdateCondition.hxql("sys_path STARTSWITH '/foo'"));
        doc.setPropertyValue(SYS_TITLE, "bar");
        e = assertThrows(QueryParseException.class, () -> session.save());
        assertEquals("sys_path not allowed in condition", e.getMessage());

        // rollback after exception in save
        TransactionHelper.setTransactionRollbackOnly();
    }

    @Test
    public void testConditionalUpdateExpr() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foo");
        doc.create();
        session.save();

        // conditional update that passes; showcase complex expression conditions
        doc.setUpdateCondition(UpdateCondition.expr(
                "content.sys_title == \"foo\" && (content.sys_primaryType == \"SysFile\" || content.sys_name == \"abcd\")"));
        doc.setPropertyValue(SYS_TITLE, "bar");
        session.save();
        assertEquals("bar", doc.getPropertyValue(SYS_TITLE));

        // commit transaction
        nextTransaction();
        doc.refresh();

        // conditional update that fails
        doc.setUpdateCondition(UpdateCondition.expr("content.sys_title == \"baz\""));
        doc.setPropertyValue(SYS_TITLE, "gee");
        assertThrows(ConditionFailedException.class, () -> session.save());

        // rollback after exception in save
        TransactionHelper.setTransactionRollbackOnly();
        nextTransaction();
        doc.refresh();

        // doc has still old value from previous transaction
        assertEquals("bar", doc.getPropertyValue(SYS_TITLE));
    }

    @Test
    public void testConditionalUpdateExprUser() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foo");
        doc.create();
        session.save();

        // commit transaction
        nextTransaction();
        doc.refresh();

        // the current user is available in the expression, which HXQL conditions cannot express
        doc.setUpdateCondition(UpdateCondition.expr("content.sys_creator == user.id"));
        doc.setPropertyValue(SYS_TITLE, "bar");
        session.save();
        assertEquals("bar", doc.getPropertyValue(SYS_TITLE));
    }

    @Test
    public void testConditionalUpdateExprConstant() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foo");
        doc.create();
        session.save();

        // commit transaction
        nextTransaction();
        doc.refresh();

        // an expression that is always true doesn't constrain the update
        doc.setUpdateCondition(UpdateCondition.expr("1 == 1"));
        doc.setPropertyValue(SYS_TITLE, "bar");
        session.save();
        assertEquals("bar", doc.getPropertyValue(SYS_TITLE));

        // an expression that is always false fails immediately, without attempting the update
        assertThrows(ConditionFailedException.class, () -> doc.setUpdateCondition(UpdateCondition.expr("1 == 2")));
    }

    @Test
    public void testConditionalUpdateExprInvalid() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foo");
        doc.create();
        session.save();

        // commit transaction
        nextTransaction();
        doc.refresh();

        // syntax error
        assertThrows(QueryParseException.class,
                () -> doc.setUpdateCondition(UpdateCondition.expr("this is not an expression")));

        // unknown toplevel name
        var e = assertThrows(QueryParseException.class,
                () -> doc.setUpdateCondition(UpdateCondition.expr("nosuchthing.foo == 1")));
        assertTrue(e.getMessage().contains("No global property: nosuchthing"), e.getMessage());

        // not a predicate
        e = assertThrows(QueryParseException.class,
                () -> doc.setUpdateCondition(UpdateCondition.expr("content.sys_title")));
        assertEquals("Condition is not a predicate: content.sys_title", e.getMessage());
    }

    @Test
    public void testCreateDocumentThrowsDocumentNotFoundExceptionWhenAnyOfTheAncestorDontExists() {
        DocumentModel parentFolder = session.createDocumentModel("/", "parentFolder", SYS_FOLDER);
        parentFolder.create();
        session.save();

        String parentPath = parentFolder.getPathAsString();
        String firstFolderId = null;
        for (int i = 1; i <= 4; i++) {
            DocumentModel folder = session.createDocumentModel(parentPath, "folder" + i, SYS_FOLDER);
            folder.create();
            assertTrue(folder.getPathAsString().startsWith("/"));
            if (i == 1) {
                firstFolderId = folder.getId();
            }
            parentPath = folder.getPathAsString();
        }
        session.save();

        session.removeDocument(new IdRef(firstFolderId));
        session.save();

        String path = parentPath;
        assertThrows(DocumentNotFoundException.class, () ->
                session.createDocumentModel(path, "file", SYS_FILE));
    }
}
