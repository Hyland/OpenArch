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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.dto.DTOPath;

public class UnknownObjectViolationTest extends AbstractValidationViolationTest {

    @Override
    protected String getExpectedMessage() {
        return "The value \"value\" is unknown.";
    }

    @Override
    protected AbstractValidationViolation getTestObject() {
        DTOPath node = DTOPath.root("key", "value");
        return new UnknownObjectViolation(node, "not", "a", "known", "key");
    }

    @Test
    @Override
    public void testGetMessageParameters() {
        // Test invalidValue is not a Map
        DTOPath node = DTOPath.root("key", "value");
        UnknownObjectViolation violation = new UnknownObjectViolation(node);
        assertEquals(List.of("value", "key"), violation.getMessageParameters(null));

        // Test invalidValue is Map, but does not contain resolver
        node = DTOPath.root("root", Map.of("child1", "value"));
        violation = new UnknownObjectViolation(node);
        assertEquals(List.of(Map.of("child1", "value"), "root"), violation.getMessageParameters(null));

        // Test with broken resolver
        node = DTOPath.root("root", Map.of("resolver", Map.of("broken", "resolverName")));
        violation = new UnknownObjectViolation(node);
        assertEquals(List.of(Map.of("resolver", Map.of("broken", "resolverName")), "root"),
                violation.getMessageParameters(null));

        // Test with resolver containing name
        node = DTOPath.root("root", Map.of("resolver", Map.of("name", "resolverName")));
        violation = new UnknownObjectViolation(node);
        assertEquals(List.of(Map.of("resolver", Map.of("name", "resolverName")), "resolverName", "root"),
                violation.getMessageParameters(null));
    }
}
