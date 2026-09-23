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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.dto.ConstraintsDTO;
import com.hyland.openarch.ecm.core.schema.dto.FieldDTO;
import com.hyland.openarch.ecm.core.schema.dto.TypeDTO;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.ConstraintUtils;
import com.hyland.openarch.ecm.core.schema.types.constraints.NotNullConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.TypeConstraint;

/**
 * The implementation for a field, which is the association of a type, a name, and default values.
 */
public class FieldImpl implements Field {

    private final String name;

    private final Type type;

    private final Type declaringType;

    private final String defaultValue;

    private final Set<Constraint> constraints;

    private final boolean fullTextIndexed;

    // null represents DEFAULT internally so empty characteristics stay omitted from the serialized model
    private final FieldCharacteristics characteristics;

    private final boolean nullable;

    public FieldImpl(String name, Type declaringType, Type type, String defaultValue,
                     Collection<Constraint> constraints) {
        this(name, declaringType, type, defaultValue, constraints, false);
    }

    public FieldImpl(String name, Type declaringType, Type type, String defaultValue,
            Collection<Constraint> constraints, boolean fullTextIndexed) {
        this(name, declaringType, type, defaultValue, constraints, fullTextIndexed, null);
    }

    public FieldImpl(String name, Type declaringType, Type type, String defaultValue,
            Collection<Constraint> constraints, boolean fullTextIndexed, FieldCharacteristics characteristics) {
        this.name = name;
        this.type = type;
        this.declaringType = declaringType;
        this.defaultValue = defaultValue;
        this.constraints = constraints == null ? Set.of() : new HashSet<>(constraints);
        this.fullTextIndexed = fullTextIndexed;
        this.characteristics = FieldCharacteristics.DEFAULT.equals(characteristics) ? null : characteristics;
        nullable = ConstraintUtils.getConstraint(this.constraints, NotNullConstraint.class) == null;
    }

    @Override
    public FieldDTO toFieldDTO(SchemaManager schemaManager) {
        String typeName = type.getName();
        FieldDTO base;
        if (type instanceof PrimitiveType || schemaManager.getType(typeName) != null) {
            // primitive type or type registered with the schema manager: a reference by name
            base = FieldDTO.ofType(typeName);
        } else {
            // anonymous type, declared inline
            TypeDTO anonymous = type.toTypeDTO(schemaManager);
            base = new FieldDTO(anonymous.extend(), anonymous.fields(), anonymous.list(), anonymous.constraints(),
                    null, false, null, anonymous.projectId());
        }

        // keep only the constraints that are the field's own, not those of its type
        Set<Constraint> fieldConstraints = new HashSet<>(constraints);
        fieldConstraints.removeAll(type.getConstraints());
        fieldConstraints.removeIf(TypeConstraint.class::isInstance);

        return new FieldDTO(base.typeRef(), base.fields(), base.list(),
                ConstraintsDTO.merge(base.constraints(), ConstraintsDTO.of(fieldConstraints)), defaultValue,
                fullTextIndexed, characteristics, base.projectId());
    }

    @Override
    public Type getDeclaringType() {
        return declaringType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Object getDefaultValue() {
        return type.decode(defaultValue);
    }

    @Override
    public boolean isNullable() {
        return nullable;
    }

    @Override
    public String toString() {
        return name + " [" + type.getName() + ']';
    }

    @Override
    public Set<Constraint> getConstraints() {
        return Collections.unmodifiableSet(constraints);
    }

    @Override
    public boolean isFullTextIndexed() {
        return fullTextIndexed;
    }

    @Override
    public FieldCharacteristics getCharacteristics() {
        return characteristics == null ? FieldCharacteristics.DEFAULT : characteristics;
    }
}
