/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PREFIX;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_ENCODING;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.DoubleLiteral;
import org.nuxeo.ecm.core.query.sql.model.IntegerLiteral;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.schema.SchemaManager;
import com.hyland.hxpr.expr.ast.Expr;
import com.hyland.hxpr.expr.ast.ExprBin;
import com.hyland.hxpr.expr.ast.ExprTern;
import com.hyland.hxpr.expr.ast.ExprUn;
import com.hyland.hxpr.expr.ast.Lit;
import com.hyland.hxpr.expr.ast.Op;
import com.hyland.hxpr.expr.ast.Val;
import com.hyland.hxpr.expr.ast.ValCall;
import com.hyland.hxpr.expr.ast.ValDot;
import com.hyland.hxpr.expr.ast.ValId;
import com.hyland.hxpr.expr.ast.ValSimple;

/**
 * Converts an Expression Security Policy expression (represented as an {@link Expr} AST) into a query operand
 * (represented as an {@link Operand} AST).
 * <p>
 * The conversion is aware of the {@code context} for the evaluation, which contains the set of "toplevel" names
 * recognized in the expression. One name (e.g., "content") is mapped to {@link #CONTENT} to represent the content
 * object being the subject of the statement, and usually one or more other names are mapped to static objects
 * (e.g., the current user).
 * <p>
 * The conversion attempts to short-circuit evaluation when all arguments of an operator are statically known or for
 * boolean expressions. This allows detection of trivial expressions and bypassing of operand generation is some
 * cases.
 */
public class ExprEvaluator {

    /** Special marker representing the content object in the context, for Operand generation. */
    public static final Object CONTENT = new Object();

    /** The context key for the content object being evaluated. */
    public static final String CONTEXT_CONTENT = "content";

    /** The context key for the current user. */
    public static final String CONTEXT_USER = "user";

    private static final String USER_ID = "id";

    private static final String USER_GROUPS = "groups";

    private static final String CONTAINS = "contains";

    private final Map<String, Object> context;

    /** SchemaManager required in order to know which fields store false as null. */
    private final SchemaManager schemaManager;

    /** Whether security-related properties may be used. */
    private final boolean allowSecurityProperties;

    /**
     * @param context the context for the evaluation
     * @param schemaManager the schema manager
     */
    public ExprEvaluator(Map<String, Object> context, SchemaManager schemaManager) {
        this(context, schemaManager, false);
    }

    /**
     * @param context the context for the evaluation
     * @param schemaManager the schema manager
     * @param allowSecurityProperties whether the properties {@link SchemaManager#isSecurityProperty} identifies may
     *            nevertheless be used, which is the case for an administrator, who can see them anyway
     */
    public ExprEvaluator(Map<String, Object> context, SchemaManager schemaManager, boolean allowSecurityProperties) {
        this.context = context;
        this.schemaManager = schemaManager;
        this.allowSecurityProperties = allowSecurityProperties;
    }

    /** The context for evaluating an expression against the content a query selects, with no user. */
    public static Map<String, Object> queryContext() {
        return context(CONTENT, null);
    }

    /**
     * The context for evaluating an expression against the content a query selects, for the given user.
     *
     * @param principal the user, or {@code null} if there is none
     */
    public static Map<String, Object> queryContext(NuxeoPrincipal principal) {
        return context(CONTENT, principal);
    }

    /**
     * The context for evaluating an expression against a given document, with no user.
     *
     * @param document the document, or {@code null} if there is none
     */
    public static Map<String, Object> documentContext(InternalDocument document) {
        return context(document, null);
    }

    /**
     * The context for evaluating an expression against a given document, for the given user.
     *
     * @param document the document, or {@code null} if there is none
     * @param principal the user, or {@code null} if there is none
     */
    public static Map<String, Object> documentContext(InternalDocument document, NuxeoPrincipal principal) {
        return context(document, principal);
    }

    private static Map<String, Object> context(Object content, NuxeoPrincipal principal) {
        // a HashMap and not a Map.of, as the content or the user may be missing
        Map<String, Object> context = new HashMap<>();
        context.put(CONTEXT_CONTENT, content);
        context.put(CONTEXT_USER, principal);
        return context;
    }

