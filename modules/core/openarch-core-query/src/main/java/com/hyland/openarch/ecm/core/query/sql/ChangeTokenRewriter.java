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

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CHANGE_TOKEN;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_GENERATION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_USER_GENERATION;

import java.util.ArrayList;
import java.util.List;

import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.sql.model.MultiExpression;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Predicates;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.query.sql.model.StringLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.WhereClause;

/**
 * Rewrites the predicates on the computed {@code sys_changeToken} property into predicates on the underlying
 * {@code sys_gen} and {@code sys_ugen} generations, which are the ones actually stored.
 * <p>
 * A change token is the string {@code "<generation>-<userGeneration>"}, so {@code sys_changeToken = '3-1'} becomes
 * {@code sys_gen = 3 AND sys_ugen = 1}. Only equality makes sense on an opaque token, so {@code =} and {@code <>} are
 * supported and anything else is rejected.
 * <p>
 * This is an HXQL-only compatibility shim: Expr never goes through the HXQL parser and keeps using the generations.
 */
public class ChangeTokenRewriter {

    /** Separator between the generation and the user generation in a change token. */
    public static final String TOKEN_SEP = "-";

    private ChangeTokenRewriter() {
        // utility class
    }

    /**
     * Returns the query with every {@code sys_changeToken} predicate of its {@code WHERE} clause rewritten.
     */
    public static Statement rewrite(Statement query) {
        WhereClause where = query.getWhereClause();
        if (where == null) {
            return query;
        }
        Predicate predicate = rewrite(where.predicate());
        if (predicate == where.predicate()) {
            // unchanged
            return query;
        }
        return query.withPredicate(predicate);
    }

    /**
     * Returns the predicate with every {@code sys_changeToken} predicate it contains rewritten.
     */
    public static Predicate rewrite(Predicate predicate) {
        if (isChangeTokenReference(predicate.lvalue)) {
            return rewriteChangeToken(predicate);
        }
        if (predicate instanceof MultiExpression multiExpression) {
            // recurse into multiexpression predicates
            List<Predicate> predicates = new ArrayList<>();
            boolean unchanged = true;
            for (Predicate original : multiExpression.predicates) {
                Predicate rewritten = rewrite(original);
                predicates.add(rewritten);
                unchanged &= rewritten == original;
            }
            if (unchanged) {
                return predicate;
            }
            return new MultiExpression(multiExpression.operator, predicates);
        }
        // recurse into lvalue/rvalue
        Operand lvalue = predicate.lvalue instanceof Predicate left ? rewrite(left) : predicate.lvalue;
        Operand rvalue = predicate.rvalue instanceof Predicate right ? rewrite(right) : predicate.rvalue;
        boolean unchanged = lvalue == predicate.lvalue && rvalue == predicate.rvalue;
        if (unchanged) {
            return predicate;
        }
        return new Predicate(lvalue, predicate.operator, rvalue);
    }

    protected static boolean isChangeTokenReference(Operand operand) {
        return operand instanceof Reference reference && SYS_CHANGE_TOKEN.equals(reference.name());
    }

    protected static Predicate rewriteChangeToken(Predicate predicate) {
        return switch (predicate.operator) {
            case EQ -> matches(literal(predicate));
            case NOTEQ -> doesNotMatch(literal(predicate));
            default -> throw new QueryParseException(
                    "Operator " + predicate.operator + " cannot be used on " + SYS_CHANGE_TOKEN);
        };
    }

    /** {@code sys_gen = generation AND sys_ugen = userGeneration} */
    protected static Predicate matches(StringLiteral token) {
        long[] generations = parse(token);
        return Predicates.and(Predicates.eq(SYS_GENERATION, generations[0]),
                Predicates.eq(SYS_USER_GENERATION, generations[1]));
    }

    /** {@code sys_gen <> generation OR sys_ugen <> userGeneration} */
    protected static Predicate doesNotMatch(StringLiteral token) {
        long[] generations = parse(token);
        return Predicates.or(Predicates.noteq(SYS_GENERATION, generations[0]),
                Predicates.noteq(SYS_USER_GENERATION, generations[1]));
    }

    protected static StringLiteral literal(Predicate predicate) {
        if (!(predicate.rvalue instanceof StringLiteral literal)) {
            throw new QueryParseException("Expected a change token value, got: " + predicate.rvalue);
        }
        return literal;
    }

    /** Returns the generation and the user generation of a {@code "<generation>-<userGeneration>"} change token. */
    protected static long[] parse(StringLiteral token) {
        String value = token.value();
        int sep = value.indexOf(TOKEN_SEP);
        if (sep > 0) {
            try {
                return new long[] { Long.parseLong(value.substring(0, sep)),
                        Long.parseLong(value.substring(sep + TOKEN_SEP.length())) };
            } catch (NumberFormatException e) {
                // fall through to the exception below
            }
        }
        throw new QueryParseException("Invalid change token: " + token);
    }

}
