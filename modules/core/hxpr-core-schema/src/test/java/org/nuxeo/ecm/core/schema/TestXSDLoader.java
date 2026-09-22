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
package org.nuxeo.ecm.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.SimpleTypeImpl;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.ConstraintUtils;
import org.nuxeo.ecm.core.schema.types.constraints.EnumConstraint;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import com.hyland.openarch.runtime.test.ResourceHelper;

public class TestXSDLoader {

    private SchemaManagerImpl schemaManager;

    private XSDLoader reader;

    @BeforeEach
    public void setUp() throws Exception {
        schemaManager = new SchemaManagerImpl();
        reader = new XSDLoader(schemaManager);
    }

    @Test
    public void testXSDReader() throws Exception {
        URL url = ResourceHelper.getResource("schema/schema.xsd");

        reader.loadSchema("MySchema", "", url);
        // Collection<Schema> schemas = typeMgr.getSchemas();
        // do not check schemas size - this is dynamic
        // assertEquals(4, schemas.size()); // file, common, MySchema
        Schema schema = schemaManager.getSchema("MySchema");
        assertEquals("MySchema", schema.getName());
        assertEquals("", schema.getPrefix());

        List<Field> fields = new ArrayList<>(schema.getFields());
        assertEquals(5, fields.size());

        // sort to assert them
        fields.sort(Comparator.comparing(f -> f.getName()));

        Iterator<Field> it = fields.iterator();
        Field field;
        field = it.next();
        assertEquals("data", field.getName());
        assertEquals("MySchema#newsml", field.getType().getName());

        field = it.next();
        assertEquals("description", field.getName());
        assertEquals("string", field.getType().getName());

        field = it.next();
        assertEquals("numericId", field.getName());
        assertEquals("long", field.getType().getName());

        field = it.next();
        assertEquals("person", field.getName());
        assertEquals("MySchema#personInfo", field.getType().getName());

        field = it.next();
        assertEquals("title", field.getName());
        assertEquals("MySchema#myString", field.getType().getName());
    }

    @Test
    public void testSequence() throws Exception {
        URL url = ResourceHelper.getResource("schema/testList.xsd");
        assertNotNull(url);
        Schema schema = reader.loadSchema("testList", "", url);
        Field field = schema.getField("participants");
        ListType type = (ListType) field.getType();
        assertEquals("testList#stringSequence", type.getName());

        String[] defaultValue = (String[]) field.getDefaultValue();
        assertEquals(Arrays.asList("titi", "toto", "tata"), Arrays.asList(defaultValue));
    }

    @Test
    public void testList() throws Exception {
        URL url = ResourceHelper.getResource("schema/testList.xsd");
        assertNotNull(url);

        Schema schema = reader.loadSchema("testList", "", url);
        Field field = schema.getField("strings");
        ListType type = (ListType) field.getType();
        assertEquals("testList#stringList", type.getName());

        String[] defaultValue = (String[]) field.getDefaultValue();
        assertEquals(Arrays.asList("titi", "toto", "tata"), Arrays.asList(defaultValue));
    }

    @Test
    public void testComplexSubList() throws Exception {
        URL url = ResourceHelper.getResource("schema/testList.xsd");
        assertNotNull(url);

        Schema schema = reader.loadSchema("testList", "", url);

        Field field = schema.getField("cplxWithSubList");
        assertTrue(field.getType().isComplexType());

        ComplexType ct = (ComplexType) field.getType();
        assertEquals(4, ct.getFieldsCount());

        Field fieldA = ct.getField("fieldA");
        assertNotNull(fieldA);
        assertEquals("string", fieldA.getType().getName());
        Field fieldB = ct.getField("fieldB");
        assertNotNull(fieldB);
        assertEquals("string", fieldB.getType().getName());

        Field items = ct.getField("items");
        assertNotNull(items);
        assertTrue(items.getType().isListType());

        Field moreitems = ct.getField("moreitems");
        assertNotNull(moreitems);
        assertTrue(moreitems.getType().isListType());

    }

    @Test
    public void testAdvancedTyping() throws Exception {
        URL url = ResourceHelper.getResource("schema/advancedSchema.xsd");
        assertNotNull(url);
        Schema schema = reader.loadSchema("advancedSchema", "", url);

        Field durField = schema.getField("dur");
        assertEquals("string", durField.getType().getName());

        Field anyField = schema.getField("any");
        assertEquals("any", anyField.getType().getName());

        Field extField = schema.getField("ext");
        Type type = extField.getType();
        assertTrue(type.isComplexType());
    }

