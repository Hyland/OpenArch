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
package org.nuxeo.ecm.core.schema.validation;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation.Type;

/**
 * A managed set of {@link ValidationViolation}.
 *
 */
public class ValidationReport {

    protected List<ValidationViolation> violations; //NOSONAR

    public ValidationReport(List<ValidationViolation> violations) {
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    /**
     * Throws a module specific ValidationException if there are violations.
     * @param violations the list of violations.
     * @param ignoreWarnings When there is a {@code validateOnly} flag in the REST API, this parameter is normally set
     *                       to {@code !validateOnly}. When there is no such flag, normally there are only errors
     *                       anyway.
     * @param createValidationException a function that creates the module specific ValidationException.
     */
    public static void checkViolations(List<ValidationViolation> violations, boolean ignoreWarnings,
            Function<ValidationReport, RuntimeException> createValidationException) {
        ValidationReport report = new ValidationReport(violations);
        if (ignoreWarnings) {
            report = report.removeWarnings();
        }
        if (report.hasViolation()) {
            throw createValidationException.apply(report);
        }
    }

    public boolean hasViolation() {
        return !violations.isEmpty();
    }

    public int numberOfViolations() {
        return violations.size();
    }

    public boolean hasError() {
        return violations.stream().anyMatch(violation -> violation.getType() == Type.ERROR);
    }

    public ValidationReport removeWarnings() {
        return new ValidationReport(violations.stream().filter( violation -> violation.getType() != Type.WARNING).toList());
    }

    public List<ValidationViolation> asList() {
        return violations;
    }

    @Override
    public String toString() {
        if (hasViolation()) {
            return violations.stream().map(v -> v.getMessage(null)).collect(Collectors.joining("\n"));
        } else {
            return "no errors";
        }
    }

}
