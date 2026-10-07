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
package com.hyland.openarch.runtime.idempotency;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.hyland.openarch.runtime.idempotency.IdempotencyHandler.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.runtime.kv.KeyValueService;
import com.hyland.openarch.runtime.kv.KeyValueStore;
import com.hyland.openarch.runtime.kv.MemKeyValueStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link KeyValueStoreIdempotencyHandler}.
 */
class KeyValueStoreIdempotencyHandlerTest {

    private static final String NAMESPACE = "idempotency:test:";

    private static final long TTL_SECONDS = 3600L;

    private MemKeyValueStore store;

    private KeyValueStoreIdempotencyHandler handler;

    private KeyValueService keyValueService;

    @BeforeEach
    void setUp() {
        IdempotencyResultTypeRegistry.register("string", String.class);
        IdempotencyResultTypeRegistry.registerList("sample-dto-list", SampleDto.class);
        store = new MemKeyValueStore();
        keyValueService = name -> store;
        handler = new KeyValueStoreIdempotencyHandler(keyValueService, "default", NAMESPACE, TTL_SECONDS);
    }

    @Test
    @DisplayName("first execute runs the operation and caches the result")
    void execute_firstCall_runsOperationAndCaches() {
        String key = "key-1";
        AtomicInteger calls = new AtomicInteger();

        Object result = handler.execute(key, () -> {
            calls.incrementAndGet();
            return "value";
        }).value();

        assertEquals("value", result);
        assertEquals(1, calls.get());
        assertTrue(handler.isCached(key));
    }

