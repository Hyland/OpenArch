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
package com.hyland.openarch.ecm.core.blob;

import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.runtime.kv.KeyValueStore;
import com.hyland.openarch.runtime.kv.KeyValueStoreDescriptor;
import com.hyland.openarch.runtime.kv.MemKeyValueStore;

/**
 * Tests the cluster-wide coordination of blob garbage collection: two nodes are simulated by two garbage collectors
 * sharing a single key/value store.
 */
public class TestBlobGarbageCollectorCluster {

    protected static final String KEY1 = "key1";

    protected static final String KEY2 = "key2";

    /** Shared by the simulated nodes, standing in for the key/value store of a real cluster. */
    protected KeyValueStore sharedStore;

    /**
     * A garbage collector for an in-memory set of blobs, standing in for a node of a cluster.
     */
    protected class Node extends AbstractBlobGarbageCollector {

        /** The blobs present in the (shared) storage. */
        protected final Set<String> blobs;

        protected final List<String> deleted = new ArrayList<>();

        /** Run just after the remote marks have been read in batches, to simulate a concurrent write. */
        protected Runnable retainNotMarkedRemotelyHook;

        /** Run while a blob is being deleted, to simulate a concurrent write racing with that deletion. */
        protected Runnable removeUnmarkedBlobHook;

        public Node(Set<String> blobs) {
            this.blobs = blobs;
        }

        @Override
        public String getStorageAddress() {
            // same id on all nodes: they garbage collect the same storage
            return "test";
        }

        @Override
        protected String getId() {
            // unused: getKVStore() is overridden below to bypass the per-provider store lookup
            return "test";
        }

        @Override
        protected KeyValueStore getKVStore() {
            // these tests simulate a cluster without a runtime, using a shared in-memory store
            return sharedStore;
        }

        @Override
        public void computeToDelete() {
            toDelete.addAll(blobs);
        }

        @Override
        protected void retainNotMarkedRemotely(Set<String> keys) {
            super.retainNotMarkedRemotely(keys);
            if (retainNotMarkedRemotelyHook != null) {
                retainNotMarkedRemotelyHook.run();
            }
        }

        @Override
        protected long removeUnmarkedBlob(String key, boolean delete) {
            if (!blobs.contains(key)) {
                return -1;
            }
            if (removeUnmarkedBlobHook != null) {
                removeUnmarkedBlobHook.run();
            }
            if (delete) {
                blobs.remove(key);
            }
            deleted.add(key);
            return 1;
        }
    }

    @BeforeEach
    public void setUp() {
        MemKeyValueStore store = new MemKeyValueStore();
        KeyValueStoreDescriptor descriptor = new KeyValueStoreDescriptor();
        descriptor.name = AbstractBlobGarbageCollector.GC_KV_STORE_NAME_PREFIX;
        store.initialize(descriptor);
        sharedStore = store;
        // don't slow down the tests; correctness doesn't depend on the value
        AbstractBlobGarbageCollector.GC_ACTIVE_CACHE = Duration.ZERO;
    }

    @AfterEach
    public void tearDown() {
        AbstractBlobGarbageCollector.GC_ACTIVE_CACHE = Duration.ofSeconds(5);
        AbstractBlobGarbageCollector.GC_DELETION_WAIT = Duration.ofSeconds(30);
        AbstractBlobGarbageCollector.GC_MARK_TTL = Duration.ofDays(1);
    }

    protected Node newNode(String... blobs) {
        return new Node(new LinkedHashSet<>(Arrays.asList(blobs)));
    }

    @Test
    public void testLockIsExclusivePerStore() {
        Set<String> blobs = new HashSet<>();
        Node node1 = new Node(blobs);
        Node node2 = new Node(blobs);

        node1.start(false);
        assertTrue(node1.isInProgress());
        assertEquals("1", node1.getRunCounter());

        // the other node can't garbage collect the same storage at the same time
        assertThrows(GCAlreadyStartedException.class, () -> node2.start(false));

        node1.stop(false);

        // once the first node is done, the other one can run, with a new run counter
        node2.start(false);
        assertEquals("3", node2.getRunCounter());
        assertNull(node1.getRunCounter());
        node2.stop(false);
    }

