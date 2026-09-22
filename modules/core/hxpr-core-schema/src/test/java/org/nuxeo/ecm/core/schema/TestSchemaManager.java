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

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.schema.types.FieldCharacteristics.Representation.INDEX;
import static org.nuxeo.ecm.core.schema.types.FieldCharacteristics.Representation.REST;
import static org.nuxeo.ecm.core.schema.types.FieldCharacteristics.Representation.STORE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core.schema")
public class TestSchemaManager {

    @Inject
    public TypeService typeService;

    public SchemaManager schemaManager;

    @BeforeEach
    public void setUp() {
        schemaManager = typeService.getDefaultSchemaManager();
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
    public void testIsSecurityProperty() {
        // non-system properties are not security properties
        assertFalse(schemaManager.isSecurityProperty("dc_title"));
        assertFalse(schemaManager.isSecurityProperty("mydc_source"));
        // non-security system properties, across the three system prefixes
        assertFalse(schemaManager.isSecurityProperty("sys_title"));
        assertFalse(schemaManager.isSecurityProperty("sysgov_isRecord"));
        assertFalse(schemaManager.isSecurityProperty("sysver_isVersion"));
        // system properties that only the former prefix rule used to classify
        assertFalse(schemaManager.isSecurityProperty("sys_ancestorIds"));
        assertFalse(schemaManager.isSecurityProperty("sys_mixinTypes"));
        assertFalse(schemaManager.isSecurityProperty("sysver_versionSeriesId"));
        // security properties
        assertTrue(schemaManager.isSecurityProperty("sys_acl"));
        assertTrue(schemaManager.isSecurityProperty("sys_acl/*/principal"));
        assertTrue(schemaManager.isSecurityProperty("sys_effectiveAcl"));
        assertTrue(schemaManager.isSecurityProperty("sys_racl"));
        assertTrue(schemaManager.isSecurityProperty("sys_secpol"));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
    public void testFalseStoredAsNullBooleansAreUsableInAQuery() {
        Stream.of("sys_isFolderish", "sys_isTrashed", "sysgov_hasLegalHold", "sysgov_isRecord", "sysver_isCheckedIn",
                "sysver_isVersion")
              .filter(schemaManager::isFalseStoredAsNull)
              .forEach(key -> assertFalse(schemaManager.isSecurityProperty(key), key));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
    public void testIsFalseStoredAsNullBoolean() {
        assertTrue(schemaManager.isFalseStoredAsNull("sys_isFolderish"));
        assertEquals(Set.of(STORE), schemaManager.getField("sys_isFolderish").getCharacteristics().falseAsNull());
        assertEquals(Set.of(STORE, REST, INDEX),
                schemaManager.getField("sys_isTrashed").getCharacteristics().falseAsNull());
        assertTrue(schemaManager.isFalseStoredAsNull("sysgov_hasLegalHold"));
        assertFalse(schemaManager.isFalseStoredAsNull("sys_title"));
        assertFalse(schemaManager.isFalseStoredAsNull("dc_title"));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
    public void testNoAutoCheckout() {
        assertTrue(schemaManager.getField("sys_acl").getCharacteristics().noAutoCheckout());
        assertTrue(schemaManager.getField("sysgov_isRecord").getCharacteristics().noAutoCheckout());
        assertFalse(schemaManager.getField("sys_title").getCharacteristics().noAutoCheckout());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
    public void testPropertyPermissions() {
        assertEquals("ManageSecurity", schemaManager.getField("sys_acl").getCharacteristics().readPermission());
        assertEquals("ManageSecurity", schemaManager.getField("sys_acl").getCharacteristics().writePermission());
        assertEquals("ManageSecurity", schemaManager.getField("sys_racl").getCharacteristics().readPermission());
        assertEquals("ManageRetention",
                schemaManager.getField("sysgov_isRecord").getCharacteristics().writePermission());
        assertNull(schemaManager.getField("sys_title").getCharacteristics().readPermission());
        assertNull(schemaManager.getField("sys_title").getCharacteristics().writePermission());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
    public void testNotOutput() {
        assertEquals(Set.of(INDEX),
                schemaManager.getField("sys_acl").getCharacteristics().notOutput());
    }

    @Test
    public void testTrivialTypeManager() {
        DocumentType[] documentTypes = schemaManager.getDocumentTypes();
        assertNotNull(documentTypes);
        assertEquals(1, documentTypes.length);
        assertEquals(SYS_CONTENT, documentTypes[0].getName());

        Schema[] schemas = schemaManager.getSchemas();
        assertNotNull(schemas);
        assertEquals(0, schemas.length);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-facet.xml")
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-facet-derived.xml")
    public void testFacetsCache() throws Exception {
        assertNotNull(schemaManager.getDocumentType("Parent"));
        assertEquals(3, schemaManager.getDocumentTypes().length);

        assertEquals("Parent", schemaManager.getDocumentType("Parent").getName());
        Set<String> tff = schemaManager.getDocumentTypeNamesForMixin("parent1");
        assertNotNull(tff);
        assertEquals(2, tff.size());
        assertTrue(tff.contains("Parent"));

        tff = schemaManager.getDocumentTypeNamesForMixin("parent1");
        assertNotNull(tff);
        assertEquals(2, tff.size());
        assertTrue(tff.contains("Parent"));
        assertTrue(tff.contains("Child"));
        Set<String> tff2 = schemaManager.getDocumentTypeNamesForMixin("parent2");
        assertEquals(tff, tff2);

        tff = schemaManager.getDocumentTypeNamesForMixin("child");
        assertNotNull(tff);
        assertEquals(1, tff.size());
        assertTrue(tff.contains("Child"));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-type-inheritance.xml")
    public void testInheritanceCache() {
        checkInheritanceCache();
    }


    private void checkInheritanceCache() {
        check_getDocumentTypeNamesExtending();
        check_getDocumentTypeNamesExtendedBy();
    }

    private void check_getDocumentTypeNamesExtending() {
        Set<String> types;

        types = schemaManager.getDocumentTypeNamesExtending("Parent");
        assertNotNull(types);
        assertEquals(2, types.size());
        assertTrue(types.contains("Parent"));
        assertTrue(types.contains("Child"));

        types = schemaManager.getDocumentTypeNamesExtending(SYS_CONTENT);
        assertNotNull(types);
        assertEquals(4, types.size());

        types = schemaManager.getDocumentTypeNamesExtending("Child");
        assertNotNull(types);
        assertEquals(1, types.size());
        assertTrue(types.contains("Child"));

        types = schemaManager.getDocumentTypeNamesExtending("TopLevel");
        assertNotNull(types);
        assertEquals(1, types.size());
        assertTrue(types.contains("TopLevel"));

        types = schemaManager.getDocumentTypeNamesExtending("Unknown");
        assertNull(types);
    }

    private void check_getDocumentTypeNamesExtendedBy() {
        Set<String> types;

        types = schemaManager.getDocumentTypeNamesExtendedBy("Parent");
        assertEquals(Set.of(SYS_CONTENT, "Parent"), types);

        types = schemaManager.getDocumentTypeNamesExtendedBy(SYS_CONTENT);
        assertEquals(Set.of(SYS_CONTENT), types);

        types = schemaManager.getDocumentTypeNamesExtendedBy("Child");
        assertEquals(Set.of(SYS_CONTENT, "Parent", "Child"), types);

        types = schemaManager.getDocumentTypeNamesExtendedBy("TopLevel");
        assertEquals(Set.of(SYS_CONTENT, "TopLevel"), types);

        types = schemaManager.getDocumentTypeNamesExtendedBy("Unknown");
        assertNull(types);
    }

    /*
     * Check that registering a child type before the parent works.
     */
    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-facet-reversed.xml")
    public void testFacetsCacheReversedRegistration() {
        Set<String> tff = schemaManager.getDocumentTypeNamesForMixin("parent1");
        assertNotNull(tff);
        assertEquals(2, tff.size());
        assertTrue(tff.contains("Parent"));
        assertTrue(tff.contains("Child"));
        Set<String> tff2 = schemaManager.getDocumentTypeNamesForMixin("parent2");
        assertEquals(tff, tff2);

        tff = schemaManager.getDocumentTypeNamesForMixin("child");
        assertNotNull(tff);
        assertEquals(1, tff.size());
        assertTrue(tff.contains("Child"));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-type-inheritance-reversed.xml")
    public void testInheritanceCacheReversedRegistration() {
        checkInheritanceCache();
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/CoreTestExtensions.xml")
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-change-doctype.xml")
    public void testMerge() throws Exception {

        DocumentType t = schemaManager.getDocumentType("myDoc3");
        assertEquals(Set.of("schema1", "common"), t.getSchemaNames());
        assertEquals(Set.of(), t.getMixins());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-supertype-loop.xml")
    public void testSupertypeLoop() {
        DocumentType t = schemaManager.getDocumentType("someDocInLoop");
        DocumentType t2 = schemaManager.getDocumentType("someDocInLoop2");
        assertEquals("someDocInLoop2", t.getSuperType().getName());
        assertNull(t2.getSuperType());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-missing-supertype.xml")
    public void testMissingSupertype() {
        DocumentType t = schemaManager.getDocumentType("someDoc");
        DocumentType t2 = schemaManager.getDocumentType("someDoc2");
        assertNull(t.getSuperType());
        assertEquals("someDoc", t2.getSuperType().getName());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-facet-missing-schema.xml")
    public void testFacetMissingSchema() {
        CompositeType f = schemaManager.getMixin("someFacet");
        assertEquals(Set.of("common"), f.getSchemaNames());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-facet-per-document.xml")
    public void testFacetNoPerDocumentQuery() throws Exception {
        assertTrue(schemaManager.getNoPerDocumentQueryMixins().contains("someFacet"));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-facet-disabled.xml")
    public void testFacetDisabled() {
        CompositeType f = schemaManager.getMixin("someFacet");
        assertNull(f);
        DocumentType t = schemaManager.getDocumentType("myDoc");
        assertNotNull(t);
        assertEquals(Set.of(), t.getMixins());
        assertEquals(Set.of(), t.getSchemaNames());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-schema-disabled.xml")
    public void testSchemaDisabled() {
        Schema s = schemaManager.getSchema("someSchema");
        assertNull(s);
        CompositeType f = schemaManager.getMixin("someFacet");
        assertEquals(Set.of("common"), f.getSchemaNames());
        DocumentType t = schemaManager.getDocumentType("myDoc");
        assertEquals(Set.of("common"), t.getSchemaNames());
        DocumentType t2 = schemaManager.getDocumentType("myDoc2");
        assertEquals(Set.of("common"), t2.getSchemaNames());
    }

    protected static Set<String> schemaNames(List<Schema> schemas) {
        return schemas.stream().map(Schema::getName).collect(toSet());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/CoreTestExtensions.xml")
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-merge-doctype.xml")
    public void testMergeDocumentType() throws Exception {

        DocumentType t = schemaManager.getDocumentType("myDoc");
        assertEquals(Set.of("schema2"), t.getSchemaNames());
        assertEquals(Set.of("viewable", "writable", "NewFacet"), t.getMixins());

        t = schemaManager.getDocumentType("myDoc2");
        assertEquals(Set.of("schema1", "schema2", "newschema", "newschema2"), t.getSchemaNames());
        assertEquals(Set.of("myfacet", "NewFacet2"), t.getMixins());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/testSchemaWithImportInclude.xml")
    public void testDeployWithIncludeAndImport() {

        Schema schema = schemaManager.getSchema("schemaWithIncludeAndImport");
        assertNotNull(schema);

        assertEquals("schemaWithIncludeAndImport#typeA", schema.getField("sii_field3").getType().getName());
        assertEquals("schemaWithIncludeAndImport#typeB", schema.getField("sii_field4").getType().getName());

        assertTrue(schema.getField("sii_field4").getType().isComplexType());
        assertTrue(schema.getField("sii_field3").getType().isComplexType());

        assertEquals(3, ((ComplexType) schema.getField("sii_field3").getType()).getFieldsCount());
        assertEquals(2, ((ComplexType) schema.getField("sii_field4").getType()).getFieldsCount());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/test-extends-append-doctypes.xml")
    public void testExtendsAppendTypes() {
        // test appending types which extend another document
        DocumentType t = schemaManager.getDocumentType("MyMergeableFolder");
        assertEquals(t.getMixins(), Set.of("specdoc", "specdoc2", "specdoc3", "specdoc4"));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.schema.tests:OSGI-INF/CoreTestExtensions.xml")
    public void testGetFacet() {
        CompositeType component = schemaManager.getMixin("fakeFacet1");
        assertNotNull(component, String.format("Facet %s should exist", "fakeFacet1"));
        assertEquals(1, component.getSchemas().size());
        assertEquals(Set.of("facetSchema1"), component.getSchemaNames());
    }

}