    @Test
    @DisplayName("repeat execute with the same key replays the cached value without re-running the operation")
    void execute_repeatCall_replaysCachedValue() {
        String key = "key-2";
        AtomicInteger calls = new AtomicInteger();

        String first = handler.execute(key, () -> {
            calls.incrementAndGet();
            return "value-" + calls.get();
        }).value();
        String second = handler.execute(key, () -> {
            calls.incrementAndGet();
            return "value-" + calls.get();
        }).value();

        assertEquals(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("different keys are cached independently under the configured namespace")
    void execute_differentKeys_doNotCollide() {
        String first = handler.execute("key-a", () -> "value-a").value();
        String second = handler.execute("key-b", () -> "value-b").value();

        assertEquals("value-a", first);
        assertEquals("value-b", second);
        assertTrue(store.get(NAMESPACE + "key-a").length > 0);
        assertTrue(store.get(NAMESPACE + "key-b").length > 0);
        JsonNode storedOutput = JsonMapper.builder().build()
                                          .readTree(store.getString(NAMESPACE + "key-a"));
        assertEquals("string", storedOutput.get("__typename").asString());
    }

    @Test
    @DisplayName("execute with a registered generic list type replays the result as its concrete "
            + "element type, not raw maps (type erasure would otherwise lose the element type on replay)")
    void execute_withRegisteredListType_repeatCallReplaysConcreteElementType() {
        String key = "key-generic-list";
        String typename = "sample-dto-list";

        List<SampleDto> first = handler.execute(key, typename,
                () -> new ArrayList<>(List.of(new SampleDto("a", 1)))).value();
        List<SampleDto> second = handler.execute(key, typename,
                () -> new ArrayList<>(List.of(new SampleDto("should-not-run", -1)))).value();

        assertEquals(first, second);
        assertInstanceOf(SampleDto.class, second.get(0));
    }

    record SampleDto(String name, int value) implements Serializable {
    }

    @Test
    @DisplayName("a RuntimeException from the operation is cached and replayed as an IdempotencyException")
    void execute_runtimeExceptionFromOperation_isCachedAndReplayed() {
        String key = "key-3";
        IllegalStateException original = new IllegalStateException("boom");

        RuntimeException firstThrown = assertThrows(RuntimeException.class, () -> handler.execute(key, () -> {
            throw original;
        }));
        Outcome replayed = handler.execute(key, () -> "should not run");

        assertSame(original, firstThrown);
        assertEquals(original.getMessage(), replayed.exception().message());
        assertTrue(replayed.isFailure());
    }

    @Test
    @DisplayName("isCached returns false when no result has been stored for the key")
    void isCached_noResultStored_returnsFalse() {
        assertFalse(handler.isCached("missing-key"));
    }

    @Test
    @DisplayName("evict removes a cached result and reports true")
    void evict_cachedKey_removesResultAndReturnsTrue() {
        String key = "key-5";
        handler.execute(key, () -> "value");

        boolean evicted = handler.evict(key);

        assertTrue(evicted);
        assertFalse(handler.isCached(key));
    }

    @Test
    @DisplayName("evict on a key with no cached result reports false")
    void evict_uncachedKey_returnsFalse() {
        assertFalse(handler.evict("never-cached"));
    }

    @Test
    @DisplayName("isCached returns false for a corrupted cache entry instead of throwing")
    void isCached_corruptedCacheEntry_returnsFalse() {
        String cacheKey = NAMESPACE + "key-corrupted-is-cached";
        store.put(cacheKey, "not-a-serialized-object".getBytes());

        assertFalse(handler.isCached("key-corrupted-is-cached"));
    }

    @Test
    @DisplayName("evict on a corrupted cache entry reports false instead of throwing")
    void evict_corruptedCacheEntry_returnsFalse() {
        String cacheKey = NAMESPACE + "key-corrupted-evict";
        store.put(cacheKey, "not-a-serialized-object".getBytes());

        assertFalse(handler.evict("key-corrupted-evict"));
    }

    @Test
    @DisplayName("a cached result expires after its TTL and the operation runs again")
    void execute_cachedResultExpiresAfterTtl_runsOperationAgain() throws InterruptedException {
        long shortTtlSeconds = 1L;
        KeyValueStoreIdempotencyHandler shortTtlHandler = new KeyValueStoreIdempotencyHandler(keyValueService, "default",
                NAMESPACE, shortTtlSeconds);
        String key = "key-ttl";
        AtomicInteger calls = new AtomicInteger();

        String first = shortTtlHandler.execute(key, () -> {
            calls.incrementAndGet();
            return "value-" + calls.get();
        }).value();
        assertTrue(shortTtlHandler.isCached(key));

        // sleep a bit more than the TTL in case expiration is late, mirroring AbstractKeyValueStoreTest#testTTL
        Thread.sleep((shortTtlSeconds + 1) * 1000);

        assertFalse(shortTtlHandler.isCached(key));
        String second = shortTtlHandler.execute(key, () -> {
            calls.incrementAndGet();
            return "value-" + calls.get();
        }).value();

        assertEquals("value-1", first);
        assertEquals("value-2", second);
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("a corrupted cache entry is ignored and the operation runs again")
    void execute_corruptedCacheEntry_fallsBackToRunningOperation() {
        String cacheKey = NAMESPACE + "key-6";
        store.put(cacheKey, "not-a-serialized-object".getBytes());

        String result = handler.execute("key-6", () -> "fresh-value").value();

        assertEquals("fresh-value", result);
    }

    @Test
    @DisplayName("a result that fails to serialize is not stored, but is still returned")
    void execute_unserializableResult_isReturnedButNotCached() {
        String key = "key-7";

        UnserializableValue result = handler.execute(key, UnserializableValue::new).value();

        assertNotNull(result);
        assertFalse(handler.isCached(key));
    }

    @Test
    @DisplayName("concurrent executes with the same key run the operation exactly once (HXCS atomic claim)")
    void execute_concurrentCallsSameKey_runOperationExactlyOnce() {
        String key = "key-concurrent";
        AtomicInteger calls = new AtomicInteger();
        int callers = 8;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return handler.execute(key, () -> {
                        calls.incrementAndGet();
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        return "value-" + calls.get();
                    }).value();
                }));
            }
            ready.await();
            start.countDown();

