/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.core.api.validation;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;

/**
 * Exception thrown when some process failed due to {@link ValidationViolation}.
 *
 * @since 7.1
 */
public class DocumentValidationException extends AbstractValidationException {

    protected static final String MESSAGE_SINGLE = "Constraint violation thrown%s: '%s'";

    protected static final String MESSAGE = "%s constraint violation(s) thrown. First one thrown%s: '%s', call "
            + DocumentValidationException.class.getSimpleName() + ".getViolations() to get the others";

    private static final long serialVersionUID = 1L;

    public DocumentValidationException(DocumentValidationReport report) {
        super(report, HttpStatus.SC_UNPROCESSABLE_ENTITY);
    }

    public DocumentValidationException(String message) {
        super(message, new DocumentValidationReport(List.of(new GlobalViolation(message))),
                HttpStatus.SC_UNPROCESSABLE_ENTITY);
    }

    @Override
    public String getMessage() {
        var report = getReport();
        if (report.hasViolation()) {
            int num = report.numberOfViolations();
            ValidationViolation violation = report.asList().get(0);
            String xpath = violation.getPath();
            var propertyMessage = StringUtils.isBlank(xpath) ? "" : String.format(" on property %s", xpath);
            String violationMessage = violation.getMessage(null);
            if (num > 1) {
                return String.format(MESSAGE, report.numberOfViolations(), propertyMessage, violationMessage);
            } else {
                return String.format(MESSAGE_SINGLE, propertyMessage, violationMessage);
            }
        } else {
            return super.getMessage();
        }
    }

}
