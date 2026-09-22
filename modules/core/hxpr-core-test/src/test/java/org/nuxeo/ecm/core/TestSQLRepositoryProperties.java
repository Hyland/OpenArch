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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_JOB_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
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
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.model.DeltaLong;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.StateDiff;
import org.nuxeo.ecm.core.storage.dbs.DBSConnection;
import org.nuxeo.ecm.core.storage.dbs.DBSRepository;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-restriction-contrib.xml")
public class TestSQLRepositoryProperties {

    private static final String MY_FILE = "MyFile";

    @Inject
    protected CoreFeature coreFeature;

    protected SchemaManager schemaManager;

    @Inject
    protected CoreSession session;

    @Inject
    RepositoryService repositoryService;

    DocumentModel doc;

    @BeforeEach
    public void setUp() {
        schemaManager = session.getSchemaManager();
        doc = session.createDocumentModel("/", "doc", "TestDocument");
        doc.create();
    }

    protected CoreSession openNewSession() {
        return CoreInstance.getCoreSession(session.getRepositoryName(), session.getPrincipal());
    }

    protected void reopenSession() {
        session = coreFeature.reopenCoreSession();
    }

    protected void nextTransaction() {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    /** Helper function to build a simple map with minimal syntax. */
    protected static Map<String, Object> map(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid number of parameters");
        }
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }

    @Test
    public void testUnknownProperty() {
        try {
            doc.getPropertyValue("nosuchprop");
            fail("Should throw PropertyNotFoundException");
        } catch (PropertyNotFoundException e) {
            assertEquals("nosuchprop", e.getPath());
        }
        try {
            doc.getPropertyValue("tp_nosuchprop");
            fail("Should throw PropertyNotFoundException");
        } catch (PropertyNotFoundException e) {
            assertEquals("tp_nosuchprop", e.getPath());
        }
        try {
            doc.getPropertyValue("nosuchschema_nosuchprop");
            fail("Should throw PropertyNotFoundException");
        } catch (PropertyNotFoundException e) {
            assertEquals("nosuchschema_nosuchprop", e.getPath());
        }
        try {
            doc.getPropertyValue("tp_complexChain/nosuchprop");
            fail("Should throw PropertyNotFoundException");
        } catch (PropertyNotFoundException e) {
            assertEquals("tp_complexChain/nosuchprop", e.getPath());
        }
        try {
            doc.getPropertyValue("tp_complexChain/complex/nosuchprop");
            fail("Should throw PropertyNotFoundException");
        } catch (PropertyNotFoundException e) {
            assertEquals("tp_complexChain/complex/nosuchprop", e.getPath());
        }
        try {
            doc.getPropertyValue("tp_complexList/notaninteger/foo");
            fail("Should throw PropertyNotFoundException");
        } catch (PropertyNotFoundException e) {
            assertEquals("tp_complexList/notaninteger/foo", e.getPath());
        }
        try {
            doc.getPropertyValue("tp_complexList/0/foo");
            fail("Should throw PropertyNotFoundException");
        } catch (PropertyNotFoundException e) {
            assertEquals("tp_complexList/0/foo", e.getPath());
        }
    }

    @Test
    public void testStringList() {
        assertNull(doc.getPropertyValue("tp_stringArray"));
        String[] values = { "foo", "bar" };
        doc.setPropertyValue("tp_stringArray", values);
        assertEquals(Arrays.asList(values), doc.getPropertyValue("tp_stringArray"));
        // set back to null
        doc.setPropertyValue("tp_stringArray", null);
        assertNull(doc.getPropertyValue("tp_stringArray"));
        // set to empty array also reads back as null
        doc.setPropertyValue("tp_stringArray", new String[0]);
        assertNull(doc.getPropertyValue("tp_stringArray"));
    }

    @Test
    public void testDateList() {
        assertNull(doc.getPropertyValue("tp_dateArray"));
        Instant instant = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        doc.setPropertyValue("tp_dateArray", List.of(instant));
        assertEquals(List.of(instant), doc.getPropertyValue("tp_dateArray"));
        // set back to null
        doc.setPropertyValue("tp_dateArray", null);
        assertNull(doc.getPropertyValue("tp_dateArray"));
        // set to empty array also reads back as null
        doc.setPropertyValue("tp_dateArray", List.of());
        assertNull(doc.getPropertyValue("tp_dateArray"));
    }

    @Test
    public void testIntList() {
        assertNull(doc.getPropertyValue("tp_intArray"));
        Long[] values = { 1L, 2L, 3L };
        doc.setPropertyValue("tp_intArray", values);
        assertEquals(Arrays.asList(values), doc.getPropertyValue("tp_intArray"));
        // set back to null
        doc.setPropertyValue("tp_intArray", null);
        assertNull(doc.getPropertyValue("tp_intArray"));
        // set to empty array also reads back as null
        doc.setPropertyValue("tp_intArray", new Long[0]);
        assertNull(doc.getPropertyValue("tp_intArray"));
    }

    @Test
    public void testDoubleList() {
        assertNull(doc.getPropertyValue("tp_doubleArray"));
        Double[] values = { 1D, 2.2D, 3.1415D };
        doc.setPropertyValue("tp_doubleArray", values);
        assertEquals(Arrays.asList(values), doc.getPropertyValue("tp_doubleArray"));
    }

