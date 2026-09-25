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
package com.hyland.openarch.rest.utils;

import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;

import java.util.function.Supplier;

import com.hyland.openarch.ecm.core.api.validation.AbstractValidationException;

/**
 * The status a configuration the repository refuses is reported with.
 * <p>
 * It is {@code 422 Unprocessable Entity}, as for every other validation failure. Some version-unaware (v1) endpoints
 * reported it as {@code 409 Conflict} before optimistic locking gave 409 the meaning of a version mismatch, and keep
 * doing so for backward compatibility, through {@link #conflictOnValidationFailure}.
 */
public final class ValidationStatus {

    private ValidationStatus() {
        // this is a utility class
    }

    /**
     * Runs a v1 write, reporting a configuration the repository refuses as {@code 409 Conflict} rather than with its
     * own status, which is what this endpoint reported before.
     * <p>
     * Only the status changes: the violations, and therefore the response body, are the ones the service reported.
     *
     * @param supplier the write to run
     * @return whatever the write returns
     */
    public static <T> T conflictOnValidationFailure(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (AbstractValidationException e) {
            throw new ConflictValidationException(e);
        }
    }

    /** A write that returns nothing, see {@link #conflictOnValidationFailure(Supplier)}. */
    public static void conflictOnValidationFailure(Runnable runnable) {
        conflictOnValidationFailure(() -> {
            runnable.run();
            return null;
        });
    }

    /** The violations another validation failure reported, carrying {@code 409 Conflict} instead. */
    private static class ConflictValidationException extends AbstractValidationException {

        private static final long serialVersionUID = 1L;

        ConflictValidationException(AbstractValidationException cause) {
            super(cause.getReport(), SC_CONFLICT);
            initCause(cause);
        }
    }

}
