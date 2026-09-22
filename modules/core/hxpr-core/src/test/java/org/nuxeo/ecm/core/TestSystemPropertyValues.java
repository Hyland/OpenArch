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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.schema.types.FieldCharacteristics.Representation.INDEX;
import static org.nuxeo.ecm.core.schema.types.FieldCharacteristics.Representation.REST;
import static org.nuxeo.ecm.core.schema.types.FieldCharacteristics.Representation.STORE;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.FieldCharacteristics;
import org.nuxeo.ecm.core.schema.types.SchemaImpl;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;

public class TestSystemPropertyValues {

    @Test
    public void defaultCharacteristicsDoNotMakeACustomFieldASystemProperty() {
        SchemaImpl schema = new SchemaImpl("custom", "custom");
        var field = schema.addField("value", StringType.INSTANCE, null, null);
        SchemaManager schemaManager = mock(SchemaManager.class);
        when(schemaManager.getSchemas()).thenReturn(new SchemaImpl[] { schema });
        CoreSession session = mock(CoreSession.class);
        when(session.getSchemaManager()).thenReturn(schemaManager);
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getCoreSession()).thenReturn(session);
        when(doc.hasSchema("custom")).thenReturn(true);

        assertEquals(FieldCharacteristics.DEFAULT, field.getCharacteristics());
        assertEquals(Map.of(), SystemPropertyValues.forOutput(doc, INDEX));
    }

    @Test
    public void falseAsNullIsAppliedPerRepresentation() {
        SchemaImpl schema = new SchemaImpl("system", "sys");
        schema.addField("storeOnly", BooleanType.INSTANCE, null, null,
                new FieldCharacteristics(true, null, false, false, false, Set.of(STORE), null, null, null, null));
        schema.addField("storeAndIndex", BooleanType.INSTANCE, null, null,
                new FieldCharacteristics(true, null, false, false, false, Set.of(STORE, INDEX), null, null, null, null));
        schema.addField("everywhere", BooleanType.INSTANCE, null, null,
                new FieldCharacteristics(true, null, false, false, false, Set.of(STORE, REST, INDEX), null, null, null,
                        null));
        SchemaManager schemaManager = mock(SchemaManager.class);
        when(schemaManager.getSchema("system")).thenReturn(schema);
        CoreSession session = mock(CoreSession.class);
        when(session.getSchemaManager()).thenReturn(schemaManager);
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getCoreSession()).thenReturn(session);
        when(doc.hasSchema("system")).thenReturn(true);
        when(doc.getPropertyValue("sys_storeOnly")).thenReturn(Boolean.FALSE);
        when(doc.getPropertyValue("sys_storeAndIndex")).thenReturn(Boolean.FALSE);
        when(doc.getPropertyValue("sys_everywhere")).thenReturn(Boolean.FALSE);

        Map<String, Object> rest = SystemPropertyValues.forOutput(doc, REST);
        assertEquals(Boolean.FALSE, rest.get("sys_storeOnly"));
        assertEquals(Boolean.FALSE, rest.get("sys_storeAndIndex"));
        assertNull(rest.get("sys_everywhere"));

        Map<String, Object> index = SystemPropertyValues.forOutput(doc, INDEX);
        assertEquals(Boolean.FALSE, index.get("sys_storeOnly"));
        assertNull(index.get("sys_storeAndIndex"));
        assertNull(index.get("sys_everywhere"));
    }
}