    @Test
    public void testDoubleListFromDecimals() {
        assertNull(doc.getPropertyValue("tp_doubleArray"));
        Object[] values = { new BigDecimal("123.456") };
        Object[] expected = { 123.456 };
        doc.setPropertyValue("tp_doubleArray", values);
        assertEquals(Arrays.asList(expected), doc.getPropertyValue("tp_doubleArray"));
    }

    @Test
    public void testListWithNullFirst() {
        assertNull(doc.getPropertyValue("tp_stringArray"));
        String[] values = { null, "bar" };
        doc.setPropertyValue("tp_stringArray", values);
        session.save();
        nextTransaction();
        session = coreFeature.reopenCoreSession();
        doc = session.getDocument(doc.getRef());
        assertEquals(Arrays.asList(values), doc.getPropertyValue("tp_stringArray"));
    }

    @Test
    public void testComplexListWithNullFirst() {
        doc = session.createDocumentModel("/", "doc2", "MyDocType");
        doc.create();
        // with a scalar list defined by a xs:complexType (and not a xs:simpleType)
        String[] values = { null, "bar" };
        doc.setPropertyValue("testList_participants", values);
        session.save();
        nextTransaction();
        session = coreFeature.reopenCoreSession();
        doc = session.getDocument(doc.getRef());
        assertEquals(Arrays.asList(values), doc.getPropertyValue("testList_participants"));
    }

    @Test
    public void testListConcurrentPushToEmpty() throws InterruptedException {
        doTestListConcurrentPush(Collections.emptyList());
    }

    @Test
    public void testListConcurrentPushToOneElement() throws InterruptedException {
        doTestListConcurrentPush(Arrays.asList("aaa"));
    }

