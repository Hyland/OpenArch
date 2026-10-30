/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.opensearch.test.nxql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.inject.Inject;

import com.hyland.openarch.async.tasks.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IterableQueryResult;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.trash.TrashService;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.opensearch.api.OpenSearchResult;
import com.hyland.openarch.opensearch.core.OpenSearchResultSetImpl;
import com.hyland.openarch.opensearch.query.OpenSearchQueryBuilder;
import com.hyland.openarch.opensearch.test.RepositoryOpenSearchFeature;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
public class TestCompareQueryAndFetch {

    private static final String MY_FILE = "MyFile";

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService oss;

    @Inject
    protected TrashService trashService;

    @BeforeEach
    public void initWorkingDocuments() throws Exception {
        oss.initIndexes(true);
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
        Instant cal = Instant.parse("2000-01-02T03:04:05.006Z");
        for (int i = 0; i < 5; i++) {
            String name = "file" + i;
            DocumentModel doc = session.createDocumentModel("/", name, MY_FILE);
            doc.setPropertyValue(SYS_TITLE, "File" + i);
            doc.setPropertyValue("mydc_nature", "Nature" + i);
            doc.setPropertyValue("mydc_rights", "Rights" + i % 2);
            doc.setPropertyValue("mydc_issued", cal);
            doc.create();
        }
        for (int i = 5; i < 10; i++) {
            String name = "note" + i;
            DocumentModel doc = session.createDocumentModel("/", name, MY_FILE);
            doc.setPropertyValue(SYS_TITLE, "Note" + i);
            doc.setPropertyValue("mydc_nature", "Nature" + i);
            doc.setPropertyValue("mydc_rights", "Rights" + i % 2);
            doc.create();
        }

        DocumentModel doc = session.createDocumentModel("/", "hidden", SYS_FOLDER);
        doc.setPropertyValue(SYS_TITLE, "HiddenFolder");
        doc.create();

        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.setPropertyValue(SYS_TITLE, "Folder");
        folder.create();

        session.checkIn(new PathRef("/file3"));

        trashService.trashDocument(session.getDocument(new PathRef("/file1")));
        trashService.trashDocument(session.getDocument(new PathRef("/note5")));

        session.checkIn(new PathRef("/file2"));

        TransactionHelper.commitOrRollbackTransaction();

        // wait for async tasks
        TaskService asyncTasksService = Framework.getService(TaskService.class);
        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(20L)));

        oss.refresh();
        TransactionHelper.startTransaction();
    }

    protected String getDigest(IterableQueryResult docs) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> doc : docs) {
            List<String> keys = new ArrayList<>(doc.keySet());
            Collections.sort(keys);
            Map<String, Object> sortedMap = new TreeMap<>();
            for (String key : keys) {
                Object value = doc.get(key);
                if (key.equals("sys_name") || key.equals("sys_parentId") || (value == null)) {
                    // MongoDB returns explicit nulls
                    continue;
                }
                sortedMap.put(key, value);
            }
            sb.append(sortedMap.entrySet().toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    protected void assertSameDocumentLists(IterableQueryResult expected, IterableQueryResult actual) throws Exception {
        assertEquals(getDigest(expected), getDigest(actual));
    }

    protected void compareOpenSearchAndCore(String nxql) throws Exception {
        IterableQueryResult coreResult = session.queryAndFetch(nxql);
        OpenSearchResult openSearchRes = oss.queryAndAggregate(new OpenSearchQueryBuilder(session).hxql(nxql).limit(20));
        try (IterableQueryResult openSearchResult = openSearchRes.getRows()) {
            assertSameDocumentLists(coreResult, openSearchResult);
            coreResult.close();
        }
    }

    @Test
    public void testSimpleSearchWithSort() throws Exception {
        compareOpenSearchAndCore("select sys_id, sys_title, mydc_nature from SysContent order by sys_id");
        compareOpenSearchAndCore("select sys_id, sys_title from SysContent where sys_isFolderish = FALSE order by sys_id");
        compareOpenSearchAndCore("select sys_id, mydc_nature from SysFile order by mydc_nature, sys_id");
        // TODO some timezone issues here...
        // compareOpenSearchAndCore("select sys_id, mydc_issued from SysFile order by sys_id");
    }

    @Test
    public void testSortStringsIsCaseInsensitiveAndMissingLast() throws Exception {
        createSortDocument("apple", "b", List.of("x", "Y"));
        createSortDocument("Zebra", "A", List.of("Z"));
        createSortDocument("banana", null, null);
        createSortDocument("Cherry", "", List.of("a"));
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        assertTrue(Framework.getService(TaskService.class).awaitCompletion(Duration.ofSeconds(20L)));
        oss.refresh();
        TransactionHelper.startTransaction();

        String select = "select sys_title from MyFile where mydc_nature = 'sorted' order by ";
        assertEquals(List.of("apple", "banana", "Cherry", "Zebra"), queryTitles(select + "sys_title"));
        assertEquals(List.of("Zebra", "Cherry", "banana", "apple"), queryTitles(select + "sys_title desc"));
        // empty values are values, a document without one goes last in both directions
        assertEquals(List.of("Cherry", "Zebra", "apple", "banana"), queryTitles(select + "mydc_rights"));
        assertEquals(List.of("apple", "Zebra", "Cherry", "banana"), queryTitles(select + "mydc_rights desc"));
        // a list sorts on its lowest value when ascending and its highest when descending
        assertEquals(List.of("Cherry", "apple", "Zebra", "banana"), queryTitles(select + "mydc_subjects"));
        assertEquals(List.of("Zebra", "apple", "Cherry", "banana"), queryTitles(select + "mydc_subjects desc"));
    }

    private void createSortDocument(String title, String rights, List<String> subjects) {
        DocumentModel doc = session.createDocumentModel("/", "sort" + title, MY_FILE);
        doc.setPropertyValue(SYS_TITLE, title);
        doc.setPropertyValue("mydc_nature", "sorted");
        if (rights != null) {
            doc.setPropertyValue("mydc_rights", rights);
        }
        if (subjects != null) {
            doc.setPropertyValue("mydc_subjects", new ArrayList<>(subjects));
        }
        doc.create();
    }

    private List<String> queryTitles(String hxql) {
        OpenSearchResult result = oss.queryAndAggregate(new OpenSearchQueryBuilder(session).hxql(hxql).limit(20));
        List<String> titles = new ArrayList<>();
        try (IterableQueryResult rows = result.getRows()) {
            for (Map<String, Object> row : rows) {
                titles.add((String) row.get(SYS_TITLE));
            }
        }
        return titles;
    }

    @Test
    public void testIteratorWithLimit() throws Exception {
        int LIMIT = 5;
        OpenSearchResult openSearchRes = oss.queryAndAggregate(
                new OpenSearchQueryBuilder(session).hxql("select sys_id FROM SysContent").limit(LIMIT));
        try (IterableQueryResult res = openSearchRes.getRows()) {
            // the number of doc in the iterator
            assertEquals(LIMIT, res.size());
            // the total number of docs that match for the query
            assertEquals(14, ((OpenSearchResultSetImpl) res).totalSize());
        }
    }

    @Test
    public void testRangeCompareDate() throws Exception {
        OpenSearchResult openSearchRes = oss.queryAndAggregate(
                new OpenSearchQueryBuilder(session).hxql("select sys_id FROM SysContent WHERE sys_created > DATE '2020-01-01'"));
        try (IterableQueryResult res = openSearchRes.getRows()) {
            assertEquals(14, ((OpenSearchResultSetImpl) res).totalSize());
        }
    }

}
