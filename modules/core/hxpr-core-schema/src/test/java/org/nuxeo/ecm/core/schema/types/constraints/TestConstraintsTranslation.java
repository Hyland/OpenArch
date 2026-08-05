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

package org.nuxeo.ecm.core.schema.types.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.types.primitives.BinaryType;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.DecimalType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.core.schema.types.resolver.TestingColorResolver;

public class TestConstraintsTranslation {

    @Test
    public void testGenericMessage() {
        AbstractConstraint constraint = new AbstractConstraint() {
            @Override
            public boolean validate(Object object) {
                return true;
            }

            @Override
            public Description getDescription() {
                HashMap<String, Object> parameters = new HashMap<>();
                parameters.put("param1", "value1");
                parameters.put("param2", "value2");
                return new Description("AlwaysWrongConstraint", parameters);
            }
        };
        checkConstraintMessage(
                "The value \"  \" does not respect the constraint AlwaysWrongConstraint{param1=value1, param2=value2}.",
                constraint);
    }

    @Test
    public void testNotNullContraintMessage() {
        checkConstraintMessage("Value is required.", NotNullConstraint.get());
    }

    @Test
    public void testPatternContraintMessage() {
        checkConstraintMessage("This value must match the format \".*\\S.*\".", new PatternConstraint(".*\\S.*"));
    }

    @Test
    public void testEnumContraintMessage() {
        checkConstraintMessage("The property must have one of the following values: tac, tic.",
                new EnumConstraint("tic", "tac"));
    }

    @Test
    public void testLengthContraintMinMaxMessage() {
        checkConstraintMessage("The property must contain between 2 and 10 character(s).", new LengthConstraint(2, 10));
    }

    @Test
    public void testLengthContraintMinMessage() {
        checkConstraintMessage("This property must contain at least 2 character(s).", new LengthConstraint(2, null));
    }

    @Test
    public void testLengthContraintMaxMessage() {
        checkConstraintMessage("This property cannot contain more than 10 character(s).",
                new LengthConstraint(null, 10));
    }

    @Test
    public void testNumericIntervalContraintMinInMaxInMessage() {
        checkConstraintMessage("This value must be greater than or equal to 2 and less than or equal to 10.",
                new NumericIntervalConstraint(2, true, 10, true));
    }

    @Test
    public void testNumericIntervalContraintMinInMaxExMessage() {
        checkConstraintMessage("This value must be greater than or equal to 2 and less than 10.",
                new NumericIntervalConstraint(2, true, 10, false));
    }

    @Test
    public void testNumericIntervalContraintMinExMaxInMessage() {
        checkConstraintMessage("This value must be greater than 2 and less than or equal to 10.",
                new NumericIntervalConstraint(2, false, 10, true));
    }

    @Test
    public void testNumericIntervalContraintMinExMaxExMessage() {
        checkConstraintMessage("This value must be greater than 2 and less than 10.",
                new NumericIntervalConstraint(2, false, 10, false));
    }

    @Test
    public void testNumericIntervalContraintMinInMessage() {
        checkConstraintMessage("This value must be greater or equal to 2.",
                new NumericIntervalConstraint(2, true, null, true));
    }

    @Test
    public void testNumericIntervalContraintMinExMessage() {
        checkConstraintMessage("This value must be greater than 2.",
                new NumericIntervalConstraint(2, false, null, true));
    }

    @Test
    public void testNumericIntervalContraintMaxInMessage() {
        checkConstraintMessage("This value must be less or equal to 10.",
                new NumericIntervalConstraint(null, true, 10, true));
    }

    @Test
    public void testNumericIntervalContraintMaxExMessage() {
        checkConstraintMessage("This value must be less than 10.",
                new NumericIntervalConstraint(null, true, 10, false));
    }

    @Test
    public void testDateIntervalContraintMinInMaxInMessage() {
        checkConstraintMessage("The date must be between 2014-11-02T00:00:00Z and 2014-11-10T00:00:00Z included.",
                new DateIntervalConstraint("2014-11-02", true, "2014-11-10", true));
    }

