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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.dto.ConstraintsDTO;
import org.nuxeo.ecm.core.schema.dto.TypeDTO;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;

/**
 * Abstract (common) implementation for a Type.
 */
public abstract class AbstractType implements Type {

    protected final String name;

    protected final Type superType;

    protected final String projectId;

    protected final Set<Constraint> constraints;

    protected AbstractType(Type superType, String name, String projectId) {
        this.name = name;
        this.superType = superType;
        this.projectId = projectId;
        constraints = new HashSet<>();
    }

    @Override
    public TypeDTO toTypeDTO(SchemaManager schemaManager) {
        return new TypeDTO(projectId, null, ConstraintsDTO.of(constraints), null, null);
    }

    @Override
    public Type getSuperType() {
        return superType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getProjectId() {
        return projectId;
    }

    @Override
    public boolean isSimpleType() {
        return false;
    }

    @Override
    public boolean isComplexType() {
        return false;
    }

    @Override
    public boolean isListType() {
        return false;
    }

    @Override
    public boolean isCompositeType() {
        return false;
    }

    @Override
    public boolean validate(Object object) {
        return true;
    }

    @Override
    public Object decode(String string) {
        return null;
    }

    @Override
    public String encode(Object object) {
        return null;
    }

    @Override
    public Set<Constraint> getConstraints() {
        Set<Constraint> localConstraints = new HashSet<>();
        if (getSuperType() instanceof SimpleType simpleType) {
            SimpleType localSuperType = simpleType;
            localConstraints.addAll(localSuperType.getConstraints());
        }
        localConstraints.addAll(constraints);
        return Collections.unmodifiableSet(localConstraints);
    }

    public void addConstraints(Collection<Constraint> localConstraints) {
        constraints.addAll(localConstraints);
    }

    protected boolean validateConstraints(Object object) {
        for (Constraint constraint : constraints) {
            if (!constraint.validate(object)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ObjectResolver getObjectResolver() {
        return null;
    }

}
