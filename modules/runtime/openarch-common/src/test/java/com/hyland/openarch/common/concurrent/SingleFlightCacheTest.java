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
package com.hyland.openarch.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** Isolated unit tests for {@link SingleFlightCache} - no mocking, no Spring/OpenArch runtime. */
class SingleFlightCacheTest {

    private static final String KEY = "key";

    /**
     * Deterministic reproduction of the "stale cleanup clobbers a newer generation" bug: a caller belonging to an OLD
     * generation (e.g. a follower whose own cleanup runs late, well after the leader already finished and a fresh
     * generation started) must not be able to evict a NEWER generation's entry just because they share the same key.
     * Constructed directly against the cache's internal state rather than via real thread timing, since forcing that
     * exact interleaving through the public API alone would require an unsafe/unportable delay of unbounded length
     * (long enough for an entire second load cycle to complete) - not something a test should rely on.
     */
    @Test
    void release_shouldNotRemoveANewerFlight_whenCalledForAStaleOne() {
        SingleFlightCache<String> cache = new SingleFlightCache<>();
        SingleFlightCache.Flight<String> flightA = new SingleFlightCache.Flight<>("A");
        SingleFlightCache.Flight<String> flightB = new SingleFlightCache.Flight<>("B");

        // Generation B is the CURRENT entry (as if A already finished, was cleaned up, and a fresh
        // generation B has since started and finished for the same key).
        cache.inFlight.put(KEY, flightB);

        // A caller from the OLD generation A calls release() with ITS OWN (stale) flight reference.
        cache.release(KEY, flightA);

        // B's entry must survive - it was never A's to remove.
        assertThat(cache.inFlight.getIfPresent(KEY)).isSameAs(flightB);
    }

    /**
     * Starts a "leader" call, then releases "follower" calls only once each is confirmed blocked inside
     * {@code cache.get} (see {@link #awaitAllBlockedOnSharedLoad}), not just "started running".
     */
    @Test
    void get_shouldCoalesceConcurrentCallers_intoSingleLoaderInvocation() throws Exception {
        SingleFlightCache<String> cache = new SingleFlightCache<>();
        AtomicInteger invocations = new AtomicInteger();
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        int followerCount = 7;
        List<Thread> followerThreads = new CopyOnWriteArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(followerCount + 1)) {
            Future<String> leader = pool.submit(() -> cache.get(KEY, () -> {
                invocations.incrementAndGet();
                loadStarted.countDown();
                await(releaseLoad);
                return "value";
            }));

            assertThat(loadStarted.await(2, TimeUnit.SECONDS)).isTrue();

            List<Future<String>> followers = IntStream.range(0, followerCount).mapToObj(_ -> pool.submit(() -> {
                followerThreads.add(Thread.currentThread());
                return cache.get(KEY, () -> {
                    invocations.incrementAndGet(); // should never run
                    return "value";
                });
            })).toList();
            awaitAllBlockedOnSharedLoad(followerThreads, followerCount);
            releaseLoad.countDown();

            assertThat(leader.get(5, TimeUnit.SECONDS)).isEqualTo("value");
            for (Future<String> follower : followers) {
                assertThat(follower.get(5, TimeUnit.SECONDS)).isEqualTo("value");
            }
        }

