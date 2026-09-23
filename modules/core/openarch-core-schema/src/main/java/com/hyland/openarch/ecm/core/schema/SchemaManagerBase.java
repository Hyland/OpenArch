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

import static java.util.Objects.requireNonNull;
import static com.hyland.openarch.common.function.FunctionUtils.sortByKeyAndMapValue;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.STORE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import com.hyland.openarch.ecm.core.schema.dto.MixinTypeDTO;
import com.hyland.openarch.ecm.core.schema.dto.PrimaryTypeDTO;
import com.hyland.openarch.ecm.core.schema.dto.SchemaDTO;
import com.hyland.openarch.ecm.core.schema.dto.TypeDTO;
import com.hyland.openarch.ecm.core.schema.types.ComplexType;
import com.hyland.openarch.ecm.core.schema.types.CompositeType;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.ListType;
import com.hyland.openarch.ecm.core.schema.types.Schema;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.ecm.core.schema.validation.violations.AlreadyExistsViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;

/**
 * Schema Manager base implementation.
 */
public class SchemaManagerBase implements SchemaManager {

    protected final Map<String, Type> types = new HashMap<>();

    /** Effective schemas. */
    protected Map<String, Schema> schemas = new LinkedHashMap<>();

    /** Effective mixins. */
    // public for tests
    public Map<String, CompositeType> mixins = new LinkedHashMap<>();

    protected Set<String> noPerDocumentQueryMixins = new HashSet<>();

    /** Effective document types. */
    // public for tests
    public Map<String, DocumentTypeImpl> documentTypes = new LinkedHashMap<>();

    // denormalized datastructures

    private Map<String, Field> fields = new HashMap<>();

    protected Map<String, Set<String>> documentTypesExtending = new HashMap<>();

    protected Map<String, Set<String>> documentTypesExtendedBy = new HashMap<>();

    protected Map<String, Set<String>> documentTypesForMixin = new HashMap<>();

    // ============================================================

    @Override
    public Type getType(String typeName) {
        return types.get(typeName);
    }

    @Override
    public Schema[] getSchemas() {
        return new ArrayList<>(schemas.values()).toArray(new Schema[0]);
    }

    @Override
    public Schema getSchema(String name) {
        return schemas.get(name);
    }

    @Override
    public CompositeType[] getMixins() {
        return new ArrayList<>(mixins.values()).toArray(new CompositeType[mixins.size()]);
    }

    @Override
    public CompositeType getMixin(String name) {
        return mixins.get(name);
    }

    @Override
    public Set<String> getNoPerDocumentQueryMixins() {
        return Collections.unmodifiableSet(noPerDocumentQueryMixins);
    }

    @Override
    public DocumentType getDocumentType(String name) {
        return documentTypes.get(name);
    }

    @Override
    public Set<String> getDocumentTypeNamesForMixin(String mixin) {
        return documentTypesForMixin.get(mixin);
    }

    @Override
    public Set<String> getDocumentTypeNamesExtending(String docTypeName) {
        return documentTypesExtending.get(docTypeName);
    }

    @Override
    public Set<String> getDocumentTypeNamesExtendedBy(String docTypeName) {
        return documentTypesExtendedBy.get(docTypeName);
    }

    @Override
    public DocumentType[] getDocumentTypes() {
        return new ArrayList<DocumentType>(documentTypes.values()).toArray(new DocumentType[0]);
    }

    protected void registerSchema(Schema schema, List<ValidationViolation> violations) {
        schemas.put(schema.getName(), schema);
        for (Field field : schema.getFields()) {
            Field oldField = fields.put(field.getName(), field);
            if (oldField != null) {
                Type oldSchema = oldField.getDeclaringType();
                ValidationViolation violation = new AlreadyExistsViolation(
                        "label.schema.application.violation.AlreadyExistsViolation.duplicateProperty", field.getName(),
                        oldSchema.getName(), oldField.getType().getName(), schema.getName(), field.getType().getName());
                violations.add(violation);
            }
        }
    }

    /*
     * ===== Fields =====
     */

