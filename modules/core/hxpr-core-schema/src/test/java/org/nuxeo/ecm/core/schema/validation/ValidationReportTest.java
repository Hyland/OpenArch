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
package org.nuxeo.ecm.core.schema.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.validation.violations.InvalidObjectViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;

public class ValidationReportTest {

    private static final SchemaDTONode testNode1 = new SchemaDTONode(Map.of("key1", "value1"), "key1");

    private static final SchemaDTONode testNode2 = new SchemaDTONode(Map.of("key2", "value2"), "key2");

    @Test
    public void testToString() {
        ValidationReport report = new ValidationReport(List.of(
            new InvalidObjectViolation(testNode1),
            new InvalidObjectViolation(testNode2)
        ));

        assertEquals("The value \"value1\" is invalid.\nThe value \"value2\" is invalid.", report.toString());

        ValidationReport emptyReport = new ValidationReport(null);

        assertEquals("no errors", emptyReport.toString());

        emptyReport = new ValidationReport(Collections.EMPTY_LIST);

        assertEquals("no errors", emptyReport.toString());
    }

    @Test
    public void testAsList() {
        List<ValidationViolation> list = List.of(
            new InvalidObjectViolation(testNode1),
            new InvalidObjectViolation(testNode2)
        );
        ValidationReport report = new ValidationReport(list);

        assertEquals(list, report.asList());
    }

    @Test
    public void testErrors() {
        List<ValidationViolation> list = List.of(
            new InvalidObjectViolation(testNode1),
            new InvalidObjectViolation(testNode2)
        );

        ValidationReport report = new ValidationReport(null);

        assertFalse(report.hasViolation());
        assertEquals(0, report.numberOfViolations());

        report = new ValidationReport(list);
        assertTrue(report.hasViolation());
        assertEquals(2, report.numberOfViolations());
    }

    @Test
    public void testRemoveWarnings() {
        List<ValidationViolation> list = List.of(
            new InvalidObjectViolation(testNode1),
            new InvalidObjectViolation(testNode2) {
                @Override
                public Type getType() {
                    return Type.WARNING;
                };
            }
        );

        ValidationReport report = new ValidationReport(list);
        assertTrue(report.hasViolation());
        assertEquals(2, report.numberOfViolations());

        report = report.removeWarnings();
        assertTrue(report.hasViolation());
        assertEquals(1, report.numberOfViolations());
    }
}
