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
