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

/**
 * Handles idempotent operation execution by caching results based on an idempotency key.
 * <p>
 * Ensures that operations are executed at most once even if called multiple times with the
 * same idempotency key, returning cached results on subsequent calls.
 */
public interface IdempotencyHandler {

    /**
     * Executes an operation idempotently, caching and returning the result.
     * <p>
     * If a result for the given key already exists in the cache:
     * <ul>
     *   <li>If it was a success, return the cached value</li>
     *   <li>If it was a failure, return the cached failure</li>
     * </ul>
     * If no cached result exists, execute the operation, cache the result or exception, and return/rethrow.
     * <p>
     * The result type is resolved from the result's runtime class, which must be registered in
     * {@link IdempotencyResultTypeRegistry}; otherwise the result is not cached. Use
     * {@link #execute(String, String, IdempotentOperation)} for a generic result type, whose
     * element types erasure would lose.
     *
     * @param <T>           the type of the operation result
     * @param key           the idempotency key value identifying this operation
     * @param operation     the operation to execute (must be idempotent and deterministic)
     * @return the outcome, carrying both the result and whether it was replayed
     * @throws RuntimeException if the operation fails or if cached failure is replayed
     */
    <T> Outcome<T> execute(String key, IdempotentOperation<T> operation);

    /**
     * Same contract as {@link #execute(String, IdempotentOperation)}, but with an explicit result
     * type key instead of one inferred from the result's runtime class.
     * <p>
     * This is required for a result type that is itself generic (e.g. {@code List<SomeDto>}): type
     * erasure means {@code result.getClass()} alone cannot recover the element type, so a replayed
     * cache hit would deserialize a parameterized collection's elements as raw {@code Map}s rather
     * than their concrete DTO type. The key must be registered in
     * {@link IdempotencyResultTypeRegistry}, for instance through
     * {@link IdempotencyResultTypeRegistry#registerList(String, Class)}.
     *
     * @param <T>       the type of the operation result
     * @param key       the idempotency key value identifying this operation
     * @param typename  the registered result type key used to deserialize the cached value, and
     *                  persisted as {@code __typename} in the cache entry
     * @param operation the operation to execute (must be idempotent and deterministic)
     * @return the outcome, carrying both the result and whether it was replayed
     * @throws RuntimeException if the operation fails or if cached failure is replayed
     */
    <T> Outcome<T> execute(String key, String typename, IdempotentOperation<T> operation);

    /**
     * Removes a cached result for the given idempotency key.
     * <p>
     * Use with caution: clearing cached results can allow duplicate operations to execute.
     *
     * @param key the idempotency key value
     * @return true if a result was removed, false if no result was cached
     */
    boolean evict(String key);

    /**
     * Checks if an operation is currently in progress for the given idempotency key.
     *
     * @param key the idempotency key value
     * @return true if an operation is in progress, false otherwise
     */
    boolean isOperationInProgress(String key);
    /**
     * Represents an idempotent operation that can be executed and cached.
     *
     * @param <T> the type of the operation result
     */
    @FunctionalInterface
    interface IdempotentOperation<T> {

        /**
         * Executes the operation.
         *
         * @return the operation result
         */
        T execute();

    }

    /**
     * Outcome of {@link #executeWithOutcome}: the operation's result, and whether it was replayed
     * from a previously cached result rather than freshly executed.
     *
     * @param <T> the type of the operation result
     */
    record Outcome<T>(T value, FailedOutCome exception, boolean replay) {

        public boolean isFailure() {
            return exception != null;
        }
    }

}
