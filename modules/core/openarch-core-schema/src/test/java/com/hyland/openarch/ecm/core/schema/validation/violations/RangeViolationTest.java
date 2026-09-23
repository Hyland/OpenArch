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
import com.hyland.openarch.ecm.core.schema.types.constraints.LengthConstraint;

public class RangeViolationTest extends AbstractValidationViolationTest {

    private static final long MAX = 1L;

    private static final long MIN = 5L;

    private static final DTOPath node = DTOPath.root("key", "value");

    @Override
    protected String getExpectedMessage() {
        return "The value \"%s\" must be less than the value \"%s\".".formatted(MIN, MAX);
    }

    @Override
    protected List<Object> getExpectedParameters() {
        return List.of("key/min", MIN, "key/max", MAX);
    }

    @Override
    protected AbstractValidationViolation getTestObject() {
        return new RangeViolation(new LengthConstraint(MIN, MAX), node, "min", "max") {
            @Override
            protected String getMessagesBundleName() {
                return "Not a bundle name";
            }
        };
    }

    @Test
    public void testGetDefaultMessageEmptyMessageInBundle() {
        RangeViolation violation = new RangeViolation(new LengthConstraint(MIN, MAX), node, "min", "max") {
            @Override
            protected String getMessagesBundleName() {
                return "schema_messages_test";
            }
        };
        assertEquals(getExpectedMessage(), violation.getMessage(Locale.ENGLISH));
    }
}