    @Test
    public void testRunCountersAreMonotonicAndNeverReused() {
        Node node = newNode();
        Set<String> runCounters = new HashSet<>();

        long previous = 0;
        for (int i = 0; i < 5; i++) {
            node.start(false);
            String runCounter = node.getRunCounter();
            long value = Long.parseLong(runCounter);
            assertTrue(value > previous, "run counter should increase: " + value + " after " + previous);
            previous = value;
            runCounters.add(runCounter);
            node.stop(false);
        }
        assertEquals(5, runCounters.size());

        // the run counter has no TTL: it outlives the runs so that a run counter is never reused
        assertEquals(Long.valueOf(previous), sharedStore.getLong(node.getRunCounterKey()));
    }

    @Test
    public void testLockIsPerStore() {
        Node node1 = newNode();
        Node node2 = new Node(new HashSet<>()) {
            @Override
            protected String getId() {
                return "other";
            }
        };

        // a garbage collection on a store doesn't prevent one on another store
        node1.start(false);
        node2.start(false);
        node2.stop(false);
        node1.stop(false);
    }

    @Test
    public void testLockIsReleasedOnFailedStart() {
        Node node1 = new Node(new HashSet<>()) {
            @Override
            public void computeToDelete() {
                throw new IllegalStateException("failed");
            }
        };
        Node node2 = newNode();

        assertThrows(IllegalStateException.class, () -> node1.start(false));
        assertNull(sharedStore.getString(node1.getLockKey()));

        // the storage isn't left locked
        node2.start(false);
        node2.stop(false);
    }

    @Test
    public void testLockLostStopsActualDeletion() {
        Node node1 = newNode(KEY1);
        node1.start(true);
        String runCounter = node1.getRunCounter();
        // simulate this node stalling past the lock's TTL and another node taking over the lock with a new run
        assertTrue(sharedStore.compareAndSet(node1.getLockKey(), runCounter, "999"));

        // the next scheduled heartbeat notices it no longer owns the lock
        node1.heartbeatLock(sharedStore);
        // writes on the former owner are now marked for the takeover run instead of only in its stale local set
        node1.mark(KEY1);
        assertEquals("1", sharedStore.getString(node1.getMarkKey("999", KEY1)));
        node1.stop(true);

        // the whole stop() is aborted without touching anything, as this node can no longer guarantee an exclusive,
        // up-to-date view of what is marked; the blob is left for the next garbage collection run to consider
        assertEquals(List.of(), node1.deleted);
        assertEquals(Set.of(KEY1), node1.blobs);
    }

    @Test
    public void testRunOutlastingTheMarkTTLStopsActualDeletion() {
        Node node1 = newNode(KEY1);
        node1.start(true);
        // simulate this node's run lasting longer than the TTL of the marks protecting keys written on other nodes
        AbstractBlobGarbageCollector.GC_MARK_TTL = Duration.ZERO;
        node1.stop(true);

        // the whole stop() is aborted without touching anything, as a mark may have expired and a key referenced on
        // another node would then look unmarked; the blob is left for the next garbage collection run to consider
        assertEquals(List.of(), node1.deleted);
        assertEquals(Set.of(KEY1), node1.blobs);
    }

    @Test
    public void testExpiredLockStopsDeletionWithoutHeartbeat() {
        Node node = newNode(KEY1);
        node.start(true);
        node.lockDeadline = System.nanoTime();
        node.stop(true);

        assertTrue(node.lockLost);
        assertEquals(List.of(), node.deleted);
        assertEquals(Set.of(KEY1), node.blobs);
        assertNull(sharedStore.getString(node.getLockKey()));
    }

    @Test
    public void testHeartbeatRefreshesLockDeadline() {
        Node node = newNode(KEY1);
        node.start(true);
        node.lockDeadline = System.nanoTime() + Duration.ofMinutes(1).toNanos();
        long before = System.nanoTime();
        node.heartbeatLock(sharedStore);
        assertTrue(node.lockDeadline - before >= AbstractBlobGarbageCollector.GC_LOCK_TTL.toNanos());
        node.stop(true);

        assertEquals(List.of(KEY1), node.deleted);
    }

