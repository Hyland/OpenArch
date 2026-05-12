/*
 * (C) Copyright 2024 Hyland (http://hyland.com/) and others.
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
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.schema.types.primitives;

import java.util.Map;

import org.nuxeo.ecm.core.schema.types.PrimitiveType;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;

/**
 * The any type.
 */
public final class AnyType extends PrimitiveType {

    public static final String ID = "any";

    public static final AnyType INSTANCE = new AnyType();

    private AnyType() {
        super(ID);
    }

    @Override
    public boolean isSimpleType() {
        return false;
    }

    @Override
    public Object convert(Object value) throws TypeException {
        if (value instanceof Map) {
            return value;
        }
        throw new TypeException(Map.class);
    }

    @Override
    public Object decode(String str) {
        return null; // XXX
    }

    @Override
    public String encode(Object object) {
        return object == null ? "" : object.toString(); // XXX
    }

    @Override
    public boolean support(Class<? extends Constraint> constraint) {
        if (NotNullConstraint.class.equals(constraint)) {
            return true;
        }
        return false;
    }

}
