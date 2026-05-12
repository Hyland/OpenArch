/*
 * (C) Copyright 2025 Hyland (https://www.hyland.com/) and others.
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
 *
 * Contributors:
 *     Abdul Mohammed
 */
package org.nuxeo.ecm.core.api;

import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

public class BulkIndexingException extends RuntimeException {

    protected final int statusCode;

    public BulkIndexingException(String message) {
        super(message);
        this.statusCode = SC_INTERNAL_SERVER_ERROR;
    }

    public BulkIndexingException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public BulkIndexingException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
