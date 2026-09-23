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
package org.nuxeo.ecm.core.api.validation;

import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;

import org.nuxeo.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.schema.validation.ValidationException;
import com.hyland.openarch.ecm.core.schema.validation.ValidationReport;

public abstract class AbstractValidationException extends NuxeoException implements ValidationException {

    private final ValidationReport report;

    private static final String MESSAGE = "%d violation(s) thrown. Call "
            + AbstractValidationException.class.getSimpleName() + ".getViolations() for more information. "
            + SC_CONFLICT;

    protected AbstractValidationException(ValidationReport report, int statusCode) {
        super(statusCode);
        this.report = report;
    }

    protected AbstractValidationException(String message, ValidationReport report, int statusCode) {
        super(message, statusCode);
        this.report = report;
    }

    @Override
    public ValidationReport getReport() {
        return report;
    }

    @Override
    public String getMessage() {
        if (report != null && report.hasError()) {
            return MESSAGE.formatted(report.numberOfViolations());
        } else {
            return super.getMessage();
        }
    }
}
