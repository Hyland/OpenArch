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
package org.nuxeo.opensearch.test;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.hyland.hxpr.async.tasks.TaskService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.api.security.impl.ACLImpl;
import com.hyland.openarch.ecm.core.api.trash.TrashService;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.opensearch.listener.OpenSearchInlineListener;
import org.nuxeo.opensearch.query.OpenSearchQueryBuilder;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.opensearch.client.opensearch._types.SearchType;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
public class TestTreeIndexing {

    private static final String IDX_NAME = "nxutest";

    @Inject
    protected TaskService asyncTasksService;

    @Inject
    CoreSession session;

    @Inject
    OpenSearchService oss;

    private boolean syncMode = false;

    private int commandProcessed;

    private void nextTransactionWithWait() throws Exception {
        nextTransactionWithWaitAssertNumberOfCommandProcessed(-1);
    }

    private void nextTransactionWithWaitAssertNumberOfCommandProcessed(int n) throws Exception {
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
        if (n >= 0) {
            assertNumberOfCommandProcessed(n);
        }
        startTransaction();
    }

    public void assertNumberOfCommandProcessed(int processed) throws Exception {
        assertEquals(processed, oss.getTotalCommandProcessed() - commandProcessed);
    }

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() throws Exception {
        asyncTasksService.awaitCompletion(Duration.ofSeconds(20));
        oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        oss.refresh();
    }

    public void startTransaction() {
        if (syncMode) {
            OpenSearchInlineListener.useSyncIndexing.set(true);
        }
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
        commandProcessed = oss.getTotalCommandProcessed();
    }

    public void activateSynchronousMode() throws Exception {
        OpenSearchInlineListener.useSyncIndexing.set(true);
        syncMode = true;
    }

    @AfterEach
    public void disableSynchronousMode() {
        OpenSearchInlineListener.useSyncIndexing.set(false);
        syncMode = false;
    }

    @BeforeEach
    public void setUpMapping() throws Exception {
        oss.initIndexes(true);
    }

    protected void buildTree() {
        String root = "/";
        for (int i = 0; i < 10; i++) {
            String name = "folder" + i;
            DocumentModel doc = session.createDocumentModel(root, name, SYS_FOLDER);
            doc.setPropertyValue(SYS_TITLE, "Folder" + i);
            doc.create();
            root = root + name + "/";
        }
        session.save();
    }

    protected void buildAndIndexTree() throws Exception {
        startTransaction();
        buildTree();

        nextTransactionWithWaitAssertNumberOfCommandProcessed(10);

        // check indexing at ES level
        SearchResponse<DocumentDTO> searchResponse = searchAll();
        assertEquals(10, searchResponse.hits().total().value());
    }

    protected SearchResponse<DocumentDTO> searchAll() {
        SearchRequest request = SearchRequest.of(b -> b.index(IDX_NAME) //
                                                       .searchType(SearchType.DfsQueryThenFetch)
                                                       .from(0)
                                                       .size(60));
        return oss.getClient().search(request);
    }

    protected SearchResponse<DocumentDTO> search(Query query) {
        SearchRequest request = SearchRequest.of(b -> b.index(IDX_NAME) //
                                                       .searchType(SearchType.DfsQueryThenFetch)
                                                       .from(0)
                                                       .size(60)
                                                       .query(query));
        return oss.getClient().search(request);
    }

    protected CoreSession getRestrictedSession() {
        return CoreInstance.getCoreSession(null, DummyLoginFeature.getTestPrincipal("toto", false));
    }

    @Test
    public void shouldIndexTree() throws Exception {
        buildAndIndexTree();

        // check sub tree search
        Query query = QueryBuilders.prefix().field(SYS_PATH).value("/folder0/folder1/folder2").build().toQuery();
        SearchResponse<DocumentDTO> searchResponse = search(query);
        assertEquals(8, searchResponse.hits().total().value());
    }

    @Test
    public void shouldUnIndexSubTree() throws Exception {
        buildAndIndexTree();

        DocumentRef ref = new PathRef("/folder0/folder1/folder2");
        assertTrue(session.exists(ref));

        startTransaction();
        session.removeDocument(ref);

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1);

