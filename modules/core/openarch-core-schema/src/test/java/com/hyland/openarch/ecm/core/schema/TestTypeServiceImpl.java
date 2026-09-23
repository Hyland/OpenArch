/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.STORE;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import com.hyland.openarch.ecm.core.schema.dto.FieldDTO;
import com.hyland.openarch.ecm.core.schema.dto.PrimaryTypeDTO;
import com.hyland.openarch.ecm.core.schema.dto.SchemaDTO;
import com.hyland.openarch.ecm.core.schema.dto.TypeDTO;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.ValueSource;

public class TestTypeServiceImpl {

    @Test
    public void testCoreSystemDefinitionsReplacePersistedOnes() {
        SchemaDTO oldSystem = new SchemaDTO(null, Map.of("sys_old", FieldDTO.ofType("string")), false, "sys");
        SchemaDTO custom = new SchemaDTO("project", Map.of("custom_value", FieldDTO.ofType("string")), false,
                "custom");
        TypeDTO oldSystemType = new TypeDTO(null, null, null, null, "string");
        ContentModelDTO persisted = new ContentModelDTO(Map.of("system", oldSystem, "custom", custom),
                Map.of("system#old", oldSystemType), null, null);

        SchemaDTO currentSystem = new SchemaDTO(null, Map.of("sys_id", FieldDTO.ofType("string")), false, "sys");
        TypeDTO currentSystemType = new TypeDTO(null, null, null, null, "string");
        ContentModelDTO defaults = new ContentModelDTO(Map.of("system", currentSystem),
                Map.of("system#current", currentSystemType), null, null);

        ContentModelDTO merged = TypeServiceImpl.withCoreSystemDefinitions(persisted, defaults);

        assertSame(currentSystem, merged.schemas().get("system"));
        assertSame(custom, merged.schemas().get("custom"));
        assertEquals(currentSystemType, merged.types().get("system#current"));
        assertFalse(merged.types().containsKey("system#old"));
    }

    @Test
    public void testFieldCharacteristicsQueryInvariants() {
        assertThrows(IllegalArgumentException.class, () -> characteristics(null, true, false));
        assertThrows(IllegalArgumentException.class, () -> characteristics(ValueSource.QUERY_VIRTUAL, true, true));
        assertThrows(IllegalArgumentException.class,
                () -> new FieldCharacteristics(true, null, false, false, false, null,
                        null, null, null, FieldCharacteristics.VERSION_WRITE_STANDARD));
        assertThrows(IllegalArgumentException.class,
                () -> new FieldCharacteristics(true, null, false, false, true, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new FieldCharacteristics(true, null, false, false, false, null, null, null, "Write", null));
        assertThrows(IllegalArgumentException.class,
                () -> new FieldCharacteristics(false, null, false, false, false, null, Set.of(STORE), null, null,
                        null));
    }

    @Test
    public void testDefaultCharacteristicsAreIndependentFromManagedStatus() {
        FieldDTO emptyCharacteristics = new FieldDTO("string", null, null, null, null, false,
                FieldCharacteristics.DEFAULT, null);
        SchemaDTO system = new SchemaDTO(null,
                Map.of("sys_absent", FieldDTO.ofType("string"), "sys_empty", emptyCharacteristics), false, "sys");
        SchemaDTO custom = new SchemaDTO(null, Map.of("custom_plain", FieldDTO.ofType("string")), false, "custom");
        SchemaManager manager = new SchemaManagerDTO(
                new ContentModelDTO(Map.of("system", system, "custom", custom), null, null, null), null, false);

        assertTrue(manager.getField("sys_absent").isRepositoryManaged());
        assertTrue(manager.getField("sys_empty").isRepositoryManaged());
        assertSame(FieldCharacteristics.DEFAULT, manager.getField("sys_absent").getCharacteristics());
        assertSame(FieldCharacteristics.DEFAULT, manager.getField("sys_empty").getCharacteristics());
        assertFalse(manager.getField("custom_plain").isRepositoryManaged());
        assertSame(FieldCharacteristics.DEFAULT, manager.getField("custom_plain").getCharacteristics());

        ContentModelDTO rendered = manager.toContentModel();
        assertNull(rendered.schemas().get("system").fields().get("sys_absent").characteristics());
        assertNull(rendered.schemas().get("system").fields().get("sys_empty").characteristics());
        assertNull(rendered.schemas().get("custom").fields().get("custom_plain").characteristics());
    }

    @Test
    public void testSystemSchemaIsInheritedFromSysContent() {
        SchemaDTO system = new SchemaDTO(null, Map.of("sys_id", FieldDTO.ofType("string")), false, "sys");
        PrimaryTypeDTO sysContent = new PrimaryTypeDTO(null, null, null, null, null);
        PrimaryTypeDTO custom = new PrimaryTypeDTO(null, "SysContent", null, null, null);
        PrimaryTypeDTO sysRelation = new PrimaryTypeDTO(null, "SysContent", null, null, null);
        SchemaManager manager = new SchemaManagerDTO(
                new ContentModelDTO(Map.of("system", system), null, null,
                        Map.of("SysContent", sysContent, "SysRelation", sysRelation, "Custom", custom)),
                null, false);

        assertTrue(manager.getDocumentType("SysContent").hasSchema("system"));
        assertTrue(manager.getDocumentType("Custom").hasSchema("system"));
        assertEquals("SysContent", manager.getDocumentType("SysRelation").getSuperType().getName());
        assertTrue(manager.getDocumentType("SysRelation").hasSchema("system"));
        assertEquals(List.of("system"),
                manager.toContentModel().primaryTypes().get("SysContent").schemas());
        assertNull(manager.toContentModel().primaryTypes().get("Custom").schemas());
        assertNull(manager.toContentModel().primaryTypes().get("SysRelation").schemas());
        assertEquals("SysContent", manager.toContentModel().primaryTypes().get("SysRelation").extend());
    }

    protected static FieldCharacteristics characteristics(ValueSource source, boolean notQueryable,
            boolean notProjectable) {
        return new FieldCharacteristics(true, source, notQueryable, notProjectable, false, null, null, null, null, null);
    }
}
