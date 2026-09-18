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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.hyland.openarch.runtime.kv.KeyValueService;

/**
 * Factory for creating {@link IdempotencyHandler} instances with configurable parameters.
 * <p>
 * This is a plain Java factory (not a Spring bean) to maintain compatibility with the Nuxeo
 * OSGi runtime.
 */
public final class IdempotencyHandlerFactory {

    public static final long DEFAULT_TTL_SECONDS = TimeUnit.HOURS.toSeconds(24);

    public static final String DEFAULT_STORE_NAME = "hxpr-idempotency";

    public static final String DEFAULT_IDEMPOTENCY_NAMESPACE = "idempotency:";

    public static final String DEFAULT_IDEMPOTENCY_KEY_PREFIX = "hxpr:idem";

    public static final String DEFAULT_IDEMPOTENCY_DEFAULT_TENANT = "default";

    public static final String IDEMPOTENCY_TENANT_PROPERTY = "hxpr.idempotency.tenant-id";


    private IdempotencyHandlerFactory() {
        // utility class
    }

    /**
     * Creates an idempotency handler with custom store name and TTL.
     *
     * @param keyValueService the key-value service for storing results
     * @param storeName       the name of the key-value store to use
     * @param namespace       namespace prefix for cache keys
     * @param ttlSeconds      time-to-live for cached results in seconds
     * @return a new IdempotencyHandler
     */
    public static IdempotencyHandler create(KeyValueService keyValueService, String storeName, String namespace,
            long ttlSeconds) {
        Objects.requireNonNull(keyValueService, "KeyValueService cannot be null");
        Objects.requireNonNull(storeName, "Store name cannot be null");
        Objects.requireNonNull(namespace, "Namespace cannot be null");
        if (storeName.isBlank()) {
            throw new IllegalArgumentException("Store name cannot be empty");
        }
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("Namespace cannot be empty");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        return new KeyValueStoreIdempotencyHandler(keyValueService, storeName, namespace, ttlSeconds);
    }

}