    @Test
    public void testInlineTyping() throws Exception {
        URL url = ResourceHelper.getResource("schema/advancedSchema.xsd");
        assertNotNull(url);
        Schema schema = reader.loadSchema("advancedSchema", "", url);

        // check regular definition of simple type with restriction Field
        Field simpleField = schema.getField("bureau");
        Type simpleFieldType = simpleField.getType();
        assertEquals("advancedSchema#BureauType", simpleFieldType.getName());
        Type superType = simpleFieldType.getSuperType();
        assertEquals("string", superType.getName());
        assertTrue(simpleFieldType instanceof SimpleTypeImpl);
        SimpleTypeImpl sSimpleFieldType = (SimpleTypeImpl) simpleFieldType;
        Set<Constraint> constraints = sSimpleFieldType.getConstraints();
        assertNotNull(constraints);
        EnumConstraint ec = ConstraintUtils.getConstraint(constraints, EnumConstraint.class);
        assertNotNull(ec);
        assertTrue(ec.getPossibleValues().contains("EFU"));

        // check inline definition of simple type with restriction Field
        Field inlineField = schema.getField("inlineBureau");
        assertNotNull(inlineField);
        Type inlineFieldType = inlineField.getType();
        assertNotNull(inlineFieldType);
        superType = inlineFieldType.getSuperType();
        assertEquals("string", superType.getName());
        assertTrue(inlineFieldType instanceof SimpleTypeImpl);
        SimpleTypeImpl sInlineFieldType = (SimpleTypeImpl) inlineFieldType;
        constraints = sInlineFieldType.getConstraints();
        assertNotNull(constraints);
        ec = ConstraintUtils.getConstraint(constraints, EnumConstraint.class);
        assertNotNull(ec);
        assertTrue(ec.getPossibleValues().contains("EFU"));

    }

    @Test
    public void testSchema12() throws Exception {

        URL url = ResourceHelper.getResource("schema/schema12.xsd");
        assertNotNull(url);
        Schema schema = reader.loadSchema("schema12", "", url);

        Field field1 = schema.getField("field1");
        assertNotNull(field1);

        assertTrue(field1.getType().isComplexType());

        ComplexType ct = (ComplexType) field1.getType();
        assertEquals(2, ct.getFieldsCount());

        assertTrue(ct.hasField("label"));
        assertTrue(ct.hasField("roles"));

        assertTrue(ct.getField("roles").getType().isListType());
        assertFalse(ct.getField("label").getType().isListType());

        Field field2 = schema.getField("self");
        assertNotNull(field2);
        assertEquals("string", field2.getType().getName());

    }

    @Test
    public void testClassifier() throws Exception {

        URL url = ResourceHelper.getResource("schema/test-classifier.xsd");
        assertNotNull(url);
        Schema schema = reader.loadSchema("classifier", "", url);

        assertNotNull(schema);

        Field field1 = schema.getField("integerBins");
        assertNotNull(field1);

        Field field2 = schema.getField("stringBins");
        assertNotNull(field2);

        // integerBins
        assertTrue(field1.getType().isListType());
        ListType ltype = (ListType) field1.getType();

        Type integerBinType = ltype.getField().getType();

        assertTrue(integerBinType.isComplexType());
        assertEquals("classifier#integerBin", integerBinType.getName());

        Field matchingValuesField = ((ComplexType) integerBinType).getField("matchingValues");
        assertNotNull(matchingValuesField);

        assertEquals("classifier#integerValues", matchingValuesField.getType().getName());

        assertTrue(matchingValuesField.getType().isListType());

        ListType integerValuesType = (ListType) matchingValuesField.getType();

        assertTrue(integerValuesType.getField().getType().isSimpleType());
        assertEquals("long", integerValuesType.getField().getType().getName());

    }

    @Test
    public void testXSExtension() throws Exception {

        URL url = ResourceHelper.getResource("schema/testExtension.xsd");
        assertNotNull(url);
        Schema schema = reader.loadSchema("extension", "", url);

        assertNotNull(schema);

        Field field = schema.getField("employee");
        assertNotNull(field);

        assertTrue(field.getType().isComplexType());

        ComplexType ct = (ComplexType) field.getType();

        // additional fields
        assertTrue(ct.hasField("address"));
        assertTrue(ct.hasField("city"));
        assertTrue(ct.hasField("country"));

        // inherited fields
        assertTrue(ct.hasField("firstname"));
        assertTrue(ct.hasField("lastname"));

        // super parent inherited fields
        assertTrue(ct.hasField("race"));

        assertEquals(6, ct.getFieldsCount());
    }

    /**
     * NXP-24660
     */
    @Test
    public void testComplexWithSameInnerField() throws Exception {

        URL url = ResourceHelper.getResource("schema/testComplexWithSameInnerField.xsd");
        assertNotNull(url);
        Schema schema = reader.loadSchema("extension", "prefix", url);

        assertNotNull(schema);

        // check complex fields
        assertTrue(schema.hasField("prefix_complexWithString"));
        assertTrue(schema.hasField("prefix_complexWithInteger"));

        // check complex types
        checkComplexTypes(schema, "extension#t_complexWithString", StringType.class);
        checkComplexTypes(schema, "extension#t_complexWithInteger", LongType.class);
    }