        SearchResponse<DocumentDTO> searchResponse = searchAll();
        assertEquals(2, searchResponse.hits().total().value());
    }

    @Test
    public void shouldIndexMovedSubTree() throws Exception {
        buildAndIndexTree();
        startTransaction();
        DocumentRef ref = new PathRef("/folder0/folder1/folder2");
        assertTrue(session.exists(ref));
        DocumentModel doc = session.getDocument(ref);

        // move in the same folder : rename
        session.move(ref, doc.getParentRef(), "folderA");

        // in sync we split recursive update into 2 commands:
        // 1 sync non recurse + 1 async recursive
        nextTransactionWithWaitAssertNumberOfCommandProcessed(syncMode ? 9 : 8);

        SearchResponse<DocumentDTO> searchResponse = searchAll();
        assertEquals(10, searchResponse.hits().total().value());

        // check sub tree search
        Query query = QueryBuilders.prefix().field(SYS_PATH).value("/folder0/folder1/folder2").build().toQuery();
        searchResponse = search(query);
        assertEquals(0, searchResponse.hits().total().value());

        query = QueryBuilders.prefix().field(SYS_PATH).value("/folder0/folder1/folderA").build().toQuery();
        searchResponse = search(query);
        assertEquals(8, searchResponse.hits().total().value());

        query = QueryBuilders.prefix().field(SYS_PATH).value("/folder0/folder1").build().toQuery();
        searchResponse = search(query);
        assertEquals(9, searchResponse.hits().total().value());

    }

    @Test
    public void shouldFilterTreeOnSecurity() throws Exception {

        buildAndIndexTree();

        DocumentModelList docs = oss.query(new OpenSearchQueryBuilder(session).hxql("select * FROM SysContent"));
        assertEquals(10, docs.totalSize());

        // check for user with no rights
        startTransaction();
        CoreSession restrictedSession = getRestrictedSession();
        docs = oss.query(new OpenSearchQueryBuilder(restrictedSession).hxql("select * FROM SysContent"));
        assertEquals(0, docs.totalSize());

        // add READ rights and check that user now has access

        DocumentRef ref = new PathRef("/folder0/folder1/folder2");
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("toto").permission(SecurityConstants.READ).build());
        session.getDocument(ref).setACL(acl, true);

        // in sync we split recursive update into 2 commands:
        // 1 sync non recurse + 1 async recursive
        nextTransactionWithWaitAssertNumberOfCommandProcessed(syncMode ? 17 : 16);

        docs = oss.query(new OpenSearchQueryBuilder(restrictedSession).hxql("select * FROM SysContent"));
        assertEquals(8, docs.totalSize());

        // block rights and check that blocking is taken into account

        ref = new PathRef("/folder0/folder1/folder2/folder3/folder4/folder5");
        acl = new ACLImpl();

        acl.add(ACE.builder()
                   .principal(SecurityConstants.EVERYONE)
                   .permission(SecurityConstants.EVERYTHING)
                   .isGranted(false)
                   .build());
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(SecurityConstants.EVERYTHING).build());

        session.getDocument(ref).setACL(acl, true);

        nextTransactionWithWaitAssertNumberOfCommandProcessed(syncMode ? 11 : 10);

        docs = oss.query(new OpenSearchQueryBuilder(restrictedSession).hxql("select * FROM SysContent"));
        assertEquals(3, docs.totalSize());
    }

    @Test
    public void shouldStoreOnlyEffectiveACEs() throws Exception {
        buildAndIndexTree();

        DocumentModelList docs = oss.query(new OpenSearchQueryBuilder(session).hxql("select * FROM SysContent"));
        assertEquals(10, docs.totalSize());

        CoreSession restrictedSession = getRestrictedSession();
        docs = oss.query(new OpenSearchQueryBuilder(restrictedSession).hxql("select * FROM SysContent"));
        assertEquals(0, docs.totalSize());

        DocumentRef ref = new PathRef("/folder0");
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("toto").permission(SecurityConstants.READ).build());
        session.getDocument(ref).setACL(acl, true);

        nextTransactionWithWait();

        docs = oss.query(new OpenSearchQueryBuilder(restrictedSession).hxql("select * FROM SysContent order by sys_title"));
        assertEquals(10, docs.totalSize());

        acl = new ACLImpl();
        // make the ACE archived
        Instant begin = Instant.now().minus(10, DAYS);
        Instant end = Instant.now().minus(2, DAYS);
        acl.add(ACE.builder().user("toto").permission(SecurityConstants.READ).begin(begin).end(end).build());
        session.getDocument(ref).setACL(acl, true);

        nextTransactionWithWait();

        docs = oss.query(new OpenSearchQueryBuilder(restrictedSession).hxql("select * FROM SysContent order by sys_title"));
        assertEquals(0, docs.totalSize());
    }

    @Disabled("trash not implemented for now")
    @Test
    public void shouldReindexSubTreeInTrash() throws Exception {
        buildAndIndexTree();
        startTransaction();
        DocumentRef ref = new PathRef("/folder0/folder1/folder2");
        assertTrue(session.exists(ref));
        Framework.getService(TrashService.class).trashDocument(session.getDocument(ref));

        // 1 moved event which triggers 1 recurse command -> 8 commands
        // 8 trashed events -> 7 commands (one of trashed events is merged into the resulted command from moved event)
        // in sync we split recursive update into 2 commands:
        // 1 sync non recurse + 1 async recursive
        nextTransactionWithWaitAssertNumberOfCommandProcessed(syncMode ? 16 : 15);

        DocumentModelList docs = oss.query(
                new OpenSearchQueryBuilder(session).hxql("select * FROM SysContent where sys_isTrashed = FALSE"));
        assertEquals(2, docs.totalSize());
    }

    @Test
    public void shouldIndexOnCopy() throws Exception {
        buildAndIndexTree();

        DocumentRef src = new PathRef("/folder0/folder1/folder2");
        DocumentRef dst = new PathRef("/folder0");
        session.copy(src, dst, "folder2-copy");

        nextTransactionWithWait();

        DocumentModelList docs = oss.query(new OpenSearchQueryBuilder(session).hxql("select * FROM SysContent"));
        assertEquals(18, docs.totalSize());
    }

    @Test
    public void shouldNotLeakScrollContextsOnRecursiveDelete() throws Exception {
        buildAndIndexTree();

        long scrollContextsBefore = getOpenScrollContexts();

        DocumentRef ref = new PathRef("/folder0");
        session.removeDocument(ref);
        nextTransactionWithWait();

        long scrollContextsAfter = getOpenScrollContexts();
        assertEquals(scrollContextsBefore, scrollContextsAfter, "Recursive delete must not leave open scroll contexts");
    }

    private long getOpenScrollContexts() {
        return oss.getClient().getOpenScrollContexts();
    }

}
