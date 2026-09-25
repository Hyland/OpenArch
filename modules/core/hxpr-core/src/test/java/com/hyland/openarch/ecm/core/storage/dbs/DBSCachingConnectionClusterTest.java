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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.dbs.DBSTransactionState.ConditionalUpdates;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.cluster.ClusterService;

/**
 * Unit tests for the transactional/cluster invalidation paths of {@link DBSCachingConnection}: connection-local caches,
 * propagation to other connections, and cluster invalidation send/receive.
 */
class DBSCachingConnectionClusterTest {

    private DBSConnection delegate;

    private DBSInvalidationsPropagator propagator;

    private DBSClusterInvalidator clusterInvalidator;

    private DBSCachingConnection conn;

    @BeforeEach
    void setUp() {
        delegate = mock(DBSConnection.class);
        DBSCachingRepository repository = mock(DBSCachingRepository.class);
        Cache<String, State> cache = CacheBuilder.newBuilder().build();
        Cache<String, String> childCache = CacheBuilder.newBuilder().build();
        // transactional backend -> connection-local caches, plus a propagator and a cluster invalidator
        when(repository.supportsTransactions()).thenReturn(true);
        when(repository.newCache(false)).thenReturn(cache);
        when(repository.newChildCache(false)).thenReturn(childCache);
        propagator = mock(DBSInvalidationsPropagator.class);
        clusterInvalidator = mock(DBSClusterInvalidator.class);
        when(repository.getInvalidationsPropagator()).thenReturn(propagator);
        when(repository.getClusterInvalidator()).thenReturn(clusterInvalidator);
        conn = new DBSCachingConnection(delegate, repository);
    }

    @Test
    void constructorRegistersInvalidationsQueue() {
        verify(propagator).addQueue(any());
    }

    @Test
    void commitSendsCollectedInvalidationsToOthers() {
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getNodeId()).thenReturn("node1");
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(ClusterService.class)).thenReturn(clusterService);
            conn.updateState("id1", new StateDiff(), mock(ConditionalUpdates.class)); // collects an invalidation
            conn.commit();
        }

        verify(clusterInvalidator).sendInvalidations(any());
        verify(propagator).propagateInvalidations(any(), any());
        verify(delegate).commit();
    }

    @Test
    void beginAppliesReceivedClusterInvalidationsAndUpdatesStatus() {
        DBSInvalidations inv = new DBSInvalidations(true);
        inv.setNodeId("node1");
        inv.setTimestamp(123L);
        DBSInvalidationsList received = new DBSInvalidationsList();
        received.add(inv);
        when(clusterInvalidator.receiveInvalidations()).thenReturn(received);

        ClusterService clusterService = mock(ClusterService.class);
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(ClusterService.class)).thenReturn(clusterService);
            conn.begin();
        }

        verify(delegate).begin();
        verify(propagator).propagateInvalidations(eq(received), any());
        verify(clusterService).updateCachesStatusPerNode("node1", 123L);
    }

    @Test
    void beginSkipsStatusUpdateWhenClusterServiceUnavailable() {
        DBSInvalidations inv = new DBSInvalidations(true);
        inv.setNodeId("node1");
        inv.setTimestamp(123L);
        DBSInvalidationsList received = new DBSInvalidationsList();
        received.add(inv);
        when(clusterInvalidator.receiveInvalidations()).thenReturn(received);

        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(ClusterService.class)).thenReturn(null);
            conn.begin(); // must not throw when the runtime is shutting down
        }

        verify(delegate).begin();
    }

    @Test
    void closeRemovesQueueAndSendsClusterInvalidation() {
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getNodeId()).thenReturn("node1");
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(ClusterService.class)).thenReturn(clusterService);
            conn.close();
        }

        verify(delegate).close();
        verify(propagator).removeQueue(any());
        verify(clusterInvalidator).sendInvalidations(any());
    }
}
