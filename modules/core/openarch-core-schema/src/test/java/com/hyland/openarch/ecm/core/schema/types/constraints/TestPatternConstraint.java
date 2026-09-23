/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.core.schema.types.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.schema.types.primitives.BinaryType;
import com.hyland.openarch.ecm.core.schema.types.primitives.BooleanType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DateType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DecimalType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DoubleType;
import com.hyland.openarch.ecm.core.schema.types.primitives.LongType;
import com.hyland.openarch.ecm.core.schema.types.primitives.StringType;

public class TestPatternConstraint {

    private static final String PATTERN = "[0-9]{4}";

    private static final String YES = "1234";

    private static final String NO = "12345";

    PatternConstraint simple = new PatternConstraint(PATTERN);

    @Test
    public void testPatternConstraintSupportedType() {
        assertTrue(DoubleType.INSTANCE.support(PatternConstraint.class));
        assertTrue(DecimalType.INSTANCE.support(PatternConstraint.class));
        assertTrue(LongType.INSTANCE.support(PatternConstraint.class));
        assertTrue(StringType.INSTANCE.support(PatternConstraint.class));
        assertFalse(DateType.INSTANCE.support(PatternConstraint.class));
        assertFalse(BinaryType.INSTANCE.support(PatternConstraint.class));
        assertFalse(BooleanType.INSTANCE.support(PatternConstraint.class));
    }

    @Test
    public void testPatternConstraintNominals() {
        assertTrue(simple.validate(YES));
        assertFalse(simple.validate(NO));
    }

    @Test
    public void testPatternConstraintNullIsOk() {
        assertTrue(simple.validate(null));
    }

    @Test
    public void testPatternConstraintHandleString() {
        assertTrue(simple.validate(YES));
        assertFalse(simple.validate(NO));
    }

    @Test
    public void testPatternConstraintHandleAnyObject() {
        assertTrue(simple.validate(new Object() {
            @Override
            public String toString() {
                return "1234";
            }
        }));
        assertFalse(simple.validate(new Object() {
            @Override
            public String toString() {
                return "12345";
            }
        }));
    }

    @Test
    public void testPatternConstraintDescription() {
        Constraint.Description description = simple.getDescription();
        assertEquals("PatternConstraint", description.getName());
        Map<String, Object> params = description.getParameters();
        assertEquals(1, params.size());
        assertTrue(params.containsKey("Pattern"));
        assertEquals(PATTERN, params.get("Pattern"));
    }

}
