/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.storage;

import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.SimpleTypeImpl;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.BinaryType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.runtime.model.DefaultComponent;

public class FullTextConfigurationImpl extends DefaultComponent implements FullTextConfigurationService {

    private static final Logger log = LogManager.getLogger(FullTextConfigurationImpl.class);

    @Override
    public FulltextConfiguration makeConfiguration(SchemaManager schemaManager, int fulltextFieldSizeLimit,
            boolean fulltextStoredInBlob) {
        FulltextConfiguration ftc = new FulltextConfiguration();
        ftc.setFulltextFieldSizeLimit(fulltextFieldSizeLimit);
        ftc.setFulltextStoredInBlob(fulltextStoredInBlob);

        // find what paths we mean by "all"
        Set<String> allBinaryPaths = new HashSet<>();
        PathsFinder pathsFinder = new PathsFinder(allBinaryPaths);
        for (Schema schema : schemaManager.getSchemas()) {
            pathsFinder.walkSchema(schema);
        }

        Map<String, Set<String>> fields = extractBlobFieldsIndexedForFullTextSearch(schemaManager);
        fillBlobPropertiesInConfiguration(fields, ftc, schemaManager);

        fields = extractStringFieldsIndexedForFullTextSearch(schemaManager);
        fillStringPropertiesInConfiguration(fields, ftc, schemaManager);

        return ftc;
    }

    private Map<String, Set<String>> extractStringFieldsIndexedForFullTextSearch(SchemaManager schemaManager) {
        var result = new HashMap<String, Set<String>>();
        Arrays.stream(schemaManager.getDocumentTypes()).filter(DocumentType::isFullTextIndexable).forEach(type -> {
            var fields = new HashSet<String>();
            type.getSchemas().stream().forEach(schema -> {
                fields.addAll(schema.getFields()
                                    .stream()
                                    .filter(Field::isFullTextIndexed)
                                    .filter(field -> field.getType() instanceof StringType)
                                    .map(Field::getName)
                                    .toList());
            });
            result.put(type.getName(), fields);
        });
        return result;
    }

    private void fillStringPropertiesInConfiguration(Map<String, Set<String>> fields, FulltextConfiguration ftc,
            SchemaManager schemaManager) {
        fields.forEach((type, listFields) -> {
            if (listFields.isEmpty()) {
                return;
            }
            ftc.getIncludedTypes().add(type);

            listFields.forEach(path -> {
                Field field = schemaManager.getField(path);
                if (field == null) {
                    log.error("Ignoring unknown property {} in fulltext configuration on document type {}", path, type);
                    return;
                }
                ftc.getPropPathStringProperties().computeIfAbsent(type, p -> new HashSet<>()).add(path);
            });
        });
    }

    private Map<String, Set<String>> extractBlobFieldsIndexedForFullTextSearch(SchemaManager schemaManager) {
        var result = new HashMap<String, Set<String>>();
        Arrays.stream(schemaManager.getDocumentTypes()).filter(DocumentType::isFullTextIndexable).forEach(type -> {
            var fields = new HashSet<String>();
            type.getSchemas().stream().forEach(schema -> fields.addAll(schema.getFields()
                                .stream()
                                .filter(Field::isFullTextIndexed)
                                .filter(this::isBlobField)
                                .map(Field::getName)
                                .toList()));
            result.put(type.getName(), fields);
        });
        return result;
    }

    private void fillBlobPropertiesInConfiguration(Map<String, Set<String>> fields, FulltextConfiguration ftc,
                                                   SchemaManager schemaManager) {
        fields.forEach((type, listFields) -> {
            if (listFields.isEmpty()) {
                return;
            }
            ftc.getIncludedTypes().add(type);

            listFields.forEach(path -> {
                Field field = schemaManager.getField(path);
                if (field == null) {
                    log.error("Ignoring unknown property {} in fulltext configuration on document type {}", path, type);
                    return;
                }
                if (isBlobField(field)) {
                    // If the field is a Blob, needed for propPathBlobProperties as
                    // DBSTransactionState.findDirtyDocuments expects this to be in the same format as what
                    // DirtyPathsFinder expects, like "content/data".
                    if (!path.endsWith("/" + KEY_BLOB_KEY)) {
                        path += "/" + KEY_BLOB_KEY;
                    }
                }
                ftc.getPropPathBlobProperties().computeIfAbsent(type, p -> new HashSet<>()).add(path);
            });

        });
    }

    private Type getBaseType(Type type) {
        if (type instanceof SimpleTypeImpl) {
            return getBaseType(type.getSuperType());
        }
        if (type instanceof ListType listType) {
            return getBaseType(listType.getFieldType());
        }
        return type;
    }

    private boolean isBlobField(Field field) {
        var type = getBaseType(field.getType());
        if (TypeConstants.isBlobType(type)) {
            type = ((ComplexType) type).getField(KEY_BLOB_KEY).getType(); // BinaryType
        }
        return type instanceof BinaryType;
    }

    /**
     * Accumulates paths for string and binary properties in schemas passed to {@link #walkSchema}.
     * <p>
     * For binaries the path includes the final "/data" part.
     */
    class PathsFinder {

        protected final Set<String> binaryPaths;

        public PathsFinder(Set<String> binaryPaths) {
            this.binaryPaths = binaryPaths;
        }

        public void walkSchema(Schema schema) {
            walkComplexType(schema, null);
        }

        protected void walkComplexType(ComplexType complexType, String path) {
            for (Field field : complexType.getFields()) {
                String name = field.getName();
                String fieldPath = path == null ? name : path + '/' + name;
                walkType(field.getType(), fieldPath);
            }
        }

        protected void walkType(Type type, String path) {
            if (type.isSimpleType()) {
                walkSimpleType(type, path);
            } else if (type.isComplexType()) {
                // complex type
                ComplexType ctype = (ComplexType) type;
                walkComplexType(ctype, path);
            } else if (type.isListType()) {
                String listPath = path + "/*";
                Type ftype = ((ListType) type).getField().getType();
                if (ftype.isComplexType()) {
                    // complex list
                    walkComplexType((ComplexType) ftype, listPath);
                } else {
                    // array
                    walkSimpleType(ftype, listPath);
                }
            } else {
                // any: ignore (assume contains no binaries)
            }
        }

        protected void walkSimpleType(Type type, String path) {
            while (type instanceof SimpleTypeImpl) {
                // type with constraint
                type = type.getSuperType();
            }
            if (type instanceof BinaryType) {
                binaryPaths.add(path);
            }
        }
    }
}
