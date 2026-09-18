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

import com.hyland.openarch.runtime.RuntimeServiceException;

import java.io.Serializable;
import java.util.Optional;

/**
 * Encapsulates the result of an idempotent operation, including the operation result and metadata.
 * <p>
 * This record can store either a successful result or a failed exception, allowing idempotent
 * operations to return or re-throw the same outcome on duplicate requests. Exactly one of
 * {@code value} or {@code exception} is expected to be non-null; use {@link #success(Serializable)}
 * or {@link #failure(Exception)} to construct instances.
 *
 * @param <T>             the type of the operation result
 * @param rawValue        the operation result if successful, or {@code null} on failure
 * @param rawException    the exception if the operation failed, or {@code null} on success
 * @param createdAtMillis the timestamp when this result was created, in milliseconds since epoch
 */
public record IdempotencyResult<T extends Serializable>(T rawValue, Exception rawException, long createdAtMillis)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a result representing a successful operation outcome.
     *
     * @param value the operation result
     * @return a new IdempotencyResult with the given value
     */
    public static <T extends Serializable> IdempotencyResult<T> success(T value) {
        return new IdempotencyResult<>(value, null, System.currentTimeMillis());
    }

    /**
     * Creates a result representing a failed operation outcome.
     *
     * @param exception the exception that occurred
     * @return a new IdempotencyResult with the given exception
     */
    public static <T extends Serializable> IdempotencyResult<T> failure(Exception exception) {
        return new IdempotencyResult<>(null, exception, System.currentTimeMillis());
    }

    /**
     * Returns whether this result represents a successful outcome.
     */
    public boolean isSuccess() {
        return rawException == null;
    }

    /**
     * Gets the result value if the operation succeeded.
     */
    public Optional<T> value() {
        return Optional.ofNullable(rawValue);
    }

    /**
     * Gets the exception if the operation failed.
     */
    public Optional<Exception> exception() {
        return Optional.ofNullable(rawException);
    }

    /**
     * Unwraps the result, returning the value or rethrowing the exception.
     * <p>
     * If the stored exception is already a {@link RuntimeException} (e.g. a domain exception such
     * as a {@code GraphException}), it is rethrown as-is so callers can rely on the same exception
     * type and mapping logic on a replayed failure as on the original, fresh failure. Only checked
     * exceptions are wrapped in a {@link RuntimeServiceException}.
     *
     * @return the operation result if successful
     * @throws RuntimeServiceException if the operation failed with a checked exception (wraps it)
     */
    public T unwrap() throws RuntimeServiceException {
        if (isSuccess()) {
            return rawValue;
        }
        if (rawException instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeServiceException(rawException);
    }

}
