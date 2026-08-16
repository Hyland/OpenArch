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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.List;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.dto.DTOPath;
import org.nuxeo.ecm.core.schema.validation.violations.InvalidObjectViolation;

public class SchemaValidationExceptionTest {

    private static final DTOPath testNode1 = DTOPath.root("key1", "value1");

    private static final DTOPath testNode2 = DTOPath.root("key2", "value2");

    private static final DTOPath testNodeBlankPath = DTOPath.root("", "value3");

    @Test
    public void getTests() {
        InvalidObjectViolation violation1 = new InvalidObjectViolation(testNode1);
        InvalidObjectViolation violation2 = new InvalidObjectViolation(testNode2);
        InvalidObjectViolation violation3 = new InvalidObjectViolation(testNodeBlankPath);

        // more than one violation: report the count and every error, including one with a blank path
        ValidationReport report = new ValidationReport(List.of(violation1, violation2, violation3));
        SchemaValidationException e = new SchemaValidationException(report);

        assertEquals("""
            Invalid content model (3 errors):
            - error on property key1: 'The value "value1" is invalid.'
            - error on property key2: 'The value "value2" is invalid.'
            - error: 'The value "value3" is invalid.'""", e.getMessage());
        assertEquals(report, e.getReport());

        // a single violation: report just that one
        report = new ValidationReport(List.of(violation1));
        e = new SchemaValidationException(report);

        assertEquals("Invalid content model on property key1: 'The value \"value1\" is invalid.'",
                e.getMessage());

        // a single violation with a blank path: omit the property clause
        report = new ValidationReport(List.of(new InvalidObjectViolation(testNodeBlankPath)));
        e = new SchemaValidationException(report);

        assertEquals("Invalid content model: 'The value \"value3\" is invalid.'", e.getMessage());

        e = new SchemaValidationException(null);

        assertNull(e.getMessage());

        e = new SchemaValidationException(new ValidationReport(Collections.EMPTY_LIST));

        assertNull(e.getMessage());

        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getStatusCode());

    }
}
