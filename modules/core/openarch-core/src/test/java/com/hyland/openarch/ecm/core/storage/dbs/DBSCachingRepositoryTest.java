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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.schema.FulltextConfiguration;
import com.hyland.openarch.ecm.core.schema.SchemaManager;

import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.cluster.ClusterService;

/**
 * Unit tests for {@link DBSCachingRepository} construction (cache init, cluster/invalidator wiring) and its delegation
 * to the wrapped {@link DBSRepository}.
 */
class DBSCachingRepositoryTest {

    private DBSRepository backend;

    /** Builds a caching repository with a mocked backend and cluster service active during construction. */
    private DBSCachingRepository build(boolean transactions, boolean clusterEnabled,
            Class<? extends DBSClusterInvalidator> invalidatorClass, String name) {
        backend = mock(DBSRepository.class);
        when(backend.supportsTransactions()).thenReturn(transactions);
        when(backend.getName()).thenReturn(name);
        DBSRepositoryDescriptor descriptor = new DBSRepositoryDescriptor();
        descriptor.name = name;
        descriptor.cacheTTL = 1L;
        descriptor.cacheMaxSize = 100L;
        descriptor.cacheConcurrencyLevel = 1;
        descriptor.clusterInvalidatorClass = invalidatorClass;
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.isEnabled()).thenReturn(clusterEnabled);
        when(clusterService.getNodeId()).thenReturn("node1");
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(ClusterService.class)).thenReturn(clusterService);
            return new DBSCachingRepository(backend, descriptor);
        }
    }

    @Test
    void nonTransactionalNoClusterBuildsGlobalCacheAndNoInvalidators() {
        DBSCachingRepository repo = build(false, false, null, "cachingRepoTestA");

        assertFalse(repo.supportsTransactions());
        assertNotNull(repo.getCache());
        assertNotNull(repo.getChildCache());
        assertNotNull(repo.getWriteClock());
        assertNull(repo.getInvalidationsPropagator());
        assertNull(repo.getClusterInvalidator());

        repo.shutdown();
        verify(backend).shutdown();
    }

    @Test
    void transactionalClusterEnabledInitializesInvalidators() {
        DBSCachingRepository repo = build(true, true, RecordingClusterInvalidator.class, "cachingRepoTestB");

        assertTrue(repo.supportsTransactions());
        // transactional backend -> each connection owns its cache, none held by the repository
        assertNull(repo.getCache());
        assertNull(repo.getChildCache());
        assertNull(repo.getWriteClock());
        assertNotNull(repo.getInvalidationsPropagator());
        DBSClusterInvalidator ci = repo.getClusterInvalidator();
        assertInstanceOf(RecordingClusterInvalidator.class, ci);
        assertEquals("node1", ((RecordingClusterInvalidator) ci).initializedNodeId);
    }

    @Test
    void transactionalClusterDisabledStillInitializesPropagator() {
        DBSCachingRepository repo = build(true, false, null, "cachingRepoTestB2");

        // each connection owns its cache, so invalidations must be propagated between them even without clustering,
        // which only adds propagation between nodes
        assertNull(repo.getCache());
        assertNotNull(repo.getInvalidationsPropagator());
        assertNull(repo.getClusterInvalidator());
    }

    @Test
    void clusterInvalidatorInstantiationFailureWrapsInRepositoryException() {
        assertThrows(RepositoryException.class,
                () -> build(true, true, FailingClusterInvalidator.class, "cachingRepoTestC"));
    }

    @Test
    void getConnectionWrapsBackendConnection() {
        DBSCachingRepository repo = build(false, false, null, "cachingRepoTestD");
        DBSConnection backendConnection = mock(DBSConnection.class);
        when(backend.getConnection()).thenReturn(backendConnection);

        DBSConnection connection = repo.getConnection();
        assertInstanceOf(DBSCachingConnection.class, connection);
    }

    @Test
    void delegatesToBackendRepository() {
        DBSCachingRepository repo = build(false, false, null, "cachingRepoTestE");

        FulltextConfiguration ftc = mock(FulltextConfiguration.class);
        when(backend.getFulltextConfiguration()).thenReturn(ftc);
        when(backend.isFulltextDisabled()).thenReturn(true);
        when(backend.isFulltextStoredInBlob()).thenReturn(true);
        Lock lock = mock(Lock.class);
        when(backend.getLock("id")).thenReturn(lock);
        Lock setLock = mock(Lock.class);
        when(backend.setLock("id", setLock)).thenReturn(lock);
        when(backend.removeLock("id", "owner")).thenReturn(lock);
        List.of(List.of("a"));
        SchemaManager schemaManager = mock(SchemaManager.class);
        when(backend.getSchemaManager()).thenReturn(schemaManager);

        assertSame(ftc, repo.getFulltextConfiguration());
        assertTrue(repo.isFulltextDisabled());
        assertTrue(repo.isFulltextStoredInBlob());
        assertSame(lock, repo.getLock("id"));
        assertSame(lock, repo.setLock("id", setLock));
        assertSame(lock, repo.removeLock("id", "owner"));
        assertEquals("cachingRepoTestE", repo.getName());
        assertSame(schemaManager, repo.getSchemaManager());

        @SuppressWarnings("unchecked")
        BiConsumer<String, String> marker = mock(BiConsumer.class);
        repo.markReferencedBlobs(marker);
        verify(backend).markReferencedBlobs(marker);

    }

    /** A cluster invalidator that records the node id passed to {@link #initialize}. */
    public static class RecordingClusterInvalidator implements DBSClusterInvalidator {

        String initializedNodeId;

        @Override
        public void initialize(String nodeId, String repositoryName) {
            this.initializedNodeId = nodeId;
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public DBSInvalidationsList receiveInvalidations() {
            return null;
        }

        @Override
        public void sendInvalidations(DBSInvalidationsList invalidations) {
            // no-op
        }
    }

    /** A cluster invalidator whose construction fails, to exercise the reflective-failure path. */
    public static class FailingClusterInvalidator implements DBSClusterInvalidator {

        public FailingClusterInvalidator() {
            throw new IllegalStateException("cannot instantiate");
        }

        @Override
        public void initialize(String nodeId, String repositoryName) {
            // unreachable
        }

        @Override
        public void close() {
            // unreachable
        }

        @Override
        public DBSInvalidationsList receiveInvalidations() {
            return null;
        }

        @Override
        public void sendInvalidations(DBSInvalidationsList invalidations) {
            // unreachable
        }
    }
}