    @Test
    public void testFailedHeartbeatDoesNotExtendLockDeadline() {
        Node node = newNode(KEY1);
        node.start(true);
        KeyValueStore failingStore = mock(KeyValueStore.class);
        when(failingStore.compareAndSet(node.getLockKey(), node.getRunCounter(), node.getRunCounter(),
                AbstractBlobGarbageCollector.GC_LOCK_TTL.toSeconds())).thenThrow(new IllegalStateException("offline"));
        long deadline = node.lockDeadline;
        node.heartbeatLock(failingStore);
        assertEquals(deadline, node.lockDeadline);
        assertFalse(node.lockLost);

        node.lockDeadline = System.nanoTime();
        node.heartbeatLock(sharedStore);
        assertTrue(node.lockLost);
        node.stop(true);
        assertEquals(List.of(), node.deleted);
    }

    @Test
    public void testSlowHeartbeatCannotReviveExpiredLease() {
        Node node = newNode(KEY1);
        node.start(true);
        KeyValueStore slowStore = mock(KeyValueStore.class);
        when(slowStore.compareAndSet(node.getLockKey(), node.getRunCounter(), node.getRunCounter(),
                AbstractBlobGarbageCollector.GC_LOCK_TTL.toSeconds())).thenAnswer(invocation -> {
                    node.lockDeadline = System.nanoTime();
                    return true;
                });
        node.heartbeatLock(slowStore);
        assertTrue(node.lockLost);
        node.stop(true);
        assertEquals(List.of(), node.deleted);
    }

    @Test
    public void testLockExpiringDuringDeletionStopsRemainingDeletions() {
        Node node = newNode(KEY1, KEY2);
        node.start(true);
        node.removeUnmarkedBlobHook = () -> node.lockDeadline = System.nanoTime();
        node.stop(true);

        assertEquals(1, node.deleted.size());
        assertEquals(1, node.blobs.size());
    }

    @Test
    public void testLockExpiringDuringClaimStopsDeletionAndReleasesClaim() {
        Node node = new Node(new HashSet<>(Set.of(KEY1))) {
            @Override
            protected boolean claimForDeletion(String key) {
                boolean claimed = super.claimForDeletion(key);
                lockDeadline = System.nanoTime();
                return claimed;
            }
        };
        node.start(true);
        String markKey = node.getMarkKey(node.getRunCounter(), KEY1);
        node.stop(true);

        assertEquals(List.of(), node.deleted);
        assertEquals(Set.of(KEY1), node.blobs);
        assertEquals(AbstractBlobGarbageCollector.MARK_DELETED, sharedStore.getString(markKey));
    }

    @Test
    public void testBlobWrittenOnOtherNodeIsProtected() {
        Node node1 = newNode(KEY1, KEY2);
        Node node2 = new Node(node1.blobs);

        node1.start(true);
        // node2 writes a blob reusing the existing unreferenced key1, while node1's GC is running
        node2.mark(KEY1);
        // node1 doesn't know about it locally, but sees the mark in the key/value store
        node1.stop(true);

        assertEquals(List.of(KEY2), node1.deleted);
        assertEquals(Set.of(KEY1), node1.blobs);
    }

    @Test
    public void testLateLocalMarkDuringStopIsHonored() {
        // the deletion candidates not marked remotely are snapshotted once before the deletion loop starts (so that
        // the remote check can be batched); a local mark arriving after that snapshot but before this key's own
        // deletion (e.g. a concurrent blob write on this same node) must still be honored
        Node node1 = new Node(new LinkedHashSet<>(Arrays.asList(KEY1, KEY2))) {
            @Override
            protected void retainNotMarkedRemotely(Set<String> keys) {
                super.retainNotMarkedRemotely(keys);
                marked.put(KEY2, MARK_IN_USE);
            }
        };

        node1.start(true);
        node1.stop(true);

        assertEquals(List.of(KEY1), node1.deleted);
        assertEquals(Set.of(KEY2), node1.blobs);
    }

    @Test
    public void testLateRemoteMarkDuringStopIsHonored() {
        // same as above but for a blob written on another node: the remote marks are read in batches before the
        // deletion loop, and a sweep can last for hours, so the mark is re-read just before each deletion
        Node node1 = new Node(new LinkedHashSet<>(Arrays.asList(KEY1, KEY2)));
        Node node2 = new Node(node1.blobs);
        node1.retainNotMarkedRemotelyHook = () -> node2.mark(KEY2);

        node1.start(true);
        node1.stop(true);

        assertEquals(List.of(KEY1), node1.deleted);
        assertEquals(Set.of(KEY2), node1.blobs);
    }

