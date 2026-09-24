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
package org.nuxeo.rest.query.spec;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.nxql.FulltextSanitizer;
import com.hyland.openarch.ecm.core.query.sql.HXQLParser;
import com.hyland.openarch.ecm.core.query.sql.model.Literal;
import com.hyland.openarch.ecm.core.query.sql.model.LiteralList;
import com.hyland.openarch.ecm.core.query.sql.model.Literals;
import com.hyland.openarch.ecm.core.query.sql.model.MultiExpression;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.rest.query.io.WhereClauseDTO.RestPredicate;
import org.nuxeo.rest.query.io.WhereClauseDTO.RestPredicateParameter;

/**
 * Turns a request's {@link RestPredicate} into an NXQL {@link Predicate}.
 * <p>
 * A predicate whose value was not supplied is dropped rather than being an error, which is what lets a single query
 * definition serve a search form where the user filled in only some of the fields. That is why building returns an
 * {@link Optional}.
 *
 * @since 12.0
 */
public class PredicateBuilder {

    /** Prefix marking a predicate parameter name, as opposed to a literal value. */
    protected static final String PARAM_PREFIX = ":";

    private PredicateBuilder() {
        // utility class
    }

    /**
     * Builds the conjunction of the given predicates, dropping those whose values were not supplied.
     */
    public static List<Predicate> build(List<RestPredicate> predicates, Map<String, Object> parameters) {
        return predicates.stream().map(p -> build(p, parameters)).flatMap(Optional::stream).toList();
    }

    /**
     * Builds one predicate, or nothing if its values were not supplied.
     */
    public static Optional<Predicate> build(RestPredicate predicate, Map<String, Object> parameters) {
        PredicateOperator operator = resolveOperator(predicate, parameters);
        String field = predicate.searchParameter();
        if (field == null) {
            throw new NuxeoException("Predicate has no searchParameter", BAD_REQUEST.value());
        }
        List<RestPredicateParameter> fields = predicate.parameters();
        return switch (operator) {
            case EMPTY, ISEMPTY -> Optional.of(new Predicate(reference(field), Operator.EQ, new StringLiteral("")));
            case ISNULL -> buildIsNull(field, value(fields, 0, parameters));
            case FULLTEXT, FULLTEXT_ALL -> buildFulltext(field, value(fields, 0, parameters));
            case BETWEEN -> buildBetween(field, value(fields, 0, parameters), value(fields, 1, parameters));
            case IN, NOTIN -> buildIn(field, operator, value(fields, 0, parameters));
            case STARTSWITH -> buildStartsWith(field, value(fields, 0, parameters));
            case EQ, NOTEQ, NOTEQ_C, LT, GT, LTEQ, GTEQ, LIKE, ILIKE, NOTLIKE, NOTILIKE -> //
                    buildComparison(field, operator.operator(), value(fields, 0, parameters));
        };
    }

    /**
     * The operator is normally given in the predicate, but it can also be taken from a request parameter, which is how
     * a search form lets the user pick one.
     */
    protected static PredicateOperator resolveOperator(RestPredicate predicate, Map<String, Object> parameters) {
        String operator = predicate.operator();
        if (operator != null && operator.startsWith(PARAM_PREFIX)) {
            Object supplied = parameters.get(operator.substring(PARAM_PREFIX.length()));
            if (supplied instanceof String string && !string.isBlank()) {
                operator = string;
            } else {
                throw new NuxeoException("No operator supplied for parameter " + operator, BAD_REQUEST.value());
            }
        }
        return PredicateOperator.forName(operator);
    }

    protected static Optional<Predicate> buildComparison(String field, Operator operator, Object value) {
        return literal(value).map(literal -> new Predicate(reference(field), operator, literal));
    }

    protected static Optional<Predicate> buildIsNull(String field, Object value) {
        if (!(value instanceof Boolean isNull)) {
            // value not supplied, or not a boolean: ignore the predicate
            return Optional.empty();
        }
        return Optional.of(new Predicate(reference(field), isNull ? Operator.ISNULL : Operator.ISNOTNULL, null));
    }

    protected static Optional<Predicate> buildFulltext(String field, Object value) {
        return literal(value).map(literal -> new Predicate(fulltextReference(field), Operator.EQ,
                new StringLiteral(FulltextSanitizer.sanitize(literal.asString()))));
    }

