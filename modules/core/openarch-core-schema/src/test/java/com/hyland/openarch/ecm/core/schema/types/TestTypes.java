/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.schema.types.primitives.AnyType;
import com.hyland.openarch.ecm.core.schema.types.primitives.BinaryType;
import com.hyland.openarch.ecm.core.schema.types.primitives.BooleanType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DateType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DecimalType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DoubleType;
import com.hyland.openarch.ecm.core.schema.types.primitives.LongType;
import com.hyland.openarch.ecm.core.schema.types.primitives.StringType;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.schema.types.primitives.*;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core.schema")
public class TestTypes {

    @Inject
    protected TypeService typeService;

    protected SchemaManager schemaManager;

    @BeforeEach
    public void setUp() {
        schemaManager = typeService.getDefaultSchemaManager();
    }

    // Primitive types
    @Test
    public void testStringType() throws TypeException {
        SimpleType type = StringType.INSTANCE;

        assertEquals("string", type.getName());
        assertTrue(type.isSimpleType());
        assertTrue(type.isPrimitive());
        assertFalse(type.isComplexType());

        assertNull(type.getSuperType());

        assertFalse(type.validate(0));
        assertTrue(type.validate(""));
        assertFalse(type.validate(true));

        // TODO: test convert method
    }

    @SuppressWarnings({ "SimplifiableJUnitAssertion", "AssertEqualsBetweenInconvertibleTypes" })
    @Test
    public void testBooleanType() throws TypeException {
        SimpleType type = BooleanType.INSTANCE;

        assertEquals("boolean", type.getName());
        assertTrue(type.isSimpleType());
        assertTrue(type.isPrimitive());
        assertFalse(type.isComplexType());

        assertNull(type.getSuperType());

        // Validation tests
        assertTrue(type.validate(0));
        assertFalse(type.validate(""));
        assertFalse(type.validate("abc"));
        assertTrue(type.validate(true));
        assertTrue(type.validate(false));

        // Conversion tests
        assertNull(type.decode(""));
        assertEquals(true, type.convert(true));
        assertEquals(false, type.convert(false));
        assertEquals(true, type.convert("true"));
        assertEquals(false, type.convert("false"));
        assertEquals(true, type.convert(1));
        assertEquals(false, type.convert(0));
        assertEquals(true, type.decode("true"));

        // Encode tests
        assertEquals("true", type.encode(true));
        assertEquals("false", type.encode(false));
        assertEquals("true", type.encode(1));
        assertEquals("false", type.encode(0));
        assertEquals("", type.encode(null));
    }

    private String convertType(Object value) throws TypeException {
        SimpleType type = BinaryType.INSTANCE;
        Charset charset = StandardCharsets.UTF_8;
        if (value instanceof String) {
            return new String(((ByteArrayInputStream) type.convert(value)).readAllBytes(), charset);
        } else if (value instanceof byte[]) {
            return new String(((ByteArrayInputStream) type.convert(value)).readAllBytes(), charset);
        }
        return null;
    }

    @Test
    public void testBinaryType() throws TypeException {
        SimpleType type = BinaryType.INSTANCE;
        Charset charset = StandardCharsets.UTF_8;

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(new byte[] { 97, 98, 99 });
        String expected = new String(byteArrayInputStream.readAllBytes(), charset);
        assertEquals(expected, convertType("abc"));
        assertEquals(expected, convertType(new byte[] {97, 98, 99}));
        assertNull(convertType(new Object()));

        assertNull(type.decode(""));
        assertEquals(expected, new String(((ByteArrayInputStream) type.decode("abc")).readAllBytes(), charset));

    }

    @Test
    public void testDoubleType() throws TypeException {
        SimpleType type = DoubleType.INSTANCE;

        assertEquals("double", type.getName());
        assertTrue(type.isSimpleType());
        assertTrue(type.isPrimitive());
        assertFalse(type.isComplexType());

        assertNull(type.getSuperType());

        // Validation tests
        assertTrue(type.validate(0));
        assertTrue(type.validate(0.0));
        assertFalse(type.validate(true));
        assertFalse(type.validate(""));
        assertFalse(type.validate("abc"));

        // Conversion tests
        assertNull(type.decode(""));
        assertEquals(0.0, type.convert(0));
        assertEquals(0.5, type.convert(0.5));
        assertEquals(3.14, type.convert("3.14"));
        assertEquals(0.0, type.decode("0.0"));
        assertEquals(3.14, type.decode("3.14"));
    }

