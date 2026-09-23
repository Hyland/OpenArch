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

import static java.util.stream.Collectors.toSet;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.dto.TypeDTO;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.TypeConstraint;

/**
 * The implementation for a List type.
 */
public class ListTypeImpl extends AbstractType implements ListType {

    protected static final String DEFAULT_VALUE_SEPARATOR = " ";

    protected final Type type;

    protected final Field field;

    public ListTypeImpl(String name, Type type, String fieldName, String defaultValue, Set<Constraint> constraints,
            String projectId) {
        super(null, name, projectId);
        Set<Constraint> computedConstraints;
        if (type instanceof SimpleType) {
            fieldName = "item";
            computedConstraints = new HashSet<>(type.getConstraints());
            computedConstraints.addAll(constraints);
            computedConstraints = computedConstraints.stream() //
                                                     .filter(c -> !(c instanceof TypeConstraint))
                                                     .collect(toSet());
        } else {
            computedConstraints = constraints;
        }
        this.type = type;
        field = new FieldImpl(fieldName, this, type, defaultValue, computedConstraints);
    }

    public ListTypeImpl(String name, Type type, String fieldName, String defaultValue, Set<Constraint> constraints) {
        this(name, type, fieldName, defaultValue, constraints, null);
    }

    public ListTypeImpl(String name, Type type, String fieldName) {
        this(name, type, fieldName, null, Set.of());
    }

    public ListTypeImpl(String name, Type type) {
        this(name, type, null);
    }

    @Override
    public TypeDTO toTypeDTO(SchemaManager schemaManager) {
        return new TypeDTO(projectId, null, null, field.toFieldDTO(schemaManager), null);
    }

    @Override
    public Type getFieldType() {
        return field.getType();
    }

    @Override
    public Field getField() {
        return field;
    }

    public Type getType() {
        return type;
    }

    @Override
    public boolean isListType() {
        return true;
    }

    @Override
    public Object decode(String string) {
        if (StringUtils.isBlank(string)) {
            return null;
        }
        String[] split = string.split(DEFAULT_VALUE_SEPARATOR);
        List<Object> decoded = new ArrayList<>(split.length);
        Class<?> klass = null;
        for (String s : split) {
            Object o = type.decode(s);
            if (klass == null && o != null) {
                klass = o.getClass();
            }
            decoded.add(o);
        }
        if (klass == null) {
            klass = Object.class;
        }
        // turn the list into a properly-typed array for the elements
        Object[] array = (Object[]) Array.newInstance(klass, decoded.size());
        return decoded.toArray(array);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean validate(Object object) {
        throw new UnsupportedOperationException("Unimplemented, use DocumentValidationService");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convert(Object object) throws TypeException {
        if (object instanceof List) {
            List<Object> list = (List<Object>) object;
            for (int i = 0, len = list.size(); i < len; i++) {
                Object value = list.get(i);
                list.set(i, type.convert(value));
            }
            return object;
        }
        throw new TypeException(List.class);
    }

    @Override
    public boolean isArray() {
        return type instanceof SimpleType;
    }

}
