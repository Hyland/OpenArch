/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.BaseValuesVisitor;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.SystemPrincipal;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.SchemaManagerBase;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.storage.dbs.DBSDocument;
import org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

/**
 * Tests using the low-level Session / Document API.
 * <p>
 * It's important to test them as they may be used by user code for security policies or versioning service
 * contributions, or blob providers.
 *
 * @since 7.3
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
public class TestDocument {

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession coreSession;

    protected SchemaManager schemaManager;

    protected InternalSession session;

    protected NuxeoPrincipal principal = new SystemPrincipal();

    @BeforeEach
    public void setUp() {
        schemaManager = coreSession.getSchemaManager();
        session = ((SessionImpl) coreSession).getSession();
    }

    protected void reopenSession() {
        coreSession = coreFeature.reopenCoreSession();
        setUp();
    }

    @FunctionalInterface
    private interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    protected final BiFunction<InternalDocument, String, Object> DocumentGetValue = InternalDocument::getValue;

    protected final TriConsumer<InternalDocument, String, Object> DocumentSetValue = InternalDocument::setValue;

    private InternalDocument addChild(InternalDocument parent, String name, String typeName) {
        DBSDocument doc = DBSDocument.newUnCreatedDocument(parent, name, typeName, session);
        doc.create();
        return doc;
    }

    @Test
    public void testGetValueErrors() {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", "TestDocument");
        tryUnknownProperty(xpath -> DocumentGetValue.apply(doc, xpath));
    }

    @Test
    public void testSetValueErrors() {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", "TestDocument");
        tryUnknownProperty(xpath -> DocumentSetValue.accept(doc, xpath, null));
    }

    protected void tryUnknownProperty(Consumer<String> c) {
        check(c, "nosuchprop", null);
        check(c, "tp_nosuchprop", null);
        check(c, "nosuchschema_nosuchprop", null);
        check(c, "tp_complexChain/nosuchprop", "Unknown segment: nosuchprop");
        check(c, "tp_complexChain/complex/nosuchprop", "Unknown segment: nosuchprop");
        check(c, "tp_complexChain/0", "Cannot use index after segment: tp_complexChain");
        check(c, "tp_complexList/notaninteger/foo", "Missing list index after segment: tp_complexList");
        check(c, "tp_complexList/0/foo", "Index out of bounds: 0");
        check(c, "tp_stringArray/foo", "Segment must be last: tp_stringArray");
    }

    protected void check(Consumer<String> c, String xpath, String detail) {
        try {
            c.accept(xpath);
            fail();
        } catch (PropertyNotFoundException e) {
            assertEquals(xpath, e.getPath());
        }
    }

    @Test
    public void testSetValueErrors2() {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc1 = addChild(root, "doc", "TestDocument");
        InternalDocument doc2 = addChild(root, "doc2", SYS_FILE);
        doc1.setValue("tp_complexList", Collections.singletonList(Collections.emptyMap()));

        BiConsumer<String, Object> c1 = (xpath, value) -> DocumentSetValue.accept(doc1, xpath, value);
        checkSet(c1, "tp_complexList", Long.valueOf(0),
                "Expected List value for: tp_complexList, got java.lang.Long instead");
        checkSet(c1, "tp_complexList/0", Long.valueOf(0),
                "Expected Map value for: tp_complexList/0, got java.lang.Long instead");
        checkSet(c1, "tp_complexList/0", Collections.singletonMap("foo", null),
                "Unknown key: foo for tp_complexList/0");

        BiConsumer<String, Object> c2 = (xpath, value) -> DocumentSetValue.accept(doc2, xpath, value);
        checkSet(c2, SYSFILE_BLOB, Long.valueOf(0), "Expected Blob value for: sysfile_blob, got java.lang.Long instead");
    }

    protected void checkSet(BiConsumer<String, Object> c, String xpath, Object value, String message) {
        try {
            c.accept(xpath, value);
            fail();
        } catch (PropertyException e) {
            assertEquals(message, e.getMessage());
        }
    }

    @Test
    public void testSimple() throws Exception {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", SYS_FILE);

        // basic property
        doc.setValue(SYS_TITLE, "title");
        assertEquals("title", doc.getValue(SYS_TITLE));

        // array
        doc.setValue("mydc_subjects", new Object[] { "a", "b" });
        assertEquals(List.of("a", "b"), doc.getValue("mydc_subjects"));
        doc.setValue("mydc_subjects", Arrays.asList("c", "d"));
        assertEquals(List.of("c", "d"), doc.getValue("mydc_subjects"));

        // blob
        Blob blob = Blobs.createBlob("hello world!");
        doc.setValue(SYSFILE_BLOB, blob);
        blob = (Blob) doc.getValue(SYSFILE_BLOB);
        assertEquals("hello world!", blob.getString());
    }

    @Test
    public void testComplexUnset() {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", "TestDocument");
        assertNull(doc.getValue("tp_complexChain/string"));
    }

    @Test
    public void testComplex() throws Exception {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", "ComplexDoc");

        String content2 = "My content 2";
        Blob blob = Blobs.createBlob("My content");
        Blob blob2 = Blobs.createBlob(content2);
        Long size1 = Long.valueOf(123);
        Long size2 = Long.valueOf(456);
        Long size3 = Long.valueOf(789);

        Map<String, Object> attachedFile = new HashMap<>();
        List<Map<String, Object>> vignettes = new ArrayList<>();
        attachedFile.put("vignettes", vignettes);
        Map<String, Object> vignette = new HashMap<>();
        vignette.put("width", size1);
        vignette.put("content", blob);
        vignettes.add(vignette); // 0
        vignettes.add(Collections.singletonMap("height", size3)); // 1
        vignettes.add(Collections.singletonMap("height", size3)); // 2
        vignettes.add(Collections.singletonMap("height", size3)); // 3

        // set recursive
        doc.setValue("cmpf_attachedFile", attachedFile);

        // set deep
        doc.setValue("cmpf_attachedFile/vignettes/0/content", blob2);
        doc.setValue("cmpf_attachedFile/vignettes/0/content/mimeType", "text/foo");
        doc.setValue("cmpf_attachedFile/vignettes/1/width", size2);
        doc.setValue("cmpf_attachedFile/vignettes/2", new HashMap<>()); // overwrite
        doc.setValue("cmpf_attachedFile/vignettes/3", null); // overwrite

        // get deep
        assertEquals("text/foo", doc.getValue("cmpf_attachedFile/vignettes/0/content/mimeType"));
        assertEquals(size1, doc.getValue("cmpf_attachedFile/vignettes/0/width"));
        assertEquals(size2, doc.getValue("cmpf_attachedFile/vignettes/1/width"));
        assertEquals(size3, doc.getValue("cmpf_attachedFile/vignettes/1/height"));
        assertNull(doc.getValue("cmpf_attachedFile/vignettes/2/height")); // was overwritten
        assertNull(doc.getValue("cmpf_attachedFile/vignettes/3/height")); // was overwritten

        // get recursive blob
        Object b = doc.getValue("cmpf_attachedFile/vignettes/0/content");
        assertTrue(b instanceof Blob);
        assertEquals(content2, ((Blob) b).getString());

        // get recursive list item
        @SuppressWarnings("unchecked")
        Map<String, Object> v0 = (Map<String, Object>) doc.getValue("cmpf_attachedFile/vignettes/0");
        assertEquals(size1, v0.get("width"));
        b = v0.get("content");
        assertEquals(content2, ((Blob) b).getString());
        Object v1 = doc.getValue("cmpf_attachedFile/vignettes/1");
        Map<String, Object> ev1 = new HashMap<>();
        ev1.put("width", size2);
        ev1.put("height", size3);
        ev1.put("label", null);
        ev1.put("content", null);
        assertEquals(ev1, v1);
        Object v2 = doc.getValue("cmpf_attachedFile/vignettes/2");
        Map<String, Object> ev2or3 = new HashMap<>();
        ev2or3.put("width", null);
        ev2or3.put("height", null);
        ev2or3.put("label", null);
        ev2or3.put("content", null);
        assertEquals(ev2or3, v2);
        Object v3 = doc.getValue("cmpf_attachedFile/vignettes/3");
        assertEquals(ev2or3, v3);

        // get recursive list
        Object list = doc.getValue("cmpf_attachedFile/vignettes");
        assertTrue(list instanceof List);
        assertEquals(4, ((List<?>) list).size());
        assertEquals(Arrays.asList(v0, ev1, ev2or3, ev2or3), list);

        // get recursive map
        Map<String, Object> atf = new HashMap<>();
        atf.put("name", null);
        atf.put("vignettes", Arrays.asList(v0, ev1, ev2or3, ev2or3));
        assertEquals(atf, doc.getValue("cmpf_attachedFile"));
    }

    @Test
    public void testComplexFiles() {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", SYS_FILE);

        Blob blob = Blobs.createBlob("My content");
        doc.setValue("sysfiles_blobs", Collections.singletonList(Collections.singletonMap("blob", blob)));
        assertEquals(blob, doc.getValue("sysfiles_blobs/0/blob"));
    }

    @Test
    public void testBlobList() throws Exception {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", "TestDocument");

        Object list = doc.getValue("tp_fileList");
        assertTrue(list instanceof List);
        assertEquals(0, ((List<?>) list).size());

        doc.setValue("tp_fileList", Collections.singletonList(Blobs.createBlob("My content")));

        list = doc.getValue("tp_fileList");
        assertTrue(list instanceof List);
        @SuppressWarnings("unchecked")
        List<Blob> blobs = (List<Blob>) list;
        assertEquals(1, blobs.size());
        assertEquals("My content", blobs.get(0).getString());
    }

    @Test
    public void testMixin() {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", SYS_FILE);

        try {
            doc.getValue("age_age");
            fail();
        } catch (PropertyNotFoundException e) {
            assertEquals("age_age", e.getPath());
        }
        try {
            doc.setValue("age_age", "123");
            fail();
        } catch (PropertyNotFoundException e) {
            assertEquals("age_age", e.getPath());
        }

        doc.setPrimaryTypeAndMixinTypes(null, Set.of("Aged"));
        doc.setValue("age_age", "123");
        assertEquals("123", doc.getValue("age_age"));
    }

    @Test
    public void testVisit() throws Exception {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", "ComplexDoc");
        doc.setPrimaryTypeAndMixinTypes(null, Set.of("WithFile"));

        Blob blob1 = Blobs.createBlob("my_blob_1");
        Blob blob2 = Blobs.createBlob("MyBlobContent");
        doc.setValue(SYSFILE_BLOB, blob1);
        doc.setValue("cmpf_attachedFile", Map.of("vignettes", List.of( //
                Map.of("width", 123L, "content", blob2), //
                Map.of("height", 555555L))));
        doc.setValue("cpx_complex", Map.of("foo", "myfoo"));

        DummyValuesVisitor visitor = new DummyValuesVisitor(false); // keepBlobs=false
        doc.visitValues(visitor);
        assertEquals("""
                {cmpf_attachedFile={
                   vignettes=[
                     _0_ {content={
                            digest=9e5d4a656a203d269259327408944b84ee4060621f74853bc50bb8ecc36451ae,
                            encoding=UTF-8,
                            key=9e5d4a656a203d269259327408944b84ee4060621f74853bc50bb8ecc36451ae,
                            length=13,
                            mimeType=text/plain
                            },
                          width=123
                         },
                     _1_ {content={}, height=555555}
                     ]
                   },
                 cpx_complex={foo=myfoo},
                 sysfile_blob={
                   digest=71917f072b7a97653e29319a8175dfa9e9c08130c4f57f27284af0919d3d7b37,
                   encoding=UTF-8,
                   key=71917f072b7a97653e29319a8175dfa9e9c08130c4f57f27284af0919d3d7b37,
                   length=9,
                   mimeType=text/plain
                 }
                }
                """.replaceAll("[ \n]", ""), visitor.buf.toString());

        visitor = new DummyValuesVisitor(true); // keepBlobs=true
        doc.visitValues(visitor);
        assertEquals("""
                {cmpf_attachedFile={
                   vignettes=[
                     _0_ {content=<Blob:MyBlobContent>, width=123},
                     _1_ {content=<Blob:null>, height=555555}
                     ]
                   },
                 cpx_complex={foo=myfoo},
                 sysfile_blob=<Blob:my_blob_1>
                }
                """.replaceAll("[ \n]", ""), visitor.buf.toString());
    }

    /** Simple visitor doing a basic job of printing a doc as pseudo-JSON. */
    public static class DummyValuesVisitor extends BaseValuesVisitor {

        public final StringBuilder buf = new StringBuilder();

        private final boolean keepBlobs;

        public DummyValuesVisitor(boolean keepBlobs) {
            this.keepBlobs = keepBlobs;
        }

        @Override
        public boolean visitActualBlobs() {
            return keepBlobs;
        }

        @Override
        public Comparator<String> keySorter() {
            return Comparator.naturalOrder();
        }

        @Override
        public void startObject() {
            buf.append("{");
        }

        @Override
        public void endObject() {
            // delete last comma
            int last = buf.length() - 1;
            if (buf.charAt(last) == ',') {
                buf.setLength(last);
            }
            buf.append("}");
        }

        @Override
        public void startObjectElement(Field field) {
            super.startObjectElement(field);
            buf.append(field.getName());
            buf.append("=");
        }

        @Override
        public void endObjectElement() {
            super.endObjectElement();
            buf.append(",");
        }

        @Override
        public void startList() {
            buf.append("[");
        }

        @Override
        public void endList() {
            int last = buf.length() - 1;
            if (buf.charAt(last) == ',') {
                buf.setLength(last);
            }
            buf.append("]");
        }

        @Override
        public void startListElement(int i) {
            super.startListElement(i);
            buf.append("_" + i + "_");
        }

        @Override
        public void endListElement() {
            super.endListElement();
            buf.append(",");
        }

        @Override
        public void value(Object value, Field field) {
            buf.append(value);
        }

        @Override
        public void list(List<Object> list, Field listField, Field elemField) {
            buf.append(list);
        }

        @Override
        public void blob(Blob blob, Object state) {
            try {
                String string = blob == null ? "null" : blob.getString();
                buf.append("<Blob:" + string + ">");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test
    public void testBlobsVisitor() throws Exception {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", "ComplexDoc");

        Blob blob1 = Blobs.createBlob("content1", "text/plain");
        Blob blob2 = Blobs.createBlob("content2", "text/html");

        List<Map<String, Object>> vignettes = new ArrayList<>();
        vignettes.add(Collections.singletonMap("content", blob1));
        vignettes.add(Collections.singletonMap("content", blob2));
        Map<String, Object> attachedFile = new HashMap<>();
        attachedFile.put("vignettes", vignettes);
        doc.setValue("cmpf_attachedFile", attachedFile);

        // list the paths
        List<String> paths = new ArrayList<>();
        doc.visitBlobs(accessor -> paths.add(accessor.getXPath()));
        assertEquals(Arrays.asList("cmpf_attachedFile/vignettes/0/content", "cmpf_attachedFile/vignettes/1/content"),
                paths);

        // get the MIME types
        List<String> mimeTypes = new ArrayList<>();
        doc.visitBlobs(accessor -> mimeTypes.add(accessor.getBlob().getMimeType()));
        assertEquals(Arrays.asList("text/plain", "text/html"), mimeTypes);

        // set the file names
        doc.visitBlobs(accessor -> {
            Blob blob = accessor.getBlob();
            blob.setFilename("myfile-" + blob.getMimeType());
            accessor.setBlob(blob);
        });
        assertEquals("myfile-text/plain", doc.getValue("cmpf_attachedFile/vignettes/0/content/filename"));
        assertEquals("myfile-text/html", doc.getValue("cmpf_attachedFile/vignettes/1/content/filename"));

        // upload new blobs
        doc.visitBlobs(accessor -> {
            try {
                String c = accessor.getBlob().getString();
                accessor.setBlob(Blobs.createBlob(c + "-updated"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Blob b1 = (Blob) doc.getValue("cmpf_attachedFile/vignettes/0/content");
        assertEquals("content1-updated", b1.getString());
        Blob b2 = (Blob) doc.getValue("cmpf_attachedFile/vignettes/1/content");
        assertEquals("content2-updated", b2.getString());
    }

    @Test
    public void testBlobsVisitorWithOldMixin() throws Exception {
        InternalDocument root = session.getRootDocument();
        InternalDocument doc = addChild(root, "doc", "ComplexDoc");
        doc.setPrimaryTypeAndMixinTypes(null, Set.of("Aged"));

        Blob blob = Blobs.createBlob("content1", "text/plain");
        doc.setValue("cmpf_attachedFile", Collections.singletonMap("vignettes",
                Collections.singletonList(Collections.singletonMap("content", blob))));

        // simulate an obsolete Aged mixin present on the document but not in the schema manager
        Map<String, CompositeType> mixins = ((SchemaManagerBase) schemaManager).mixins;
        CompositeType agedMixin = mixins.remove("Aged");
        try {
            // list the paths
            List<String> paths = new ArrayList<>();
            doc.visitBlobs(accessor -> paths.add(accessor.getXPath()));
            assertEquals(Arrays.asList("cmpf_attachedFile/vignettes/0/content"), paths);
        } finally {
            mixins.put("Aged", agedMixin);
        }
    }

    @Test
    public void testGetLockInfoFromCache() {
        // Mocking the session, LockManager, and MongoDBConnection to ensure the same instance of MongoDBConnection
        // is used throughout the test, allowing us to accurately verify the number of method calls made on it.
        var spySession = spy(session);
        var spyLockManager = spy(spySession.getLockManager());
        var spyMongoDBConnection = spy(((MongoDBRepository) spyLockManager).getConnection());
        when(spySession.getLockManager()).thenReturn(spyLockManager);
        when(((MongoDBRepository) spyLockManager).getConnection()).thenReturn(spyMongoDBConnection);

        // Creating a document and acquiring a lock. At this point, MongoDB should be invoked to set the lock on the document.
        var doc = DBSDocument.newUnCreatedDocument(spySession.getRootDocument(), "docToLock", SYS_FILE, spySession);
        var lock = new Lock("Peter", Instant.now());
        doc.setLock(lock);
        verify(spyMongoDBConnection, times(1)).setLock(doc.getId(), lock);

        // At this point, the lock should be retrieved from the cache, so MongoDB should not be invoked.
        var lockInfo = doc.getLock();
        assertEquals(lock, lockInfo);
        verify(spyMongoDBConnection, times(0)).getLock(doc.getId());
    }

}
