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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.kv.KeyValueService;

/**
 * Implementation of {@link IdempotencyHandler} that stores results in a {@link KeyValueService}
 * for persistent idempotency across requests.
 * <p>
 * Results are serialized to bytes and stored with an expiration TTL to prevent unbounded cache
 * growth. The cache key is derived from the idempotency key and a namespace prefix.
 *
 * @since 12.0
 */
public final class KeyValueStoreIdempotencyHandler implements IdempotencyHandler {

    private static final Logger log = LogManager.getLogger(KeyValueStoreIdempotencyHandler.class);

    private final KeyValueStore keyValueStore;

    private final String namespace;

    private final long ttlSeconds;

    /**
     * Creates a new idempotency handler using the given key-value service.
     *
     * @param keyValueService the key-value service for obtaining stores
     * @param storeName       the name of the key-value store to use
     * @param namespace       prefix for cache keys (e.g., "idempotency:commands:")
     * @param ttlSeconds      time-to-live for cached results in seconds
     */
    public KeyValueStoreIdempotencyHandler(KeyValueService keyValueService, String storeName, String namespace,
            long ttlSeconds) {
        this.keyValueStore = keyValueService.getKeyValueStore(storeName);
        this.namespace = namespace;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public <T extends Serializable> T execute(IdempotencyKey key, IdempotentOperation<T> operation) {
        String cacheKey = getCacheKey(key);

        IdempotencyResult<T> cached = retrieveCached(cacheKey);
        if (cached != null) {
            log.debug("Idempotency cache hit for key={}", key.value());
            return cached.unwrap();
        }

        try {
            T result = operation.execute();
            IdempotencyResult<T> successResult = IdempotencyResult.success(result);
            storeResult(cacheKey, successResult);
            log.debug("Idempotency cache stored success for key={}", key.value());
            return result;
        } catch (RuntimeException e) {
            IdempotencyResult<T> failureResult = IdempotencyResult.failure(e);
            storeResult(cacheKey, failureResult);
            log.debug("Idempotency cache stored failure for key={}", key.value());
            throw e;
        } catch (Exception e) {
            RuntimeException wrapped = new RuntimeException("Operation failed", e);
            IdempotencyResult<T> failureResult = IdempotencyResult.failure(wrapped);
            storeResult(cacheKey, failureResult);
            log.debug("Idempotency cache stored wrapped failure for key={}", key.value());
            throw wrapped;
        }
    }

    @Override
    public boolean isCached(IdempotencyKey key) {
        String cacheKey = getCacheKey(key);
        return keyValueStore.get(cacheKey) != null;
    }

    @Override
    public boolean evict(IdempotencyKey key) {
        String cacheKey = getCacheKey(key);
        if (keyValueStore.get(cacheKey) != null) {
            keyValueStore.put(cacheKey, (byte[]) null);
            return true;
        }
        return false;
    }

    private String getCacheKey(IdempotencyKey key) {
        return namespace + key.value();
    }

    @SuppressWarnings("unchecked")
    private <T extends Serializable> IdempotencyResult<T> retrieveCached(String cacheKey) {
        try {
            byte[] cached = keyValueStore.get(cacheKey);
            if (cached == null || cached.length == 0) {
                return null;
            }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(cached))) {
                return (IdempotencyResult<T>) ois.readObject();
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve idempotency result from cache for key={}: {}", cacheKey, e.getMessage());
            return null;
        }
    }

    private <T extends Serializable> void storeResult(String cacheKey, IdempotencyResult<T> result) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(result);
            }
            keyValueStore.put(cacheKey, baos.toByteArray(), ttlSeconds);
        } catch (Exception e) {
            log.warn("Failed to store idempotency result in cache for key={}: {}", cacheKey, e.getMessage());
        }
    }

}