    @Override
    public Field getField(String xpath) {
        requireNonNull(xpath, "null xpath");
        if (xpath.contains("/")) {
            // need to resolve subfields
            String[] properties = xpath.split("/");
            Field field = getField(properties[0]);
            for (int i = 1; i < properties.length; i++) {
                if (field == null) {
                    break;
                }
                boolean finalCall = i == properties.length - 1;
                field = getField(field, properties[i], finalCall);
            }
            return field;
        } else {
            return fields.get(xpath);
        }
    }

    private Field getField(Field parent, String subFieldName, boolean finalCall) {
        Type type = parent.getType();
        if (type.isListType()) {
            ListType listType = (ListType) type;
            // remove indexes in case of multiple values
            if ("*".equals(subFieldName)) {
                if (finalCall) {
                    return resolveSubField(listType, null, true);
                } else {
                    return parent;
                }
            } else {
                try {
                    Integer.valueOf(subFieldName);
                    if (finalCall) {
                        return resolveSubField(listType, null, true);
                    } else {
                        return parent;
                    }
                } catch (NumberFormatException e) {
                    return resolveSubField(listType, subFieldName, false);
                }
            }
        } else if (type.isComplexType()) {
            ComplexType ctype = (ComplexType) type;
            return ctype.getField(subFieldName);
        } else {
            return null;
        }
    }

    private Field resolveSubField(ListType listType, String subName, boolean fallbackOnSubElement) {
        Field field;
        Type itemType = listType.getFieldType();
        if (itemType.isComplexType() && subName != null) {
            ComplexType complexType = (ComplexType) itemType;
            field = complexType.getField(subName);
        } else if (fallbackOnSubElement) {
            field = listType.getField();
        } else {
            field = null;
        }
        return field;
    }

    // ============================================================

    // utilities

    protected void computeDenormalizations() {
        documentTypesExtending.clear();
        documentTypesExtendedBy.clear();
        documentTypesForMixin.clear();

        documentTypes.values().forEach(doctype -> {
            // precompute documentTypesExtending
            String name = doctype.getName();
            for (Type p = doctype; p != null; p = p.getSuperType()) {
                documentTypesExtending.computeIfAbsent(p.getName(), k -> new HashSet<>(Set.of(k))) //
                                      .add(name);
                documentTypesExtendedBy.computeIfAbsent(name, k -> new HashSet<>(Set.of(k))) //
                                       .add(p.getName());
            }
            // precompute documentTypesForMixin
            for (String mixin : doctype.getMixins()) {
                documentTypesForMixin.computeIfAbsent(mixin, k -> new HashSet<>()).add(name);
            }
        });
    }

    // ============================================================

    // DTO dump

    @Override
    public ContentModelDTO toContentModel() {
        return new ContentModelDTO( //
                sortByKeyAndMapValue(schemas, this::toSchemaDTO), //
                sortByKeyAndMapValue(types, this::toTypeDTO), //
                sortByKeyAndMapValue(mixins, this::toMixinTypeDTO), //
                sortByKeyAndMapValue(documentTypes, this::toDocumentTypeDTO));
    }

    @Override
    public boolean isSecurityProperty(String name) {
        int slash = name.indexOf('/');
        Field field = getField(slash < 0 ? name : name.substring(0, slash));
        return field != null && field.getCharacteristics().readPermission() != null;
    }

    @Override
    public boolean isFalseStoredAsNull(String name) {
        Field field = getField(name);
        return field != null && field.getCharacteristics().falseAsNull().contains(STORE);
    }

    private TypeDTO toTypeDTO(Entry<String, Type> e) {
        return e.getValue().toTypeDTO(this);
    }

    private SchemaDTO toSchemaDTO(Entry<String, Schema> e) {
        return e.getValue().toSchemaDTO(this);
    }

    private MixinTypeDTO toMixinTypeDTO(Entry<String, CompositeType> e) {
        boolean noPerDocumentQuery = noPerDocumentQueryMixins.contains(e.getKey());
        return e.getValue().toMixinTypeDTO(noPerDocumentQuery);
    }

    private PrimaryTypeDTO toDocumentTypeDTO(Entry<String, DocumentTypeImpl> e) {
        return e.getValue().toDocumentTypeDTO();
    }

}