    /**
     * Converts the expression into a query operand.
     *
     * @param expr the expression
     * @return the operand
     * @throws IllegalArgumentException if there's an error
     */
    public Operand evaluateOperand(Expr expr) throws IllegalArgumentException {
        Object obj = visit(expr);
        if (obj instanceof Operand op) {
            return op;
        } else if (obj == CONTENT) {
            throw invalidExpr(expr);
        } else {
            return toLiteral(obj, expr);
        }
    }

    /**
     * Evaluates the expression into a pure value.
     *
     * @param expr the expression
     * @return the value
     * @throws IllegalArgumentException if there's an error
     */
    public Object evaluateValue(Expr expr) throws IllegalArgumentException {
        Object obj = visit(expr);
        if (obj instanceof Operand || obj == CONTENT) {
            throw invalidExpr(expr);
        } else {
            return obj;
        }
    }

    /**
     * Evaluates the expression into a basic value. If references are encountered in the expression (from the
     * {@link #CONTENT} in the context), an {@link Operand} (usually a {@link Predicate}) is built instead.
     *
     * @param expr the expression
     * @return the result (a basic value or an {@link Operand})
     * @throws IllegalArgumentException if there's an error
     */
    private Object visit(Expr expr) throws IllegalArgumentException {
        return switch (expr) {
            case Lit lit -> lit.toObject();
            case Val val -> visitVal(val);
            case ExprUn(var op, var e) -> unary(op, visit(e), expr);
            case ExprBin(var left, var op, var right) -> binary(visit(left), op, visit(right), expr);
            case ExprTern(var pred, var iftrue, var iffalse) -> ternary(visit(pred), visit(iftrue), visit(iffalse),
                    expr);
        };
    }

    private Object visitVal(Val v) {
        return switch (v) {
            case ValId(var name) -> {
                Object obj = context.get(name);
                if (obj == null) {
                    throw new IllegalArgumentException("No global property: " + name);
                }
                yield obj;
            }
            case ValCall c -> throw new IllegalArgumentException("Cannot call: " + v.toStringPretty());
            case ValDot(var left, var right) -> visitDot(visitVal(left), right, v);
        };
    }

    private Object visitDot(Object left, ValSimple right, Expr expr) {
        return switch (right) {
            case ValId(var name) -> {
                Object value;
                if (left == null) {
                    value = null;
                } else if (left == CONTENT) {
                    // "content" properties are turned into query references
                    checkAllowedProperty(name, expr);
                    value = new Reference(name);
                } else if (left instanceof Reference ref) {
                    value = new Reference(ref.name() + "/" + name);
                } else if (left instanceof InternalDocument idoc) {
                    value = getInternalDocumentValue(idoc, name, expr);
                } else if (left instanceof NuxeoPrincipal principal) {
                    value = getPrincipalValue(principal, name);
                } else if (left instanceof Map<?, ?> map) {
                    value = map.get(name);
                } else {
                    throw new IllegalArgumentException("Cannot dereference: " + expr.toStringPretty());
                }
                // normalization
                yield normalizeValue(name, value);
            }
            case ValCall(var name, var args) -> {
                List<Object> values = args.stream().map(this::visitArg).toList();
                yield switch (name) {
                    case CONTAINS -> visitContains(left, values, expr);
                    default -> throw new IllegalArgumentException("Cannot call: " + expr.toStringPretty());
                };
            }
        };
    }

    /**
     * Checks that the given property of the content is allowed for evaluation.
     *
     * @param name the property name
     * @param expr the expression, for error reporting
     * @throws IllegalArgumentException if the property is a disallowed system property
     */
    private void checkAllowedProperty(String name, Expr expr) {
        if (!allowSecurityProperties && schemaManager.isSecurityProperty(name)) {
            throw new IllegalArgumentException(
                    "Disallowed system property: " + name + " in: " + expr.toStringPretty());
        }
    }

    private Object getInternalDocumentValue(InternalDocument idoc, String name, Expr expr) {
        checkAllowedProperty(name, expr);
        try {
            return name.startsWith(SYS_PREFIX)
                    ? idoc.getPropertyValue(name)
                    : idoc.getValue(name);
        } catch (PropertyException e) {
            return null;
        }
    }

