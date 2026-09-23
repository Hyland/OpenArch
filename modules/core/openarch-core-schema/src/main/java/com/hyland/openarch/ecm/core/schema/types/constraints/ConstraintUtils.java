/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.core.schema.types.constraints;

import static java.util.stream.Collectors.toSet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import com.hyland.openarch.ecm.core.schema.types.TypeException;
import com.hyland.openarch.ecm.core.schema.types.primitives.DateType;
import com.hyland.openarch.runtime.RuntimeServiceException;

/**
 * Some usefull utils for Constraint API.
 *
 * @since 7.1
 */
public final class ConstraintUtils {

    public static final String DATE_FORMAT = "yyyy-MM-dd";

    private ConstraintUtils() {
    }

    /**
     * Supports {@link Instant}, {@link Number} and {@link String} formatted as YYYY-MM-DD
     * or YYYY-MM-DDTHH:MM:SS.sss+TZ
     *
     * @param object Any object
     * @return a date represented as number of milliseconds since january 1 1970 if the object is supported, null
     *         otherwise.
     * @since 7.1
     */
    public static Long objectToTimeMillis(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof Number number) {
            return number.longValue();
        }
        try {
            return DateType.INSTANCE.convert(object).toEpochMilli();
        } catch (TypeException | ArithmeticException e) {
            return null;
        }
    }

    /**
     * Supports any object which toString method return a numeric as String.
     *
     * @param object Any object
     * @return a BigDecimal if the object represent a number, null otherwise.
     * @since 7.1
     */
    public static BigDecimal objectToBigDecimal(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return new BigDecimal(object.toString());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Supports any object which toString method return a positive numeric as String.
     *
     * @param object Any object
     * @return a positive long value (rounded if needed) if the object represent a positive numeric, null otherwise.
     * @since 7.1
     */
    public static Long objectToPostiveLong(Object object) {
        if (object == null) {
            return null;
        }
        try {
            Long result = Long.parseLong(object.toString());
            if (result >= 0) {
                return result;
            } else {
                return null;
            }
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Constraint> T getConstraint(Collection<Constraint> constraints, Class<T> constraintClass) {
        for (Constraint constraint : constraints) {
            if (constraint.getClass().equals(constraintClass)) {
                return (T) constraint;
            }
        }
        return null;
    }

    private static final Set<Function<Map<String, Object>, Constraint>> MAP_TO_CONSTRAINTS_FUNCS = new HashSet<>(List.of( //
            NotNullConstraint::fromMap, //
            LengthConstraint::fromMap, //
            NumericIntervalConstraint::fromMap, //
            DateIntervalConstraint::fromMap, //
            EnumConstraint::fromMap, //
            PatternConstraint::fromMap, //
            ObjectResolverConstraint::fromMap));

    @SuppressWarnings("unchecked")
    public static Set<Constraint> constraintsFromMap(Object o) {
        if (o == null) {
            return Set.of();
        }
        if (!(o instanceof Map map)) {
            throw new RuntimeServiceException("Invalid constraints: " + o);
        }
        return MAP_TO_CONSTRAINTS_FUNCS.stream() //
                                       .map(f -> f.apply(map))
                                       .filter(Objects::nonNull)
                                       .collect(toSet());
    }

}
