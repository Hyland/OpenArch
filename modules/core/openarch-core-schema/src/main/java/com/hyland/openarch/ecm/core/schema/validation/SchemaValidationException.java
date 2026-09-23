/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema.validation;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;

/**
 * Exception thrown when some process failed due to {@link SchemaValidationViolation}.
 *
 */
public class SchemaValidationException extends RuntimeException implements ValidationException {

    private static final long serialVersionUID = 1L;

    protected final ValidationReport report;

    public SchemaValidationException(ValidationReport report) {
        this.report = report;
    }

    @Override
    public ValidationReport getReport() {
        return report;
    }

    @Override
    public String getMessage() {
        if (report != null && report.hasViolation()) {
            List<ValidationViolation> violations = report.asList();
            StringBuilder message = new StringBuilder("Invalid content model");
            if (violations.size() == 1) {
                appendViolation(message, violations.get(0));
            } else {
                message.append(" (%d errors):".formatted(violations.size()));
                for (ValidationViolation violation : violations) {
                    message.append("\n- error");
                    appendViolation(message, violation);
                }
            }
            return message.toString();
        } else {
            return super.getMessage();
        }
    }

    private static void appendViolation(StringBuilder message, ValidationViolation violation) {
        String xpath = violation.getPath();
        if (StringUtils.isNotBlank(xpath)) {
            message.append(" on property %s".formatted(xpath));
        }
        message.append(": '%s'".formatted(violation.getMessage(null)));
    }

}
