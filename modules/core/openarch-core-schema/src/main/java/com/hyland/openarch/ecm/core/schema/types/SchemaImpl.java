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

import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.dto.SchemaDTO;
import com.hyland.openarch.ecm.core.schema.dto.TypeDTO;

/**
 * The implementation of a Schema
 */
public class SchemaImpl extends ComplexTypeImpl implements Schema {

    public boolean isVersionWritabe;

    /**
     * Constructor for a schema. Its types (fields) are then added through {@link #registerType}.
     */
    public SchemaImpl(String name, String prefix) {
        this(name, prefix, false, null);
    }

    public SchemaImpl(String name, String prefix, boolean isVersionWritabe, String projectId) {
        super(null, name, prefix, projectId);
        this.isVersionWritabe = isVersionWritabe;
    }

    @Override
    public SchemaDTO toSchemaDTO(SchemaManager schemaManager) {
        return new SchemaDTO(projectId, toFieldsDTO(schemaManager), isVersionWritabe,
                prefix.isEmpty() ? null : prefix);
    }

    @Override
    public TypeDTO toTypeDTO(SchemaManager schemaManager) {
        throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + name + ')';
    }

    @Override
    public boolean isVersionWritabe() {
        return isVersionWritabe;
    }

}
