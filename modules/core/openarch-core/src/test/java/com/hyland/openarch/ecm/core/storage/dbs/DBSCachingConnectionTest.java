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
package com.hyland.openarch.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.PartialList;
import com.hyland.openarch.ecm.core.api.ScrollResult;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.dbs.DBSTransactionState.ConditionalUpdates;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Unit tests for the caching/delegation behavior of {@link DBSCachingConnection}, exercising the non-transactional,
 * no-cluster path with real Guava caches and a mocked backend connection.
 */
class DBSCachingConnectionTest {

    private DBSConnection delegate;

    private AtomicLong writeClock;

    private DBSCachingConnection conn;

    @BeforeEach
    void setUp() {
        delegate = mock(DBSConnection.class);
        DBSCachingRepository repository = mock(DBSCachingRepository.class);
        Cache<String, State> cache = CacheBuilder.newBuilder().build();
        Cache<String, String> childCache = CacheBuilder.newBuilder().build();
        writeClock = new AtomicLong();
        // supportsTransactions() defaults to false -> repository-wide cache path, no propagator, no cluster
        when(repository.getCache()).thenReturn(cache);
        when(repository.getChildCache()).thenReturn(childCache);
        when(repository.getWriteClock()).thenReturn(writeClock);
        conn = new DBSCachingConnection(delegate, repository);
    }

    private static State state(String id, String parentId, String name) {
        State s = new State();
        s.put(SYS_ID, id);
        if (parentId != null) {
            s.put(SYS_PARENT_ID, parentId);
        }
        if (name != null) {
            s.put(SYS_NAME, name);
        }
        return s;
    }

    @Test
    void readStateCachesAndReusesResultOnSecondRead() {
        State s1 = state("id1", null, null);
        when(delegate.readState("id1")).thenReturn(s1);

        assertSame(s1, conn.readState("id1"));
        assertSame(s1, conn.readState("id1"));

        verify(delegate, times(1)).readState("id1");
    }

    @Test
    void readStateWithNullBackendReturnsNullAndDoesNotCache() {
        when(delegate.readState("missing")).thenReturn(null);

        assertNull(conn.readState("missing"));
        assertNull(conn.readState("missing"));

        // nothing cached, so the backend is hit again
        verify(delegate, times(2)).readState("missing");
    }

    @Test
    void concurrentWriteDuringReadDropsStaleCacheEntry() {
        State s1 = state("id1", null, null);
        // simulate a write landing between the read snapshot and putInCache by bumping the clock inside the read
        when(delegate.readState("id1")).thenAnswer(inv -> {
            writeClock.incrementAndGet();
            return s1;
        });

        // the state is still returned to the caller, but the possibly-stale entry is dropped from the cache
        assertSame(s1, conn.readState("id1"));

        conn.readState("id1");
        verify(delegate, times(2)).readState("id1");
    }

    @Test
    void readStatesCachesBackendResultsAndMergesWithCache() {
        State s1 = state("id1", null, null);
        State s2 = state("id2", null, null);
        when(delegate.readStates(List.of("id1", "id2"))).thenReturn(new ArrayList<>(List.of(s1, s2)));

        List<State> first = conn.readStates(List.of("id1", "id2"));
        assertEquals(2, first.size());

        // both are now cached: idsToRetrieve becomes empty
        when(delegate.readStates(List.of())).thenReturn(new ArrayList<>());
        List<State> second = conn.readStates(List.of("id1", "id2"));
        assertEquals(2, second.size());
        assertTrue(second.containsAll(List.of(s1, s2)));

        verify(delegate, times(1)).readStates(List.of("id1", "id2"));
    }

    @Test
    void createStateDoesNotCache() {
        State s1 = state("id1", null, null);
        conn.createState(s1);
        verify(delegate).createState(s1);

        // not cached: a subsequent read goes to the backend
        when(delegate.readState("id1")).thenReturn(s1);
        conn.readState("id1");
        verify(delegate).readState("id1");
    }

    @Test
    void createStatesDoesNotCache() {
        List<State> states = List.of(state("id1", null, null), state("id2", null, null));
        conn.createStates(states);
        verify(delegate).createStates(states);
    }

    @Test
    void updateStateInvalidatesCache() {
        State s1 = state("id1", null, null);
        when(delegate.readState("id1")).thenReturn(s1);
        conn.readState("id1"); // populate cache

        StateDiff diff = new StateDiff();
        ConditionalUpdates cond = mock(ConditionalUpdates.class);
        conn.updateState("id1", diff, cond);
        verify(delegate).updateState("id1", diff, cond);

        // cache was invalidated: next read hits the backend again
        conn.readState("id1");
        verify(delegate, times(2)).readState("id1");
    }

    @Test
    void deleteStatesInvalidatesCache() {
        State s1 = state("id1", null, null);
        when(delegate.readState("id1")).thenReturn(s1);
        conn.readState("id1"); // populate cache

        conn.deleteStates(Set.of("id1"));
        verify(delegate).deleteStates(Set.of("id1"));

        conn.readState("id1");
        verify(delegate, times(2)).readState("id1");
    }

    @Test
    void readChildStateCachesLookup() {
        State child = state("cid", "parent", "childName");
        when(delegate.readChildState("parent", "childName", Set.of())).thenReturn(child);

        assertSame(child, conn.readChildState("parent", "childName", Set.of()));
        assertSame(child, conn.readChildState("parent", "childName", Set.of()));

        verify(delegate, times(1)).readChildState("parent", "childName", Set.of());
    }

