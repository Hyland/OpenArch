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

import java.io.Serializable;
import java.util.Optional;

/**
 * Encapsulates the result of an idempotent operation, including the operation result and metadata.
 * <p>
 * This class can store either a successful result or a failed exception, allowing idempotent
 * operations to return or re-throw the same outcome on duplicate requests.
 *
 * @param <T> the type of the operation result
 * @since 12.0
 */
public final class IdempotencyResult<T extends Serializable> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final T value;

    private final Exception exception;

    private final long createdAtMillis;

    private IdempotencyResult(T value, Exception exception, long createdAtMillis) {
        this.value = value;
        this.exception = exception;
        this.createdAtMillis = createdAtMillis;
    }

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
        return exception == null;
    }

    /**
     * Gets the result value if the operation succeeded.
     */
    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    /**
     * Gets the exception if the operation failed.
     */
    public Optional<Exception> exception() {
        return Optional.ofNullable(exception);
    }

    /**
     * Gets the timestamp when this result was created, in milliseconds since epoch.
     */
    public long createdAtMillis() {
        return createdAtMillis;
    }

    /**
     * Unwraps the result, returning the value or rethrowing the exception.
     *
     * @return the operation result if successful
     * @throws RuntimeException if the operation failed (wraps the exception if needed)
     */
    @SuppressWarnings("unchecked")
    public T unwrap() throws RuntimeException {
        if (isSuccess()) {
            return value;
        }
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        throw new RuntimeException(exception);
    }

}
