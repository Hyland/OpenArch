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
package org.nuxeo.common;

/**
 * Implemented by exceptions that know how long the client should wait before retrying, so the REST layer can
 * advertise that delay in the {@code Retry-After} header rather than a fixed default.
 */
public interface RetryAfterHint {

    /**
     * @return the delay in whole seconds; must be strictly positive and must never be shorter than the delay the
     *         client will actually be subject to
     */
    int getRetryAfterSeconds();
}
