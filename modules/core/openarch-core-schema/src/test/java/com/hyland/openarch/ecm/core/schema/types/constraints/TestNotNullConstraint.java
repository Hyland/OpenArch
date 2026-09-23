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

public class TestNotNullConstraint {

    @Test
    public void testNotNullConstraintSupportedType() {
        assertTrue(DoubleType.INSTANCE.support(NotNullConstraint.class));
        assertTrue(DecimalType.INSTANCE.support(NotNullConstraint.class));
        assertTrue(LongType.INSTANCE.support(NotNullConstraint.class));
        assertTrue(StringType.INSTANCE.support(NotNullConstraint.class));
        assertTrue(DateType.INSTANCE.support(NotNullConstraint.class));
        assertTrue(BinaryType.INSTANCE.support(NotNullConstraint.class));
        assertTrue(BooleanType.INSTANCE.support(NotNullConstraint.class));
    }

    @Test
    public void testNotNullConstraintNominals() {
        assertTrue(NotNullConstraint.get().validate(123));
        assertTrue(NotNullConstraint.get().validate("123"));
        assertTrue(NotNullConstraint.get().validate(NotNullConstraint.get()));
        assertFalse(NotNullConstraint.get().validate(null));
    }

    @Test
    public void testNotNullConstraintDescription() {
        Constraint.Description description = NotNullConstraint.get().getDescription();
        assertEquals("NotNullConstraint", description.getName());
        Map<String, Object> params = description.getParameters();
        assertEquals(0, params.size());
    }

}
