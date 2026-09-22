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
package org.nuxeo.ecm.core.schema.types;

import static java.util.Objects.requireNonNull;
import static com.hyland.openarch.common.function.FunctionUtils.sortByKeyAndMapValue;
import static org.nuxeo.ecm.core.schema.TypeConstants.PREFIX_SEPARATOR;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.dto.ConstraintsDTO;
import org.nuxeo.ecm.core.schema.dto.FieldDTO;
import org.nuxeo.ecm.core.schema.dto.TypeDTO;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;

/**
 * A Complex Type holds several fields.
 */
public class ComplexTypeImpl extends AbstractType implements ComplexType {

    /** The fields held by this complex type. */
    protected final Map<String, Field> fields = new HashMap<>();

    protected final String prefix;

    public ComplexTypeImpl(ComplexType superType, String name, String prefix, String projectId) {
        super(superType, name, projectId);
        // for composite types, they already include schemas from supertypes
        // if (superType != null) {
        // for (Field field : superType.getFields()) {
        // addField(field);
        // }
        // }
        requireNonNull(prefix, "null prefix");
        this.prefix = prefix;
    }

    public ComplexTypeImpl(ComplexType superType, String name) {
        this(superType, name, "", null);
    }

    @Override
    public TypeDTO toTypeDTO(SchemaManager schemaManager) {
        return new TypeDTO(projectId, toFieldsDTO(schemaManager), ConstraintsDTO.of(constraints), null, null);
    }

    /** The fields, sorted by name so that the output is stable. */
    protected Map<String, FieldDTO> toFieldsDTO(SchemaManager schemaManager) {
        return sortByKeyAndMapValue(fields, e -> e.getValue().toFieldDTO(schemaManager));
    }

    // also called by CompositeTypeImpl
    public void addField(Field field) {
        fields.put(field.getName(), field);
    }

    // called by XSDLoader
    @Override
    public Field addField(String name, Type type, String defaultValue, Collection<Constraint> constraints) {
        return addField(name, type, defaultValue, constraints, null);
    }

    @Override
    public Field addField(String name, Type type, String defaultValue, Collection<Constraint> constraints,
            FieldCharacteristics characteristics) {
        if (!prefix.isEmpty()) {
            name = prefix + PREFIX_SEPARATOR + name;
        }
        FieldImpl field = new FieldImpl(name, this, type, defaultValue, constraints, false, characteristics);
        addField(field);
        return field;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public Field getField(String name) {
        return fields.get(name);
    }

    @Override
    public Collection<Field> getFields() {
        return fields.values();
    }

    @Override
    public int getFieldsCount() {
        return fields.size();
    }

    @Override
    public boolean hasField(String name) {
        return fields.containsKey(name);
    }

    @Override
    public boolean hasFields() {
        return !fields.isEmpty();
    }

    @Override
    public boolean isComplexType() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean validate(Object object) {
        throw new UnsupportedOperationException("Unimplemented, use DocumentValidationService");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + name + ')';
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convert(Object object) throws TypeException {
        if (object instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) object;
            for (Entry<Object, Object> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                Field field = getField(key);
                if (field == null) {
                    throw new IllegalArgumentException("Field " + key + " is not defined for the complex type "
                            + getName());
                }
                entry.setValue(field.getType().convert(entry.getValue()));
            }
            return object;
        }
        throw new TypeException(Map.class);
    }

}
