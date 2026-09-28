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

import static com.hyland.openarch.runtime.idempotency.IdempotencyHandlerFactory.DEFAULT_IDEMPOTENCY_NAMESPACE;
import static com.hyland.openarch.runtime.idempotency.IdempotencyHandlerFactory.DEFAULT_STORE_NAME;
import static com.hyland.openarch.runtime.idempotency.IdempotencyHandlerFactory.DEFAULT_TTL_SECONDS;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.kv.KeyValueService;

/**
 * Thread-safe lazy supplier of the default {@link IdempotencyHandler}.
 * <p>
 * Lazy on purpose: every known caller creates one instance of this supplier as a field of a
 * component that can itself be instantiated before the Nuxeo runtime that owns
 * {@link KeyValueService} is available (e.g. a Spring {@code @Component} created while the Spring
 * context starts up, or an OSGi {@code DefaultComponent} created before its {@code start()} runs).
 * The handler is therefore resolved on first use rather than at construction, and cached from then
 * on in an {@link AtomicReference}. The factory call is cheap and side-effect-free, so under rare
 * contention it may run more than once, but only one resulting instance is ever published and
 * returned to all callers.
 * <p>
 * Each caller owns its own instance of this class; the underlying {@link IdempotencyHandler} it
 * resolves to is effectively shared across all callers because it is backed by the same default
 * key-value store, namespace, and TTL (see {@link IdempotencyHandlerFactory}).
 */
public final class DefaultIdempotencyHandlerSupplier implements Supplier<IdempotencyHandler> {

    private final AtomicReference<IdempotencyHandler> handlerRef = new AtomicReference<>();

    @Override
    public IdempotencyHandler get() {
        return handlerRef.updateAndGet(current -> current != null ? current
                : IdempotencyHandlerFactory.create(Framework.getService(KeyValueService.class), DEFAULT_STORE_NAME,
                        DEFAULT_IDEMPOTENCY_NAMESPACE, DEFAULT_TTL_SECONDS));
    }

}