    protected static Optional<Predicate> buildBetween(String field, Object min, Object max) {
        Optional<Literal> minLiteral = literal(min);
        Optional<Literal> maxLiteral = literal(max);
        if (minLiteral.isPresent() && maxLiteral.isPresent()) {
            LiteralList bounds = new LiteralList(List.of(minLiteral.get(), maxLiteral.get()));
            return Optional.of(new Predicate(reference(field), Operator.BETWEEN, bounds));
        }
        // an open-ended range is a plain comparison
        if (maxLiteral.isPresent()) {
            return Optional.of(new Predicate(reference(field), Operator.LTEQ, maxLiteral.get()));
        }
        if (minLiteral.isPresent()) {
            return Optional.of(new Predicate(reference(field), Operator.GTEQ, minLiteral.get()));
        }
        return Optional.empty();
    }

    protected static Optional<Predicate> buildIn(String field, PredicateOperator operator, Object value) {
        List<Literal> literals = literals(value);
        if (literals.isEmpty()) {
            return Optional.empty();
        }
        if (literals.size() == 1) {
            // a one-element list is an equality
            Operator equality = operator == PredicateOperator.NOTIN ? Operator.NOTEQ : Operator.EQ;
            return Optional.of(new Predicate(reference(field), equality, literals.get(0)));
        }
        return Optional.of(new Predicate(reference(field), operator.operator(), new LiteralList(literals)));
    }

    protected static Optional<Predicate> buildStartsWith(String field, Object value) {
        List<Literal> literals = literals(value);
        if (literals.isEmpty()) {
            return Optional.empty();
        }
        if (literals.size() == 1) {
            return Optional.of(new Predicate(reference(field), Operator.STARTSWITH, literals.get(0)));
        }
        // STARTSWITH takes a single prefix, so several of them are a disjunction
        List<Predicate> alternatives = literals.stream()
                                               .map(literal -> new Predicate(reference(field), Operator.STARTSWITH,
                                                       literal))
                                               .toList();
        return Optional.of(new MultiExpression(Operator.OR, alternatives));
    }

    /**
     * A field name goes into the query as an identifier, which HXQL has no way to quote, so one that is not a valid
     * identifier has to be rejected rather than escaped: unchecked, a name such as {@code p = 'x' OR q} would go into
     * the query as syntax rather than as a name. The grammar itself decides what is valid.
     */
    protected static Reference reference(String field) {
        try {
            return HXQLParser.parseReference(field);
        } catch (QueryParseException e) {
            throw new NuxeoException("Not a valid field name: " + field, e, BAD_REQUEST.value());
        }
    }

    /**
     * A full-text predicate matches under {@code sys_fulltext}, so a plain field name is qualified with it.
     */
    protected static Reference fulltextReference(String field) {
        return reference(field.startsWith(SYS_FULLTEXT) ? field : SYS_FULLTEXT + '.' + field);
    }

    /**
     * The value bound to a predicate's nth field, or its default value, or {@code null} if neither was supplied.
     */
    protected static Object value(List<RestPredicateParameter> fields, int index, Map<String, Object> parameters) {
        if (index >= fields.size()) {
            return null;
        }
        RestPredicateParameter field = fields.get(index);
        String name = field.name();
        if (name == null) {
            // a predicate with no parameter name is a static one, carrying its value inline
            return field.defaultValue();
        }
        Object value = parameters.get(name.startsWith(PARAM_PREFIX) ? name.substring(PARAM_PREFIX.length()) : name);
        return value != null ? value : field.defaultValue();
    }

    /**
     * Converts a supplied value to a literal, or nothing if it was not supplied. A blank string counts as not
     * supplied, so that an empty search field does not narrow the results to the empty string.
     */
    protected static Optional<Literal> literal(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String string) {
            String trimmed = string.trim();
            return trimmed.isEmpty() ? Optional.empty() : Optional.of(new StringLiteral(trimmed));
        }
        if (value instanceof Collection || value.getClass().isArray()) {
            throw new NuxeoException("Expected a single value, got a list: " + value, BAD_REQUEST.value());
        }
        return Optional.of(toLiteral(value));
    }

    /**
     * Converts a supplied value to a list of literals, accepting a single value as a list of one. Blank strings are
     * skipped, as they are for a single value.
     */
    protected static List<Literal> literals(Object value) {
        if (value == null) {
            return List.of();
        }
        Collection<?> values;
        if (value instanceof Collection<?> collection) {
            values = collection;
        } else if (value instanceof Object[] array) {
            values = Arrays.asList(array);
        } else {
            // a single value is a list of one, or nothing if it was blank
            return literal(value).map(List::of).orElse(List.of());
        }
        List<Literal> literals = new ArrayList<>(values.size());
        for (Object element : values) {
            literal(element).ifPresent(literals::add);
        }
        return literals;
    }

    protected static Literal toLiteral(Object value) {
        try {
            return Literals.toLiteral(value);
        } catch (RuntimeException e) {
            throw new NuxeoException("Cannot use a value of type " + value.getClass().getName() + " in a predicate",
                    e, BAD_REQUEST.value());
        }
    }

}