    @Test
    public void testDateIntervalContraintMinInMaxExMessage() {
        checkConstraintMessage("The date must be between 2014-11-02T00:00:00Z included and 2014-11-10T00:00:00Z excluded.",
                new DateIntervalConstraint("2014-11-02", true, "2014-11-10", false));
    }

    @Test
    public void testDateIntervalContraintMinExMaxInMessage() {
        checkConstraintMessage("The date must be between 2014-11-02T00:00:00Z excluded and 2014-11-10T00:00:00Z included.",
                new DateIntervalConstraint("2014-11-02", false, "2014-11-10", true));
    }

    @Test
    public void testDateIntervalContraintMinExMaxExMessage() {
        checkConstraintMessage("The date must be between 2014-11-02T00:00:00Z excluded and 2014-11-10T00:00:00Z excluded.",
                new DateIntervalConstraint("2014-11-02", false, "2014-11-10", false));
    }

    @Test
    public void testDateIntervalContraintMinInMessage() {
        checkConstraintMessage("The date must be after 2014-11-02T00:00:00Z included.",
                new DateIntervalConstraint("2014-11-02", true, null, true));
    }

    @Test
    public void testDateIntervalContraintMinExMessage() {
        checkConstraintMessage("The date must be after 2014-11-02T00:00:00Z excluded.",
                new DateIntervalConstraint("2014-11-02", false, null, true));
    }

    @Test
    public void testDateIntervalContraintMaxInMessage() {
        checkConstraintMessage("The date must be earlier than 2014-11-10T00:00:00Z included.",
                new DateIntervalConstraint(null, true, "2014-11-10", true));
    }

    @Test
    public void testDateIntervalContraintMaxExMessage() {
        checkConstraintMessage("The date must be earlier than 2014-11-10T00:00:00Z excluded.",
                new DateIntervalConstraint(null, true, "2014-11-10", false));
    }

    @Test
    public void testTypeConstraintBinaryMessage() {
        checkConstraintMessage("The value \"  \" contains no data.", new TypeConstraint(BinaryType.INSTANCE));
    }

    @Test
    public void testTypeConstraintBooleanMessage() {
        checkConstraintMessage("The value \"  \" must be a boolean.", new TypeConstraint(BooleanType.INSTANCE));
    }

    @Test
    public void testTypeConstraintLongMessage() {
        checkConstraintMessage("The value \"  \" must be an integer.", new TypeConstraint(LongType.INSTANCE));
    }

    @Test
    public void testTypeConstraintDateMessage() {
        checkConstraintMessage("The value \"  \" is not a valid date.", new TypeConstraint(DateType.INSTANCE));
    }

    @Test
    public void testTypeConstraintStringMessage() {
        checkConstraintMessage("The value \"  \" must be a text.", new TypeConstraint(StringType.INSTANCE));
    }

    @Test
    public void testTypeConstraintDoubleMessage() {
        checkConstraintMessage("The value \"  \" must be a floating point number.", new TypeConstraint(DoubleType.INSTANCE));
    }

    @Test
    public void testTypeConstraintDecimalMessage() {
        checkConstraintMessage("The value \"  \" must be a decimal number.", new TypeConstraint(DecimalType.INSTANCE));
    }

    enum Color {
        RED, GREEN, BLUE;
    }

    @Test
    public void testExternalReferenceConstraintMessage() {
        TestingColorResolver resolver = new TestingColorResolver();
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put(TestingColorResolver.COLOR_MODE, TestingColorResolver.MODE.PRIMARY.name());
        resolver.configure(parameters);
        checkConstraintMessage("\"  \" is not a correct primary color", new ObjectResolverConstraint(resolver));
    }

    private void checkConstraintMessage(String expected, Constraint constraint) {
        String message = constraint.getErrorMessage("  ", Locale.ENGLISH);
        assertFalse(message.contains(Constraint.MESSAGES_KEY));
        assertEquals(expected, message);
    }

}
