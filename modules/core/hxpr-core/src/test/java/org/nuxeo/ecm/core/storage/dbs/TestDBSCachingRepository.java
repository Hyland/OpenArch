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
package org.nuxeo.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.StateDiff;
import org.nuxeo.runtime.cluster.BasicClusterFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(BasicClusterFeature.class)
public class TestDBSCachingRepository {

    private DBSCachingRepository repository;

    private DBSCachingConnection connection;

    private DBSConnection subConnection;

    private DBSClusterInvalidator invalidator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void before() {
        subConnection = mock(DBSConnection.class);
        when(subConnection.readState(any())).then(invocation -> newState(invocation.getArguments()[0].toString()));
        when(subConnection.readStates(anyList())).then(
                invocation -> ((List<String>) invocation.getArguments()[0]).stream().map(id -> {
                    State state = new State();
                    state.put(SYS_ID, id);
                    return state;
                }).collect(Collectors.toList())); // must be mutable
        when(subConnection.readChildState(any(), any(), any())).then(invocation -> {
            String parentId = (String) invocation.getArguments()[0];
            String name = (String) invocation.getArguments()[1];
            State state = newState(parentId.toString() + "_" + name);
            state.put(SYS_PARENT_ID, parentId);
            state.put(SYS_NAME, name);
            return state;
        });
        invalidator = mock(DBSClusterInvalidator.class);
        DBSRepositoryDescriptor descriptor = newDBSRepositoryDescriptor();
        DBSRepository subRepository = mock(DBSRepository.class);
        repository = new DBSCachingRepository(subRepository, descriptor);
        var spiedRepository = Mockito.spy(repository);
        doReturn(invalidator).when(spiedRepository).getClusterInvalidator();
        connection = new DBSCachingConnection(subConnection, spiedRepository);
    }

    @AfterEach
    public void after() {
        // Used to remove metrics
        repository.shutdown();
    }

    @Test
    public void testReadState() {
        String id = "ID";

        // First read - call sub repository
        State dbState = connection.readState(id);
        verify(subConnection, times(1)).readState(id);

        // Second read - call cache
        State cachedState = connection.readState(id);
        verify(subConnection, times(1)).readState(id);

        assertEquals(dbState, cachedState);
    }

    @Test
    public void testReadStates() {
        String id1 = "ID1";
        String id2 = "ID2";

        // First read - call sub repository
        List<State> dbStates = connection.readStates(Collections.singletonList(id1));
        verify(subConnection, times(1)).readStates(Collections.singletonList(id1));

        // Second read - call cache for id1 and repository for id2
        List<State> cachedStates = connection.readStates(Arrays.asList(id1, id2));
        verify(subConnection, times(1)).readStates(Collections.singletonList(id2));

        assertEquals(1, dbStates.size());
        assertEquals(2, cachedStates.size());
        assertTrue(cachedStates.contains(dbStates.get(0)));
    }

    @Test
    public void testUpdateState() {
        String id = "ID";

        // First add a state in cache
        connection.readState(id);
        connection.readState(id);
        verify(subConnection, times(1)).readState(id);

        // Second update this state
        connection.updateState(id, mock(StateDiff.class), null);
        verify(subConnection, times(1)).updateState(eq(id), any(), any());

        // Check state is no longer in cache
        connection.readState(id);
        verify(subConnection, times(2)).readState(id);
    }

    @Test
    public void testDeleteStates() {
        String id = "ID";

        // First add a state in cache
        connection.readState(id);
        connection.readState(id);
        verify(subConnection, times(1)).readState(id);

        // Second delete this state
        connection.deleteStates(Collections.singleton(id));
        verify(subConnection, times(1)).deleteStates(Collections.singleton(id));

        // Check state is no longer in cache
        connection.readState(id);
        verify(subConnection, times(2)).readState(id);
    }

    @Test
    public void testReadChildState() {
        String parentId = "PARENT-ID";
        String name = "NAME";
        String id = parentId + "_" + name;

        // First read - call sub repository
        State dbState = connection.readChildState(parentId, name, Collections.emptySet());
        verify(subConnection, times(1)).readChildState(eq(parentId), eq(name), any());

        // Second read - call cache
        State cachedState = connection.readChildState(parentId, name, Collections.emptySet());
        verify(subConnection, times(1)).readChildState(eq(parentId), eq(name), any());

        assertEquals(dbState, cachedState);

        // Third read cached state from cache with readState
        cachedState = connection.readState(id);
        assertEquals(dbState, cachedState);
    }

