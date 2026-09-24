/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.api;

import static jakarta.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;

/**
 * An exception thrown if a condition failed.
 */
public class ConditionFailedException extends RepositoryException {

    private static final long serialVersionUID = 1L;

    public ConditionFailedException() {
        super(SC_PRECONDITION_FAILED);
    }

    public ConditionFailedException(String message) {
        super(message, SC_PRECONDITION_FAILED);
    }

    public ConditionFailedException(String message, Throwable cause) {
        super(message, cause, SC_PRECONDITION_FAILED);
    }

    public ConditionFailedException(Throwable cause) {
        super(cause, SC_PRECONDITION_FAILED);
    }

}
