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
package com.hyland.openarch.opensearch.test;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_ORDERED_FOLDER;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import com.hyland.openarch.async.tasks.TaskService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.trash.TrashService;
import com.hyland.openarch.ecm.core.security.RetentionExpiredFinderScheduledJob;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.opensearch.io.DocumentDTO;
import com.hyland.openarch.opensearch.listener.OpenSearchInlineListener;
import com.hyland.openarch.opensearch.query.OpenSearchQueryBuilder;
import com.hyland.openarch.runtime.test.runner.ConsoleLogLevelThreshold;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SearchType;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

/**
 * Test "on the fly" indexing via the listener system
 */
@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
public class TestAutomaticIndexing {

    private static final String IDX_NAME = "nxutest";

    private static final String MY_FILE = "MyFile";

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService oss;

    @Inject
    protected TrashService trashService;

    @Inject
    protected TaskService asyncTasksService;

    private boolean syncMode = false;

    private int commandProcessed;

    private DocumentRef v1Ref;

    private DocumentRef v2Ref;

    private DocumentRef v3Ref;

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

    // Number of processed command since the startTransaction
    public void assertNumberOfCommandProcessed(int processed) {
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

    public void activateSynchronousMode() {
        OpenSearchInlineListener.useSyncIndexing.set(true);
        syncMode = true;
    }

    @AfterEach
    public void restoreAsyncAndConsoleLog() {
        OpenSearchInlineListener.useSyncIndexing.set(false);
        syncMode = false;
    }

    protected void startTransaction() {
        if (syncMode) {
            OpenSearchInlineListener.useSyncIndexing.set(true);
        }
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
        commandProcessed = oss.getTotalCommandProcessed();
    }

    @BeforeEach
    public void setupIndex() throws Exception {
        oss.initIndexes(true);
    }

    @Test
    public void shouldNotIndexRootDocument() throws Exception {
        startTransaction();

        // check no indexing was performed
        nextTransactionWithWaitAssertNumberOfCommandProcessed(0);

        // check no root document from search response
        SearchResponse<DocumentDTO> searchResponse = searchAll();
        assertEquals(0, searchResponse.hits().total().value());
    }

    protected SearchResponse<DocumentDTO> searchAll() {
        SearchRequest request = new SearchRequest.Builder().index(IDX_NAME) //
                                                           .searchType(SearchType.DfsQueryThenFetch)
                                                           .from(0)
                                                           .size(60)
                                                           .build();
        return oss.getClient().search(request);
    }

    protected SearchResponse<DocumentDTO> search(Query query) {
        SearchRequest request = new SearchRequest.Builder().index(IDX_NAME) //
                                                           .searchType(SearchType.DfsQueryThenFetch)
                                                           .from(0)
                                                           .size(60)
                                                           .query(query)
                                                           .build();
        return oss.getClient().search(request);
    }

    @Test
    public void shouldNotIndexRootDocumentDuringReindexAll() throws Exception {
        startTransaction();
        // Re-index all repositories
        for (String repositoryName : oss.getRepositoryNames()) {
            oss.dropAndInitRepositoryIndex(repositoryName);
            oss.runReindexingWorker(repositoryName, "SELECT sys_id FROM SysContent");
        }

        // check no indexing was performed
        nextTransactionWithWaitAssertNumberOfCommandProcessed(0);

        // check no root document from search response
        SearchResponse<DocumentDTO> searchResponse = searchAll();
        assertEquals(0, searchResponse.hits().total().value());
    }

    @Test
    public void shouldIndexDocument() throws Exception {
        startTransaction();
        // create 10 docs
        for (int i = 0; i < 10; i++) {
            DocumentModel doc = session.createDocumentModel("/", "testDoc" + i, SYS_FILE);
            doc.setPropertyValue(SYS_TITLE, "TestMe" + i);
            doc.create();
        }
        // merge 5
        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.getDocument(new PathRef("/testDoc" + i));
            doc.setPropertyValue(SYS_DESCRIPTION, "Description TestMe" + i);
        }

        nextTransactionWithWaitAssertNumberOfCommandProcessed(10);

        SearchResponse<DocumentDTO> searchResponse = searchAll();
        assertEquals(10, searchResponse.hits().total().value());
    }

