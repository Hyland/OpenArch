/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.opensearch;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.opensearch.api.OpenSearchClient;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.opensearch.commands.IndexingCommand.Type;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;

/**
 * Unit tests for {@link OpenSearchServiceImpl#processDeleteCommandRecursive} and
 * {@link OpenSearchServiceImpl#safelyClearScroll}.
 * <p>
 * Uses Mockito spies to stub out OpenSearch client and infrastructure calls so the pre-check retry logic, interrupt
 * handling, and scroll cleanup can be exercised deterministically without a running OpenSearch cluster.
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class ProcessDeleteCommandRecursiveTest {

    private static final String REPO = "test";

    private static final String INDEX = "nxutest";

    private static final String DOC_ID = "doc-001";

    private static final String DOC_PATH = "/folder0";

    private static final String SCROLL_ID = "scroll-abc";

    private OpenSearchServiceImpl service;

    private OpenSearchClient client;

    private IndexingCommand cmd;

    @BeforeEach
    public void setUp() {
        service = mock(OpenSearchServiceImpl.class, CALLS_REAL_METHODS);
        client = mock(OpenSearchClient.class);

        doReturn(client).when(service).getClient();
        doReturn(INDEX).when(service).getWriteIndexForRepository(REPO);
        doReturn(DOC_PATH).when(service).getPathOfDocFromEs(REPO, DOC_ID);
        doReturn(false).when(service).hasEmbeddingsIndex(REPO);
        doNothing().when(service).postDeletionGhostDocumentCheck(anyString(), anyString(), anyList());

        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn(DOC_ID);
        when(doc.getRepositoryName()).thenReturn(REPO);
        cmd = new IndexingCommand(doc, Type.DELETE, false, true);
    }

    // -----------------------------------------------------------------------
    // safelyClearScroll — unit tests for the helper directly
    // -----------------------------------------------------------------------

    @Test
    public void safelyClearScroll_nullScrollId_doesNotCallClearScroll() {
        service.safelyClearScroll(null, DOC_ID);
        verify(client, never()).clearScroll(anyString());
    }

    @Test
    public void safelyClearScroll_nonNullScrollId_callsClearScroll() {
        service.safelyClearScroll(SCROLL_ID, DOC_ID);
        verify(client, times(1)).clearScroll(SCROLL_ID);
    }

    @Test
    public void safelyClearScroll_clearScrollThrows_exceptionIsSwallowed() {
        doThrow(new RuntimeException("context already closed")).when(client).clearScroll(SCROLL_ID);
        // must not propagate
        service.safelyClearScroll(SCROLL_ID, DOC_ID);
        verify(client, times(1)).clearScroll(SCROLL_ID);
    }

    // -----------------------------------------------------------------------
    // processDeleteCommandRecursive — pre-check retry paths
    // -----------------------------------------------------------------------

    @Test
    public void preCheck_hitsOnFirstAttempt_noRetry() throws Exception {
        SearchResponse<DocumentDTO> withHits = searchResponseWithHits(1);
        SearchResponse<DocumentDTO> emptyWithScroll = emptyScrollResponse(SCROLL_ID);

        // pre-check returns hits immediately; main scroll returns empty (no deletion loop)
        when(client.search(any())).thenReturn(withHits, emptyWithScroll);

        invokeProcessDeleteCommandRecursive();

        // pre-check called once, main scroll search called once → 2 total search calls
        verify(client, times(2)).search(any());
        // main scroll had a scrollId → clearScroll called exactly once (no extra retry-related calls)
        verify(client, times(1)).clearScroll(SCROLL_ID);
    }

    @Test
    public void preCheck_noHitsFirstFourAttempts_hitsOnFifth() throws Exception {
        SearchResponse<DocumentDTO> noHits = emptySearchResponse();
        SearchResponse<DocumentDTO> withHits = searchResponseWithHits(1);
        SearchResponse<DocumentDTO> emptyWithScroll = emptyScrollResponse(SCROLL_ID);

        // 4 empty pre-check responses, then hits on the 5th, then empty main scroll
        when(client.search(any())).thenReturn(noHits, noHits, noHits, noHits, withHits, emptyWithScroll);

        invokeProcessDeleteCommandRecursive();

        // 5 pre-check calls + 1 main scroll call = 6
        verify(client, times(6)).search(any());
        // main scroll had a scrollId → clearScroll called once
        verify(client, times(1)).clearScroll(SCROLL_ID);
    }

    @Test
    public void preCheck_noHitsAllFiveAttempts_noSleepOnLastAttempt() throws Exception {
        SearchResponse<DocumentDTO> noHits = emptySearchResponse();
        SearchResponse<DocumentDTO> emptyWithScroll = emptyScrollResponse(SCROLL_ID);

        // 5 empty pre-check responses, then empty main scroll
        when(client.search(any())).thenReturn(noHits, noHits, noHits, noHits, noHits, emptyWithScroll);

        invokeProcessDeleteCommandRecursive();

        // all 5 pre-check attempts exhausted + 1 main scroll call
        verify(client, times(6)).search(any());
    }

    @Test
    public void preCheck_interruptedDuringSleep_threadInterruptFlagSet() throws Exception {
        // Arrange: first pre-check returns no hits so the loop tries to sleep.
        // Stub Thread.sleep by interrupting the test thread before invoking.
        SearchResponse<DocumentDTO> noHits = emptySearchResponse();
        SearchResponse<DocumentDTO> emptyWithScroll = emptyScrollResponse(null);
        when(client.search(any())).thenReturn(noHits, emptyWithScroll);

        // Pre-interrupt the thread so the first Thread.sleep throws InterruptedException
        Thread.currentThread().interrupt();

        invokeProcessDeleteCommandRecursive();

        // Thread interrupt flag must be re-set by the catch block
        assertTrue(Thread.interrupted(), "Thread interrupt flag must be set after InterruptedException");

        // Despite interruption, the method continues and performs the main scroll
        verify(client, times(2)).search(any());
    }

    // -----------------------------------------------------------------------
    // processDeleteCommandRecursive — scroll cleanup paths
    // -----------------------------------------------------------------------

    @Test
    public void mainScroll_emptyFirstPage_clearScrollCalledWithScrollId() throws Exception {
        SearchResponse<DocumentDTO> withHits = searchResponseWithHits(1);
        SearchResponse<DocumentDTO> emptyWithScroll = emptyScrollResponse(SCROLL_ID);

        when(client.search(any())).thenReturn(withHits, emptyWithScroll);

        invokeProcessDeleteCommandRecursive();

        verify(client, times(1)).clearScroll(SCROLL_ID);
    }

    @Test
    public void mainScroll_nullScrollId_clearScrollNotCalled() throws Exception {
        SearchResponse<DocumentDTO> withHits = searchResponseWithHits(1);
        SearchResponse<DocumentDTO> emptyNullScroll = emptyScrollResponse(null);

        when(client.search(any())).thenReturn(withHits, emptyNullScroll);

        invokeProcessDeleteCommandRecursive();

        verify(client, never()).clearScroll(anyString());
    }

    @Test
    public void mainScroll_clearScrollThrows_exceptionSwallowedAndDoesNotFailDeletion() throws Exception {
        SearchResponse<DocumentDTO> withHits = searchResponseWithHits(1);
        SearchResponse<DocumentDTO> emptyWithScroll = emptyScrollResponse(SCROLL_ID);
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        emptyScrollResponse(SCROLL_ID);

        when(client.search(any())).thenReturn(withHits, emptyWithScroll);
        when(client.bulk(any())).thenReturn(bulkResponse);
        doThrow(new RuntimeException("already closed")).when(client).clearScroll(SCROLL_ID);

        // must not throw
        invokeProcessDeleteCommandRecursive();

        verify(client, times(1)).clearScroll(SCROLL_ID);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void invokeProcessDeleteCommandRecursive() throws Exception {
        Method method = OpenSearchServiceImpl.class.getDeclaredMethod("processDeleteCommandRecursive",
                IndexingCommand.class);
        method.setAccessible(true);
        method.invoke(service, cmd);
    }

    private SearchResponse<DocumentDTO> emptySearchResponse() {
        SearchResponse<DocumentDTO> response = mock(SearchResponse.class);
        HitsMetadata<DocumentDTO> hits = mock(HitsMetadata.class);
        when(hits.hits()).thenReturn(List.of());
        TotalHits total = mock(TotalHits.class);
        when(total.value()).thenReturn(0L);
        when(hits.total()).thenReturn(total);
        when(response.hits()).thenReturn(hits);
        when(response.scrollId()).thenReturn(null);
        return response;
    }

    private SearchResponse<DocumentDTO> emptyScrollResponse(String scrollId) {
        SearchResponse<DocumentDTO> response = mock(SearchResponse.class);
        HitsMetadata<DocumentDTO> hits = mock(HitsMetadata.class);
        when(hits.hits()).thenReturn(List.of());
        TotalHits total = mock(TotalHits.class);
        when(total.value()).thenReturn(0L);
        when(hits.total()).thenReturn(total);
        when(response.hits()).thenReturn(hits);
        when(response.scrollId()).thenReturn(scrollId);
        return response;
    }

    private SearchResponse<DocumentDTO> searchResponseWithHits(int count) {
        SearchResponse<DocumentDTO> response = mock(SearchResponse.class);
        HitsMetadata<DocumentDTO> hits = mock(HitsMetadata.class);
        // for non-empty check we just need hits().hits() to be non-empty
        Hit<DocumentDTO> hit = mock(Hit.class);
        when(hit.id()).thenReturn(DOC_ID);
        when(hit.index()).thenReturn(INDEX);
        when(hit.source()).thenReturn(null);
        when(hits.hits()).thenReturn(List.of(hit));
        TotalHits total = mock(TotalHits.class);
        when(total.value()).thenReturn((long) count);
        when(hits.total()).thenReturn(total);
        when(response.hits()).thenReturn(hits);
        when(response.scrollId()).thenReturn(null);
        return response;
    }
}