    @Test
    public void testLongType() throws TypeException {
        SimpleType type = LongType.INSTANCE;

        assertEquals("long", type.getName());
        assertTrue(type.isSimpleType());
        assertTrue(type.isPrimitive());
        assertFalse(type.isComplexType());

        assertNull(type.getSuperType());

        // Validation tests
        assertTrue(type.validate(0));
        assertFalse(type.validate(""));
        assertFalse(type.validate(true));

        // Conversion tests
        assertNull(type.decode(""));
        assertEquals(0L, type.convert(0));
        assertEquals(0L, type.convert("0"));
        assertEquals(0L, type.convert(0.5));
        assertEquals(0L, type.decode("0"));
    }

    @Test
    public void testDateType() throws TypeException {
        SimpleType type = DateType.INSTANCE;

        assertEquals("date", type.getName());
        assertTrue(type.isSimpleType());
        assertTrue(type.isPrimitive());
        assertFalse(type.isComplexType());

        assertNull(type.getSuperType());

        // Validation tests
        assertTrue(type.validate(Instant.now()));
        assertFalse(type.validate(new Date()));
        assertFalse(type.validate(Calendar.getInstance()));

        // Conversion tests
        assertNull(type.decode(""));
        Instant instant = Instant.parse("2022-07-28T00:00:00Z");
        assertEquals(instant, type.convert("2022-07-28"));
        assertEquals(instant, type.convert("2022-07-28T00:00:00Z"));
        assertEquals(instant, type.convert("2022-07-28T00:00:00+00:00"));
        assertEquals(instant, type.convert("2022-07-28T00:00:00+0000"));
        assertEquals(instant, type.convert("2022-07-28T00:00:00.000Z"));
        assertEquals(instant, type.convert("2022-07-28T00:00:00.000+00:00"));
        assertEquals(instant, type.convert("2022-07-28T00:00:00.000+0000"));
        assertEquals(instant, type.convert(instant));
        // same instant but with different timezone on input
        assertEquals(instant, type.convert("2022-07-28T01:00:00+01:00"));
        assertEquals(instant, type.convert("2022-07-28T01:00:00+0100"));
        assertEquals(instant, type.convert("2022-07-28T01:00:00.000+01:00"));
        assertEquals(instant, type.convert("2022-07-28T01:00:00.000+0100"));
        assertEquals(instant, type.convert("2022-07-27T23:00:00-01:00"));
        assertEquals(instant, type.convert("2022-07-27T23:00:00-0100"));
        assertEquals(instant, type.convert("2022-07-27T23:00:00.000-01:00"));
        assertEquals(instant, type.convert("2022-07-27T23:00:00.000-0100"));

        assertNull(type.encode(new Object()));
        assertEquals("2022-07-28T00:00:00Z", type.encode(instant));
    }

    @Test
    public void testDecimalType() throws TypeException {
        SimpleType type = DecimalType.INSTANCE;

        assertEquals("decimal", type.getName());
        assertTrue(type.isSimpleType());
        assertTrue(type.isPrimitive());
        assertFalse(type.isComplexType());

        assertNull(type.getSuperType());

        // Validation tests
        assertTrue(type.validate(0));
        assertTrue(type.validate(0.0));
        assertFalse(type.validate(true));
        assertFalse(type.validate(""));
        assertFalse(type.validate("abc"));

        // Conversion tests
        assertNull(type.decode(""));
        assertEquals(BigDecimal.ZERO, type.convert(0));
        assertEquals(new BigDecimal("0.5"), type.convert(0.5));
        assertEquals(new BigDecimal("3.14"), type.convert("3.14"));
        assertEquals(BigDecimal.ZERO, type.decode("0"));
        assertEquals(new BigDecimal("3.14"), type.decode("3.14"));
    }

    // Custom types
    @Test
    public void testListType() throws TypeException {
        ListType type = new ListTypeImpl("list type", StringType.INSTANCE);

        assertTrue(type.isListType());
        assertEquals("list type", type.getName());
        assertFalse(type.isCompositeType());
        assertFalse(type.isSimpleType());
        assertFalse(type.isComplexType());
        assertNull(type.getSuperType());
        assertEquals(StringType.INSTANCE, type.getFieldType());

        try {
            type.validate(0);
            fail();
        } catch (UnsupportedOperationException e) {
            // ok
        }

        // Conversion tests
        assertNull(type.decode(""));
        Object[] array = (Object[]) type.decode("foo bar");
        assertEquals(Arrays.asList("foo", "bar"), Arrays.asList(array));

        // TODO: add tests for collections once this is implemented
    }

