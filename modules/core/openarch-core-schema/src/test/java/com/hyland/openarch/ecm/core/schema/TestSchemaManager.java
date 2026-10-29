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
package com.hyland.openarch.ecm.core.schema;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.INDEX;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.REST;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.STORE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_CONTENT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.schema.types.ComplexType;
import com.hyland.openarch.ecm.core.schema.types.CompositeType;
import com.hyland.openarch.ecm.core.schema.types.Schema;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.common.xmap.Resource;
import com.hyland.openarch.runtime.RuntimeServiceException;
import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import com.hyland.openarch.ecm.core.schema.dto.ModelMapCodec;
import com.hyland.openarch.ecm.core.schema.validation.SchemaValidationException;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("com.hyland.openarch.ecm.core.schema")
@Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-root-type.xml")
public class TestSchemaManager {

    @Inject
    public TypeService typeService;

    public SchemaManager schemaManager;

    @BeforeEach
    public void setUp() {
        schemaManager = typeService.getDefaultSchemaManager();
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-model-composition.xml")
    public void testJsonComposition() {
        assertTrue(schemaManager.getDocumentType("Composed").hasSchema("composed"));
        assertEquals("long", schemaManager.getField("composed_extra").getType().getName());
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-blob-value-type.xml")
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-blob-schema.xml")
    public void testBlobKeysPaths() {
        assertEquals(List.of(List.of("fl_blob", "key")), schemaManager.getBlobKeysPaths());
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-blob-value-type.xml")
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-blob-schema.xml")
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-schema-manager.xml")
    public void testSchemasReferenceSharedBlobType() {
        assertSame(schemaManager.getType("blob"), schemaManager.getField("fl_blob").getType());
        assertSame(schemaManager.getType("blob"), schemaManager.getField("facetSchema1_anyContent").getType());
        assertEquals(4, ((ComplexType) schemaManager.getType("blob")).getFieldsCount());
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
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
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
    public void testFalseStoredAsNullBooleansAreUsableInAQuery() {
        Stream.of("sys_isFolderish", "sys_isTrashed", "sysgov_hasLegalHold", "sysgov_isRecord", "sysver_isCheckedIn",
                "sysver_isVersion")
              .filter(schemaManager::isFalseStoredAsNull)
              .forEach(key -> assertFalse(schemaManager.isSecurityProperty(key), key));
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
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
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
    public void testNoAutoCheckout() {
        assertTrue(schemaManager.getField("sys_acl").getCharacteristics().noAutoCheckout());
        assertTrue(schemaManager.getField("sysgov_isRecord").getCharacteristics().noAutoCheckout());
        assertFalse(schemaManager.getField("sys_title").getCharacteristics().noAutoCheckout());
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
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
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-system-characteristics.xml")
    public void testNotOutput() {
        assertEquals(Set.of(INDEX),
                schemaManager.getField("sys_acl").getCharacteristics().notOutput());
    }

    @Test
    public void testTrivialTypeManager() {
        assertNull(schemaManager.getDocumentType("Composed"));
        DocumentType[] documentTypes = schemaManager.getDocumentTypes();
        assertNotNull(documentTypes);
        assertEquals(1, documentTypes.length);
        assertEquals(SYS_CONTENT, documentTypes[0].getName());

        Schema[] schemas = schemaManager.getSchemas();
        assertNotNull(schemas);
        assertEquals(0, schemas.length);
    }

    @Test
    public void testUnavailableResolverIsOptionalOnlyDuringBootstrap() {
        Map<String, Object> map = new ObjectMapper().readValue("""
                {"schemas":{"custom":{"fields":{"custom_value":{"type":"string",
                 "constraints":{"resolver":{"name":"absentResolver"}}}}}}}
                """, Map.class);
        ContentModelDTO dto = ModelMapCodec.read(map);
        SchemaManager bootstrap = new SchemaManagerDTO(dto, new ArrayList<>(), false, true);
        assertTrue(bootstrap.getField("custom_value").getConstraints().isEmpty());
        assertThrows(SchemaValidationException.class, () -> new SchemaManagerDTO(dto, null, false));
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-facet.xml")
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-facet-derived.xml")
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
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-type-inheritance.xml")
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
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-facet-reversed.xml")
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
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-type-inheritance-reversed.xml")
    public void testInheritanceCacheReversedRegistration() {
        checkInheritanceCache();
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-blob-value-type.xml")
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-schema-manager.xml")
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-change-doctype.xml")
    public void testMerge() throws Exception {

        DocumentType t = schemaManager.getDocumentType("myDoc3");
        assertEquals(Set.of("schema1", "common"), t.getSchemaNames());
        assertEquals(Set.of(), t.getMixins());
    }

    @Test
    public void testSupertypeLoop() {
        assertInvalidModel("model/test-supertype-loop-types.json");
    }

    @Test
    public void testMissingSupertype() {
        assertInvalidModel("model/test-missing-supertype-types.json");
    }

    @Test
    public void testFacetMissingSchema() {
        assertInvalidModel("model/test-facet-missing-schema-types.json");
    }

    private void assertInvalidModel(String resource) {
        ModelDescriptor descriptor = new ModelDescriptor();
        descriptor.src = resource;
        descriptor.resource = new Resource(new Context(), resource);
        assertThrows(RuntimeServiceException.class,
                () -> ModelComposer.compose(schemaManager.toContentModel(), List.of(descriptor)));
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-facet-per-document.xml")
    public void testFacetNoPerDocumentQuery() throws Exception {
        assertTrue(schemaManager.getNoPerDocumentQueryMixins().contains("someFacet"));
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-facet-disabled.xml")
    public void testFacetDisabled() {
        CompositeType f = schemaManager.getMixin("someFacet");
        assertNull(f);
        DocumentType t = schemaManager.getDocumentType("myDoc");
        assertNotNull(t);
        assertEquals(Set.of(), t.getMixins());
        assertEquals(Set.of(), t.getSchemaNames());
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-schema-disabled.xml")
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
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-blob-value-type.xml")
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-schema-manager.xml")
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-merge-doctype.xml")
    public void testMergeDocumentType() throws Exception {

        DocumentType t = schemaManager.getDocumentType("myDoc");
        assertEquals(Set.of("schema2"), t.getSchemaNames());
        assertEquals(Set.of("viewable", "writable", "NewFacet"), t.getMixins());

        t = schemaManager.getDocumentType("myDoc2");
        assertEquals(Set.of("schema1", "schema2", "newschema", "newschema2"), t.getSchemaNames());
        assertEquals(Set.of("myfacet", "NewFacet2"), t.getMixins());
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/testSchemaWithImportInclude.xml")
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
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-extends-append-doctypes.xml")
    public void testExtendsAppendTypes() {
        // test appending types which extend another document
        DocumentType t = schemaManager.getDocumentType("MyMergeableFolder");
        assertEquals(t.getMixins(), Set.of("specdoc", "specdoc2", "specdoc3", "specdoc4"));
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-blob-value-type.xml")
    @Deploy("com.hyland.openarch.ecm.core.schema.tests:OSGI-INF/test-schema-manager.xml")
    public void testGetFacet() {
        CompositeType component = schemaManager.getMixin("fakeFacet1");
        assertNotNull(component, String.format("Facet %s should exist", "fakeFacet1"));
        assertEquals(1, component.getSchemas().size());
        assertEquals(Set.of("facetSchema1"), component.getSchemaNames());
    }

}
