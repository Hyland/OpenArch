/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.opensearch.test.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import com.hyland.openarch.async.tasks.TaskService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.opensearch.api.OpenSearchScrollResult;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.opensearch.api.OpenSearchIterableQueryResultImpl;
import com.hyland.openarch.opensearch.query.OpenSearchQueryBuilder;
import com.hyland.openarch.opensearch.test.RepositoryOpenSearchFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
@Deploy("com.hyland.openarch.opensearch.core:schemas-test-contrib.xml")
public class TestOpenSearchIterableQueryResultImpl {

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService oss;

    @Inject
    protected TaskService asyncTasksService;

    @BeforeEach
    public void setupIndex() throws Exception {
        oss.initIndexes(true);
    }

    @Test
    public void testIterableScroll() throws Exception {

        buildAndIndexTree(100);

        String nxql = "select sys_id, sys_path FROM SysContent";
        // Request the first batch
        OpenSearchQueryBuilder queryBuilder = new OpenSearchQueryBuilder(session).hxql(nxql).limit(20).onlyOpenSearchResponse();
        OpenSearchScrollResult res = oss.scroll(queryBuilder, 10000);

        // Init wrapper
        OpenSearchService spiedEss = spy(oss);
        OpenSearchIterableQueryResultImpl iterable = new OpenSearchIterableQueryResultImpl(spiedEss, res);
        assertEquals(100, iterable.size());
        assertEquals(0, iterable.pos());
        assertTrue(iterable.mustBeClosed());
        assertTrue(iterable.hasNext());

        List<Map<String, Object>> rows = new ArrayList<>(100);
        while (iterable.hasNext()) {
            rows.add(iterable.next());
        }
        assertEquals(100, rows.size());
        // Each 20 items, a request is made to ES, check that
        // So a request is made at 20, 40, 60, 80
        verify(spiedEss, times(4)).scroll(any());
        assertEquals(1, oss.getClient().getOpenScrollContexts());

        iterable.close();
        verify(spiedEss, times(1)).clearScroll(any());
        assertEquals(0, oss.getClient().getOpenScrollContexts());

    }

    @Test
    public void testIterableSkipTo() throws Exception {

        buildAndIndexTree(100);

        String nxql = "select sys_id, sys_path FROM SysContent";
        // Request the first batch
        OpenSearchQueryBuilder queryBuilder = new OpenSearchQueryBuilder(session).hxql(nxql).limit(20).onlyOpenSearchResponse();
        OpenSearchScrollResult res = oss.scroll(queryBuilder, 10000);

        // Init wrapper
        OpenSearchService spiedEss = spy(oss);
        OpenSearchIterableQueryResultImpl iterable = new OpenSearchIterableQueryResultImpl(spiedEss, res);
        iterable.skipTo(70);
        // Each 20 items, a request is made to ES, check that
        // So a request is made at 20, 40, 60
        verify(spiedEss, times(3)).scroll(any());

        List<Map<String, Object>> rows = new ArrayList<>(30);
        while (iterable.hasNext()) {
            rows.add(iterable.next());
        }
        assertEquals(30, rows.size());

        // A request is made at 80
        verify(spiedEss, times(4)).scroll(any());

        iterable.close();
        verify(spiedEss, times(1)).clearScroll(any());

    }

    @Test
    public void testConstructor() {
        Map<String, Type> map = new HashMap<>();
        String nxql = "select sys_id, sys_path FROM SysContent";
        OpenSearchQueryBuilder queryBuilder = new OpenSearchQueryBuilder(session).hxql(nxql).limit(20).onlyOpenSearchResponse();
        OpenSearchScrollResult res = oss.scroll(queryBuilder, 10000);
        res.getQueryBuilder().setSelectFieldsAndTypes(map);
        assertThrows(IllegalArgumentException.class, () -> new OpenSearchIterableQueryResultImpl(oss, res));
    }

    protected void buildAndIndexTree(int docCount) throws Exception {
        startTransaction();
        buildTree(docCount);
        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
        startTransaction();
    }

    protected void buildTree(int docCount) {
        String root = "/";
        for (int i = 0; i < docCount; i++) {
            String name = "folder" + i;
            DocumentModel doc = session.createDocumentModel(root, name, SYS_FOLDER);
            doc.setPropertyValue(SYS_TITLE, "Folder" + i);
            doc.create();
        }
        session.save();
    }

    protected void startTransaction() {
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
    }

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() throws Exception {
        asyncTasksService.awaitCompletion(Duration.ofSeconds(20));
        oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        oss.refresh();
    }

}
