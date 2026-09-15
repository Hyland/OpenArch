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
package com.hyland.hxpr.runtime.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.runtime.kv.KeyValueService;
import com.hyland.openarch.runtime.kv.MemKeyValueStore;

/**
 * Unit tests for {@link KeyValueStoreIdempotencyHandler}.
 */
class KeyValueStoreIdempotencyHandlerTest {

    private static final String NAMESPACE = "idempotency:test:";

    private static final long TTL_SECONDS = 3600L;

    private MemKeyValueStore store;

    private IdempotencyHandler handler;

    @BeforeEach
    void setUp() {
        store = new MemKeyValueStore();
        KeyValueService keyValueService = name -> store;
        handler = new KeyValueStoreIdempotencyHandler(keyValueService, "default", NAMESPACE, TTL_SECONDS);
    }

    @Test
    @DisplayName("first execute runs the operation and caches the result")
    void execute_firstCall_runsOperationAndCaches() {
        IdempotencyKey key = IdempotencyKey.of("key-1");
        AtomicInteger calls = new AtomicInteger();

        String result = handler.execute(key, () -> {
            calls.incrementAndGet();
            return "value";
        });

        assertEquals("value", result);
        assertEquals(1, calls.get());
        assertTrue(handler.isCached(key));
    }

    @Test
    @DisplayName("repeat execute with the same key replays the cached value without re-running the operation")
    void execute_repeatCall_replaysCachedValue() {
        IdempotencyKey key = IdempotencyKey.of("key-2");
        AtomicInteger calls = new AtomicInteger();

        String first = handler.execute(key, () -> {
            calls.incrementAndGet();
            return "value-" + calls.get();
        });
        String second = handler.execute(key, () -> {
            calls.incrementAndGet();
            return "value-" + calls.get();
        });

        assertEquals(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("different keys are cached independently under the configured namespace")
    void execute_differentKeys_doNotCollide() {
        String first = handler.execute(IdempotencyKey.of("key-a"), () -> "a");
        String second = handler.execute(IdempotencyKey.of("key-b"), () -> "b");

        assertEquals("a", first);
        assertEquals("b", second);
        assertTrue(store.get(NAMESPACE + "key-a").length > 0);
        assertTrue(store.get(NAMESPACE + "key-b").length > 0);
    }

    @Test
    @DisplayName("a RuntimeException from the operation is cached and rethrown as-is on replay")
    void execute_runtimeExceptionFromOperation_isCachedAndReplayed() {
        IdempotencyKey key = IdempotencyKey.of("key-3");
        IllegalStateException original = new IllegalStateException("boom");

        RuntimeException firstThrown = assertThrows(RuntimeException.class, () -> handler.execute(key, () -> {
            throw original;
        }));
        RuntimeException replayedThrown = assertThrows(RuntimeException.class,
                () -> handler.execute(key, () -> "should not run"));

        assertSame(original, firstThrown);
        assertEquals(original.getClass(), replayedThrown.getClass());
        assertEquals(original.getMessage(), replayedThrown.getMessage());
    }

    @Test
    @DisplayName("a checked exception from the operation is wrapped, cached, and rethrown wrapped on replay")
    void execute_checkedExceptionFromOperation_isWrappedCachedAndReplayed() {
        IdempotencyKey key = IdempotencyKey.of("key-4");
        IOException checked = new IOException("checked failure");

        RuntimeException firstThrown = assertThrows(RuntimeException.class, () -> handler.execute(key, () -> {
            throw checked;
        }));
        RuntimeException replayedThrown = assertThrows(RuntimeException.class,
                () -> handler.execute(key, () -> "should not run"));

        assertTrue(firstThrown.getCause() instanceof IOException);
        assertEquals(firstThrown.getMessage(), replayedThrown.getMessage());
    }

    @Test
    @DisplayName("isCached returns false when no result has been stored for the key")
    void isCached_noResultStored_returnsFalse() {
        assertFalse(handler.isCached(IdempotencyKey.of("missing-key")));
    }

    @Test
    @DisplayName("evict removes a cached result and reports true")
    void evict_cachedKey_removesResultAndReturnsTrue() {
        IdempotencyKey key = IdempotencyKey.of("key-5");
        handler.execute(key, () -> "value");

        boolean evicted = handler.evict(key);

        assertTrue(evicted);
        assertFalse(handler.isCached(key));
    }

    @Test
    @DisplayName("evict on a key with no cached result reports false")
    void evict_uncachedKey_returnsFalse() {
        assertFalse(handler.evict(IdempotencyKey.of("never-cached")));
    }

    @Test
    @DisplayName("isCached returns false for a corrupted cache entry instead of throwing")
    void isCached_corruptedCacheEntry_returnsFalse() {
        String cacheKey = NAMESPACE + "key-corrupted-is-cached";
        store.put(cacheKey, "not-a-serialized-object".getBytes());

        assertFalse(handler.isCached(IdempotencyKey.of("key-corrupted-is-cached")));
    }

    @Test
    @DisplayName("evict on a corrupted cache entry reports false instead of throwing")
    void evict_corruptedCacheEntry_returnsFalse() {
        String cacheKey = NAMESPACE + "key-corrupted-evict";
        store.put(cacheKey, "not-a-serialized-object".getBytes());

        assertFalse(handler.evict(IdempotencyKey.of("key-corrupted-evict")));
    }

    @Test
    @DisplayName("a cached result expires after its TTL and the operation runs again")
    void execute_cachedResultExpiresAfterTtl_runsOperationAgain() throws InterruptedException {
        long shortTtlSeconds = 1L;
        KeyValueService keyValueService = name -> store;
        IdempotencyHandler shortTtlHandler = new KeyValueStoreIdempotencyHandler(keyValueService, "default",
                NAMESPACE, shortTtlSeconds);
        IdempotencyKey key = IdempotencyKey.of("key-ttl");
        AtomicInteger calls = new AtomicInteger();

        String first = shortTtlHandler.execute(key, () -> {
            calls.incrementAndGet();
            return "value-" + calls.get();
        });
        assertTrue(shortTtlHandler.isCached(key));

        // sleep a bit more than the TTL in case expiration is late, mirroring AbstractKeyValueStoreTest#testTTL
        Thread.sleep((shortTtlSeconds + 1) * 1000);

        assertFalse(shortTtlHandler.isCached(key));
        String second = shortTtlHandler.execute(key, () -> {
            calls.incrementAndGet();
            return "value-" + calls.get();
        });

        assertEquals("value-1", first);
        assertEquals("value-2", second);
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("a corrupted cache entry is ignored and the operation runs again")
    void execute_corruptedCacheEntry_fallsBackToRunningOperation() {
        String cacheKey = NAMESPACE + "key-6";
        store.put(cacheKey, "not-a-serialized-object".getBytes());

        String result = handler.execute(IdempotencyKey.of("key-6"), () -> "fresh-value");

        assertEquals("fresh-value", result);
    }

    @Test
    @DisplayName("a result that fails to serialize is not stored, but is still returned")
    void execute_unserializableResult_isReturnedButNotCached() {
        IdempotencyKey key = IdempotencyKey.of("key-7");

        UnserializableValue result = handler.execute(key, UnserializableValue::new);

        assertNotNull(result);
        assertFalse(handler.isCached(key));
    }

    @Test
    @DisplayName("concurrent executes with the same key run the operation exactly once (HXCS atomic claim)")
    void execute_concurrentCallsSameKey_runOperationExactlyOnce() throws InterruptedException {
        IdempotencyKey key = IdempotencyKey.of("key-concurrent");
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
                        Thread.sleep(150);
                        return "value-" + calls.get();
                    });
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
    void execute_claimLeaseExpires_waitingCallerRunsOperationAfterward() throws InterruptedException {
        long shortTtlSeconds = 1L;
        KeyValueService keyValueService = name -> store;
        IdempotencyHandler shortLeaseHandler = new KeyValueStoreIdempotencyHandler(keyValueService, "default",
                NAMESPACE, shortTtlSeconds);
        IdempotencyKey key = IdempotencyKey.of("key-lease-expiry");
        String cacheKey = NAMESPACE + key.value();
        // Simulate another caller having claimed the key with a short-lived lease.
        store.put(cacheKey, "HXPR-IDEMPOTENCY-CLAIM-V1:".getBytes(StandardCharsets.UTF_8), shortTtlSeconds);

        String result = shortLeaseHandler.execute(key, () -> "value-after-lease-expiry");

        assertEquals("value-after-lease-expiry", result);
    }

    @Test
    @DisplayName("a caller throws IdempotencyClaimTimeoutException if a concurrent claim never resolves")
    void execute_claimNeverResolves_throwsClaimTimeoutException() {
        long shortTtlSeconds = 1L;
        KeyValueService keyValueService = name -> store;
        IdempotencyHandler shortLeaseHandler = new KeyValueStoreIdempotencyHandler(keyValueService, "default",
                NAMESPACE, shortTtlSeconds);
        IdempotencyKey key = IdempotencyKey.of("key-lease-stuck");
        String cacheKey = NAMESPACE + key.value();
        // Simulate another caller holding a claim with a lease far longer than this handler's own lease
        // (which is capped at shortTtlSeconds), so the waiting caller's deadline is reached first.
        store.put(cacheKey, "HXPR-IDEMPOTENCY-CLAIM-V1:".getBytes(StandardCharsets.UTF_8), 3600L);

        assertThrows(IdempotencyClaimTimeoutException.class,
                () -> shortLeaseHandler.execute(key, () -> "should not run"));
    }

    /**
     * Serializable by contract, but fails during {@code writeObject}, to exercise the
     * {@code storeResult} failure path.
     */
    private static final class UnserializableValue implements Serializable {

        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new IOException("cannot serialize");
        }

    }

}
