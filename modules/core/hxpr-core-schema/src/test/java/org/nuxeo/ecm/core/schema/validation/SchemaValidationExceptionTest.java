/*
 * (C) Copyright 2022 Hyland (https://www.hyland.com/) and others.
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
 *     David Edwards
 */
package org.nuxeo.ecm.core.schema.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.validation.violations.InvalidObjectViolation;

public class SchemaValidationExceptionTest {

    private static final SchemaDTONode testNode1 = new SchemaDTONode(Map.of("key1", "value1"), "key1");

    private static final SchemaDTONode testNode2 = new SchemaDTONode(Map.of("key2", "value2"), "key2");

    @Test
    public void getTests() {
        ValidationReport report = new ValidationReport(
                List.of(new InvalidObjectViolation(testNode1), new InvalidObjectViolation(testNode2)));
        SchemaValidationException e = new SchemaValidationException(report);

        assertEquals(e.getMessage(), "2 violation(s) thrown. Call %s.getViolations() for more information.".formatted(
                SchemaValidationException.class.getSimpleName()));
        assertEquals(report, e.getReport());

        e = new SchemaValidationException(null);

        assertNull(e.getMessage());

        e = new SchemaValidationException(new ValidationReport(Collections.EMPTY_LIST));

        assertNull(e.getMessage());

        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getStatusCode());

    }
}
