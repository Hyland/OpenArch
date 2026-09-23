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
package com.hyland.openarch.ecm.core.schema.validation.violations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.schema.dto.DTOPath;

public class AlreadyExistsViolationTest extends AbstractValidationViolationTest {

    private static final DTOPath invalidNode = DTOPath.root("1st", null).child("2nd", "invalidValue");
    private static final DTOPath originalNode = DTOPath.root("1st", null).child("2nd", "originalValue");

    private static final AlreadyExistsViolation modifiedViolation= new AlreadyExistsViolation(invalidNode, originalNode, "modified");
    private static final AlreadyExistsViolation removedViolation = new AlreadyExistsViolation(originalNode, "removed");

    @Override
    protected String getExpectedMessage() {
        return "The value \"value\" cannot be applied as \"key\" already exists.";
    }

    @Override
    protected AbstractValidationViolation getTestObject() {
        return new AlreadyExistsViolation(DTOPath.root("key", "value"), "not", "a", "known", "key");
    }

    @Test
    public void getInvalidValueTest() {
        assertEquals("invalidValue", modifiedViolation.getInvalidValue());
        assertEquals("originalValue", removedViolation.getInvalidValue());
    }

    @Test
    public void testGetMessage() {
        String expected = "The value \"originalValue\" already exists at \"1st/2nd\" and cannot be removed.";
        assertEquals(expected, removedViolation.getMessage(null));
        assertEquals(expected, removedViolation.getMessage(Locale.ENGLISH));

        expected = "The value \"invalidValue\" cannot be applied as \"1st/2nd\" already exists with value \"originalValue\".";
        assertEquals(expected, modifiedViolation.getMessage(null));
        assertEquals(expected, modifiedViolation.getMessage(Locale.ENGLISH));
    }

    @Test
    @Override
    public void testGetMessageParameters() {
        List<Object> expected = List.of(
            "originalValue",
            "1st/2nd"
        );
        assertEquals(expected, removedViolation.getMessageParameters(null));

        expected = List.of(
            "invalidValue",
            "originalValue",
            "1st/2nd"
        );
        assertEquals(expected, modifiedViolation.getMessageParameters(null));
    }

}