        // Despite followerCount concurrent waiters, the loader ran exactly once.
        assertThat(invocations.get()).isEqualTo(1);
    }

    /** Same race-free shape as above, proving concurrent waiters share a FAILED load too. */
    @Test
    void get_concurrentCallers_shouldAllReceiveSameFailure_whenLoaderFails() throws Exception {
        SingleFlightCache<String> cache = new SingleFlightCache<>();
        AtomicInteger invocations = new AtomicInteger();
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        int followerCount = 7;
        List<Thread> followerThreads = new CopyOnWriteArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(followerCount + 1)) {
            Future<String> leader = pool.submit(() -> cache.get(KEY, () -> {
                invocations.incrementAndGet();
                loadStarted.countDown();
                await(releaseLoad);
                throw new IllegalStateException("boom");
            }));

            assertThat(loadStarted.await(2, TimeUnit.SECONDS)).isTrue();

            List<Future<String>> followers = IntStream.range(0, followerCount).mapToObj(_ -> pool.submit(() -> {
                followerThreads.add(Thread.currentThread());
                return cache.get(KEY, () -> {
                    invocations.incrementAndGet(); // should never run
                    throw new IllegalStateException("should never run");
                });
            })).toList();
            awaitAllBlockedOnSharedLoad(followerThreads, followerCount);
            releaseLoad.countDown();

            assertThatThrownBy(() -> leader.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                                                                     .hasCauseInstanceOf(IllegalStateException.class)
                                                                     .hasRootCauseMessage("boom");
            for (Future<String> follower : followers) {
                assertThatThrownBy(() -> follower.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                                                                           .hasCauseInstanceOf(
                                                                                   IllegalStateException.class)
                                                                           .hasRootCauseMessage("boom");
            }
        }

        // Concurrent waiters share the SAME failure - the loader runs once, not once per caller.
        assertThat(invocations.get()).isEqualTo(1);
    }

    @Test
    void get_sequentialCalls_shouldEachInvokeLoader() {
        SingleFlightCache<String> cache = new SingleFlightCache<>();
        AtomicInteger invocations = new AtomicInteger();

        String first = cache.get(KEY, () -> {
            invocations.incrementAndGet();
            return "value";
        });
        String second = cache.get(KEY, () -> {
            invocations.incrementAndGet();
            return "value";
        });

        assertThat(first).isEqualTo("value");
        assertThat(second).isEqualTo("value");
        // Sequential calls aren't coalesced: the entry is invalidated right after each use.
        assertThat(invocations.get()).isEqualTo(2);
    }

    /**
     * Documents the invariant the removal of the old {@code invalidate(key)} call in the catch block relies on: Guava
     * already removes a failed load's placeholder by reference before propagating the exception, so no residual entry
     * is left for our own code to (unsafely) clean up.
     */
    @Test
    void get_afterFailedLoad_shouldLeaveNoResidualEntry() {
        SingleFlightCache<String> cache = new SingleFlightCache<>();

        assertThatThrownBy(() -> cache.get(KEY, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cache.inFlight.getIfPresent(KEY)).isNull();
    }

    /**
     * Safety net for the one case normal cleanup can't cover: a caller that dies between the load and its
     * {@code release}, leaving its entry behind. Simulated by writing an entry that no caller ever releases.
     */
    @Test
    void inFlight_shouldEvictAnAbandonedEntry_afterItsMaxFlightDurationElapses() {
        SingleFlightCache<String> cache = new SingleFlightCache<>(Duration.ofMillis(1));
        cache.inFlight.put(KEY, new SingleFlightCache.Flight<>("abandoned"));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (cache.inFlight.getIfPresent(KEY) == null) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        throw new AssertionError("Timed out waiting for the abandoned entry to be evicted");
    }

    @Test
    void get_afterFailedLoad_shouldRetryOnNextCall() {
        SingleFlightCache<String> cache = new SingleFlightCache<>();
        AtomicInteger invocations = new AtomicInteger();

        assertThatThrownBy(() -> cache.get(KEY, () -> {
            invocations.incrementAndGet();
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("boom");

        String result = cache.get(KEY, () -> {
            invocations.incrementAndGet();
            return "value";
        });

        // A failed load doesn't poison the key.
        assertThat(result).isEqualTo("value");
        assertThat(invocations.get()).isEqualTo(2);
    }

    /**
     * Guava's Cache rejects null keys ({@code NullPointerException} on both {@code get} and {@code invalidate}), unlike
     * the durable OpenArch caches this sits alongside - a null key must bypass coalescing entirely rather than blow up.
     */
    @Test
    void get_withNullKey_shouldBypassCacheAndCallLoaderDirectly() {
        SingleFlightCache<String> cache = new SingleFlightCache<>();
        AtomicInteger invocations = new AtomicInteger();

        String result = cache.get(null, () -> {
            invocations.incrementAndGet();
            return "value";
        });

        assertThat(result).isEqualTo("value");
        assertThat(invocations.get()).isEqualTo(1);
    }

    @Test
    void get_withNullKey_shouldPropagateLoaderExceptionUnwrapped() {
        SingleFlightCache<String> cache = new SingleFlightCache<>();

        assertThatThrownBy(() -> cache.get(null, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Polls until all follower threads are blocked (WAITING/TIMED_WAITING) - proof each joined the leader's load rather
     * than just "started running". Uses {@link LockSupport#parkNanos}, not {@code Thread.sleep} (java:S2925).
     */
    private static void awaitAllBlockedOnSharedLoad(List<Thread> followerThreads, int expectedCount) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (followerThreads.size() == expectedCount && followerThreads.stream().allMatch(t -> {
                Thread.State state = t.getState();
                return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
            })) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        throw new AssertionError("Timed out waiting for all followers to block on the shared in-flight load");
    }
}
