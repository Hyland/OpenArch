/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.query.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.sql.model.Function;
import com.hyland.openarch.ecm.core.query.sql.model.Literal;
import com.hyland.openarch.ecm.core.query.sql.model.LiteralList;
import com.hyland.openarch.ecm.core.query.sql.model.Literals;
import com.hyland.openarch.ecm.core.query.sql.model.MultiExpression;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.query.sql.model.OperandList;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.Parameter;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.SelectClause;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.query.sql.model.WhereClause;

/**
 * Replaces the named {@link Parameter}s of a parsed query with the values bound to them.
 * <p>
 * Binding works on the tree, not on the query text, so a value can never be read as syntax: whatever it contains, it
 * ends up as a single {@link Literal}. This is the only supported way to get request-supplied values into a query.
 *
 * @since 12.0
 */
public class QueryParameterBinder {

    private QueryParameterBinder() {
        // utility class
    }

    /**
     * Returns the query with every named parameter replaced by its bound value.
     *
     * @param query the query, which may contain named parameters
     * @param parameters the values to bind, by parameter name
     * @throws QueryParseException if the query references a parameter that has no value, or if a value cannot be used
     *             where the parameter appears
     */
    public static Statement bind(Statement query, Map<String, Object> parameters) {
        Map<String, Object> params = parameters == null ? Map.of() : parameters;
        SelectClause select = bindSelect(query.getSelectClause(), params);
        WhereClause where = query.getWhereClause() == null ? null
                : new WhereClause((Predicate) bindPredicate(query.getWhereClause().predicate(), params));
        return new Statement(select, query.getFromClause(), where, query.getOrderByClause());
    }

    protected static SelectClause bindSelect(SelectClause select, Map<String, Object> params) {
        if (select == null || select.elements().isEmpty()) {
            return select;
        }
        return new SelectClause(select.elements().stream().map(o -> bindOperand(o, params)).toList());
    }

    protected static Operand bindPredicate(Operand predicate, Map<String, Object> params) {
        if (predicate instanceof MultiExpression multiExpression) {
            List<Predicate> predicates = multiExpression.predicates.stream()
                                                                   .map(p -> (Predicate) bindPredicate(p, params))
                                                                   .toList();
            return new MultiExpression(multiExpression.operator, predicates);
        }
        if (!(predicate instanceof Predicate expression)) {
            return bindOperand(predicate, params);
        }
        Operand lvalue = bindPredicate(expression.lvalue, params);
        Operand rvalue = expression.rvalue == null ? null : bindRvalue(expression, params);
        if (lvalue == expression.lvalue && rvalue == expression.rvalue) {
            return expression;
        }
        return new Predicate(lvalue, expression.operator, rvalue);
    }

    /**
     * Binds the right-hand side of an expression, which is a list of values for {@code IN} and {@code BETWEEN} and a
     * single value otherwise.
     */
    protected static Operand bindRvalue(Predicate expression, Map<String, Object> params) {
        Operator operator = expression.operator;
        boolean wantsList = operator == Operator.IN || operator == Operator.NOTIN || operator == Operator.BETWEEN
                || operator == Operator.NOTBETWEEN;
        Operand rvalue = expression.rvalue;
        if (!wantsList) {
            return bindPredicate(rvalue, params);
        }
        if (rvalue instanceof Parameter parameter) {
            // the whole list is a single parameter, as in "IN :subjects"
            return toLiteralList(parameter, valueOf(parameter, params));
        }
        if (rvalue instanceof OperandList operandList) {
            // some elements are parameters, as in "IN (:a, 'b')"
            List<Literal> literals = new ArrayList<>(operandList.size());
            for (Operand operand : operandList) {
                Operand boundOperand = bindOperand(operand, params);
                if (!(boundOperand instanceof Literal literal)) {
                    throw new QueryParseException(
                            "Expected a literal in a " + operator + " list, got: " + boundOperand);
                }
                literals.add(literal);
            }
            return new LiteralList(literals);
        }
        return rvalue;
    }

    protected static Operand bindOperand(Operand operand, Map<String, Object> params) {
        if (operand instanceof Parameter parameter) {
            Object value = valueOf(parameter, params);
            if (value instanceof Collection || value.getClass().isArray()) {
                throw new QueryParseException(
                        "Parameter " + parameter + " is a list, but a single value is expected here");
            }
            return toLiteral(parameter, value);
        }
        if (operand instanceof Predicate expression) {
            return bindPredicate(expression, params);
        }
        if (operand instanceof Function function) {
            if (function.args() == null) {
                return function;
            }
            List<Operand> args = function.args().elements().stream().map(arg -> bindOperand(arg, params)).toList();
            return new Function(function.name(), new OperandList(args));
        }
        return operand;
    }

    protected static Object valueOf(Parameter parameter, Map<String, Object> params) {
        Object value = params.get(parameter.name());
        if (value == null) {
            throw new QueryParseException("No value for query parameter " + parameter);
        }
        return value;
    }

    protected static LiteralList toLiteralList(Parameter parameter, Object value) {
        List<?> values;
        if (value instanceof Collection<?> collection) {
            // not List.copyOf, which rejects a null element with a raw NPE before toLiteral can report it properly
            values = new ArrayList<>(collection);
        } else if (value instanceof Object[] array) {
            values = Arrays.asList(array);
        } else if (value.getClass().isArray()) {
            throw new QueryParseException("Parameter " + parameter + " is an array of primitives, which is unsupported");
        } else {
            // a single value is a list of one
            values = List.of(value);
        }
        return new LiteralList(values.stream().map(v -> toLiteral(parameter, v)).toList());
    }

    protected static Literal toLiteral(Parameter parameter, Object value) {
        if (value == null) {
            throw new QueryParseException("Null value in query parameter " + parameter);
        }
        try {
            return Literals.toLiteral(value);
        } catch (RuntimeException e) {
            throw new QueryParseException(
                    "Cannot use value of type " + value.getClass().getName() + " for query parameter " + parameter, e);
        }
    }

}
