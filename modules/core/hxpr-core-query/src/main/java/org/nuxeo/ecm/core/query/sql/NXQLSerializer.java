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
package org.nuxeo.ecm.core.query.sql;

import static org.nuxeo.common.utils.DateUtils.formatISODateTime;

import java.util.List;

import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.DateLiteral;
import org.nuxeo.ecm.core.query.sql.model.DoubleLiteral;
import org.nuxeo.ecm.core.query.sql.model.Function;
import org.nuxeo.ecm.core.query.sql.model.IntegerLiteral;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.OperandList;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Parameter;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.query.sql.model.WhereClause;

/**
 * Renders an NXQL abstract syntax tree back to NXQL text.
 * <p>
 * This is the single place where NXQL text is produced from a tree, and the counterpart of
 * {@link SQLQueryParser#parse}: {@code parse(serialize(parse(q)))} is equal to {@code parse(q)} for any parsable
 * {@code q}. In particular string literals are escaped here and nowhere else, so a tree holding attacker-controlled
 * values cannot produce a query that means something other than the tree says.
 * <p>
 * Parentheses are emitted only where operator precedence requires them.
 *
 * @since 12.0
 */
public class NXQLSerializer {

    // precedence levels, lowest binding first
    protected static final int PREC_OR = 1;

    protected static final int PREC_AND = 2;

    protected static final int PREC_NOT = 3;

    protected static final int PREC_COMPARISON = 4;

    protected static final int PREC_ATOM = 5;

    private NXQLSerializer() {
        // utility class
    }

    /**
     * Renders a complete query.
     */
    public static String serialize(SQLQuery query) {
        StringBuilder sb = new StringBuilder();
        appendSelect(sb, query.getSelectClause());
        appendFrom(sb, query);
        appendWhere(sb, query.getWhereClause());
        appendOrderBy(sb, query.getOrderByClause());
        if (query.getLimit() > 0) {
            sb.append(" LIMIT ").append(query.getLimit());
            if (query.getOffset() > 0) {
                sb.append(" OFFSET ").append(query.getOffset());
            }
        }
        return sb.toString();
    }

    /**
     * Renders a standalone predicate, as it would appear after {@code WHERE}.
     */
    public static String serialize(Predicate predicate) {
        StringBuilder sb = new StringBuilder();
        appendOperand(sb, predicate, PREC_OR);
        return sb.toString();
    }

    /**
     * Renders a standalone operand: a reference, a literal, a function or an expression.
     */
    public static String serialize(Operand operand) {
        StringBuilder sb = new StringBuilder();
        appendOperand(sb, operand, PREC_OR);
        return sb.toString();
    }

