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
package com.hyland.hxpr.expr;

import java.util.Map;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.schema.SchemaManager;

import com.hyland.hxpr.expr.ast.Expr;

/**
 * Turns an expression about a content object into the query predicate it means, for the standard context where
 * {@code content} is the content object the expression is about and {@code user} is the current user.
 *
 * @see ExprEvaluator
 */
public class ExprPredicates {

    private ExprPredicates() {
        // utility class
    }

    /**
     * Converts an expression into a query operand.
     * <p>
     * The result is a {@link Predicate}, or a {@link BooleanLiteral} when the expression does not depend on the content
     * and the evaluator could therefore reduce it to a constant.
     *
     * @param expr the expression
     * @param principal the current user
     * @param schemaManager the schema manager
     * @return the operand
     * @throws QueryParseException if the expression is invalid
     */
    public static Operand toOperand(String expr, NuxeoPrincipal principal, SchemaManager schemaManager) {
        try {
            Expr parsed = new ExpressionVisitor().parse(expr);
            Map<String, Object> context = ExprEvaluator.queryContext(principal);
            return new ExprEvaluator(context, schemaManager).evaluateOperand(parsed);
        } catch (IllegalArgumentException e) {
            // both the parser and the evaluator signal errors this way
            throw new QueryParseException("Invalid expression: " + expr + ": " + e.getMessage(), e);
        }
    }

    /**
     * Converts an expression into a predicate usable as a query filter.
     * <p>
     * An expression that does not depend on the content is reduced to a constant by the evaluator: an always true one
     * filters nothing out and is returned as {@code null}, an always false one as {@link Predicates#FALSE} which no
     * document matches.
     *
     * @param expr the expression
     * @param principal the current user
     * @param schemaManager the schema manager
     * @return the predicate, or {@code null} if the expression filters nothing out
     * @throws QueryParseException if the expression is invalid or is not a predicate
     */
    public static Predicate toPredicate(String expr, NuxeoPrincipal principal, SchemaManager schemaManager) {
        Operand operand = toOperand(expr, principal, schemaManager);
        return switch (operand) {
            case Predicate predicate -> predicate;
            case BooleanLiteral(var value) -> value ? null : Predicates.FALSE;
            case null, default -> throw new QueryParseException("Expression is not a predicate: " + expr);
        };
    }

}
