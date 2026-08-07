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
package org.nuxeo.ecm.core.schema.validation.violations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.dto.DTOPath;

public class PrimaryTypeValidationViolationTest extends AbstractValidationViolationTest {

    private static final DTOPath originalNode = DTOPath.root("1st", null).child("2nd", "originalValue");

    private static final PrimaryTypeValidationViolation removedViolation = new PrimaryTypeValidationViolation(originalNode, "removed");

    @Override
    protected String getExpectedMessage() {
        return "The value \"value\" already exists at \"key\" and cannot be removed as primary types are not allowed to be removed.";
    }

    @Override
    protected PrimaryTypeValidationViolation getTestObject() {
        return new PrimaryTypeValidationViolation(DTOPath.root("key", "value"), "not", "a", "known", "key");
    }

    @Test
    public void testGetMessage() {
        String expected = "The value \"originalValue\" already exists at \"1st/2nd\" and cannot be removed as primary types are not allowed to be removed.";
        assertEquals(expected, removedViolation.getMessage(null));
        assertEquals(expected, removedViolation.getMessage(Locale.ENGLISH));

    }

    @Test
    @Override
    public void testGetMessageParameters() {
        List<Object> expected = List.of(
                "originalValue",
                "1st/2nd"
        );
        assertEquals(expected, removedViolation.getMessageParameters(null));

    }
}
