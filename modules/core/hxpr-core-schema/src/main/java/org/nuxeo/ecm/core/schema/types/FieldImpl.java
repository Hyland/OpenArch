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

import static java.lang.Boolean.TRUE;
import static org.nuxeo.common.function.FunctionUtils.sortByKeyAndMapValue;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.FULL_TEXT_INDEXED;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.ConstraintUtils;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.TypeConstraint;

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

    private final boolean nullable;

    public FieldImpl(String name, Type declaringType, Type type, String defaultValue,
                     Collection<Constraint> constraints) {
        this(name, declaringType, type, defaultValue, constraints, false);
    }

    public FieldImpl(String name, Type declaringType, Type type, String defaultValue,
            Collection<Constraint> constraints, boolean fullTextIndexed) {
        this.name = name;
        this.type = type;
        this.declaringType = declaringType;
        this.defaultValue = defaultValue;
        this.constraints = constraints == null ? Set.of() : new HashSet<>(constraints);
        this.fullTextIndexed = fullTextIndexed;
        nullable = ConstraintUtils.getConstraint(this.constraints, NotNullConstraint.class) == null;
    }

    @Override
    public Object toFieldDtoOrReference(SchemaManager schemaManager) {
        Map<String, Object> dto;
        String typeName = type.getName();
        if (type instanceof PrimitiveType || schemaManager.getType(typeName) != null) {
            // primitive type or type registered with schema manager
            dto = new LinkedHashMap<>();
            dto.put("extends", typeName);
        } else {
            // anonymous type
            dto = type.toTypeDTO(schemaManager);
        }

        // constraints
        Map<String, Object> constraintsDTO;
        if (dto.containsKey("constraints")) {
            constraintsDTO = (Map<String, Object>) dto.get("constraints");
        } else {
            constraintsDTO = new LinkedHashMap<>();
        }
        // keep only those field-related, not those from its type
        Set<Constraint> fieldConstraints = new HashSet<>(constraints);
        fieldConstraints.removeAll(type.getConstraints());
        fieldConstraints.stream() //
                        .filter(c -> !(c instanceof TypeConstraint))
                        .forEach(c -> constraintsDTO.putAll(c.toDTO()));
        if (!constraintsDTO.isEmpty()) {
            dto.put("constraints", sortByKeyAndMapValue(constraintsDTO, Entry::getValue));
        }

        if (defaultValue != null) {
            dto.put("default",  defaultValue);
        }

        if (fullTextIndexed) {
            dto.put(FULL_TEXT_INDEXED, TRUE);
        }

        if (dto.size() == 1 && dto.containsKey("extends")) {
            // just "extends" (no constraints), return it directly
            return dto.get("extends");
        } else {
            return dto;
        }
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
}
