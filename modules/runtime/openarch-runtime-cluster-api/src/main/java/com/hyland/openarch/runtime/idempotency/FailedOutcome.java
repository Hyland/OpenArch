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
 * Represents a failed outcome with a message and a status code. The message and the status code are extracted from the
 * exception that caused the failure. This record is used to communicate the failure details in a structured way.
 *
 * @param message the failure message
 * @param statusCode the HTTP status code
 */
public record FailedOutcome(String message, int statusCode) {

    public static final String MESSAGE_FIELD = "message";

    public static final String STATUS_CODE_FIELD = "statusCode";

    /** HTTP 500, used when the originating exception carries no explicit status code. */
    private static final int SC_INTERNAL_SERVER_ERROR = 500;

    public FailedOutcome(String message) {
        this(message, SC_INTERNAL_SERVER_ERROR);
    }

}