    @Test
    void readChildStateInvalidatesMovedEntry() {
        State original = state("cid", "parent1", "nameA");
        when(delegate.readChildState("parent1", "nameA", Set.of())).thenReturn(original);
        conn.readChildState("parent1", "nameA", Set.of()); // caches childCache[parent1_nameA]=cid

        // the document is moved: same id, new parent/name repopulated through readState
        conn.updateState("cid", new StateDiff(), mock(ConditionalUpdates.class)); // evict cid from state cache
        State moved = state("cid", "parent2", "nameB");
        when(delegate.readState("cid")).thenReturn(moved);
        conn.readState("cid"); // state cache now holds the moved state

        // stale childCache entry points at cid but its state no longer matches parent1/nameA
        State refetched = state("cid2", "parent1", "nameA");
        when(delegate.readChildState("parent1", "nameA", Set.of())).thenReturn(refetched);
        assertSame(refetched, conn.readChildState("parent1", "nameA", Set.of()));

        verify(delegate, times(2)).readChildState("parent1", "nameA", Set.of());
    }

    @Test
    void readPartialStateBypassesCache() {
        State partial = state("id1", null, null);
        when(delegate.readPartialState("id1", List.of("a"))).thenReturn(partial);

        assertSame(partial, conn.readPartialState("id1", List.of("a")));

        // not cached: a full read still goes to the backend
        conn.readState("id1");
        verify(delegate).readState("id1");
    }

    @Test
    void getLockDelegates() {
        Lock lock = mock(Lock.class);
        when(delegate.getLock("id1")).thenReturn(lock);
        assertSame(lock, conn.getLock("id1"));
    }

    @Test
    void setLockDelegatesAndInvalidatesCache() {
        State s1 = state("id1", null, null);
        when(delegate.readState("id1")).thenReturn(s1);
        conn.readState("id1"); // populate cache

        Lock previous = mock(Lock.class);
        Lock newLock = mock(Lock.class);
        when(delegate.setLock("id1", newLock)).thenReturn(previous);
        assertSame(previous, conn.setLock("id1", newLock));

        conn.readState("id1");
        verify(delegate, times(2)).readState("id1");
    }

    @Test
    void removeLockDelegatesAndInvalidatesCache() {
        State s1 = state("id1", null, null);
        when(delegate.readState("id1")).thenReturn(s1);
        conn.readState("id1"); // populate cache

        Lock removed = mock(Lock.class);
        when(delegate.removeLock("id1", "owner")).thenReturn(removed);
        assertSame(removed, conn.removeLock("id1", "owner"));

        conn.readState("id1");
        verify(delegate, times(2)).readState("id1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void simpleReadMethodsDelegate() {
        when(delegate.getRootId()).thenReturn("root");
        when(delegate.generateNewId()).thenReturn("new");
        when(delegate.hasChild("p", "n", Set.of())).thenReturn(true);
        when(delegate.queryKeyValuePresence("k", "v", Set.of())).thenReturn(true);
        when(delegate.getSchemaManager()).thenReturn(mock(SchemaManager.class));

        List<State> byKey = List.of(state("id1", null, null));
        when(delegate.queryKeyValue("k", "v", Set.of())).thenReturn(byKey);
        when(delegate.queryKeyValue("k1", "v1", "k2", "v2", Set.of())).thenReturn(byKey);

        Stream<State> descendants = Stream.of(state("id1", null, null));
        when(delegate.getDescendants("id", Set.of())).thenReturn(descendants);
        Stream<State> descendantsLimited = Stream.of(state("id1", null, null));
        when(delegate.getDescendants("id", Set.of(), 5)).thenReturn(descendantsLimited);

        PartialList<java.util.Map<String, Object>> partialList = mock(PartialList.class);
        DBSExpressionEvaluator evaluator = mock(DBSExpressionEvaluator.class);
        when(delegate.queryAndFetch(evaluator, null, false, 0, 0, 0)).thenReturn(partialList);
        ScrollResult<String> scroll = mock(ScrollResult.class);
        when(delegate.scroll(evaluator, 10, 60)).thenReturn(scroll);
        ScrollResult<String> scrollById = mock(ScrollResult.class);
        when(delegate.scroll("scrollId")).thenReturn(scrollById);

        assertEquals("root", conn.getRootId());
        assertEquals("new", conn.generateNewId());
        assertTrue(conn.hasChild("p", "n", Set.of()));
        assertTrue(conn.queryKeyValuePresence("k", "v", Set.of()));
        assertSame(byKey, conn.queryKeyValue("k", "v", Set.of()));
        assertSame(byKey, conn.queryKeyValue("k1", "v1", "k2", "v2", Set.of()));
        assertSame(descendants, conn.getDescendants("id", Set.of()));
        assertSame(descendantsLimited, conn.getDescendants("id", Set.of(), 5));
        assertSame(partialList, conn.queryAndFetch(evaluator, null, false, 0, 0, 0));
        assertSame(scroll, conn.scroll(evaluator, 10, 60));
        assertSame(scrollById, conn.scroll("scrollId"));
        assertSame(conn.getSchemaManager(), conn.getSchemaManager());
    }

    @Test
    void beginCommitRollbackDelegate() {
        conn.begin();
        verify(delegate).begin();

        conn.commit();
        verify(delegate).commit();

        conn.rollback();
        verify(delegate).rollback();
    }

    @Test
    void closeClosesDelegate() {
        conn.close();
        verify(delegate).close();
    }

    @Test
    void closeDoesNotSendClusterInvalidationsWithoutCluster() {
        conn.close();
        // no cluster invalidator configured -> only the backend connection is closed
        verify(delegate).close();
        verify(delegate, never()).begin();
    }
}
