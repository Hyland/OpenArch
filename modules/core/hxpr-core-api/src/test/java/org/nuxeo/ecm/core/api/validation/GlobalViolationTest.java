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
package org.nuxeo.ecm.core.api.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

public class GlobalViolationTest {

    private static final String MESSAGE_TEST_STRING = "My Message";

    private static final GlobalViolation VIOLATION_TEST = new GlobalViolation(MESSAGE_TEST_STRING, List.of(1, "string"));

    @Test
    public void testGetInvalidValue() {
        assertNull(VIOLATION_TEST.getInvalidValue());
    }

    @Test
    public void testGetMessage() {
        assertEquals(MESSAGE_TEST_STRING, VIOLATION_TEST.getMessage(null));
    }

    @Test
    public void testGetMessageKey() {
        assertEquals(MESSAGE_TEST_STRING, VIOLATION_TEST.getMessageKey());
    }

    @Test
    public void testGetMessageParameters() {
        assertEquals(List.of(1, "string"), VIOLATION_TEST.getMessageParameters(null));
    }

    @Test
    public void testGetPath() {
        assertNull(VIOLATION_TEST.getPath());
    }
}
