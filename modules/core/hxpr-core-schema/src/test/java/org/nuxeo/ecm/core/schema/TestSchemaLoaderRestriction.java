/*
 * (C) Copyright 2014-2017 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.DateIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.EnumConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.LengthConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.PatternConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.TypeConstraint;
import org.nuxeo.ecm.core.schema.types.primitives.BinaryType;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.DecimalType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.runtime.test.ResourceHelper;

public class TestSchemaLoaderRestriction {

    public static final String NS_XSD = "http://www.w3.org/2001/XMLSchema";

    private Schema schema;

    @BeforeEach
    public void setUp() throws Exception {
        XSDLoader reader = new XSDLoader(new SchemaManagerImpl());
        URL url = ResourceHelper.getResource("schema/testrestriction.xsd");
        schema = reader.loadSchema("testrestriction", "", url);
    }

    @Test
    public void testBinaryRestrictions() {
        Field field = schema.getField("binaryConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(2, constraints.size());
        assertTrue(constraints.contains(new TypeConstraint(BinaryType.INSTANCE)));
        assertTrue(constraints.contains(NotNullConstraint.get()));
    }

    @Test
    public void testBooleanRestrictions() {
        Field field = schema.getField("booleanConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(2, constraints.size());
        assertTrue(constraints.contains(new TypeConstraint(BooleanType.INSTANCE)));
        assertTrue(constraints.contains(NotNullConstraint.get()));
    }

    @Test
    public void testStringRestrictions() {
        Field field = schema.getField("stringConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(5, constraints.size());
        assertTrue(constraints.contains(NotNullConstraint.get()));
        assertTrue(constraints.contains(new TypeConstraint(StringType.INSTANCE)));
        assertTrue(constraints.contains(new PatternConstraint("[^3]*")));
        assertTrue(constraints.contains(new LengthConstraint(2, 4)));
        assertTrue(constraints.contains(new EnumConstraint(Arrays.asList("1", "1234", "22", "333", "4444", "55555"))));
    }

    @Test
    public void testDoubleRestrictions() {
        Field field = schema.getField("doubleConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(5, constraints.size());
        assertTrue(constraints.contains(new TypeConstraint(DoubleType.INSTANCE)));
        assertTrue(constraints.contains(NotNullConstraint.get()));
        assertTrue(constraints.contains(new PatternConstraint("[^6]*")));
        assertTrue(constraints.contains(new EnumConstraint(Arrays.asList("2014.15", "2015.1555", "2016.15", "2017.15",
                "2017.15555", "2017.155555", "2018.15"))));
        assertTrue(constraints.contains(new NumericIntervalConstraint(2015.001d, true, 2017.999d, true)));
    }

    @Test
    public void testDecimalRestrictions() {
        Field field = schema.getField("decimalConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(5, constraints.size());
        assertTrue(constraints.contains(new TypeConstraint(DecimalType.INSTANCE)));
        assertTrue(constraints.contains(NotNullConstraint.get()));
        assertTrue(constraints.contains(new PatternConstraint("[^6]*")));
        assertTrue(constraints.contains(new EnumConstraint(Arrays.asList("2014.15", "2015.1555", "2016.15", "2017.15",
                "2017.15555", "2017.155555", "2018.15"))));
        assertTrue(constraints.contains(new NumericIntervalConstraint(2015.001d, true, 2017.999d, true)));
    }

    @Test
    public void testDateRestrictions() {
        Field field = schema.getField("dateConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(3, constraints.size());
        assertTrue(constraints.contains(new TypeConstraint(DateType.INSTANCE)));
        assertTrue(constraints.contains(NotNullConstraint.get()));
        assertTrue(constraints.contains(new DateIntervalConstraint(Instant.parse("2015-01-01T00:00:00Z"), true,
                Instant.parse("2016-12-31T00:00:00Z"), true)));
    }

    @Test
    public void testMaxLength() {
        Field field = schema.getField("stringConstraints");
        Set<Constraint> constraints = field.getConstraints();
        assertTrue(constraints.contains(new LengthConstraint(2, 4)));
    }

    @Test
    public void testElementNillable() {
        doTestNillableCase("elementShouldNotBeNullButCanBeNull1", true);
        doTestNillableCase("elementShouldNotBeNullButCanBeNull2", true);
        doTestNillableCase("elementShouldNotBeNullButCanBeNull3", true);
        doTestNillableCase("elementCanBeNull1", true);
        doTestNillableCase("elementCanBeNull2", true);
        doTestNillableCase("elementCanBeNull3", true);
        doTestNillableCase("elementCanBeNull4", true);
        doTestNillableCase("elementCannotBeNull1", false);
        doTestNillableCase("elementCannotBeNull2", false);
    }

    private void doTestNillableCase(String name, boolean expected) {
        Field field = schema.getField(name);
        assertEquals(!expected, field.getConstraints().contains(NotNullConstraint.get()));
        assertEquals(expected, field.isNullable());
    }

    private void doTestNillableCaseOnComplexTypeAttribute(String name, boolean expected) {
        Field field = schema.getField(name);
        Field attr = ((ComplexType) field.getType()).getField("attr");
        assertEquals(!expected, attr.getConstraints().contains(NotNullConstraint.get()));
        assertEquals(expected, attr.isNullable());
    }

    @Test
    public void testSimpleListWithConstraints() {
        Field subList = schema.getField("simpleListWithConstraints");
        assertNotNull(subList);
        Type type = ((ListType) subList.getType()).getFieldType();
        Set<Constraint> constraints = type.getConstraints();
        assertEquals(2, constraints.size());
    }

    @Test
    public void testListOfListRestriction() {
        ListType listType = (ListType) schema.getField("listOfLists").getType();
        ComplexType itemType = (ComplexType) listType.getFieldType();
        ListType subListType = (ListType) itemType.getField("stringListItem").getType();
        Set<Constraint> constraints = subListType.getFieldType().getConstraints();
        assertEquals(2, constraints.size());
    }

}