    @Test
    public void shouldNotIndexDocumentBecauseOfRollback() throws Exception {
        startTransaction();
        // create 10 docs
        activateSynchronousMode();
        for (int i = 0; i < 10; i++) {
            DocumentModel doc = session.createDocumentModel("/", "testDoc" + i, SYS_FILE);
            doc.setPropertyValue(SYS_TITLE, "TestMe" + i);
            doc.create();
        }
        // Save session to prevent NXP-14494
        session.save();
        TransactionHelper.setTransactionRollbackOnly();

        nextTransactionWithWaitAssertNumberOfCommandProcessed(0);

        SearchRequest request = new SearchRequest.Builder().index(IDX_NAME) //
                                                           .searchType(SearchType.DfsQueryThenFetch)
                                                           .from(0)
                                                           .size(60)
                                                           .build();
        SearchResponse<DocumentDTO> searchResponse = oss.getClient().search(request);

        assertEquals(0, searchResponse.hits().total().value());
        assertFalse(oss.isIndexingInProgress());
    }

    @Test
    public void shouldUnIndexDocument() throws Exception {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "testDoc", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "TestMe");
        doc.create();

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1);

        SearchRequest request = new SearchRequest.Builder().index(IDX_NAME) //
                                                           .searchType(SearchType.DfsQueryThenFetch)
                                                           .from(0)
                                                           .size(60)
                                                           .build();
        SearchResponse<DocumentDTO> searchResponse = oss.getClient().search(request);
        assertEquals(1, searchResponse.hits().total().value());

        // now delete the document
        session.removeDocument(doc.getRef());

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1);

        startTransaction();
        searchResponse = oss.getClient().search(request);
        assertEquals(0, searchResponse.hits().total().value());
    }

    @Test
    public void shouldReIndexDocument() throws Exception {
        startTransaction();
        // create 10 docs
        for (int i = 0; i < 10; i++) {
            DocumentModel doc = session.createDocumentModel("/", "testDoc" + i, SYS_FILE);
            doc.setPropertyValue(SYS_TITLE, "TestMe" + i);
            doc.setPropertyValue("mydc_nature", "A");
            doc.create();
        }

        nextTransactionWithWaitAssertNumberOfCommandProcessed(10);

        Query query = QueryBuilders.match().field("mydc_nature").query(FieldValue.of("A")).build().toQuery();
        SearchResponse<DocumentDTO> searchResponse = search(query);
        assertEquals(10, searchResponse.hits().total().value());

        int i = 0;
        for (Hit<DocumentDTO> hit : searchResponse.hits().hits()) {
            i++;
            if (i > 8) {
                break;
            }
            DocumentModel doc = session.getDocument(new IdRef(hit.id()));
            doc.setPropertyValue("mydc_nature", "B");
        }

        nextTransactionWithWaitAssertNumberOfCommandProcessed(8);

        query = QueryBuilders.match().field("mydc_nature").query(FieldValue.of("A")).build().toQuery();
        searchResponse = search(query);
        assertEquals(2, searchResponse.hits().total().value());

        query = QueryBuilders.match().field("mydc_nature").query(FieldValue.of("B")).build().toQuery();
        searchResponse = search(query);
        assertEquals(8, searchResponse.hits().total().value());
    }

    protected String createBigString(int length, char c) {
        return new String(new char[length]).replace('\0', c);
    }

    @Test
    public void shouldIndexLargeToken() throws Exception {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "myFile", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "search " + createBigString(40000, 'a') + " bar");
        // term > 32k cannot be indexed by lucene
        // but es discard them with the ignore_above and the with the custom tokenizer
        doc.create();

        nextTransactionWithWait();

        DocumentModelList ret = oss.query(
                new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent WHERE sys_fulltext.sys_title = 'search'"));
        assertEquals(1, ret.totalSize());
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent WHERE sys_fulltext.sys_title = 'bar'"));
        assertEquals(1, ret.totalSize());

    }

    @Disabled("trash not implemented for now")
    @Test
    public void shouldUnIndexUsingTrashService() throws Exception {
        startTransaction();
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();
        session.save();

        shouldUnIndexUsingTrashService(doc);
    }

    @Disabled("trash not implemented for now")
    @Test
    public void shouldUnIndexUsingTrashServiceWithoutRenaming() throws Exception {
        startTransaction();
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();
        session.save();

        doc.putContextData(TrashService.DISABLE_TRASH_RENAMING, Boolean.TRUE);
        shouldUnIndexUsingTrashService(doc);
    }

    protected void shouldUnIndexUsingTrashService(DocumentModel doc) throws Exception {
        nextTransactionWithWaitAssertNumberOfCommandProcessed(2); // 2 creations

        trashService.trashDocument(doc);

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1); // 1 update

        DocumentModelList ret = oss.query(
                new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent WHERE sys_isTrashed = FALSE"));
        assertEquals(1, ret.totalSize());
        doc = session.getDocument(doc.getRef());
        trashService.untrashDocument(doc);

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1);

        ret = oss.query(new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent WHERE sys_isTrashed = FALSE"));
        assertEquals(2, ret.totalSize());

        SearchResponse<DocumentDTO> searchResponse = searchAll();
        assertEquals(2, searchResponse.hits().total().value());

        trashService.purgeDocuments(session, Collections.singletonList(doc.getRef()));

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1);

        searchResponse = searchAll();
        assertEquals(1, searchResponse.hits().total().value());
    }

    @Test
    public void shouldIndexOnCopy() throws Exception {
        startTransaction();
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/", "file", SYS_FILE);
        doc.create();

        nextTransactionWithWaitAssertNumberOfCommandProcessed(2);

        DocumentRef src = doc.getRef();
        DocumentRef dst = new PathRef("/");
        session.copy(src, dst, "file2");
        // turn the sync flag after the action
        OpenSearchInlineListener.useSyncIndexing.set(true);

        nextTransactionWithWait();

        SearchResponse<DocumentDTO> searchResponse = searchAll();
        assertEquals(3, searchResponse.hits().total().value());
    }

    @Test
    public void shouldHandleCreateDelete() throws Exception {
        startTransaction();
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/folder", "note", SYS_FILE);
        doc.create();
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        // we don't wait for async
        TransactionHelper.startTransaction();
        session.removeDocument(folder.getRef());

        nextTransactionWithWait();
    }

    @Test
    @ConsoleLogLevelThreshold("ERROR")
    public void shouldHandleUpdateOnTransientDocBis() throws Exception {
        startTransaction();
        DocumentModel tmpDoc = session.createDocumentModel("/", "file", SYS_FILE);
        tmpDoc.setPropertyValue(SYS_TITLE, "TestMe");
        tmpDoc.create(); // Send an ES_INSERT cmd

        tmpDoc.setPropertyValue(SYS_TITLE, "NewTitle"); // ES_UPDATE with transient, merged

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1);

        DocumentModelList docs = oss.query(
                new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent Where sys_title='NewTitle'"));
        assertEquals(1, docs.totalSize());
    }

    @Test
    @ConsoleLogLevelThreshold("ERROR")
    public void shouldHandleUpdateBeforeInsertOnTransientDoc() throws Exception {
        startTransaction();
        DocumentModel folder = session.createDocumentModel("/", "section", SYS_FOLDER);
        folder.create();

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1);
    }

    @Test
    public void shouldIndexOrderedFolder() throws Exception {
        startTransaction();
        DocumentModel ofolder = session.createDocumentModel("/", "ofolder", SYS_ORDERED_FOLDER);
        ofolder.create();
        DocumentModel file1 = session.createDocumentModel("/ofolder", "testfile1", SYS_FILE);
        file1.create();
        DocumentModel file2 = session.createDocumentModel("/ofolder", "testfile2", SYS_FILE);
        file2.create();
        DocumentModel file3 = session.createDocumentModel("/ofolder", "testfile3", SYS_FILE);
        file3.create();
        DocumentModel folder4 = session.createDocumentModel("/ofolder", "folder4", SYS_FOLDER);
        folder4.create();
        DocumentModel file = session.createDocumentModel("/ofolder/folder4", "testfile", SYS_FILE);
        file.create();

        nextTransactionWithWaitAssertNumberOfCommandProcessed(6);

        DocumentModelList ret = oss.query(new OpenSearchQueryBuilder(session).hxql(
                String.format("SELECT * FROM SysContent WHERE sys_parentId='%s' ORDER BY sys_pos", ofolder.getId())));
        assertEquals(4, ret.totalSize());
        assertEquals(file1.getId(), ret.get(0).getId());
        assertEquals(file2.getId(), ret.get(1).getId());
        assertEquals(file3.getId(), ret.get(2).getId());

        session.orderBefore(ofolder.getRef(), "testfile3", "testfile2");

        // only the 4 direct children are reindexed
        nextTransactionWithWaitAssertNumberOfCommandProcessed(7);

        ret = oss.query(new OpenSearchQueryBuilder(session).hxql(
                String.format("SELECT * FROM SysContent WHERE sys_parentId='%s' ORDER BY sys_pos", ofolder.getId())));
        assertEquals(4, ret.totalSize());
        assertEquals(file1.getId(), ret.get(0).getId());
        assertEquals(file3.getId(), ret.get(1).getId());
        assertEquals(file2.getId(), ret.get(2).getId());
    }

    @Disabled
    //disabled until versioning is reworked
    @Test
    public void shouldNotIndexRecursivelyVersionFolder() throws Exception {
        startTransaction();
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel file1 = session.createDocumentModel("/folder", "testfile1", SYS_FILE);
        file1.create();
        DocumentModel file2 = session.createDocumentModel("/folder", "testfile2", SYS_FILE);
        file2.create();

        folder.setPropertyValue(SYS_TITLE, "v1");
        DocumentRef v1 = folder.checkIn();

        folder.setPropertyValue(SYS_TITLE, "v2");
        folder.checkIn();

        // 3 docs (2 files + 1 folder checkout) + 1 update on folder when first version is created + 2 versions of folder
        nextTransactionWithWaitAssertNumberOfCommandProcessed(6);

        DocumentModelList ret = oss.query(new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent"));
        assertEquals(5, ret.totalSize());

        // delete the first version
        session.removeDocument(v1);

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1);

        ret = oss.query(new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent"));
        assertEquals(4, ret.totalSize());
    }

    /*
     * NXP-23033
     */
    @Test
    public void shouldIndexAfterVersionRestored() throws Exception {
        createADocumentWith3Versions();

        DocumentModelList ret = oss.query(
                new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent WHERE sysver_isVersion = FALSE AND sys_title='v3'"));
        assertEquals(1, ret.totalSize());
        DocumentModel doc = ret.get(0);
        assertEquals("v3", doc.getPropertyValue(SYS_TITLE));

        // restore the document to v2 and check version in ES
        session.restoreVersion(v2Ref);

        nextTransactionWithWait();

        ret = oss.query(
                new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent WHERE sysver_isVersion = FALSE AND sys_title='v2'"));
        assertEquals(1, ret.totalSize());
        assertEquals("v2", ret.get(0).getPropertyValue(SYS_TITLE));
    }

    /*
     * NXP-23033
     */
    @Test
    public void shouldIndexAfterPublishThenRestore() throws Exception {
        startTransaction();
        // create a document
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/", "file", MY_FILE);
        doc.create();
        doc.setPropertyValue(SYS_TITLE, "v1");

        nextTransactionWithWait();
        doc.refresh();

        // publish
        DocumentRef verRef = session.checkIn(doc.getRef());
        DocumentModel v1 = session.getDocument(verRef);

        nextTransactionWithWait();
        doc.refresh();

        // update document and version it
        doc.setPropertyValue(SYS_TITLE, "v2");
        doc.checkIn();

        nextTransactionWithWait();
        doc.refresh();

        // check ES
        DocumentModelList ret = oss.query(new OpenSearchQueryBuilder(session).hxql(
                "SELECT * FROM SysContent WHERE sys_path = '/file' and sysver_isVersion = FALSE AND sys_title='v2'"));
        assertEquals(1, ret.totalSize());
        assertEquals("v2", ret.get(0).getPropertyValue(SYS_TITLE));

        // restore document to 1
        session.restoreVersion(v1.getRef());

        nextTransactionWithWait();

        // check ES
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql(
                "SELECT * FROM SysContent WHERE sys_path = '/file' and sysver_isVersion = FALSE AND sys_title='v1'"));
        assertEquals(1, ret.totalSize());
        assertEquals("v1", ret.get(0).getPropertyValue(SYS_TITLE));
    }

    protected void createADocumentWith3Versions() throws Exception {
        startTransaction();
        DocumentModel file1 = session.createDocumentModel("/", "testfile1", MY_FILE);
        file1.create();
        file1.setPropertyValue(SYS_TITLE, "v1");
        v1Ref = file1.checkIn();

        nextTransactionWithWait();
        file1.refresh();

        file1.setPropertyValue(SYS_TITLE, "v2");
        v2Ref = file1.checkIn();

        nextTransactionWithWait();
        file1.refresh();

        file1.setPropertyValue(SYS_TITLE, "v3");
        v3Ref = file1.checkIn();

        nextTransactionWithWait();
    }

    @Test
    public void sortOnUnmappedField() {

        // sort on a field that does not exist on the mapping and not present in the index
        DocumentModelList ret = oss.query(
                new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent ORDER BY mydc_source"));
        assertEquals(0, ret.totalSize());

        // sort on internal field
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent ORDER BY sys_pos"));
        assertEquals(0, ret.totalSize());

    }

    @Disabled("record not implemented for now")
    @Test
    public void shouldIndexUpdatedRecord() throws Exception {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "mydoc", SYS_FILE);
        doc.create();

        nextTransactionWithWait();

        // no record found in index
        String nxql = "SELECT * FROM SysContent WHERE sysgov_isRecord = TRUE";
        DocumentModelList ret = oss.query(new OpenSearchQueryBuilder(session).hxql(nxql));
        assertEquals(0, ret.totalSize());

        // make the doc a record
        session.setRecord(doc.getRef(), true);

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1); // update

        // record is now found
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql(nxql));
        assertEquals(1, ret.totalSize());

    }

    @Disabled("record not implemented for now")
    @Test
    public void shouldIndexUpdatedRetention() throws Exception {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "mydoc", SYS_FILE);
        doc.create();
        session.save();

        // no retention found in index
        String nxql1 = "SELECT * FROM SysContent WHERE sysgov_retainUntil IS NOT NULL";
        DocumentModelList ret1 = oss.query(new OpenSearchQueryBuilder(session).hxql(nxql1));
        assertEquals(0, ret1.totalSize());

        // set retention to two seconds in the future
        Instant twoSeconds = Instant.now().plus(2, SECONDS);
        session.setRecord(doc.getRef(), true);
        session.setRetainUntil(doc.getRef(), twoSeconds);

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1); // update

        // retention is now found
        ret1 = oss.query(new OpenSearchQueryBuilder(session).hxql(nxql1));
        assertEquals(1, ret1.totalSize());

        // wait 2s to pass retention expiration date
        Thread.sleep(2_000);
        // trigger manually instead of waiting for scheduler
        new RetentionExpiredFinderScheduledJob().run();
        // wait for all bulk commands to be executed
        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(60)), "Bulk action didn't finish");

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1); // update

        // null retention is not found anymore
        ret1 = oss.query(new OpenSearchQueryBuilder(session).hxql(nxql1));
        assertEquals(0, ret1.totalSize());
    }

    @Disabled("record not implemented for now")
    @Test
    public void shouldIndexUpdatedLegalHold() throws Exception {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "mydoc", SYS_FILE);
        doc.create();
        session.save();

        // no retention found in index
        String nxql = "SELECT * FROM SysContent WHERE sysgov_hasLegalHold = TRUE";
        DocumentModelList ret = oss.query(new OpenSearchQueryBuilder(session).hxql(nxql));
        assertEquals(0, ret.totalSize());

        // set legal hold
        session.setRecord(doc.getRef(), true);
        session.setLegalHold(doc.getRef(), true);

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1); // update

        // legal hold is now found
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql(nxql));
        assertEquals(1, ret.totalSize());

        // remove legal hold
        session.setLegalHold(doc.getRef(), false);

        nextTransactionWithWaitAssertNumberOfCommandProcessed(1); // update

        // legal hold is not found anymore
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql(nxql));
        assertEquals(0, ret.totalSize());
    }

}