    private Object getPrincipalValue(NuxeoPrincipal principal, String name) {
        return switch (name) {
            case USER_ID -> principal.getId();
            case USER_GROUPS -> principal.getGroups();
            default -> {
                Object value = null;
                var properties = principal.getProperties();
                if (properties != null && properties.containsKey(name)) {
                    value = properties.get(name);
                } else {
                    var attributes = principal.getUserAttributes();
                    if (attributes != null) {
                        value = attributes.get(name);
                    }
                }
                yield value;
            }
        };
    }

    private Object normalizeValue(String name, Object value) {
        if (value instanceof Integer i) {
            value = Long.valueOf(i);
        } else if (value instanceof Float f) {
            value = Double.valueOf(f);
        } else if (value == null && schemaManager.isFalseStoredAsNull(name)) {
            value = false;
        } else if (value instanceof ManagedBlob blob) {
            Map<String, Object> map = new HashMap<>();
            map.put(KEY_BLOB_KEY, blob.getKey());
            map.put(KEY_BLOB_DIGEST, blob.getDigest());
            map.put(KEY_BLOB_ENCODING, blob.getEncoding());
            map.put(KEY_BLOB_FILENAME, blob.getFilename());
            map.put(KEY_BLOB_LENGTH, blob.getLength());
            map.put(KEY_BLOB_MIME_TYPE, blob.getMimeType());
            value = map;
        }
        return value;
    }

    private Object visitArg(Expr expr) {
        Object obj = visit(expr);
        if (obj == null || obj instanceof String || obj instanceof Long || obj instanceof Double
                || obj instanceof Reference) {
            return obj;
        }
        throw invalidExpr(expr);
    }

    private Object visitContains(Object left, List<Object> right, Expr expr) {
        if (left instanceof List<?> list) {
            if (right.isEmpty()) {
                throw new IllegalArgumentException("Cannot use contains with no arguments: " + expr.toStringPretty());
            }
            // check for somelist.contains(content.some_prop)
            // -> some_prop IN (list elements)
            if (right.size() == 1 && right.iterator().next() instanceof Reference ref) {
                // if the list is empty then IN cannot match
                if (list.isEmpty()) {
                    return false;
                }
                // return a predicate with IN and a LiteralList
                return new Predicate(ref, Operator.IN, toLiteralList(list, expr));
            }
            // check that there are no references
            if (right.stream().anyMatch(Reference.class::isInstance)) {
                throw invalidExpr(expr);
            }
            // list may disallow nulls and right may contain nulls
            // which means that list.containsAll(right) could crash
            // so instead use CollectionUtils.containsAll
            return CollectionUtils.containsAll(list, right);
        }
        throw new IllegalArgumentException("Cannot use contains on non-list: " + expr.toStringPretty());
    }

    /**
     * The HXQL operator an expression operator maps onto.
     *
     * @throws IllegalArgumentException for an arithmetic operator, which HXQL has none of
     */
    private Operator visit(Op op) {
        return switch (op) {
            case ADD, SUB, MUL, DIV -> throw new IllegalArgumentException("Not a query operator: " + op);
            case EQ -> Operator.EQ;
            case NOTEQ -> Operator.NOTEQ;
            case LT -> Operator.LT;
            case GT -> Operator.GT;
            case LTEQ -> Operator.LTEQ;
            case GTEQ -> Operator.GTEQ;
            case AND -> Operator.AND;
            case OR -> Operator.OR;
            case NOT -> Operator.NOT;
        };
    }

    // unary predicate
    private Object unary(Op op, Object obj, Expr expr) {
        // the only unary operator is NOT
        if (obj instanceof Operand operand) {
            return new Predicate(operand, visit(op), null);
        } else if (obj == null) {
            return null;
        } else if (obj instanceof Boolean b) {
            return !b.booleanValue();
        }
        throw invalidExpr(expr);
    }

    // binary predicate / expression
    private Object binary(Object left, Op op, Object right, Expr expr) {
        if (left instanceof Operand || right instanceof Operand) {
            Operand lo = left instanceof Operand o ? o : toLiteral(left, expr);
            Operand ro = right instanceof Operand o ? o : toLiteral(right, expr);
            return binary(lo, op, ro, expr);
        } else if (left == CONTENT || right == CONTENT) {
            throw invalidExpr(expr);
        } else {
            return eval(left, op, right, expr);
        }
    }

