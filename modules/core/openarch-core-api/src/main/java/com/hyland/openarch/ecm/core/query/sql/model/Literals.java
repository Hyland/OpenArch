/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.query.sql.model;

import static java.time.temporal.ChronoUnit.DAYS;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.List;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.query.QueryParseException;

/**
 * Helper class for {@link Literal} and {@link LiteralList}.
 *
 * @since 9.3
 */
public class Literals {

    private Literals() {
        // this is a utility class
    }

    public static Object valueOf(Operand operand) {
        if (operand instanceof LiteralList literalList) {
            return valueOf(literalList);
        } else if (operand instanceof Literal literal) {
            return valueOf(literal);
        }
        throw new QueryParseException("Operand is not a Literal neither a LiteralList, op=" + operand);
    }

    public static List<Object> valueOf(LiteralList litList) {
        return litList.elements().stream().map(Literals::valueOf).toList();
    }

    public static Object valueOf(Literal lit) {
        return switch (lit) {
            case BooleanLiteral(var value) -> Boolean.valueOf(value);
            case DoubleLiteral(var value) -> Double.valueOf(value);
            case IntegerLiteral(var value) -> Long.valueOf(value);
            case StringLiteral(var value) -> value;
            case DateLiteral(var value, var onlyDate) -> {
                Instant date = value.toInstant();
                if (onlyDate) {
                    date = date.truncatedTo(DAYS);
                }
                yield date;
            }
        };
    }

    public static Literal toLiteral(Object value) {
        if (value instanceof Boolean boolVal) {
            return new BooleanLiteral(boolVal);
        } else if (value instanceof Instant instant) {
            return new DateLiteral(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
        } else if (value instanceof Temporal tempVal) {
            return new DateLiteral(ZonedDateTime.from(tempVal));
        } else if (value instanceof Double doubleVal) {
            return new DoubleLiteral(doubleVal);
        } else if (value instanceof Float floatVal) {
            return new DoubleLiteral(floatVal);
        } else if (value instanceof BigDecimal bigDecimal) {
            return new DoubleLiteral(bigDecimal); // we don't have a DecimalLiteral
        } else if (value instanceof Integer intVal) {
            return new IntegerLiteral(intVal);
        } else if (value instanceof Long longVal) {
            return new IntegerLiteral(longVal);
        } else if (value instanceof String strVal) {
            return new StringLiteral(strVal);
        }
        throw new RepositoryException("Unknown type to convert to literal, value=" + value);
    }

}