            String first = futures.get(0).get(10, TimeUnit.SECONDS);
            for (Future<String> future : futures) {
                assertEquals(first, future.get(10, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            throw new AssertionError("Concurrent execution failed", e);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("a caller waits out a concurrent claim's expired lease and then runs the operation")
    void execute_claimLeaseExpires_waitingCallerRunsOperationAfterward() {
        long shortTtlSeconds = 1L;
        IdempotencyHandler shortLeaseHandler = new KeyValueStoreIdempotencyHandler(keyValueService, "default",
                NAMESPACE, shortTtlSeconds);
        String key = "key-lease-expiry";
        String cacheKey = NAMESPACE + key;
        // Simulate another caller having claimed the key with a short-lived lease.
        store.put(cacheKey, claimMarker(), shortTtlSeconds);

        String result = shortLeaseHandler.execute(key, () -> "value-after-lease-expiry").value();

        assertEquals("value-after-lease-expiry", result);
    }

    @Test
    @DisplayName("a caller throws IdempotencyClaimTimeoutException if a concurrent claim never resolves")
    void execute_claimNeverResolves_throwsClaimTimeoutException() {
        long shortTtlSeconds = 1L;
        IdempotencyHandler shortLeaseHandler = new KeyValueStoreIdempotencyHandler(keyValueService, "default",
                NAMESPACE, shortTtlSeconds);
        String key = "key-lease-stuck";
        String cacheKey = NAMESPACE + key;
        // Simulate another caller holding a claim with a lease far longer than this handler's own lease
        // (which is capped at shortTtlSeconds), so the waiting caller's deadline is reached first.
        store.put(cacheKey, claimMarker(), 3600L);

        assertThrows(IdempotencyClaimTimeoutException.class,
                () -> shortLeaseHandler.execute(key, () -> "should not run"));
    }

    @Test
    @DisplayName("isCached returns false while a claim is in progress for the key")
    void isCached_claimInProgress_returnsFalse() {
        String cacheKey = NAMESPACE + "key-in-progress-is-cached";
        store.put(cacheKey, claimMarker(), TTL_SECONDS);

        assertFalse(handler.isCached("key-in-progress-is-cached"));
    }

    @Test
    @DisplayName("evict returns false and leaves the claim marker untouched while a claim is in progress")
    void evict_claimInProgress_returnsFalseAndDoesNotClearClaim() {
        String cacheKey = NAMESPACE + "key-in-progress-evict";
        byte[] marker = claimMarker();
        store.put(cacheKey, marker, TTL_SECONDS);

        boolean evicted = handler.evict("key-in-progress-evict");

        assertFalse(evicted);
        assertArrayEquals(marker, store.get(cacheKey));
    }

    @Test
    @DisplayName("a persistence failure while finalizing a success is logged but the result is still returned")
    void execute_finalizePersistenceFails_resultStillReturned() {
        String key = "key-finalize-throws";
        String cacheKey = NAMESPACE + key;
        KeyValueStore mockStore = mock(KeyValueStore.class);
        when(mockStore.getString(cacheKey)).thenReturn(null);
        when(mockStore.compareAndSet(eq(cacheKey), isNull(), anyString(), anyLong())).thenReturn(true);
        when(mockStore.compareAndSet(eq(cacheKey), notNull(), anyString(), anyLong()))
                .thenThrow(new RuntimeException("store unavailable"));
        KeyValueService mockKeyValueService = name -> mockStore;
        IdempotencyHandler mockHandler = new KeyValueStoreIdempotencyHandler(mockKeyValueService, "default", NAMESPACE,
                TTL_SECONDS);

        String result = mockHandler.execute(key, () -> "value").value();

        assertEquals("value", result);
    }

    @Test
    @DisplayName("a claim reclaimed by another caller before finalize still returns the fresh result")
    void execute_finalizeReclaimedByAnotherCaller_resultStillReturned() {
        String key = "key-finalize-reclaimed";
        String cacheKey = NAMESPACE + key;
        KeyValueStore mockStore = mock(KeyValueStore.class);
        when(mockStore.getString(cacheKey)).thenReturn(null);
        when(mockStore.compareAndSet(eq(cacheKey), isNull(), anyString(), anyLong())).thenReturn(true);
        when(mockStore.compareAndSet(eq(cacheKey), notNull(), anyString(), anyLong())).thenReturn(false);
        KeyValueService mockKeyValueService = name -> mockStore;
        IdempotencyHandler mockHandler = new KeyValueStoreIdempotencyHandler(mockKeyValueService, "default", NAMESPACE,
                TTL_SECONDS);

        String result = mockHandler.execute(key, () -> "fresh-value").value();

        assertEquals("fresh-value", result);
    }

    @Test
    @DisplayName("a caller interrupted while waiting for a concurrent claim throws IdempotencyClaimTimeoutException"
            + " wrapping InterruptedException")
    void execute_interruptedWhileWaitingForClaim_throwsClaimTimeoutException() throws InterruptedException {
        String key = "key-interrupted";
        String cacheKey = NAMESPACE + key;
        store.put(cacheKey, claimMarker(), TTL_SECONDS);
        AtomicReference<Throwable> caught = new AtomicReference<>();

        Thread waiter = new Thread(() -> {
            try {
                handler.execute(key, () -> "should not run");
            } catch (Throwable t) {
                caught.set(t);
            }
        });
        waiter.interrupt();
        waiter.start();
        waiter.join(TimeUnit.SECONDS.toMillis(10));

        assertFalse(waiter.isAlive());
        assertInstanceOf(IdempotencyClaimTimeoutException.class, caught.get());
        assertInstanceOf(InterruptedException.class, caught.get().getCause());
    }

    @Test
    @DisplayName("successive claims on the same key use distinct owner markers")
    void claimMarkers_successiveClaims_areDistinct() {
        String key = "key-distinct-markers";
        String cacheKey = NAMESPACE + key;
        List<String> markers = new ArrayList<>();
        KeyValueStore recordingStore = mock(KeyValueStore.class);
        when(recordingStore.getString(cacheKey)).thenReturn(null);
        when(recordingStore.compareAndSet(eq(cacheKey), isNull(), anyString(), anyLong())).thenAnswer(invocation -> {
            markers.add(invocation.getArgument(2));
            return true;
        });
        when(recordingStore.compareAndSet(eq(cacheKey), notNull(), anyString(), anyLong())).thenReturn(true);
        KeyValueService recordingKeyValueService = name -> recordingStore;
        IdempotencyHandler recordingHandler = new KeyValueStoreIdempotencyHandler(recordingKeyValueService, "default",
                NAMESPACE, TTL_SECONDS);

        recordingHandler.execute(key, () -> "first");
        recordingHandler.execute(key, () -> "second");

        assertEquals(2, markers.size());
        assertNotEquals(markers.get(0), markers.get(1));
    }

    @Test
    @DisplayName("a stale claimant does not overwrite the entry published by a newer claim")
    void finalizeResult_staleClaimant_doesNotOverwriteNewerClaim() {
        String key = "key-stale-finalize";
        String cacheKey = NAMESPACE + key;
        byte[] newerClaim = claimMarker();
        // Delegates to the real store, but simulates another caller timing this claim out and
        // reclaiming the slot with its own marker while the guarded operation is running.
        KeyValueStore hijackingStore = mock(KeyValueStore.class);
        when(hijackingStore.getString(cacheKey)).thenAnswer(invocation -> store.getString(cacheKey));
        when(hijackingStore.compareAndSet(eq(cacheKey), isNull(), anyString(), anyLong())).thenAnswer(invocation -> {
            boolean claimed = store.compareAndSet(cacheKey, (String) null, invocation.getArgument(2),
                    invocation.getArgument(3));
            store.put(cacheKey, newerClaim, TTL_SECONDS);
            return claimed;
        });
        when(hijackingStore.compareAndSet(eq(cacheKey), notNull(), anyString(), anyLong()))
                .thenAnswer(invocation -> store.compareAndSet(cacheKey, invocation.<String>getArgument(1),
                        invocation.<String>getArgument(2), invocation.<Long>getArgument(3)));
        IdempotencyHandler staleHandler = handlerFor(hijackingStore);

        String staleResult = staleHandler.execute(key, () -> "stale-value").value();

        assertEquals("stale-value", staleResult);
        assertArrayEquals(newerClaim, store.get(cacheKey));
    }

    private KeyValueStoreIdempotencyHandler handlerFor(KeyValueStore kvStore) {
        KeyValueService kvService = name -> kvStore;
        return new KeyValueStoreIdempotencyHandler(kvService, "default", NAMESPACE, TTL_SECONDS);
    }

    /** Builds a valid, unique in-progress claim marker as written by the handler. */
    private static byte[] claimMarker() {
        return JsonMapper.builder()
                         .build()
                         .writeValueAsString(IdempotencyEntry.pending(UUID.randomUUID().toString()))
                         .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Serializable by contract, but fails during JSON serialization, to exercise the
     * {@code storeResult} failure path.
     */
    private static final class UnserializableValue implements Serializable {

        private static final long serialVersionUID = 1L;

        public String getValue() {
            throw new IllegalStateException("cannot serialize to JSON");
        }

    }

    @Test
    @DisplayName("sanitizeForLog neutralizes CR, LF, and tab so a malicious key cannot forge or split log lines")
    void sanitizeForLog_neutralizesCrLfAndTab() {
        assertEquals("key__injected-fake-log-line_", KeyValueStoreIdempotencyHandler.sanitizeForLog(
                "key\r\ninjected-fake-log-line\t"));
        assertEquals("plain-key", KeyValueStoreIdempotencyHandler.sanitizeForLog("plain-key"));
        assertNull(KeyValueStoreIdempotencyHandler.sanitizeForLog(null));
    }

    @Test
    @DisplayName("execute with a key containing CR/LF still works and never propagates the raw control characters "
            + "into the cache key used for logging")
    void execute_withCrLfInKey_isHandledSafely() {
        String maliciousKey = "attacker-key\r\nfake-log-entry=true";

        Object result = handler.execute(maliciousKey, () -> "value").value();

        assertEquals("value", result);
        assertTrue(handler.isCached(maliciousKey));
        String sanitizedCacheKey = KeyValueStoreIdempotencyHandler.sanitizeForLog(NAMESPACE + maliciousKey);
        assertEquals("attacker-key__fake-log-entry=true", sanitizedCacheKey.substring(NAMESPACE.length()));
    }

    @Test
    @DisplayName("a replayed RepositoryException failure preserves the original HTTP status code")
    void execute_replaysRepositoryExceptionFailure_preservesHttpErrorCode() {
        String key = "key-repository-exception";
        RepositoryException original = new RepositoryException("conflict", 409);

        RuntimeException firstThrown = assertThrows(RuntimeException.class, () -> handler.execute(key, () -> {
            throw original;
        }));
        Outcome replayed = handler.execute(key, () -> "should not run");

        assertSame(original, firstThrown);
        assertTrue(replayed.isFailure());
        assertEquals(original.getMessage(), replayed.exception().message());
        assertEquals(409, replayed.exception().statusCode());
    }

    @Test
    @DisplayName("a cache hit whose recorded type is not registered throws IdempotencyPersistenceException")
    void execute_cacheHitWithUnresolvableTypename_throwsIdempotencyPersistenceException() {
        String key = "key-unresolvable-typename";
        String cacheKey = NAMESPACE + key;
        String badTypeJson = "{\"pending\":false,\"__typename\":\"unregistered-type\","
                + "\"value\":{\"foo\":\"bar\"}}";
        store.put(cacheKey, badTypeJson.getBytes(StandardCharsets.UTF_8), TTL_SECONDS);

        assertThrows(KeyValueStoreIdempotencyHandler.IdempotencyPersistenceException.class,
                () -> handler.execute(key, () -> "should not run"));
    }

    @Test
    @DisplayName("a failure to release the claim after an unserializable result is logged but the result is still "
            + "returned")
    void execute_releaseClaimThrows_resultStillReturned() {
        String key = "key-release-claim-throws";
        String cacheKey = NAMESPACE + key;
        KeyValueStore mockStore = mock(KeyValueStore.class);
        when(mockStore.getString(cacheKey)).thenReturn(null);
        when(mockStore.compareAndSet(eq(cacheKey), isNull(), anyString(), anyLong())).thenReturn(true);
        when(mockStore.compareAndSet(eq(cacheKey), anyString(), isNull()))
                .thenThrow(new RuntimeException("release failed"));
        KeyValueStoreIdempotencyHandler mockHandler = handlerFor(mockStore);

        UnserializableValue result = mockHandler.execute(key, UnserializableValue::new).value();

        assertNotNull(result);
        assertFalse(mockHandler.isCached(key));
    }

    @Test
    @DisplayName("a syntactically valid but structurally unrecognized cache entry is ignored and the operation "
            + "runs again")
    void execute_unrecognizedJsonObjectShape_fallsBackToRunningOperation() {
        String key = "key-unrecognized-shape";
        String cacheKey = NAMESPACE + key;
        store.put(cacheKey, "{}".getBytes(StandardCharsets.UTF_8));

        String result = handler.execute(key, () -> "fresh-value").value();

        assertEquals("fresh-value", result);
    }

    @Test
    @DisplayName("a pending cache entry without a claimId is decoded without throwing")
    void execute_pendingEntryWithoutClaimId_isHandledSafely() throws InterruptedException {
        String key = "key-pending-no-claimid";
        String cacheKey = NAMESPACE + key;
        store.put(cacheKey, "{\"pending\":true}".getBytes(StandardCharsets.UTF_8), TTL_SECONDS);
        AtomicReference<Throwable> caught = new AtomicReference<>();

        Thread waiter = new Thread(() -> {
            try {
                handler.execute(key, () -> "should not run");
            } catch (Throwable t) {
                caught.set(t);
            }
        });
        waiter.interrupt();
        waiter.start();
        waiter.join(TimeUnit.SECONDS.toMillis(10));

        assertFalse(waiter.isAlive());
        assertInstanceOf(IdempotencyClaimTimeoutException.class, caught.get());
        assertInstanceOf(InterruptedException.class, caught.get().getCause());
    }

    @Test
    @DisplayName("constructor builds a working handler with the default store, namespace and TTL")
    void constructor_defaults_buildsWorkingHandler() {
        IdempotencyHandler defaultHandler = new KeyValueStoreIdempotencyHandler(keyValueService,
                IdempotencyHandler.DEFAULT_STORE_NAME, IdempotencyHandler.DEFAULT_IDEMPOTENCY_NAMESPACE,
                IdempotencyHandler.DEFAULT_TTL_SECONDS);

        assertNotNull(defaultHandler);
        assertEquals("value", defaultHandler.execute("defaults-key", () -> "value").value());
    }

    @Test
    @DisplayName("constructor rejects a null KeyValueService")
    void constructor_nullKeyValueService_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new KeyValueStoreIdempotencyHandler(null, "default", NAMESPACE, 60L));
    }

    @Test
    @DisplayName("constructor rejects a null store name")
    void constructor_nullStoreName_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new KeyValueStoreIdempotencyHandler(keyValueService, null, NAMESPACE, 60L));
    }