    // ternary expression
    private Object ternary(Object test, Object iftrue, Object iffalse, Expr expr) {
        if (test == null) {
            return null;
        } else if (test instanceof Boolean b) {
            return b ? iftrue : iffalse;
        }
        throw invalidExpr(expr);
    }

    // string promotion and boolean constant folding
    private Operand binary(Operand left, Op op, Operand right, Expr expr) {
        // promote string to numeric for comparisons with Reference
        // TODO should do type check on reference, but this requires a full schema manager
        // (and then we'd be able to include EQ and NOTEQ here too)
        if (op == Op.GT || op == Op.LT || op == Op.GTEQ || op == Op.LTEQ) {
            if (left instanceof StringLiteral(var string) && right instanceof Reference) {
                left = toNumberOrStringLiteral(toNumberMaybe(string));
            }
            if (left instanceof Reference && right instanceof StringLiteral(var string)) {
                right = toNumberOrStringLiteral(toNumberMaybe(string));
            }
        }

        // boolean constant folding
        if (op == Op.AND) {
            if (left instanceof BooleanLiteral bl) {
                if (bl.value()) {
                    return right; // true AND p -> p
                } else {
                    return bl; // false AND p -> false
                }
            } else if (right instanceof BooleanLiteral bl) {
                if (bl.value()) {
                    return left; // p AND true -> p
                } else {
                    return bl; // p AND false -> false
                }
            }
        } else if (op == Op.OR) {
            if (left instanceof BooleanLiteral bl) {
                if (bl.value()) {
                    return bl; // true OR p -> true
                } else {
                    return right; // false OR p -> p
                }
            } else if (right instanceof BooleanLiteral bl) {
                if (bl.value()) {
                    return bl; // p OR true -> true
                } else {
                    return left; // p OR false -> p
                }
            }
        }

        if (op == Op.ADD || op == Op.SUB || op == Op.MUL || op == Op.DIV) {
            // arithmetic is fine when evaluating against a document, but a query cannot express it: no storage
            // supports it, so there is nothing to hand a filter built out of it to
            throw new IllegalArgumentException(
                    "Cannot use arithmetic on a content property in a query: " + expr.toStringPretty());
        }
        Operator operator = visit(op);
        return switch (op) {
            case EQ, NOTEQ -> {
                if (left == null || right == null) {
                    Operand operand = left == null ? right : left;
                    operator = op == Op.EQ ? Operator.ISNULL : Operator.ISNOTNULL;
                    yield new Predicate(operand, operator, null);
                } else {
                    yield new Predicate(left, operator, right);
                }
            }
            case AND, OR, GT, LT, GTEQ, LTEQ -> new Predicate(left, operator, right);
            case ADD, SUB, MUL, DIV, NOT -> throw new IllegalStateException("Not a binary query operator: " + op);
        };
    }

