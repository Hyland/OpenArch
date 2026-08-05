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
package org.nuxeo.ecm.core.schema.types.primitives;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.schema.types.PrimitiveType;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.EnumConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.PatternConstraint;

/**
 * The double type.
 */
public final class DoubleType extends PrimitiveType {

    public static final String ID = "double";

    public static final DoubleType INSTANCE = new DoubleType();

    private DoubleType() {
        super(ID);
    }

    @Override
    public Object convert(Object value) throws TypeException {
        if (value instanceof Double) {
            return value;
        } else if (value instanceof Float f) {
            // use toString and not doubleValue to avoid unexpected over-precision
            return Double.valueOf(f.toString());
        } else if (value instanceof Number number) {
            return Double.valueOf(number.doubleValue());
        } else if (value instanceof String string) {
            try {
                return Double.valueOf(string);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        throw new TypeException(Double.class);
    }

    @Override
    public Object decode(String str) {
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        try {
            return Double.valueOf(str);
        } catch (NumberFormatException e) {
            return Double.valueOf(0.0);
        }
    }

    @Override
    public String encode(Object object) {
        if (object instanceof Double) {
            return object.toString();
        } else if (object instanceof Number) {
            return object.toString();
        } else {
            return object != null ? (String) object : "";
        }
    }

    protected Object readResolve() {
        return INSTANCE;
    }

    @Override
    public boolean support(Class<? extends Constraint> constraint) {
        if (NotNullConstraint.class.equals(constraint)) {
            return true;
        }
        if (EnumConstraint.class.equals(constraint)) {
            return true;
        }
        if (PatternConstraint.class.equals(constraint)) {
            return true;
        }
        return NumericIntervalConstraint.class.equals(constraint);
    }

}