    @Test
    public void testClose() {
        connection.close();
        ArgumentCaptor<DBSInvalidationsList> captureInvals = ArgumentCaptor.forClass(DBSInvalidationsList.class);
        verify(invalidator, times(1)).sendInvalidations(captureInvals.capture());
        var capturedInvals = captureInvals.getAllValues();
        assertEquals(capturedInvals.size(), 1);
        var invals = capturedInvals.get(0);
        assertEquals(invals.getInvalidations().size(), 1);
        assertTrue(invals.getInvalidations().get(0).all);
    }

    @Test
    public void testCommitInvalidatesAndCommits() {
        // CIN-12299: Test that commit() invalidates cache and commits
        String id = "ID";

        // First read a state to cache it
        connection.readState(id);
        verify(subConnection, times(1)).readState(id);

        // Update the state to generate invalidations
        connection.updateState(id, mock(StateDiff.class), null);

        // Commit
        connection.commit();

        // Verify that commit was called on the underlying connection
        verify(subConnection, times(1)).commit();

        // After commit, the state should no longer be in cache
        connection.readState(id);
        verify(subConnection, times(2)).readState(id);
    }

    @Test
    public void testCommitCallsUnderlyingCommit() {
        // CIN-12299: Test that commit delegates to underlying connection
        connection.commit();

        // Verify that commit was called on the underlying connection
        verify(subConnection, times(1)).commit();
    }

    @Test
    public void testBeginCallsUnderlyingBegin() {
        // CIN-12299: Test that begin() delegates to underlying connection
        connection.begin();

        // Verify that begin was called on the underlying connection
        verify(subConnection, times(1)).begin();
    }