    // binary evaluation
    private Object eval(Object left, Op op, Object right, Expr expr) {
        switch (op) {
            case EQ, NOTEQ, GT, LT, GTEQ, LTEQ:
                // promote string to numeric for comparisons
                if (left instanceof String string && (right instanceof Long || right instanceof Double || right == null)) {
                    left = toNumberMaybe(string);
                }
                if (right instanceof String string && (left instanceof Long || left instanceof Double || left == null)) {
                    right = toNumberMaybe(string);
                }
                break;
            case ADD:
                // promote numeric to string for concatenation
                if (left instanceof String && (right instanceof Long || right instanceof Double)) {
                    right = right.toString();
                }
                if (right instanceof String && (left instanceof Long || left instanceof Double)) {
                    left = left.toString();
                }
                break;
            default:
                // nothing
        }

        // promote long to double
        if (left instanceof Long lv && right instanceof Double) {
            left = Double.valueOf(lv);
        } else if (left instanceof Double && right instanceof Long rv) {
            right = Double.valueOf(rv);
        }

        // all null
        if (left == null && right == null) {
            return switch (op) {
                case AND, OR -> null;
                case EQ -> true;
                case NOTEQ -> false;
                default -> throw invalidExpr(expr);
            };
        }
        // boolean
        else if ((left instanceof Boolean && right == null) || (left == null && right instanceof Boolean)) {
            boolean v = left == null ? (Boolean) right : (Boolean) left;
            return switch (op) {
                case AND -> v ? null : false;
                case OR -> v ? true : null;
                case EQ -> false;
                case NOTEQ -> true;
                default -> throw invalidExpr(expr);
            };
        } else if (left instanceof Boolean lv && right instanceof Boolean rv) {
            return switch (op) {
                case AND -> lv && rv;
                case OR -> lv || rv;
                case EQ -> lv.equals(rv);
                case NOTEQ -> !lv.equals(rv);
                default -> throw invalidExpr(expr);
            };
        }
        // string
        else if ((left instanceof String && right == null) || (left == null && right instanceof String)) {
            return switch (op) {
                case EQ -> false;
                case NOTEQ -> true;
                case ADD -> (String) left + (String) right;
                default -> throw invalidExpr(expr);
            };
        } else if (left instanceof String lv && right instanceof String rv) {
            return switch (op) {
                case EQ -> lv.equals(rv);
                case NOTEQ -> !lv.equals(rv);
                case ADD -> lv + rv;
                default -> throw invalidExpr(expr);
            };
        }
        // long
        else if ((left instanceof Long && right == null) || (left == null && right instanceof Long)) {
            return switch (op) {
                case EQ -> false;
                case NOTEQ -> true;
                case GT, LT, GTEQ, LTEQ -> false;
                case ADD, SUB, MUL, DIV -> null;
                default -> throw invalidExpr(expr);
            };
        } else if (left instanceof Long lv && right instanceof Long rv) {
            return switch (op) {
                case EQ -> lv.equals(rv);
                case NOTEQ -> !lv.equals(rv);
                case GT -> lv > rv;
                case LT -> lv < rv;
                case GTEQ -> lv >= rv;
                case LTEQ -> lv <= rv;
                case ADD -> lv + rv;
                case SUB -> lv - rv;
                case MUL -> lv * rv;
                case DIV -> lv / rv;
                default -> throw invalidExpr(expr);
            };
        }
        // double
        else if ((left instanceof Double && right == null) || (left == null && right instanceof Double)) {
            return switch (op) {
                case EQ -> false;
                case NOTEQ -> true;
                case GT, LT, GTEQ, LTEQ -> false;
                case ADD, SUB, MUL, DIV -> null;
                default -> throw invalidExpr(expr);
            };
        } else if (left instanceof Double lv && right instanceof Double rv) {
            return switch (op) {
                case EQ -> lv.equals(rv);
                case NOTEQ -> !lv.equals(rv);
                case GT -> lv > rv;
                case LT -> lv < rv;
                case GTEQ -> lv >= rv;
                case LTEQ -> lv <= rv;
                case ADD -> lv + rv;
                case SUB -> lv - rv;
                case MUL -> lv * rv;
                case DIV -> lv / rv;
                default -> throw invalidExpr(expr);
            };
        }
        throw invalidExpr(expr);
    }

    private LiteralList toLiteralList(List<?> list, Expr expr) {
            return new LiteralList(list.stream().map(o -> toLiteral(o, expr)).toList());
    }

    private Literal toLiteral(Object v, Expr expr) {
        return switch (v) {
            case null -> null;
            case Boolean b -> new BooleanLiteral(b);
            case String s -> new StringLiteral(s);
            case Integer i -> new IntegerLiteral(i);
            case Long l -> new IntegerLiteral(l);
            case Float f -> new DoubleLiteral(f);
            case Double d -> new DoubleLiteral(d);
            default -> throw new IllegalArgumentException("Illegal expression: " + expr.toStringPretty());
        };
    }

    private Literal toNumberOrStringLiteral(Object v) {
        return switch (v) {
            case String s -> new StringLiteral(s);
            case Long l -> new IntegerLiteral(l);
            case Double d -> new DoubleLiteral(d);
            default -> throw new IllegalArgumentException("Illegal expression: " + v);
        };
    }

    private Object toNumberMaybe(String string) {
        try {
            return Long.valueOf(string);
        } catch (NumberFormatException e) {
            // fall through
        }
        try {
            double d = Double.parseDouble(string);
            if (Double.isFinite(d)) { // ignore infinites and NaN
                return Double.valueOf(d);
            } // else fall through
        } catch (NumberFormatException e) {
            // fall through
        }
        // can't convert, keep a string
        return string;
    }

    private static IllegalArgumentException invalidExpr(Expr expr) {
        return new IllegalArgumentException("Invalid expression: " + expr.toStringPretty());
    }

}