    @Test
    public void testWriteWaitsWhileTheBlobIsBeingDeleted() throws InterruptedException {
        Node node1 = newNode(KEY1);
        Node node2 = new Node(node1.blobs);
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch written = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());

        node1.removeUnmarkedBlobHook = () -> {
            // the key is claimed and the blob is being deleted; a write wanting to reference it must not proceed
            claimed.countDown();
            try {
                assertFalse(written.await(500, TimeUnit.MILLISECONDS), "the write should still be waiting");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            order.add("deleted");
        };

        Thread writer = new Thread(() -> {
            try {
                claimed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            node2.mark(KEY1);
            order.add("written");
            written.countDown();
        });
        writer.start();
        node1.start(true);
        node1.stop(true);
        writer.join();

        // the write didn't reference a blob that was about to vanish: it waited for the deletion to be over, and now
        // sees the blob gone, so it writes it again rather than merely pointing at it
        assertEquals(List.of("deleted", "written"), order);
        assertEquals(List.of(KEY1), node1.deleted);
    }

    @Test
    public void testWriteFailsIfTheDeletionNeverCompletes() {
        AbstractBlobGarbageCollector.GC_DELETION_WAIT = Duration.ofMillis(100);
        Node node1 = newNode(KEY1);
        Node node2 = new Node(node1.blobs);

        node1.start(true);
        // simulate a node that claimed the key and then died before publishing the outcome of the deletion
        assertTrue(node1.claimForDeletion(KEY1));

        // rather than risk referencing a blob that may still be deleted, the write fails and is left to be retried
        BlobBeingDeletedException e = assertThrows(BlobBeingDeletedException.class, () -> node2.mark(KEY1));
        assertTrue(e.getMessage().contains(KEY1), e.getMessage());
        // the caller is told it is transient and when to come back, rather than getting an opaque 500
        assertEquals(SC_SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals(AbstractBlobGarbageCollector.GC_LOCK_TTL.toSeconds(), e.getRetryAfterSeconds());
        // same on the node running the garbage collection itself
        assertThrows(BlobBeingDeletedException.class, () -> node1.mark(KEY1));

        node1.stop(true);
    }

    @Test
    public void testClaimIsRefusedForABlobMarkedOnAnotherNode() {
        Node node1 = newNode(KEY1);
        Node node2 = new Node(node1.blobs);

        node1.start(true);
        node2.mark(KEY1);

        // the claim loses against the mark, so the blob is left alone
        assertFalse(node1.claimForDeletion(KEY1));
        node1.stop(true);

        assertEquals(List.of(), node1.deleted);
        assertEquals(Set.of(KEY1), node1.blobs);
    }

    @Test
    public void testMarkIsScopedToTheRun() {
        Node node1 = newNode(KEY1);
        Node node2 = new Node(node1.blobs);

        // a mark left over from a previous run doesn't protect a blob during the next one
        node1.start(true);
        node2.mark(KEY1);
        node1.stop(true);
        assertEquals(Set.of(KEY1), node1.blobs);

        Node node3 = new Node(node1.blobs);
        node3.start(true);
        node3.stop(true);
        assertEquals(List.of(KEY1), node3.deleted);
        assertTrue(node3.blobs.isEmpty());
    }

    @Test
    public void testMarksAreCheckedInBatches() {
        // more keys than a single batch, to exercise the batching of the key/value store reads
        int count = 3 * AbstractBlobGarbageCollector.GC_MARK_BATCH_SIZE + 7;
        Set<String> blobs = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            blobs.add("key" + i);
        }
        Node node1 = new Node(blobs);
        Node node2 = new Node(blobs);

        node1.start(true);
        // every third blob is rewritten on the other node during the GC
        Set<String> kept = new HashSet<>();
        for (int i = 0; i < count; i += 3) {
            String key = "key" + i;
            node2.mark(key);
            kept.add(key);
        }
        node1.stop(true);

        assertEquals(kept, node1.blobs);
        assertEquals(count - kept.size(), node1.deleted.size());
    }

    @Test
    public void testNoMarkWrittenWhenNoGarbageCollectionRuns() {
        Node node = newNode(KEY1);

        node.mark(KEY1);

        // nothing was written to the key/value store, blob writes don't pay for it outside a GC
        assertNull(sharedStore.getString(node.getMarkKey("any", KEY1)));
    }
}