    @Test
    @DisplayName("constructor rejects a blank store name")
    void constructor_blankStoreName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new KeyValueStoreIdempotencyHandler(keyValueService, "  ", NAMESPACE, 60L));
    }

    @Test
    @DisplayName("constructor rejects a null namespace")
    void constructor_nullNamespace_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new KeyValueStoreIdempotencyHandler(keyValueService, "default", null, 60L));
    }

    @Test
    @DisplayName("constructor rejects a blank namespace")
    void constructor_blankNamespace_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new KeyValueStoreIdempotencyHandler(keyValueService, "default", "  ", 60L));
    }

    @Test
    @DisplayName("constructor rejects a zero TTL")
    void constructor_zeroTtl_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new KeyValueStoreIdempotencyHandler(keyValueService, "default", NAMESPACE, 0L));
    }

    @Test
    @DisplayName("constructor rejects a negative TTL")
    void constructor_negativeTtl_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new KeyValueStoreIdempotencyHandler(keyValueService, "default", NAMESPACE, -1L));
    }

    @Test
    @DisplayName("default constants expose the documented defaults")
    void defaultConstants_matchDocumentedValues() {
        assertEquals(TimeUnit.HOURS.toSeconds(24), IdempotencyHandler.DEFAULT_TTL_SECONDS);
        assertEquals("idempotency", IdempotencyHandler.DEFAULT_STORE_NAME);
        assertEquals("idempotency:", IdempotencyHandler.DEFAULT_IDEMPOTENCY_NAMESPACE);
    }

}
