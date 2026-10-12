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
 * @param retryAfter the retry after hint
 */
public record FailedOutcome(String type, String message, int statusCode, int retryAfter) {

    public static final String MESSAGE_FIELD = "message";

    public static final String STATUS_CODE_FIELD = "statusCode";

    public static final String TYPE_FIELD = "type";

    public static final String RETRY_AFTER_HINT_FIELD = "retryAfter";

    public FailedOutcome(String type, String message, int statusCode) {
        this(type, message, statusCode, 0);
    }
}