    /**
     * Read-repopulates-stale race, deterministic version. A concurrent reader (t1) reads V1 from the repository
     * <em>before</em> a committing update (t2) writes V2 and evicts the cache; t1's {@code putInCache(V1)} then lands
     * <em>after</em> t2's eviction, so the stale V1 sticks in the shared cache and every later reader (e.g. the async
     * indexing loader) reads it.
     * <p>
     * The interleaving is forced with two latches so no thread-timing luck is needed:
     * <ol>
     * <li>t1 enters {@code readState}, misses the cache, and blocks inside the repository read holding V1;</li>
     * <li>t2 runs {@code updateState} (writes V2 + evicts the cache) to completion;</li>
     * <li>t1 is released and finishes its {@code putInCache(V1)}.</li>
     * </ol>
     * With the write clock guard, t1 must detect that an invalidation happened since it started reading and must
     * <em>not</em> repopulate the cache with V1; the next read must then miss and load fresh V2. Without the guard the
     * assertion fails: the cache serves the stale V1.
     */
    @Test
    public void testConcurrentReadDoesNotRepopulateStaleAfterInvalidate() throws InterruptedException {
        String id = "ID";
        String key = "my_title";

        CountDownLatch readerHoldsV1 = new CountDownLatch(1);
        CountDownLatch updateDone = new CountDownLatch(1);
        AtomicReference<String> title = new AtomicReference<>("title-v1");

        // the repository serves V1 while t1 holds it (blocked until the update evicts), then V2 afterwards; t1's
        // read blocks until t2 has committed V2 and evicted the cache to force the put-after-evict interleaving
        doAnswer(invocation -> {
            State state = newState(id);
            state.put(key, title.get());
            if (readerHoldsV1.getCount() > 0) {
                readerHoldsV1.countDown();
                await(updateDone);
            }
            return state;
        }).when(subConnection).readState(eq(id));

        Thread t1 = new Thread(() -> connection.readState(id));
        t1.start();

        // wait until t1 is inside the repository read holding V1, then commit V2 and evict
        readerHoldsV1.await();
        title.set("title-v2");
        connection.updateState(id, mock(StateDiff.class), null);
        updateDone.countDown();

        t1.join();

        // next reader must NOT see the stale V1: t1's putInCache must have been denied by the write clock guard,
        // so this read misses the cache and reloads fresh V2 from the repository
        State afterRace = connection.readState(id);
        assertEquals("title-v2", afterRace.get(key),
                "stale V1 repopulated the cache after the invalidate (read-repopulates-stale race)");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private State newState(String id) {
        State state = new State();
        state.put(SYS_ID, id);
        return state;
    }

    private DBSRepositoryDescriptor newDBSRepositoryDescriptor() {
        DBSRepositoryDescriptor descriptor = new DBSRepositoryDescriptor();
        descriptor.setCacheEnabled(true);
        descriptor.cacheTTL = 10L;
        descriptor.cacheMaxSize = 1000L;
        descriptor.cacheConcurrencyLevel = 1000;
        return descriptor;
    }

    /**
     * A received batch is a <em>list</em> of {@link DBSInvalidations}, drained destructively from the cluster buffer
     * and the connection queue before any of it is applied: whatever is not evicted during this drain can never be
     * redelivered and stays stale in the cache. So a failure while recording the per-node caches status -- which is
     * only bookkeeping -- must not abort the drain and drop the invalidations that come after it in the batch.
     * <p>
     * Regression test for the {@code ClusterServiceImpl.updateCachesStatusPerNode} NPE, whose real damage was not the
     * logged error but the silently dropped evictions behind it.
     */
    @Test
    public void testFailureToUpdateCachesStatusStillAppliesLaterInvalidations() {
        String poisoned = "POISONED_ID";
        String later = "LATER_ID";

        // cache both documents
        connection.readState(poisoned);
        connection.readState(later);
        verify(subConnection, times(1)).readState(poisoned);
        verify(subConnection, times(1)).readState(later);

        // a batch of two invalidations from two different nodes; the first one's bookkeeping will blow up
        var first = new DBSInvalidations();
        first.addAll(Collections.singleton(poisoned));
        first.setNodeId("nodeA");
        var second = new DBSInvalidations();
        second.addAll(Collections.singleton(later));
        second.setNodeId("nodeB");
        second.setTimestamp(456L);
        var batch = new DBSInvalidationsList();
        batch.add(first);
        batch.add(second);

        var spied = Mockito.spy(connection);
        doThrow(new NullPointerException("Cannot invoke \"java.lang.Long.longValue()\" because \"timestamp\" is null"))
                .when(spied)
                .updateCachesStatus(argThat(inv -> "nodeA".equals(inv.getNodeId())));
        when(invalidator.receiveInvalidations()).thenReturn(batch);

        // capture rather than assert straight away, so that a regression in either guarantee below is reported on its
        // own terms instead of the eviction check being masked by the throw
        Throwable escaped = null;
        try {
            spied.begin();
        } catch (RuntimeException e) {
            escaped = e;
        }

        // guarantee 1, the silent one: BOTH documents were evicted. LATER_ID is the regression -- it sits after the
        // failing invalidation in the same already-drained batch, and used to be dropped when the exception unwound
        // out of the forEach, leaving it stale in the cache with nothing left to redeliver it
        connection.readState(poisoned);
        connection.readState(later);
        verify(subConnection, times(2)).readState(poisoned);
        verify(subConnection, times(2)).readState(later);

        // guarantee 2: a bookkeeping failure must not surface to the caller and fail the request
        assertNull(escaped, "the caches-status failure escaped the drain: " + escaped);
    }

    @Test
    public void testGetLockDelegatesToSubConnection() {
        connection.getLock("ID");

        verify(subConnection).getLock("ID");
    }

    @Test
    public void testSetLockDelegatesAndInvalidates() {
        connection.setLock("ID", null);

        verify(subConnection).setLock("ID", null);
    }

    @Test
    public void testRemoveLockDelegatesAndInvalidates() {
        connection.removeLock("ID", "owner");

        verify(subConnection).removeLock("ID", "owner");
    }

    @Test
    public void testCreateClusterInvalidatorThrowsWhenClassMissing() {
        DBSRepositoryDescriptor descriptor = new DBSRepositoryDescriptor();

        assertThrows(org.nuxeo.ecm.core.api.NuxeoException.class,
                () -> repository.createClusterInvalidator(descriptor));
    }

    @Test
    public void testCreateClusterInvalidatorInstantiatesConfiguredClass() {
        DBSRepositoryDescriptor descriptor = new DBSRepositoryDescriptor();
        descriptor.clusterInvalidatorClass = DummyClusterInvalidator.class;

        DBSClusterInvalidator createdInvalidator = repository.createClusterInvalidator(descriptor);

        assertTrue(createdInvalidator instanceof DummyClusterInvalidator);
    }

    public static class DummyClusterInvalidator implements DBSClusterInvalidator {
        @Override
        public void initialize(String nodeId, String repositoryName) {
            // no-op: this test stub only needs to be instantiable to verify createClusterInvalidator wiring
        }

        @Override
        public void close() {
            // no-op: this test stub holds no resources to release
        }

        @Override
        public DBSInvalidationsList receiveInvalidations() {
            return null;
        }

        @Override
        public void sendInvalidations(DBSInvalidationsList invalidations) {
            // no-op: this test stub only verifies createClusterInvalidator wiring, it never propagates invalidations
        }
    }
}
