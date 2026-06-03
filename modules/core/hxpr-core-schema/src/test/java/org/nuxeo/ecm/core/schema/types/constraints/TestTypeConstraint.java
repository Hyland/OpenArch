/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package org.nuxeo.ecm.core.schema.types.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.types.PrimitiveType;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.primitives.BinaryType;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.DecimalType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;

public class TestTypeConstraint {

    private static final Number NUMBER = new Number() {
        private static final long serialVersionUID = 1L;

        @Override
        public long longValue() {
            return 12l;
        }

        @Override
        public int intValue() {
            return 12;
        }

        @Override
        public float floatValue() {
            return 12.7f;
        }

        @Override
        public double doubleValue() {
            return 12.7;
        }

        @Override
        public String toString() {
            return "12.7";
        }
    };

    private static final List<?> ANY_OBJECT = Collections.EMPTY_LIST;

    private static final String SIMPLE_DATE = "2014-05-15";

    private static final String SIMPLE_DATE_TIME_ZONED = "2014-05-15T00:00:00Z";

    private static final String DATE_TIME_ZONED = "2024-01-23T16:07:42.060Z";

    @Test
    public void testAnyTypeConstraintAcceptNull() {
        assertTrue(new TypeConstraint(new PrimitiveType("ImpossibleTypeConstraint") {
            @Override
            public Object convert(Object value) throws TypeException {
                return value;
            }

            @Override
            public boolean validate(Object object) {
                return false;
            }

            @Override
            public boolean support(Class<? extends Constraint> constraint) {
                return false;
            }
        }).validate(null));
    }

    @Test
    public void testBooleanAcceptPrimitiveBoolean() {
        assertTrue(new TypeConstraint(BooleanType.INSTANCE).validate(true));
    }

    @Test
    public void testBooleanAcceptBoolanObject() {
        assertTrue(new TypeConstraint(BooleanType.INSTANCE).validate(Boolean.FALSE));
    }

    @Test
    public void testBooleanAcceptBooleanAsString() {
        assertTrue(new TypeConstraint(BooleanType.INSTANCE).validate("false"));
    }

    @Test
    public void testBooleanDeniedRandomStrings() {
        assertFalse(new TypeConstraint(BooleanType.INSTANCE).validate("foo"));
    }

    @Test
    public void testBooleanDeniedStupidThings() {
        assertFalse(new TypeConstraint(BooleanType.INSTANCE).validate(ANY_OBJECT));
    }

    @Test
    public void testBinaryAcceptAnything() {
        assertTrue(new TypeConstraint(BinaryType.INSTANCE).validate(ANY_OBJECT));
    }

    @Test
    public void testStringDeniedStupidThings() {
        assertFalse(new TypeConstraint(StringType.INSTANCE).validate(ANY_OBJECT));
    }

    @Test
    public void testLongAcceptNumbers() {
        assertTrue(new TypeConstraint(LongType.INSTANCE).validate(NUMBER));
    }

    @Test
    public void testLongAcceptIntegerAsString() {
        assertTrue(new TypeConstraint(LongType.INSTANCE).validate("12"));
    }

    @Test
    public void testLongDeniedStupidThings() {
        assertFalse(new TypeConstraint(LongType.INSTANCE).validate(ANY_OBJECT));
    }

    @Test
    public void testDoubleAcceptNumbers() {
        assertTrue(new TypeConstraint(DoubleType.INSTANCE).validate(NUMBER));
    }

    @Test
    public void testDoubleAcceptIntegerAsString() {
        assertTrue(new TypeConstraint(DoubleType.INSTANCE).validate("12"));
    }

    @Test
    public void testDoubleDeniedStupidThings() {
        assertFalse(new TypeConstraint(DoubleType.INSTANCE).validate(ANY_OBJECT));
    }

    @Test
    public void testDecimalAcceptNumbers() {
        assertTrue(new TypeConstraint(DecimalType.INSTANCE).validate(NUMBER));
    }

    @Test
    public void testDecimalAcceptIntegerAsString() {
        assertTrue(new TypeConstraint(DecimalType.INSTANCE).validate("12"));
    }

    @Test
    public void testDecimalDeniedStupidThings() {
        assertFalse(new TypeConstraint(DecimalType.INSTANCE).validate(ANY_OBJECT));
    }

    @Test
    public void testDateAcceptInstant() {
        assertTrue(new TypeConstraint(DateType.INSTANCE).validate(Instant.now()));
    }

    @Test
    public void testDateDenyDate() {
        assertFalse(new TypeConstraint(DateType.INSTANCE).validate(new Date()));
    }

    @Test
    public void testDateDenyCalendar() {
        assertFalse(new TypeConstraint(DateType.INSTANCE).validate(new GregorianCalendar()));
    }

    @Test
    public void testDateDeniedStupidThings() {
        assertFalse(new TypeConstraint(DateType.INSTANCE).validate(ANY_OBJECT));
    }

    @Test
    public void testDateTypeWithoutTimeZone() {
        assertTrue(new TypeConstraint(DateType.INSTANCE).validate(SIMPLE_DATE));
    }

    @Test
    public void testDateConversionOutput() throws TypeException {
        var dateNoTimeZones = DateType.INSTANCE.convert(SIMPLE_DATE);
        assertTrue(dateNoTimeZones instanceof Instant);
        assertEquals(SIMPLE_DATE_TIME_ZONED, dateNoTimeZones.toString());

        var dateTimeZoned = DateType.INSTANCE.convert(DATE_TIME_ZONED);
        assertTrue(dateTimeZoned instanceof Instant);
        assertEquals(DATE_TIME_ZONED, dateTimeZoned.toString());
    }

}