    @Test
    public void testCompositeType() throws TypeException {
        CompositeTypeImpl type = new CompositeTypeImpl(null, "composite type", null, null);

        assertTrue(type.isCompositeType());
        assertEquals("composite type", type.getName());
        assertFalse(type.isSimpleType());
        // XXX: Not is CompositeType API. Why?
        // assertFalse(type.isPrimitive());
        assertFalse(type.isComplexType());
        assertNull(type.getSuperType());

        // Author schemas API
        assertFalse(type.hasSchema("inexisting schema"));
        assertTrue(type.getSchemas().isEmpty());

        // Author fields API
        assertTrue(type.getFields().isEmpty());

        try {
            type.validate(0);
            fail();
        } catch (UnsupportedOperationException e) {
            // ok
        }
    }

    @Test
    public void testAnyType() {
        Type type = AnyType.INSTANCE;

        assertEquals("any", type.getName());
        assertFalse(type.isSimpleType());
        assertFalse(type.isComplexType());

        assertNull(type.getSuperType());

        // Validation tests
        assertFalse(type.validate(0));
        assertFalse(type.validate(0.0));
        assertFalse(type.validate(""));
        assertFalse(type.validate(true));
        assertFalse(type.validate(Instant.now()));
        assertFalse(type.validate(BigDecimal.ZERO));
        assertFalse(type.validate(List.of()));

        assertTrue(type.validate(Map.of()));

        // Conversion tests
        assertNull(type.decode(""));
    }

    @Test
    public void testSchema() {
        String name = "name";
        String prefix = "prefix";
        Schema schema = new SchemaImpl(name, prefix);

        assertEquals(schema.getName(), name);
        assertEquals(schema.getPrefix(), prefix);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema:OSGI-INF/test-advanced-schema.xml")
    public void testFieldFromXpath() throws Exception {
        Field field = schemaManager.getField("tp_foo");
        assertNull(field);
        field = schemaManager.getField("dc_title");
        assertEquals("dc_title", field.getName());
        assertEquals("string", field.getType().getName());
        field = schemaManager.getField("dc_contributors");
        assertEquals("dc_contributors", field.getName());
        assertEquals("dublincore#contributorList", field.getType().getName());
        field = schemaManager.getField("tp_stringArray");
        assertEquals("tp_stringArray", field.getName());
        assertEquals("testProperties#stringArrayType", field.getType().getName());
        field = schemaManager.getField("tp_stringArray/*");
        assertEquals("item", field.getName());
        assertEquals("string", field.getType().getName());
        field = schemaManager.getField("tp_stringArray/1");
        assertEquals("item", field.getName());
        assertEquals("string", field.getType().getName());
        field = schemaManager.getField("tp_complexChain");
        assertEquals("tp_complexChain", field.getName());
        assertEquals("testProperties#complexChain", field.getType().getName());
        field = schemaManager.getField("tp_complexChain/stringItem");
        assertEquals("stringItem", field.getName());
        assertEquals("string", field.getType().getName());
        field = schemaManager.getField("tp_complexChain/complexItem");
        assertEquals("complexItem", field.getName());
        assertEquals("testProperties#complexType", field.getType().getName());
        field = schemaManager.getField("tp_complexList/*/stringItem");
        assertEquals("stringItem", field.getName());
        assertEquals("string", field.getType().getName());
        field = schemaManager.getField("tp_complexList/0/stringItem");
        assertEquals("stringItem", field.getName());
        assertEquals("string", field.getType().getName());

        field = schemaManager.getField("tp_listOfLists");
        assertEquals("tp_listOfLists", field.getName());
        assertEquals("testProperties#listOfListsType", field.getType().getName());
        field = schemaManager.getField("tp_listOfLists/*");
        assertEquals("testProperties#listOfListsItemType", field.getType().getName());
        field = schemaManager.getField("tp_listOfLists/*/stringItem");
        assertEquals("stringItem", field.getName());
        assertEquals("string", field.getType().getName());
        // XXX maybe this one should resolve to null (invalid item index in xpath) (?)
        field = schemaManager.getField("tp_listOfLists/stringItem");
        assertEquals("stringItem", field.getName());
        assertEquals("string", field.getType().getName());
        field = schemaManager.getField("tp_listOfLists/*/stringListItem/*");
        assertEquals("item", field.getName());
        assertEquals("string", field.getType().getName());
        field = schemaManager.getField("tp_listOfLists/*/stringListItem/0");
        assertEquals("item", field.getName());
        assertEquals("string", field.getType().getName());
        // XXX maybe this one should resolve to null (invalid item index in xpath) (?)
        field = schemaManager.getField("tp_listOfLists/stringListItem");
        assertEquals("stringListItem", field.getName());
        assertEquals("testProperties#stringList", field.getType().getName());
    }

}
