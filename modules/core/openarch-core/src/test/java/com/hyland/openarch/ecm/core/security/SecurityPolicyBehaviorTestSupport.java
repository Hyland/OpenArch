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
package com.hyland.openarch.ecm.core.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.ecm.core.schema.types.CompositeType;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.Schema;
import com.hyland.openarch.runtime.api.Framework;

/** Shared Mockito helpers for the security-policy behavior tests. */
final class SecurityPolicyBehaviorTestSupport {

    private SecurityPolicyBehaviorTestSupport() {
    }

    /** Stubs the schema manager so that {@code mixinName} in {@code repository} exposes the given fields. */
    static void stubMixinFields(MockedStatic<Framework> framework, String repository, String mixinName,
            String... fieldNames) {
        TypeService typeService = mock(TypeService.class);
        SchemaManager schemaManager = mock(SchemaManager.class);
        CompositeType mixin = mock(CompositeType.class);
        Schema schema = mock(Schema.class);
        List<Field> fields = new ArrayList<>();
        for (String fieldName : fieldNames) {
            Field field = mock(Field.class);
            when(field.getName()).thenReturn(fieldName);
            fields.add(field);
        }
        framework.when(() -> Framework.getService(TypeService.class)).thenReturn(typeService);
        when(typeService.getSchemaManager(repository)).thenReturn(schemaManager);
        when(schemaManager.getMixin(mixinName)).thenReturn(mixin);
        when(mixin.getSchemas()).thenReturn(List.of(schema));
        when(schema.getFields()).thenReturn(fields);
    }
}
