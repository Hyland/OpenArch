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
package org.nuxeo.ecm.core.schema.validation.violations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.validation.SchemaDTONode;

public class InvalidObjectViolationTest extends AbstractValidationViolationTest {

    private static final SchemaDTONode node = new SchemaDTONode(Map.of("key", "value"), "key");

    @Override
    protected String getExpectedMessage() {
        return "The value \"value\" is invalid.";
    }

    @Override
    public List<Object> getExpectedParameters() {
        return List.of("value", "key");
    }

    @Override
    public AbstractValidationViolation getTestObject() {
        return new InvalidObjectViolation(node, "not", "a", "known", "key");
    }

    @Test
    public void testGetDefaultMessageEmptyMessageKey() {
        InvalidObjectViolation violation = new InvalidObjectViolation(node) {
            @Override
            protected String getMessagesBundleName() {
                return "schema_messages_test";
            }
        };
        assertEquals(getExpectedMessage(), violation.getMessage(Locale.ENGLISH));
    }

}
