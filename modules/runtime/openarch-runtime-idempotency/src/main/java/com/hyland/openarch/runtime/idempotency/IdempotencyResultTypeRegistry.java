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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;

/**
 * Registry of idempotency result types that may be replayed from the key-value store.
 * <p>
 * The registry key is persisted as {@code __typename} in the cached result. Register every result
 * type returned by an idempotent operation before that operation is invoked. The persisted key is
 * intentionally decoupled from the Java class name so cache data never selects a class directly.
 */
public final class IdempotencyResultTypeRegistry {

    private static final ConcurrentMap<String, Class<?>> TYPES_BY_KEY = new ConcurrentHashMap<>();

    private static final ConcurrentMap<Class<?>, String> KEYS_BY_TYPE = new ConcurrentHashMap<>();

    private IdempotencyResultTypeRegistry() {
        // Utility class.
    }

    /**
     * Registers a result type under a stable cache key.
     *
     * @param key        the value to persist in the cache entry's {@code __typename} property
     * @param resultType the class used to deserialize the cached JSON tree
     */
    public static synchronized void register(String key, Class<?> resultType) {
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("Idempotency result type key cannot be blank");
        }
        Objects.requireNonNull(resultType, "Idempotency result type cannot be null");

        Class<?> registeredType = TYPES_BY_KEY.get(key);
        if (registeredType != null && registeredType != resultType) {
            throw new IllegalArgumentException(
                    "Idempotency result type key is already registered for " + registeredType.getName());
        }
        String registeredKey = KEYS_BY_TYPE.get(resultType);
        if (registeredKey != null && !registeredKey.equals(key)) {
            throw new IllegalArgumentException(
                    "Idempotency result type is already registered under key " + registeredKey);
        }
        TYPES_BY_KEY.put(key, resultType);
        KEYS_BY_TYPE.put(resultType, key);
    }

    static String getKey(Class<?> resultType) {
        return KEYS_BY_TYPE.get(resultType);
    }

    static Class<?> getType(String key) {
        return TYPES_BY_KEY.get(key);
    }
}
