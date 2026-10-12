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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

/**
 * Registry of idempotency result types that may be replayed from the key-value store.
 * <p>
 * The registry key is persisted as {@code __typename} in the cached result. Register every result
 * type returned by an idempotent operation before that operation is invoked. The persisted key is
 * intentionally decoupled from the Java class name so cache data never selects a class directly.
 * <p>
 * Types are stored as Jackson {@link JavaType} instances, so generic result types are supported:
 * a {@code List<DocumentDTO>} keeps its element type on replay instead of being deserialized back
 * as raw maps. Register a list result type with {@link #registerList(String, Class)}:
 *
 * <pre>{@code
 * IdempotencyResultTypeRegistry.registerList("document-dto-list", DocumentDTO.class);
 * }</pre>
 */
public final class IdempotencyResultTypeRegistry {

    private static final TypeFactory TYPE_FACTORY = TypeFactory.createDefaultInstance();

    private static final ConcurrentMap<String, JavaType> TYPES_BY_KEY = new ConcurrentHashMap<>();

    private static final ConcurrentMap<JavaType, String> KEYS_BY_TYPE = new ConcurrentHashMap<>();

    private IdempotencyResultTypeRegistry() {
        // Utility class.
    }

    /**
     * Registers a non-generic result type under a stable cache key.
     *
     * @param key        the value to persist in the cache entry's {@code __typename} property
     * @param resultType the class used to deserialize the cached JSON tree
     */
    public static void register(String key, Class<?> resultType) {
        Objects.requireNonNull(resultType, "Idempotency result type cannot be null");
        registerJavaType(key, TYPE_FACTORY.constructType(resultType));
    }

    /**
     * Registers a {@code List<elementType>} result type under a stable cache key.
     * <p>
     * A replayed value is deserialized as a list of {@code elementType} instances.
     *
     * @param key         the value to persist in the cache entry's {@code __typename} property
     * @param elementType the class of the list elements
     */
    public static void registerList(String key, Class<?> elementType) {
        Objects.requireNonNull(elementType, "Idempotency result element type cannot be null");
        registerJavaType(key, TYPE_FACTORY.constructCollectionType(List.class, elementType));
    }

    /**
     * Binds {@code resultType} to {@code key}, rejecting any binding that would make an existing
     * key or an existing type ambiguous. Re-registering the very same pair is a no-op.
     */
    private static synchronized void registerJavaType(String key, JavaType resultType) {
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException();
        }

        JavaType registeredType = TYPES_BY_KEY.get(key);
        if (registeredType != null && !registeredType.equals(resultType)) {
            throw new IllegalArgumentException();
        }
        String registeredKey = KEYS_BY_TYPE.get(resultType);
        if (registeredKey != null && !registeredKey.equals(key)) {
            throw new IllegalArgumentException();
        }
        TYPES_BY_KEY.put(key, resultType);
        KEYS_BY_TYPE.put(resultType, key);
    }

    /**
     * Returns the cache key a non-generic result type was registered under.
     * <p>
     * The lookup is erasure-based: a generic type such as {@code List<DocumentDTO>} can only be
     * resolved through the key it was registered with, never from its runtime class.
     *
     * @param resultType the registered class
     * @return the registered key, or {@code null} if the class is not registered
     */
    static String getKey(Class<?> resultType) {
        return KEYS_BY_TYPE.get(TYPE_FACTORY.constructType(resultType));
    }

    /**
     * Returns the type registered under a cache key.
     *
     * @param key the {@code __typename} value read from a cache entry
     * @return the registered type, or {@code null} if the key is not registered
     */
    static JavaType getType(String key) {
        return TYPES_BY_KEY.get(key);
    }
}
