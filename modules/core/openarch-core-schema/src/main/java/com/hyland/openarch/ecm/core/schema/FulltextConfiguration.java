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

import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_ROOT;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.hyland.openarch.ecm.core.schema.types.ComplexType;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.ListType;
import com.hyland.openarch.ecm.core.schema.types.Schema;
import com.hyland.openarch.ecm.core.schema.types.SimpleTypeImpl;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.ecm.core.schema.types.primitives.BinaryType;
import com.hyland.openarch.ecm.core.schema.types.primitives.StringType;

/**
 * Fulltext configuration derived from a content-model snapshot, with optional repository settings.
 * Collections are retained directly and must not be modified by callers.
 */
public record FulltextConfiguration(Map<String, Set<String>> propPathBlobProperties,
        Map<String, Set<String>> propPathStringProperties, Set<String> includedTypes, int fieldSizeLimit,
        boolean storedInBlob) {

    /** Computes the model metadata; repository settings are supplied by {@link #withFieldSizeLimitAndStoredInBlob}. */
    public static FulltextConfiguration fromSchemaManager(SchemaManager schemaManager) {
        Map<String, Set<String>> blobProperties = new HashMap<>();
        Map<String, Set<String>> stringProperties = new HashMap<>();
        Set<String> types = new HashSet<>();
        for (DocumentType type : schemaManager.getDocumentTypes()) {
            if (!type.isFullTextIndexable()) {
                continue;
            }
            Set<String> blobs = new HashSet<>();
            Set<String> strings = new HashSet<>();
            for (Schema schema : type.getSchemas()) {
                for (Field field : schema.getFields()) {
                    if (!field.isFullTextIndexed()) {
                        continue;
                    }
                    if (isBlobField(field)) {
                        String path = field.getName();
                        if (!path.endsWith("/" + KEY_BLOB_KEY)) {
                            path += "/" + KEY_BLOB_KEY;
                        }
                        blobs.add(path);
                    }
                    if (field.getType() instanceof StringType) {
                        strings.add(field.getName());
                    }
                }
            }
            if (!blobs.isEmpty()) {
                blobProperties.put(type.getName(), blobs);
                types.add(type.getName());
            }
            if (!strings.isEmpty()) {
                stringProperties.put(type.getName(), strings);
                types.add(type.getName());
            }
        }
        return new FulltextConfiguration(blobProperties, stringProperties, types, 0, false);
    }

    /** Returns a configuration with repository settings, sharing this instance's model metadata directly. */
    public FulltextConfiguration withFieldSizeLimitAndStoredInBlob(int fieldSizeLimit, boolean storedInBlob) {
        return new FulltextConfiguration(propPathBlobProperties, propPathStringProperties, includedTypes,
                fieldSizeLimit, storedInBlob);
    }

    public boolean isFulltextIndexable(String typeName) {
        return !SYS_ROOT.equals(typeName) && includedTypes.contains(typeName);
    }

    private static Type getBaseType(Type type) {
        if (type instanceof SimpleTypeImpl) {
            return getBaseType(type.getSuperType());
        }
        if (type instanceof ListType listType) {
            return getBaseType(listType.getFieldType());
        }
        return type;
    }

    private static boolean isBlobField(Field field) {
        Type type = getBaseType(field.getType());
        if (TypeConstants.isBlobType(type)) {
            type = ((ComplexType) type).getField(KEY_BLOB_KEY).getType();
        }
        return type instanceof BinaryType;
    }
}
