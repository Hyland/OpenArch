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

import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_MINOR_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_VERSION;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.SYSTEM_SCHEMAS;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.REST;

import java.util.LinkedHashMap;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation;
import com.hyland.openarch.ecm.core.schema.types.Schema;

/**
 * Produces schema-typed raw system values. Transport adapters own any wire-specific conversion.
 */
public final class SystemPropertyValues {

    private static final Long ZERO = Long.valueOf(0);

    private SystemPropertyValues() {
    }

    public static Map<String, Object> forOutput(DocumentModel doc, Representation representation) {
        SchemaManager schemaManager = getSchemaManager(doc);
        Map<String, Object> values = new LinkedHashMap<>();
        for (String schemaName : SYSTEM_SCHEMAS) {
            if (!doc.hasSchema(schemaName)) {
                continue;
            }
            Schema schema = schemaManager.getSchema(schemaName);
            for (Field field : schema.getFields()) {
                FieldCharacteristics characteristics = field.getCharacteristics();
                if (characteristics.notOutput().contains(representation)) {
                    continue;
                }
                String name = field.getName();
                if (representation == REST && !hasReadPermission(doc, characteristics.readPermission())) {
                    continue;
                }
                values.put(name, getRawValue(doc, name, characteristics, representation));
            }
        }
        if (ZERO.equals(values.get(SYSVER_VERSION)) && ZERO.equals(values.get(SYSVER_MINOR_VERSION))) {
            values.remove(SYSVER_VERSION);
            values.remove(SYSVER_MINOR_VERSION);
        }
        return values;
    }

    public static boolean isRepositoryManagedField(DocumentModel doc, String name) {
        Field field = getSchemaManager(doc).getField(name);
        return field != null && field.isRepositoryManaged();
    }

    private static boolean hasReadPermission(DocumentModel doc, String permission) {
        if (permission == null) {
            return true;
        }
        CoreSession session = doc.getCoreSession();
        return session != null && session.hasPermission(doc.getRef(), permission);
    }

    private static Object getRawValue(DocumentModel doc, String name, FieldCharacteristics characteristics,
            Representation representation) {
        Object value = doc.getPropertyValue(name);
        if (Boolean.FALSE.equals(value) && characteristics.falseAsNull().contains(representation)) {
            return null;
        }
        return value;
    }

    private static SchemaManager getSchemaManager(DocumentModel doc) {
        return doc.getCoreSession().getSchemaManager();
    }
}
