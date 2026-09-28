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
 * Thrown when a caller gives up waiting for a concurrent execution of the same idempotency key to
 * either finish and publish its result, or have its claim lease expire.
 * <p>
 * This can only happen if another thread or process is actively holding the claim for longer than
 * its lease, which should be sized to comfortably exceed the expected duration of the guarded
 * operation. Callers may catch this exception to retry later, or surface a conflict response (e.g.
 * HTTP 409) to the original caller.
 */
public final class IdempotencyClaimTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception with the given message.
     *
     * @param message the detail message
     */
    public IdempotencyClaimTimeoutException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public IdempotencyClaimTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

}