    protected void doTestListConcurrentPush(List<String> start) throws InterruptedException {
        doc.setPropertyValue("tp_stringArray", start);
        session.save();
        nextTransaction();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Exception> exc = Collections.synchronizedList(new ArrayList<>());
        executor.execute(() -> addValueToDoc(doc.getRef(), "ddd", barrier, exc));
        executor.execute(() -> addValueToDoc(doc.getRef(), "eee", barrier, exc));
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        if (!exc.isEmpty()) {
            NuxeoException e = new NuxeoException("Exceptions in threads");
            exc.forEach(e::addSuppressed);
            throw e;
        }

        nextTransaction();
        doc.refresh();
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) doc.getPropertyValue("tp_stringArray");
        Set<String> expected = new HashSet<>(start);
        expected.add("ddd");
        expected.add("eee");
        assertEquals(expected, new HashSet<>(list));
    }

    protected void addValueToDoc(DocumentRef ref, String value, CyclicBarrier barrier, List<Exception> exc) {
        try {
            TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(session, s -> {
                // wait for both threads to be at the same point
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NuxeoException(e);
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new NuxeoException(e);
                }
                // add the value to the existing list
                DocumentModel d = s.getDocument(ref);
                List<Object> list = (List<Object>) d.getPropertyValue("tp_stringArray");
                if (list == null) {
                    list = List.of();
                }
                list = new ArrayList<>(list); // copy
                list.add(value);
                d.setPropertyValue("tp_stringArray", list);
                s.save();
            }));
        } catch (Exception e) { // NOSONAR
            exc.add(e);
            throw e;
        }
    }

    @Test
    public void testListConcurrentPull() throws InterruptedException {
        doc.setPropertyValue("tp_stringArray", List.of("foo", "bar", "baz"));
        session.save();
        nextTransaction();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Exception> exc = Collections.synchronizedList(new ArrayList<>());
        executor.execute(() -> removeValueFromDoc("foo", barrier, exc));
        executor.execute(() -> removeValueFromDoc("bar", barrier, exc));
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        if (!exc.isEmpty()) {
            NuxeoException e = new NuxeoException("Exceptions in threads");
            exc.forEach(e::addSuppressed);
            throw e;
        }

        nextTransaction();
        doc.refresh();
        Object object = doc.getPropertyValue("tp_stringArray");
        assertEquals(Arrays.asList("baz"), object);
    }

    protected void removeValueFromDoc(String value, CyclicBarrier barrier, List<Exception> exc) {
        try {
            TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(session, s -> {
                // wait for both threads to be at the same point
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NuxeoException(e);
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new NuxeoException(e);
                }
                // remove the value from the existing list
                DocumentModel d = s.getDocument(new PathRef("/doc"));
                List<Object> list = (List<Object>) d.getPropertyValue("tp_stringArray");
                if (list == null) {
                    list = List.of();
                }
                list = new ArrayList<>(list); // copy
                list.remove(value);
                d.setPropertyValue("tp_stringArray", list);
                s.save();
            }));
        } catch (Exception e) { // NOSONAR
            exc.add(e);
            throw e;
        }
    }

    @Test
    public void testComplexList() {
        // not null on list
        assertTrue(doc.getPropertyValue("tp_complexList") instanceof List);
        assertEquals(0, ((List<?>) doc.getPropertyValue("tp_complexList")).size());
        ArrayList<Map<String, Object>> values = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("string", "foo");
        Instant instant = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        item.put("date", instant);
        item.put("int", 3L);
        values.add(item);
        doc.setPropertyValue("tp_complexList", values);

        Object actual = doc.getPropertyValue("tp_complexList");
        assertTrue(actual instanceof List);
        assertEquals(1, ((List<?>) actual).size());
        assertEquals(item, ((List<?>) actual).get(0));
    }

    @Test
    public void testComplexListChange() {
        List<Map<String, Object>> values = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        Map<String, Object> item2 = new HashMap<>();
        Map<String, Object> item3 = new HashMap<>();
        List<?> actual;

        item1.put("string", "foo");
        item1.put("int", Long.valueOf(123));
        values.add(item1);
        doc.setPropertyValue("tp_complexList", values);

        session.save();

        reopenSession();
        doc = session.getDocument(doc.getRef());

        actual = (List<?>) doc.getPropertyValue("tp_complexList");
        assertEquals(1, actual.size());
        assertComplexListElements(actual, 0, "foo", 123);

        // add to list
        // (also modifies first element)
        item1.put("string", "foo2");

        item2.put("string", "bar");
        item2.put("int", Long.valueOf(999));
        values.add(item2);
        item3.put("string", "baz");
        item3.put("int", Long.valueOf(314));
        values.add(item3);
        doc.setPropertyValue("tp_complexList", values);

        session.save();

        reopenSession();
        doc = session.getDocument(doc.getRef());

        actual = (List<?>) doc.getPropertyValue("tp_complexList");
        assertEquals(3, actual.size());
        assertComplexListElements(actual, 0, "foo2", 123);
        assertComplexListElements(actual, 1, "bar", 999);
        assertComplexListElements(actual, 2, "baz", 314);

        // change list

        item1.put("int", Long.valueOf(111));
        item2.put("int", Long.valueOf(222));
        item3.put("int", Long.valueOf(333));
        doc.setPropertyValue("tp_complexList", values);

        session.save();

        reopenSession();
        doc = session.getDocument(doc.getRef());

        actual = (List<?>) doc.getPropertyValue("tp_complexList");
        assertEquals(3, actual.size());
        assertComplexListElements(actual, 0, "foo2", 111);
        assertComplexListElements(actual, 1, "bar", 222);
        assertComplexListElements(actual, 2, "baz", 333);

        // remove from list

        values.remove(0);
        values.remove(0);
        doc.setPropertyValue("tp_complexList", values);

        session.save();

        reopenSession();
        doc = session.getDocument(doc.getRef());

        actual = (List<?>) doc.getPropertyValue("tp_complexList");
        assertEquals(1, actual.size());
        assertComplexListElements(actual, 0, "baz", 333);
    }

    @Test
    public void testComplexListChangeAfterClear() { // NOSONAR (no need to assert anything)
        // complex list with one element
        doc.setPropertyValue("tp_complexList", List.of(Map.of("string", "foo")));
        session.save();

        // clear
        doc.setPropertyValue("tp_complexList", Collections.emptyList());
        // don't save the session here
        // re-add one element (-> delete + insert in database)
        doc.setPropertyValue("tp_complexList", List.of(Map.of("string", "bar")));
        session.save(); // save succeeds, no unique constraint problem
    }

    @Test
    public void testComplexListNullInStorage() {
        // we check for the fact that storage is null (actually unset) by using
        // a query (which would fail if we stored an empty list)
        String query = "SELECT * FROM TestDocument WHERE tp_complexList IS NULL";
        DocumentModelList dml;

        // on create
        session.save();
        dml = session.query(query);
        assertEquals(1, dml.size());

        // on update
        doc.setPropertyValue("tp_complexList", List.of(map("string", "foo")));
        session.save();
        doc.setPropertyValue("tp_complexList", null);
        session.save();

        // check after update
        dml = session.query(query);
        assertEquals(1, dml.size());
    }

    // DBS-only test for in-db data corruption(?) (NXP-21278)
    @Test
    public void testComplexListElementNullInStorage() {
        doc.setPropertyValue("tp_complexList", List.of( //
                Collections.singletonMap("string", "foo"), //
                Collections.singletonMap("string", "bar")));
        String id = doc.getId();
        session.save();
        nextTransaction();

        // change data
        StateDiff diff = new StateDiff();
        State state = new State();
        state.put("string", "bar");
        diff.put("tp_complexList", Arrays.asList( //
                null, // null as first element of the list
                state));
        changeDoc(id, diff);

        // check that we don't crash on read
        doc = session.getDocument(doc.getRef());
        List<?> list = (List<?>) doc.getPropertyValue("tp_complexList");
        assertEquals(2, list.size());
        assertComplexListElements(list, 0, null, -1);
        assertComplexListElements(list, 1, "bar", -1);
    }

    @Test
    public void testComplexListPartialUpdate() {
        List<Map<String, Object>> list = Arrays.asList(Collections.singletonMap("string", "foo"));
        doc.setPropertyValue("tp_complexList", list);
        session.save();

        // change just the int
        doc.setPropertyValue("tp_complexList/0/int", Long.valueOf(1));
        session.save();

        // refetch, the string should still be here
        doc = session.getDocument(doc.getRef());
        List<?> actual = (List<?>) doc.getPropertyValue("tp_complexList");
        assertEquals(1, actual.size());
        assertComplexListElements(actual, 0, "foo", 1);
    }

    protected static void assertComplexListElements(List<?> list, int i, String string, int theint) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) list.get(i);
        assertEquals(string, map.get("string"));
        assertEquals(theint == -1 ? null : Long.valueOf(theint), map.get("int"));
    }

    @Test
    public void testComplexListConcurrentPushToEmpty() throws InterruptedException {
        doTestComplexListConcurrentPush(Collections.emptyList());
    }

    @Test
    public void testComplexListConcurrentPushToOneElement() throws InterruptedException {
        doTestComplexListConcurrentPush(Arrays.asList("aaa"));
    }

    protected void doTestComplexListConcurrentPush(List<String> startStrings) throws InterruptedException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String string : startStrings) {
            list.add(Collections.singletonMap("string", string));
        }
        doc.setPropertyValue("tp_complexList", list);
        session.save();
        nextTransaction();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Exception> exc = Collections.synchronizedList(new ArrayList<>());
        executor.execute(() -> addComplexToDoc(doc.getRef(), "ddd", barrier, exc));
        executor.execute(() -> addComplexToDoc(doc.getRef(), "eee", barrier, exc));
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        if (!exc.isEmpty()) {
            NuxeoException e = new NuxeoException("Exceptions in threads");
            exc.forEach(e::addSuppressed);
            throw e;
        }

        nextTransaction();
        doc.refresh();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> newList = (List<Map<String, Object>>) doc.getPropertyValue("tp_complexList");
        Set<String> expected = new HashSet<>(startStrings);
        expected.add("ddd");
        expected.add("eee");
        Set<String> actual = new HashSet<>();
        for (Map<String, Object> map : newList) {
            actual.add((String) map.get("string"));
        }
        assertEquals(expected, actual);
    }

    protected void addComplexToDoc(DocumentRef ref, String string, CyclicBarrier barrier, List<Exception> exc) {
        try {
            TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(session, s -> {
                // wait for both threads to be at the same point
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NuxeoException(e);
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new NuxeoException(e);
                }
                // add the value to the existing list
                DocumentModel d = s.getDocument(ref);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) d.getPropertyValue("tp_complexList");
                list.add(Collections.singletonMap("string", string));
                d.setPropertyValue("tp_complexList", list);
                s.save();
            }));
        } catch (Exception e) { // NOSONAR
            exc.add(e);
            throw e;
        }
    }

    // NXP-2468
    @Test
    public void testBlobListValue() {
        // not null on list
        assertTrue(doc.getPropertyValue("tp_fileList") instanceof List);
        assertEquals(0, ((List<?>) doc.getPropertyValue("tp_fileList")).size());
        ArrayList<Blob> values = new ArrayList<>();
        Blob blob = Blobs.createBlob("My content");
        values.add(blob);
        doc.setPropertyValue("tp_fileList", values);

        Object actual = doc.getPropertyValue("tp_fileList");
        assertTrue(actual instanceof List);

        @SuppressWarnings("unchecked")
        List<Blob> blobs = (List<Blob>) actual;
        assertEquals(1, blobs.size());
        assertNotNull(blobs.get(0));
    }

    // NXP-2301
    @Test
    public void testSubBlobValue() {
        // not null on list
        assertTrue(doc.getPropertyValue("tp_fileComplexList") instanceof List);
        assertEquals(0, ((List<?>) doc.getPropertyValue("tp_fileComplexList")).size());
        ArrayList<Map<String, Object>> values = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        Blob blob = Blobs.createBlob("My content");
        item.put("blob", blob);
        item.put("filename", "My filename");
        values.add(item);
        doc.setPropertyValue("tp_fileComplexList", values);

        Object actual = doc.getPropertyValue("tp_fileComplexList");
        assertTrue(actual instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) actual;
        assertEquals(1, items.size());
        assertNotNull(items.get(0));
        Map<String, Object> actualItem = items.get(0);
        assertEquals("My filename", actualItem.get("filename"));
        assertTrue(actualItem.get("blob") instanceof Blob);

        Blob actualBlob = (Blob) doc.getPropertyValue("tp_fileComplexList/0/blob");
        assertNotNull(actualBlob);
    }

    @Test
    public void testComplexPropertySubValue() {
        doc.setPropertyValue("tp_complex/string", "test");
        assertEquals("test", doc.getPropertyValue("tp_complex/string"));
    }

    @Test
    public void testTwoComplexInSameSchema() throws InterruptedException {
        // create a doc with two complex lists in the same schema
        doc = session.createDocumentModel("/", "mydoc", "MyDocType2");
        doc.create();
        DocumentRef docRef = doc.getRef();
        session.save();
        nextTransaction();
        doc.refresh();
        assertNull(doc.getPropertyValue("cpx_complex"));

        // another thread changes one of the complex properties
        Thread t = new Thread(() -> TransactionHelper.runInTransaction(
                () -> CoreInstance.doPrivileged(session.getRepositoryName(), s -> {
                    DocumentModel d = s.getDocument(docRef);
                    d.setPropertyValue("cpx_complex", map("foo", "1"));
                    s.save();
                })));
        t.start();
        t.join();

        // the main thread needs to commit the transaction (long-running process for instance)
        nextTransaction();
        doc.refresh();

        // change property "complex2"
        doc.setPropertyValue("cpx_complex2", map("bar", "1"));
        session.save();

        // check that the value changed by the thread hasn't been overwritten
        doc.refresh();
        assertEquals(map("foo", "1", "bar", null), doc.getPropertyValue("cpx_complex"));
    }

    @Test
    public void testSaveComplexTwice() {
        testComplexList();
        doc.setPropertyValue("tp_stringArray", new String[] {}); // dirty dp
        session.save();
    }

    // not many tests, logs have to be looked at to confirm behavior
    @Test
    public void testUpdateMinimalChanges() {
        // populate some properties
        testStringList();
        testDateList();
        testComplexList();
        testBlobListValue();
        session.save();

        reopenSession();
        doc.refresh();
        // change just one of the collection properties
        doc.setPropertyValue("tp_stringArray", new String[] { "baz" });
        session.save();
        // check that the minimal number of updates are done in the db
    }

    // toplevel complex list
    @Test
    public void testXPath1UnCreated() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doTestXPath1(doc);
    }

    @Test
    public void testXPath1() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        doTestXPath1(doc);
    }

    private void doTestXPath1(DocumentModel doc) {
        List<Object> files = new ArrayList<>(2);
        Map<String, Object> f = new HashMap<>();
        Blob blob = Blobs.createBlob("blob1");
        f.put("blob", blob);
        files.add(f);
        doc.setPropertyValue("sysfiles_blobs", files);
        assertEquals(blob, doc.getPropertyValue("sysfiles_blobs/0/blob"));
    }

    // other complex list
    @Test
    public void testXPath2UnCreated() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "ComplexDoc");
        doTestXPath2(doc);
    }

    @Test
    public void testXPath2() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "ComplexDoc");
        doc.create();
        doTestXPath2(doc);
    }

    private void doTestXPath2(DocumentModel doc) {
        HashMap<String, Object> attachedFile = new HashMap<>();
        List<Map<String, Object>> vignettes = new ArrayList<>();
        attachedFile.put("vignettes", vignettes);
        Map<String, Object> vignette = new HashMap<>();
        Long width = Long.valueOf(123);
        vignette.put("width", width);
        vignettes.add(vignette);
        doc.setPropertyValue("cmpf_attachedFile", attachedFile);
        assertEquals(width, doc.getPropertyValue("cmpf_attachedFile/vignettes/0/width"));
    }

    @Test
    public void testRestriction() {
        doc = session.createDocumentModel("/", "doc2", "Restriction");
        doc.setPropertyValue("restr_shortstring", "foo");
        doc.create();
        doc = session.getDocument(doc.getRef());
        assertEquals("foo", doc.getPropertyValue("restr_shortstring"));
    }

    @Test
    public void testPropertyIsSameAsBlob() {
        doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();

        DocumentModel doc2 = session.createDocumentModel("/", "file2", SYS_FILE);
        Blob blob2 = Blobs.createBlob("hello world!");
        doc2.setPropertyValue(SYSFILE_BLOB, blob2);
        doc2.create();

        // same blob content, should compare equal

        DocumentModel doc3 = session.createDocumentModel("/", "file3", SYS_FILE);
        Blob blob3 = Blobs.createBlob("hello world!");
        doc3.setPropertyValue(SYSFILE_BLOB, blob3);
        doc3.create();

        // different blob content

        DocumentModel doc4 = session.createDocumentModel("/", "file3", SYS_FILE);
        Blob blob4 = Blobs.createBlob("this is goodbye");
        doc4.setPropertyValue(SYSFILE_BLOB, blob4);
        doc4.create();

        // compare directly two StorageBlobs
        // same
        assertEquals(doc2.getPropertyValue(SYSFILE_BLOB), doc3.getPropertyValue(SYSFILE_BLOB));
        // different
        assertNotEquals(doc2.getPropertyValue(SYSFILE_BLOB), doc4.getPropertyValue(SYSFILE_BLOB));

        // compare a StringBlob and a StorageBlob
        assertEquals(blob2, doc3.getPropertyValue(SYSFILE_BLOB));
        assertEquals(doc3.getPropertyValue(SYSFILE_BLOB), blob2);

        // compare a StringBlob and a StorageBlob
        assertEquals(blob3, doc3.getPropertyValue(SYSFILE_BLOB));
        assertEquals(doc3.getPropertyValue(SYSFILE_BLOB), blob3);

        // compare a StringBlob and a StringBlob
        assertEquals(blob2, blob3);
        assertEquals(blob3, blob2);

        // compare a StringBlob with a different StringBlob
        assertNotEquals(blob2, blob4);
        assertNotEquals(blob4, blob2);
        assertNotEquals(blob3, blob4);
        assertNotEquals(blob4, blob3);
    }

    @Test
    public void testPropertyDelta() {
        int base = 100;
        int delta = 123;
        doc = session.createDocumentModel("/", "doc", "MyDocType");
        doc.setPropertyValue("my_integer", Long.valueOf(base));
        doc.create();
        session.save();

        doc.setPropertyValue("my_integer", DeltaLong.valueOf(Long.valueOf(base), delta));

        // write another property in the same schema
        // to make sure the delta is not applied twice
        doc.setPropertyValue("my_string", "foo");

        session.save();
        doc = session.getDocument(new IdRef(doc.getId()));

        // after save() and refetch we now have a Long
        Object value = doc.getPropertyValue("my_integer");
        assertTrue(value instanceof Long, value.getClass().getName());
        assertEquals(Long.valueOf(base + delta), value);

        // write another property in the same schema
        // to make sure the delta is not applied twice
        doc.setPropertyValue("my_string", "bar");
        session.save();

        reopenSession();

        // after refetch it's a Long with the correct incremented value
        doc = session.getDocument(new IdRef(doc.getId()));
        value = doc.getPropertyValue("my_integer");
        assertTrue(value instanceof Long, value.getClass().getName());
        assertEquals(Long.valueOf(base + delta), value);
    }

    @Test
    public void testPropertyDeltaTwice() {
        int base = 100;
        int delta = 123;
        doc = session.createDocumentModel("/", "doc", "MyDocType");
        Long v1 = Long.valueOf(base);
        doc.setPropertyValue("my_integer", v1);
        doc.create();
        session.save();

        DeltaLong v2 = DeltaLong.valueOf(v1, delta);
        doc.setPropertyValue("my_integer", v2);
        DeltaLong v3 = DeltaLong.valueOf(v2, delta);
        doc.setPropertyValue("my_integer", v3);

        session.save();

        reopenSession();

        // after refetch it's a Long with the correct incremented value
        doc = session.getDocument(new IdRef(doc.getId()));
        Object value = doc.getPropertyValue("my_integer");
        assertTrue(value instanceof Long, value.getClass().getName());
        assertEquals(Long.valueOf(base + delta * 2), value);
    }

    @Test
    public void testPropertyDeltaWithoutSave() {
        doc = session.createDocumentModel("/", "doc", "MyDocType");
        doc.setPropertyValue("my_integer", Long.valueOf(100));
        doc.create();
        session.save();

        // reset to 0 then apply increment without intervening database save
        doc.setPropertyValue("my_integer", Long.valueOf(0));
        doc.setPropertyValue("my_integer", DeltaLong.valueOf(Long.valueOf(0), 123));
        session.save();

        reopenSession();

        // refetch
        doc = session.getDocument(new IdRef(doc.getId()));
        Object value = doc.getPropertyValue("my_integer");
        assertTrue(value instanceof Long, value.getClass().getName());
        assertEquals(Long.valueOf(123), value);
    }

    @Test
    public void testPropertyDeltaAfterNull() {
        doc = session.createDocumentModel("/", "doc", "MyDocType");
        doc.setPropertyValue("my_integer", Long.valueOf(0));
        doc.create();
        session.save();

        // set to null
        doc.setPropertyValue("my_integer", null);
        session.save();

        // now apply the delta
        doc.setPropertyValue("my_integer", DeltaLong.valueOf(null, 123));
        session.save();

        reopenSession();

        // after refetch it's a Long with the correct value
        doc = session.getDocument(new IdRef(doc.getId()));
        Object value = doc.getPropertyValue("my_integer");
        assertNotNull(value);
        assertTrue(value instanceof Long, value.getClass().getName());
        assertEquals(123, ((Long) value).longValue());
    }

    /**
     * Checks that writing several documents using batching with some of them having Delta and some not doesn't fail.
     */
    @Test
    public void testPropertyDeltaBatching() {
        int n = 10;
        int base = 100;
        for (int i = 0; i < n; i++) {
            DocumentModel doc = session.createDocumentModel("/", "doc" + i, "MyDocType");
            doc.setPropertyValue("my_integer", Long.valueOf(base));
            doc.create();
        }
        session.save();

        // updates

        for (int i = 0; i < n; i++) {
            DocumentModel doc = session.getDocument(new PathRef("/doc" + i));
            Object value;
            if (i < n / 2) {
                value = Long.valueOf(i);
            } else {
                // delta whose base is not the actual base, to check
                // that we really do an increment instead of setting
                // the full value
                value = DeltaLong.valueOf(Long.valueOf(base), i);
            }
            doc.setPropertyValue("my_integer", value);
            if (i % 2 == 0) {
                // also sometimes change another property
                doc.setPropertyValue("my_string", "foo" + i);
            }
        }
        session.save();

        // check result after re-reading from database
        reopenSession();

        for (int i = 0; i < n; i++) {
            DocumentModel doc = session.getDocument(new PathRef("/doc" + i));
            Object value = doc.getPropertyValue("my_integer");
            Object expected;
            if (i < n / 2) {
                expected = Long.valueOf(i);
            } else {
                expected = Long.valueOf(base + i);
            }
            assertEquals(expected, value, doc.getName());
        }
    }

    /**
     * Checks that even on document creation using a Delta doesn't fail.
     */
    @Test
    @Disabled("fails due to everything now prefetched; may be removed if DeltaLong is removed")
    public void testPropertyDeltaOnCreate() {
        doc = session.createDocumentModel("/", "doc", "MyDocType");
        doc.setPropertyValue("my_integer", DeltaLong.valueOf(Long.valueOf(100), 123));
        doc.create();
        session.save();

        Object value = doc.getPropertyValue("my_integer");
        assertTrue(value instanceof Long, value.getClass().getName());
        assertEquals(223, ((Long) value).longValue());
    }

    @Test
    public void testMigrationListVsString() {
        // create a doc
        DocumentModel doc = session.createDocumentModel("/", "domain", "MyDocType");
        doc.create();
        String id = doc.getId();
        session.save();

        nextTransaction();

        // change data
        StateDiff diff = new StateDiff();
        diff.put("mydc_subjects", "a"); // put a string instead of an array
        diff.put(SYS_TITLE, List.of("aa", "bb")); // put a list instead of a string
        changeDoc(id, diff);

        // check that we don't crash on read
        doc = session.getDocument(doc.getRef());
        assertEquals(List.of("a"), doc.getPropertyValue("mydc_subjects"));
        assertEquals("aa", doc.getPropertyValue(SYS_TITLE));
    }

    // change data in the database to make it like we just migrated the fields from different types
    protected void changeDoc(String id, StateDiff diff) {
        Repository repository = repositoryService.getRepository(session.getRepositoryName());
        try (DBSConnection connection = ((DBSRepository) repository).getConnection()) {
            connection.updateState(id, diff, null);
        }
    }

    @Test
    public void testComplexListArrayElementResetWithNull() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", "MyDocType");
        doc.setPropertyValue("testList_complexlist", List.of(Map.of("array", new String[] { "foo" })));
        doc.create();
        session.save();

        // set map value to null
        doc.setPropertyValue("testList_complexlist", List.of(Collections.singletonMap("array", null)));
        session.save();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) doc.getPropertyValue("testList_complexlist");
        assertEquals(1, list.size());
        Map<String, Object> map = list.get(0);
        Object[] array = (Object[]) map.get("array");
        assertNull(array);
    }

    @Test
    public void testComplexListArrayElementResetWithEmptyArray() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", "MyDocType");
        doc.setPropertyValue("testList_complexlist", List.of(Map.of("array", new String[] { "foo" })));
        doc.create();
        session.save();

        // set map value to empty array
        doc.setPropertyValue("testList_complexlist", List.of(Map.of("array", new String[] {})));
        session.save();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) doc.getPropertyValue("testList_complexlist");
        assertEquals(1, list.size());
        Map<String, Object> map = list.get(0);
        Object[] array = (Object[]) map.get("array");
        assertNull(array);
    }

    @Test
    public void testComplexListArrayElementModify() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", "MyDocType");
        doc.setPropertyValue("testList_complexlist", List.of(Map.of("array", new String[] { "foo" })));
        doc.create();
        session.save();

        // replace map value with new array of same size
        doc.setPropertyValue("testList_complexlist", List.of(Map.of("array", new String[] { "bar" })));
        session.save();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) doc.getPropertyValue("testList_complexlist");
        assertEquals(1, list.size());
        Map<String, Object> map = list.get(0);
        assertEquals(List.of("bar"), map.get("array"));
    }

    @Test
    public void testComplexListArrayElementGrow() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", "MyDocType");
        doc.setPropertyValue("testList_complexlist", List.of(Map.of("array", new String[] { "foo" })));
        doc.create();
        session.save();

        // replace map value with new array of bigger size
        doc.setPropertyValue("testList_complexlist", List.of(Map.of("array", new String[] { "bar", "baz" })));
        session.save();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) doc.getPropertyValue("testList_complexlist");
        assertEquals(1, list.size());
        Map<String, Object> map = list.get(0);
        assertEquals(List.of("bar", "baz"), map.get("array"));
    }

    @Test
    public void testComplexListArrayElementShrink() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", "MyDocType");
        doc.setPropertyValue("testList_complexlist", List.of(Map.of("array", new String[] { "foo", "bar" })));
        doc.create();
        session.save();

        // replace map value with new array of smaller size
        doc.setPropertyValue("testList_complexlist", List.of(Map.of("array", new String[] { "baz" })));
        session.save();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) doc.getPropertyValue("testList_complexlist");
        assertEquals(1, list.size());
        Map<String, Object> map = list.get(0);
        assertEquals(List.of("baz"), map.get("array"));
    }

    /**
     * <pre>
     * write {"foo": "foo1"}
     * =&gt; {"foo": "foo1", "bar": null}
     * </pre>
     */
    @Test
    public void testGetComplexMap() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", "MyDocType2");
        doc.setPropertyValue("cpx_complex", map("foo", "foo1"));
        doc.create();
        session.save();

        // check
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedMap = (Map<String, Object>) doc.getPropertyValue("cpx_complex");
        Map<String, Object> expectedMap = map("foo", "foo1", "bar", null);
        assertEquals(expectedMap, updatedMap);
    }

    /**
     * <pre>
     * from {"foo": "foo1", "bar": "bar1"}
     * write {"foo": "foo2"}
     * =&gt; {"foo": "foo2", "bar": null}
     * COMPAT {"foo": "foo2", "bar": "bar1"}
     * </pre>
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSetComplexMap() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", "MyDocType2");
        doc.setPropertyValue("cpx_complex", map("foo", "foo1", "bar", "bar1"));
        doc.create();
        session.save();

        // update
        doc.setPropertyValue("cpx_complex", map("foo", "foo2"));
        session.save();

        // check
        Map<String, Object> updatedMap = (Map<String, Object>) doc.getPropertyValue("cpx_complex");
        Map<String, Object> expectedMap = map("foo", "foo2", "bar", null);
        assertEquals(expectedMap, updatedMap);

        // update again
        // do an individual update through property xpath
        doc.setPropertyValue("cpx_complex/bar", "bar2");

        // check
        updatedMap = (Map<String, Object>) doc.getPropertyValue("cpx_complex");
        expectedMap = map("foo", "foo2", "bar", "bar2");
        assertEquals(expectedMap, updatedMap);
    }

    /**
     * <pre>
     * from {"foo": "foo1", "bar": "bar1"}
     * write {"foo": null}
     * =&gt; {"foo": null, "bar": null}
     * COMPAT: {"foo": null, "bar": "bar1"}
     * </pre>
     */
    @Test
    public void testSetComplexMapWithNullValue() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", "MyDocType2");
        doc.setPropertyValue("cpx_complex", map("foo", "foo1", "bar", "bar1"));
        doc.create();
        session.save();

        // update with a null value
        doc.setPropertyValue("cpx_complex", map("foo", null));
        session.save();

        // check
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedMap = (Map<String, Object>) doc.getPropertyValue("cpx_complex");
        Map<String, Object> expectedMap = map("foo", null, "bar", null);
        assertEquals(expectedMap, updatedMap);
    }

    /**
     * <pre>
     * from [{"foo": "foo1", "bar": "bar1"}]
     * write [{"foo": "foo2"}]
     * =&gt; [{"foo": "foo2", "bar": null}]
     * COMPAT: [{"foo": "foo2", "bar": "bar1"}]
     * </pre>
     */
    @Test
    public void testSetComplexMapInList() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", "MyDocType2");
        doc.setPropertyValue("cpxl_complexList", List.of(map("foo", "foo1", "bar", "bar1")));
        doc.create();
        session.save();

        // update
        List<Map<String, Object>> updateList = Arrays.asList(map("foo", "foo2"));
        doc.setPropertyValue("cpxl_complexList", updateList);
        session.save();

        // check
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updatedList = (List<Map<String, Object>>) doc.getPropertyValue("cpxl_complexList");
        List<Map<String, Object>> expectedList = Arrays.asList(map("foo", "foo2", "bar", null));
        assertEquals(expectedList, updatedList);
    }

    @Test
    public void testInternalSystemPropertiesOnVersion() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_FILE);
        doc.create();
        DocumentRef verRef = doc.checkIn();
        CoreInstance.doPrivileged(session.getRepositoryName(), systemSession -> {
            DocumentModel version = systemSession.getDocument(verRef);
            version.setPropertyValue(SYS_FULLTEXT_BINARY, "foobar");
            version.setPropertyValue(SYS_FULLTEXT_JOB_ID, "foobar");
            version.setPropertyValue(SYS_IS_TRASHED, Boolean.TRUE);
        });
    }

    @Test
    public void testSystemPropertyFulltextBinary() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        String fulltext = "hello world";
        CoreInstance.doPrivileged(session.getRepositoryName(), systemSession -> {
            systemSession.getDocument(doc.getRef()).setPropertyValue(SYS_FULLTEXT_BINARY, fulltext);
        });
        String res = (String) doc.getPropertyValue(SYS_FULLTEXT_BINARY);
        assertEquals(fulltext, res);
        assertEquals(fulltext, doc.getPropertyValue(SYS_FULLTEXT_BINARY));
    }

    @Test
    public void testDecimal() {
        doc = session.createDocumentModel("/", "doc", "MyDocType");
        BigDecimal d = new BigDecimal("1234567890.1234567890123456789");
        doc.setPropertyValue("my_decimal", d);
        doc.create();
        session.save();

        reopenSession();
        doc.refresh();

        assertEquals(d, doc.getPropertyValue("my_decimal"));
    }

    @Test
    public void testDecimalStoredInDouble() {
        doc = session.createDocumentModel("/", "doc", "MyDocType");
        BigDecimal input = new BigDecimal("1234567890.1234567890123456789");
        double expected = 1234567890.12345678901234567D; // precision is lost
        doc.setPropertyValue("my_double", input);
        doc.create();
        session.save();

        reopenSession();
        doc.refresh();

        assertEquals(expected, doc.getPropertyValue("my_double"));
    }

    @Test
    public void testFloatStoredInDouble() {
        doc = session.createDocumentModel("/", "doc", "MyDocType");
        float input = 123.456F;
        double expected = 123.456D; // and not 123.45600128173828 (if we used Float.doubleValue())
        doc.setPropertyValue("my_double", input);
        doc.create();
        session.save();

        reopenSession();
        doc.refresh();

        assertEquals(expected, doc.getPropertyValue("my_double"));
    }

    @Test
    public void testStringStoredInDouble() {
        doc = session.createDocumentModel("/", "doc", "MyDocType");
        String input = "123.456";
        double expected = 123.456D;
        doc.setPropertyValue("my_double", input);
        doc.create();
        session.save();

        reopenSession();
        doc.refresh();

        assertEquals(expected, doc.getPropertyValue("my_double"));
    }

}