    protected static void appendSelect(StringBuilder sb, SelectClause select) {
        sb.append("SELECT ");
        if (select == null || select.elements.isEmpty()) {
            sb.append('*');
            return;
        }
        boolean first = true;
        for (Operand operand : select.elements) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            appendOperand(sb, operand, PREC_ATOM);
        }
    }

    protected static void appendFrom(StringBuilder sb, SQLQuery query) {
        sb.append(" FROM ").append(String.join(", ", query.getFromClause().elements));
    }

    protected static void appendWhere(StringBuilder sb, WhereClause where) {
        if (where == null || where.predicate() == null || isEmptyConjunction(where.predicate())) {
            return;
        }
        sb.append(" WHERE ");
        appendOperand(sb, where.predicate(), PREC_OR);
    }

    protected static void appendOrderBy(StringBuilder sb, OrderByClause orderBy) {
        if (orderBy == null || orderBy.elements() == null || orderBy.elements().isEmpty()) {
            return;
        }
        sb.append(" ORDER BY ");
        boolean first = true;
        for (OrderByExpr expr : orderBy.elements()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            appendOperand(sb, expr.reference(), PREC_ATOM);
            if (expr.isDescending()) {
                sb.append(" DESC");
            }
        }
    }

    /**
     * Appends an operand, parenthesizing it if it binds less tightly than the context requires.
     */
    protected static void appendOperand(StringBuilder sb, Operand operand, int minPrecedence) {
        int precedence = precedenceOf(operand);
        boolean parenthesize = precedence < minPrecedence;
        if (parenthesize) {
            sb.append('(');
        }
        if (operand instanceof MultiExpression multiExpression) {
            appendMultiExpression(sb, multiExpression);
        } else if (operand instanceof Predicate expression) {
            appendExpression(sb, expression);
        } else if (operand instanceof Reference reference) {
            appendReference(sb, reference);
        } else if (operand instanceof LiteralList literalList) {
            appendList(sb, literalList);
        } else if (operand instanceof Literal literal) {
            appendLiteral(sb, literal);
        } else if (operand instanceof Function function) {
            appendFunction(sb, function);
        } else if (operand instanceof OperandList operandList) {
            appendList(sb, operandList);
        } else if (operand instanceof Parameter parameter) {
            sb.append(':').append(parameter.name());
        } else {
            throw new QueryParseException("Cannot serialize operand: " + operand);
        }
        if (parenthesize) {
            sb.append(')');
        }
    }

    protected static void appendMultiExpression(StringBuilder sb, MultiExpression multiExpression) {
        List<Predicate> predicates = multiExpression.predicates;
        if (predicates.isEmpty()) {
            // an empty conjunction is vacuously true, an empty disjunction vacuously false
            appendOperand(sb, multiExpression.operator == Operator.OR ? Predicates.FALSE : Predicates.TRUE, PREC_OR);
            return;
        }
        int precedence = precedenceOf(multiExpression);
        boolean first = true;
        for (Predicate predicate : predicates) {
            if (!first) {
                sb.append(' ').append(multiExpression.operator).append(' ');
            }
            first = false;
            appendOperand(sb, predicate, precedence);
        }
    }

    protected static void appendExpression(StringBuilder sb, Predicate expression) {
        Operator operator = expression.operator;
        if (operator == Operator.BETWEEN || operator == Operator.NOTBETWEEN) {
            @SuppressWarnings("unchecked")
            List<? extends Operand> bounds = (List<? extends Operand>) expression.rvalue;
            appendOperand(sb, expression.lvalue, PREC_ATOM);
            sb.append(' ').append(operator).append(' ');
            appendOperand(sb, bounds.get(0), PREC_ATOM);
            sb.append(" AND ");
            appendOperand(sb, bounds.get(1), PREC_ATOM);
            return;
        }
        if (expression.rvalue == null) {
            // unary
            if (expression.isSuffix()) {
                appendOperand(sb, expression.lvalue, PREC_ATOM);
                sb.append(' ').append(operator);
            } else {
                sb.append(operator).append(' ');
                appendOperand(sb, expression.lvalue, precedenceOf(expression));
            }
            return;
        }
        int precedence = precedenceOf(expression);
        appendOperand(sb, expression.lvalue, precedence);
        sb.append(' ').append(operator).append(' ');
        // every binary operator is left-associative, so a right operand that binds equally tightly needs its own
        // parentheses to survive re-parsing; requiring one level tighter is what asks for them
        appendOperand(sb, expression.rvalue, precedence + 1);
    }

    protected static void appendReference(StringBuilder sb, Reference reference) {
        if (reference.ftHint() != null) {
            sb.append(reference.ftHint()).append(' ');
        }
        if (reference.cast() != null) {
            sb.append(reference.cast()).append('(').append(reference.name()).append(')');
        } else {
            sb.append(reference.name());
        }
    }

    protected static void appendFunction(StringBuilder sb, Function function) {
        sb.append(function.name()).append('(');
        if (function.args() != null) {
            boolean first = true;
            for (Operand arg : function.args()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                appendOperand(sb, arg, PREC_ATOM);
            }
        }
        sb.append(')');
    }

    protected static void appendList(StringBuilder sb, List<? extends Operand> operands) {
        sb.append('(');
        boolean first = true;
        for (Operand operand : operands) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            appendOperand(sb, operand, PREC_ATOM);
        }
        sb.append(')');
    }

    /**
     * Renders every kind of literal explicitly, rather than leaning on their toString, which delegates back here.
     */
    protected static void appendLiteral(StringBuilder sb, Literal literal) {
        switch (literal) {
            case StringLiteral(var value) -> sb.append(NXQL.escapeString(value));
            case BooleanLiteral(var value) -> sb.append(value ? "TRUE" : "FALSE");
            case IntegerLiteral(var value) -> sb.append(value);
            case DoubleLiteral(var value) -> sb.append(value);
            case DateLiteral(var value, var onlyDate) -> sb.append(onlyDate ? "DATE '" : "TIMESTAMP '")
                                                           .append(formatISODateTime(value, onlyDate))
                                                           .append('\'');
        }
    }

    /**
     * Whether the operand is an empty conjunction, which is vacuously true and so can be left out of a where clause
     * altogether. An empty disjunction cannot: it is vacuously false, and dropping it would turn a query matching
     * nothing into one matching everything.
     */
    protected static boolean isEmptyConjunction(Operand operand) {
        return operand instanceof MultiExpression multiExpression && multiExpression.predicates.isEmpty()
                && multiExpression.operator == Operator.AND;
    }

    protected static int precedenceOf(Operand operand) {
        if (!(operand instanceof Predicate expression)) {
            return PREC_ATOM;
        }
        return switch (expression.operator) {
            case OR -> PREC_OR;
            case AND -> PREC_AND;
            case NOT -> PREC_NOT;
            default -> PREC_COMPARISON;
        };
    }

}
