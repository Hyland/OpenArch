/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.api;

import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;

/**
 * Exception thrown when a method tries to create a document that already exists through copy or move.
 *
 * @see CoreSession#copy
 * @see CoreSession#move
 * @see CoreSession#removeDocument
 * @see CoreSession#removeDocuments
 * @see CoreSession#removeChildren
 */
public class DocumentExistsException extends NuxeoException {

    private static final long serialVersionUID = 1L;

    public DocumentExistsException() {
        super(SC_CONFLICT);
    }

    public DocumentExistsException(String message) {
        super(message, SC_CONFLICT);
    }

    public DocumentExistsException(String message, Throwable cause) {
        super(message, cause, SC_CONFLICT);
    }

    public DocumentExistsException(Throwable cause) {
        super(cause, SC_CONFLICT);
    }

}
