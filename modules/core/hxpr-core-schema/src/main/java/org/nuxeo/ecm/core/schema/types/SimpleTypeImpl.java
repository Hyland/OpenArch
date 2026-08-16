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

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.dto.ConstraintsDTO;
import org.nuxeo.ecm.core.schema.dto.TypeDTO;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;

/**
 * Implementation of a simple type that is not primitive (and therefore has constraints).
 */
public class SimpleTypeImpl extends AbstractType implements SimpleType {

    private ObjectResolver resolver;

    private PrimitiveType primitiveType;

    public SimpleTypeImpl(SimpleType superType, String name, String projectId) {
        super(superType, name, projectId);
        primitiveType = superType.getPrimitiveType();
    }

    public SimpleTypeImpl(SimpleType superType, String name) {
        this(superType, name, null);
    }

    @Override
    public TypeDTO toTypeDTO(SchemaManager schemaManager) {
        // the supertype chain supplies "extends" and any inherited constraints
        TypeDTO superDTO = superType.toTypeDTO(schemaManager);
        ConstraintsDTO constraints = ConstraintsDTO.merge(superDTO.constraints(),
                super.toTypeDTO(schemaManager).constraints());
        return new TypeDTO(projectId, null, constraints, null, superDTO.extend());
    }

    @Override
    public boolean validate(Object object) {
        if (object == null) {
            return true;
        }
        if (validateConstraints(object)) {
            return getSuperType().validate(object);
        }
        return false;
    }

    @Override
    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isSimpleType() {
        return true;
    }

    public void setResolver(ObjectResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public ObjectResolver getObjectResolver() {
        return resolver;
    }

    @Override
    public Object decode(String str) {
        return primitiveType.decode(str);
    }

    @Override
    public String encode(Object value) {
        return primitiveType.encode(value);
    }

    @Override
    public Object convert(Object value) throws TypeException {
        return primitiveType.convert(value);
    }

}