    private void checkComplexTypes(Schema schema, String typeName, Class<? extends Type> expectedInnerType) {
        Type type = schemaManager.getType(typeName);
        assertNotNull(type);
        assertTrue(type.isComplexType());
        ComplexType complexType = (ComplexType) type;
        assertEquals(1, complexType.getFieldsCount());

        // check inner field/type
        Field valueField = complexType.getFields().iterator().next();
        assertNotNull(valueField);
        Type valueType = valueField.getType();
        assertTrue(expectedInnerType.isInstance(valueType.getSuperType()),
                typeName + " inner value is a " + valueType.getSuperType().getClass());
    }

    /**
     * NXP-24660
     */
    @Test
    public void testListWithSameInnerField() throws Exception {

        URL url = ResourceHelper.getResource("schema/testListWithSameInnerField.xsd");
        assertNotNull(url);
        Schema schema = reader.loadSchema("extension", "prefix", url);

        assertNotNull(schema);

        // check complex fields
        assertTrue(schema.hasField("prefix_listWithString"));
        assertTrue(schema.hasField("prefix_listWithInteger"));

        // check complex types
        checkListTypes(schema, "extension#t_listWithString", StringType.class);
        checkListTypes(schema, "extension#t_listWithInteger", LongType.class);
    }

    private void checkListTypes(Schema schema, String typeName, Class<? extends Type> expectedInnerType) {
        Type type = schemaManager.getType(typeName);
        assertNotNull(type);
        assertTrue(type.isListType());
        ListType listType = (ListType) type;

        // check inner field/type
        Field valueField = listType.getField();
        assertNotNull(valueField);
        Type valueType = valueField.getType();
        assertTrue(expectedInnerType.isInstance(valueType.getSuperType()),
                typeName + " inner value is a " + valueType.getSuperType().getClass());
    }

    /**
     * NXP-24660
     */
    @Test
    public void testComplexWithSameInnerListAndField() throws Exception {

        URL url = ResourceHelper.getResource("schema/testComplexWithSameInnerListAndField.xsd");
        assertNotNull(url);
        schemaManager.types.clear();
        Schema schema = reader.loadSchema("extension", "prefix", url);

        assertNotNull(schema);

        // check complex fields
        assertTrue(schema.hasField("prefix_complexWithString"));
        assertTrue(schema.hasField("prefix_complexWithInteger"));

        // check complex types
        checkComplexWithListAndFieldTypes(schema, "extension#t_complexWithString", StringType.class);
        checkComplexWithListAndFieldTypes(schema, "extension#t_complexWithInteger", LongType.class);
    }

    private void checkComplexWithListAndFieldTypes(Schema schema, String typeName,
            Class<? extends Type> expectedInnerType) {
        Type type = schemaManager.getType(typeName);
        assertNotNull(type);
        assertTrue(type.isComplexType());
        ComplexType complexType = (ComplexType) type;
        assertEquals(2, complexType.getFieldsCount());

        List<Field> fields = new ArrayList<>(complexType.getFields());
        fields.sort(Comparator.comparing(f -> f.getName()));

        // check inner field/type
        Field listField = fields.get(0);
        assertNotNull(listField);
        Type listType = listField.getType();
        assertTrue(listType instanceof ListType);
        Type listItemType = ((ListType) listType).getFieldType();
        assertTrue(expectedInnerType.isInstance(listItemType.getSuperType()),
                typeName + " inner value is a " + listItemType.getSuperType().getClass());

        Field valueField = fields.get(1);
        assertNotNull(valueField);
        Type valueType = valueField.getType();
        assertTrue(expectedInnerType.isInstance(valueType.getSuperType()),
                typeName + " inner value is a " + valueType.getSuperType().getClass());
    }

    @Test
    public void testDuplicateProperty() throws Exception {
        URL url = ResourceHelper.getResource("schema/schema3.xsd");
        URL url2 = ResourceHelper.getResource("schema/schema3b.xsd");
        reader.loadSchema("s1", "pref", url);
        try {
            // read another schema but with same prefix and field, to generate a field name collision
            reader.loadSchema("s2", "pref", url2);
            fail("should have detected duplicate");
        } catch (TypeException e) {
            assertEquals("Duplicate property \"pref_validator\" in schemas \"s1\" (string) and \"s2\" (long).", e.getMessage());
        }
    }

    @Test
    public void testInvalidManagedBoolean() {
        URL url = ResourceHelper.getResource("schema/invalid-managed-boolean.xsd");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> reader.loadSchema("system", "sys", url));
        assertEquals("Invalid boolean value: TRUE", e.getMessage());
    }

}
