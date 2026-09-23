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
package org.nuxeo.ecm.core;

import static java.lang.reflect.Modifier.isStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.lang.reflect.Field;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.DocumentConstants;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core")
public class SystemPropertyCatalogTest {

    @Inject
    protected TypeService typeService;

    @Test
    public void testAllSystemConstantsAreRepositoryManaged() throws IllegalAccessException {
        SchemaManager schemaManager = typeService.getDefaultSchemaManager();
        for (Field constant : DocumentConstants.class.getFields()) {
            String constantName = constant.getName();
            if (!isStatic(constant.getModifiers()) || constantName.endsWith("_PREFIX")
                    || !isSystemPropertyConstant(constantName)) {
                continue;
            }
            String propertyName = (String) constant.get(null);
            com.hyland.openarch.ecm.core.schema.types.Field field = schemaManager.getField(propertyName);
            assertNotNull(field, propertyName);
            assertTrue(field.isRepositoryManaged(), propertyName);
        }
    }

    @Test
    public void testSystemSchemaIsInheritedFromSysContent() {
        SchemaManager schemaManager = typeService.getDefaultSchemaManager();
        assertTrue(schemaManager.getDocumentType(SYS_CONTENT).hasSchema("system"));
        assertTrue(schemaManager.getDocumentType(SYS_FILE).hasSchema("system"));
        assertEquals(SYS_CONTENT, schemaManager.getDocumentType("SysRelation").getSuperType().getName());
        assertTrue(schemaManager.getDocumentType("SysRelation").hasSchema("system"));
    }

    @Test
    public void testCompatibilityTypes() {
        SchemaManager schemaManager = typeService.getDefaultSchemaManager();
        assertNotNull(schemaManager.getType("system#contributorList"));
    }

    protected static boolean isSystemPropertyConstant(String name) {
        return name.startsWith("SYS_") || name.startsWith("SYSGOV_") || name.startsWith("SYSVER_");
    }
}
