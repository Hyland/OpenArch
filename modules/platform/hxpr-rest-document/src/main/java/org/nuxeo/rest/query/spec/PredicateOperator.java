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

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;

/**
 * The operators a query predicate can use, as spelled in a request.
 * <p>
 * Most map straight onto an NXQL {@link Operator}; the rest are shorthands that expand into one, which is why the
 * mapping lives here rather than being a name lookup.
 *
 * @since 12.0
 */
public enum PredicateOperator {

    EQ("=", Operator.EQ), //
    NOTEQ("<>", Operator.NOTEQ), //
    /** Alternative spelling of {@code <>}. */
    NOTEQ_C("!=", Operator.NOTEQ), //
    LT("<", Operator.LT), //
    GT(">", Operator.GT), //
    LTEQ("<=", Operator.LTEQ), //
    GTEQ(">=", Operator.GTEQ), //
    LIKE("LIKE", Operator.LIKE), //
    ILIKE("ILIKE", Operator.ILIKE), //
    NOTLIKE("NOT LIKE", Operator.NOTLIKE), //
    NOTILIKE("NOT ILIKE", Operator.NOTILIKE), //
    BETWEEN("BETWEEN", Operator.BETWEEN), //
    IN("IN", Operator.IN), //
    NOTIN("NOT IN", Operator.NOTIN), //
    STARTSWITH("STARTSWITH", Operator.STARTSWITH), //
    /** True when the field is the empty string. */
    EMPTY("EMPTY", Operator.EQ), //
    /** Alternative spelling of {@code EMPTY}. */
    ISEMPTY("ISEMPTY", Operator.EQ), //
    /** Full-text match; the field is looked up under {@code sys_fulltext}. */
    FULLTEXT("FULLTEXT", Operator.EQ), //
    /** Alternative spelling of {@code FULLTEXT}, kept for backward compatibility. */
    FULLTEXT_ALL("FULLTEXT ALL", Operator.EQ), //
    /** {@code IS NULL} when the value is true, {@code IS NOT NULL} when it is false. */
    ISNULL("IS NULL", Operator.ISNULL);

    protected static final Map<String, PredicateOperator> BY_NAME = Arrays.stream(values())
                                                                         .collect(Collectors.toMap(
                                                                                 PredicateOperator::operatorName,
                                                                                 Function.identity()));

    private final String operatorName;

    private final Operator operator;

    PredicateOperator(String operatorName, Operator operator) {
        this.operatorName = operatorName;
        this.operator = operator;
    }

    /** The operator as spelled in a request. */
    public String operatorName() {
        return operatorName;
    }

    /** The NXQL operator this maps onto. */
    public Operator operator() {
        return operator;
    }

    /**
     * @throws NuxeoException with a 400 status if the name is not a known operator
     */
    public static PredicateOperator forName(String name) {
        PredicateOperator operator = name == null ? null : BY_NAME.get(name.trim().toUpperCase(Locale.ROOT));
        if (operator == null) {
            throw new NuxeoException("Unsupported predicate operator: " + name, BAD_REQUEST.value());
        }
        return operator;
    }

}
