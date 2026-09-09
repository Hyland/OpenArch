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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.runtime.kv.KeyValueService;
import com.hyland.openarch.runtime.kv.MemKeyValueStore;

/**
 * Unit tests for {@link IdempotencyHandlerFactory}.
 */
class IdempotencyHandlerFactoryTest {

    private final MemKeyValueStore store = new MemKeyValueStore();

    private final KeyValueService keyValueService = name -> store;

    @Test
    @DisplayName("create with namespace only uses the default store and default TTL")
    void create_namespaceOnly_usesDefaults() {
        IdempotencyHandler handler = IdempotencyHandlerFactory.create(keyValueService, "idempotency:test:");

        assertNotNull(handler);
        assertEquals("value", handler.execute(IdempotencyKey.of("key-1"), () -> "value"));
    }

    @Test
    @DisplayName("create with a TTL in seconds uses the default store")
    void create_withTtlSeconds_usesDefaultStore() {
        IdempotencyHandler handler = IdempotencyHandlerFactory.create(keyValueService, "idempotency:test:", 60L);

        assertEquals("value", handler.execute(IdempotencyKey.of("key-2"), () -> "value"));
    }

    @Test
    @DisplayName("create with a TTL and TimeUnit converts to seconds and uses the default store")
    void create_withTtlAndTimeUnit_convertsToSeconds() {
        IdempotencyHandler handler = IdempotencyHandlerFactory.create(keyValueService, "idempotency:test:", 1,
                TimeUnit.HOURS);

        assertEquals("value", handler.execute(IdempotencyKey.of("key-3"), () -> "value"));
    }

    @Test
    @DisplayName("create with store name, namespace, TTL and TimeUnit converts to seconds")
    void create_withStoreNamespaceTtlAndTimeUnit_convertsToSeconds() {
        IdempotencyHandler handler = IdempotencyHandlerFactory.create(keyValueService, "custom-store",
                "idempotency:test:", 30, TimeUnit.MINUTES);

        assertEquals("value", handler.execute(IdempotencyKey.of("key-4"), () -> "value"));
    }

    @Test
    @DisplayName("create with full arguments builds a working handler")
    void create_fullArguments_buildsWorkingHandler() {
        IdempotencyHandler handler = IdempotencyHandlerFactory.create(keyValueService, "custom-store",
                "idempotency:test:", 120L);

        assertEquals("value", handler.execute(IdempotencyKey.of("key-5"), () -> "value"));
    }

    @Test
    @DisplayName("create rejects a null KeyValueService")
    void create_nullKeyValueService_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> IdempotencyHandlerFactory.create(null, "default", "idempotency:test:", 60L));
    }

    @Test
    @DisplayName("create rejects a null store name")
    void create_nullStoreName_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> IdempotencyHandlerFactory.create(keyValueService, null, "idempotency:test:", 60L));
    }

    @Test
    @DisplayName("create rejects a blank store name")
    void create_blankStoreName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> IdempotencyHandlerFactory.create(keyValueService, "  ", "idempotency:test:", 60L));
    }

    @Test
    @DisplayName("create rejects a null namespace")
    void create_nullNamespace_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> IdempotencyHandlerFactory.create(keyValueService, "default", null, 60L));
    }

    @Test
    @DisplayName("create rejects a blank namespace")
    void create_blankNamespace_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> IdempotencyHandlerFactory.create(keyValueService, "default", "  ", 60L));
    }

    @Test
    @DisplayName("create rejects a zero TTL")
    void create_zeroTtl_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> IdempotencyHandlerFactory.create(keyValueService, "default", "idempotency:test:", 0L));
    }

    @Test
    @DisplayName("create rejects a negative TTL")
    void create_negativeTtl_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> IdempotencyHandlerFactory.create(keyValueService, "default", "idempotency:test:", -1L));
    }

    @Test
    @DisplayName("default constants expose the documented defaults")
    void defaultConstants_matchDocumentedValues() {
        assertEquals(TimeUnit.HOURS.toSeconds(24), IdempotencyHandlerFactory.DEFAULT_TTL_SECONDS);
        assertEquals("default", IdempotencyHandlerFactory.DEFAULT_STORE_NAME);
    }

}
