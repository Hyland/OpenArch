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
package org.nuxeo.ecm.core.schema.types.primitives;

import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.schema.types.PrimitiveType;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.EnumConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.PatternConstraint;

/**
 * The decimal type.
 */
public final class DecimalType extends PrimitiveType {

    public static final String ID = "decimal";

    public static final DecimalType INSTANCE = new DecimalType();

    private DecimalType() {
        super(ID);
    }

    @Override
    public Object convert(Object value) throws TypeException {
        if (value instanceof BigDecimal) {
            return value;
        } else if (value instanceof Integer i) {
            return BigDecimal.valueOf(i);
        } else if (value instanceof Long l) {
            return BigDecimal.valueOf(l);
        } else if (value instanceof Float f) {
            return BigDecimal.valueOf(f);
        } else if (value instanceof Double d) {
            return BigDecimal.valueOf(d);
        } else if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        } else if (value instanceof String string) {
            try {
                return new BigDecimal(string);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        throw new TypeException(BigDecimal.class);
    }

    @Override
    public Object decode(String string) {
        if (StringUtils.isEmpty(string)) {
            return null;
        }
        try {
            return new BigDecimal(string);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public String encode(Object object) {
        if (object == null) {
            return "";
        }
        return ((BigDecimal) object).toString();
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
