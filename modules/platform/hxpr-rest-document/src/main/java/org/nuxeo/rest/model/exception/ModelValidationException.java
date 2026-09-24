/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.model.exception;

import org.apache.http.HttpStatus;
import com.hyland.openarch.ecm.core.api.validation.AbstractValidationException;
import com.hyland.openarch.ecm.core.schema.validation.ValidationReport;

/**
 * A content model the repository refuses, reported as {@code 422 Unprocessable Entity} like every other validation
 * failure.
 * <p>
 * The version-unaware (v1) endpoints report it as {@code 409 Conflict} for backward compatibility, by wrapping their
 * writes in {@code ValidationStatus.conflictOnValidationFailure}.
 */
public class ModelValidationException extends AbstractValidationException {

    private static final long serialVersionUID = 1L;

    public ModelValidationException(ValidationReport report) {
        super(report, HttpStatus.SC_UNPROCESSABLE_ENTITY);
    }
}
