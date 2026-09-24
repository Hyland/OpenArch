/*
 * (C) Copyright 2018-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.api.model;

import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;

import com.hyland.openarch.ecm.core.api.RepositoryException;

/**
 * Exception indicating that a version cannot be modified.
 *
 * @since 10.3
 */
public class VersionNotModifiableException extends RepositoryException {

    private static final long serialVersionUID = 1L;

    public VersionNotModifiableException() {
        super(SC_UNPROCESSABLE_ENTITY);
    }

    public VersionNotModifiableException(String message) {
        super(message, SC_UNPROCESSABLE_ENTITY);
    }

    public VersionNotModifiableException(String message, Throwable cause) {
        super(message, cause, SC_UNPROCESSABLE_ENTITY);
    }

    public VersionNotModifiableException(Throwable cause) {
        super(cause, SC_UNPROCESSABLE_ENTITY);
    }

}
