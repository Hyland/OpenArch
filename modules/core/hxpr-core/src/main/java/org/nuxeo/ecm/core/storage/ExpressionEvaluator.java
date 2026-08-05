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
package org.nuxeo.ecm.core.storage;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_JOB_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.CharUtils;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.DateLiteral;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.Function;
import org.nuxeo.ecm.core.query.sql.model.IntegerLiteral;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.Literals;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;

/**
 * Evaluator for an {@link Expression}.
 *
 * @since 5.9.4
 */
public abstract class ExpressionEvaluator {

    /** pseudo NXQL to resolve internal path. */
    public static final String NXQL_SYS_PATH = "sys___path";

    protected static final String DATE_CAST = "DATE";

    /**
     * Interface for a class that knows how to resolve a path into an id.
     */
    public interface PathResolver {
        /**
         * Returns the id for a given path.
         *
         * @param path the path
         * @return the id, or {@code null} if not found
         */
        String getIdForPath(String path);
    }

    public final PathResolver pathResolver;

    protected ExpressionEvaluator(PathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    public Object walkExpression(Expression expr) {
        Operator op = expr.operator;
        Operand lvalue = expr.lvalue;
        Operand rvalue = expr.rvalue;
        Reference ref = lvalue instanceof Reference lvalueRef ? lvalueRef : null;
        String name = ref != null ? ref.name : null;
        String cast = ref != null ? ref.cast : null;
        if (DATE_CAST.equals(cast)) {
            checkDateLiteralForCast(rvalue, name);
        }

        if (SYS_PATH.equals(name)) {
            return walkEcmPath(lvalue, op, rvalue);
        } else if (SYS_ANCESTOR_IDS.equals(name)) {
            return walkAncestorId(lvalue, op, rvalue);
        } else if (SYS_IS_TRASHED.equals(name)) {
            return walkIsTrashed(lvalue, op, rvalue);
        } else if (name != null && name.startsWith(SYS_FULLTEXT) && !SYS_FULLTEXT_JOB_ID.equals(name)) {
            return walkEcmFulltext(lvalue, op, rvalue);
        }
        return switch (op) {
            case STARTSWITH -> walkStartsWith(lvalue, rvalue);
            case LT -> walkLt(lvalue, rvalue);
            case GT -> walkGt(lvalue, rvalue);
            case EQ -> walkEq(lvalue, rvalue);
            case NOTEQ -> walkNotEq(lvalue, rvalue);
            case LTEQ -> walkLtEq(lvalue, rvalue);
            case GTEQ -> walkGtEq(lvalue, rvalue);
            case AND -> expr instanceof MultiExpression multiExpression //
                    ? walkMultiExpression(multiExpression)
                    : walkAnd(lvalue, rvalue);
            case NOT -> walkNot(lvalue);
            case OR -> expr instanceof MultiExpression multiExpression //
                    ? walkMultiExpression(multiExpression)
                    : walkOr(lvalue, rvalue);
            case LIKE -> walkLike(lvalue, rvalue, true, false);
            case ILIKE -> walkLike(lvalue, rvalue, true, true);
            case NOTLIKE -> walkLike(lvalue, rvalue, false, false);
            case NOTILIKE -> walkLike(lvalue, rvalue, false, true);
            case IN -> walkIn(lvalue, rvalue, true);
            case NOTIN -> walkIn(lvalue, rvalue, false);
            case ISNULL -> walkIsNull(lvalue);
            case ISNOTNULL -> walkIsNotNull(lvalue);
            case BETWEEN -> walkBetween(lvalue, rvalue, true);
            case NOTBETWEEN -> walkBetween(lvalue, rvalue, false);
            case SUM, SUB, MUL, DIV -> //
                    throw new UnsupportedOperationException(op.toString());
        };
    }

    protected void checkDateLiteralForCast(Operand value, String name) {
        if (value instanceof DateLiteral dateLiteral && !dateLiteral.onlyDate()) {
            throw new QueryParseException("DATE() cast must be used with DATE literal, not TIMESTAMP: " + name);
        }
    }

    protected Boolean walkEcmPath(Operand lvalue, Operator op, Operand rvalue) {
        if (op == Operator.STARTSWITH) {
            return walkStartsWith(lvalue, rvalue);
        }
        if (op != Operator.EQ && op != Operator.NOTEQ) {
            throw new QueryParseException(SYS_PATH + " requires = or <> operator");
        }
        if (!(rvalue instanceof StringLiteral lit)) {
            throw new QueryParseException(SYS_PATH + " requires literal path as right argument");
        }
        String path = lit.value();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (pathResolver == null) {
            throw new QueryParseException(SYS_PATH + " not allowed in condition"); // conditional update
        }
        String id = pathResolver.getIdForPath(path);
        Object right = walkReference(new Reference(SYS_ID));
        if (id == null) {
            return FALSE;
        }
        Boolean eq = eq(id, right);
        return op == Operator.EQ ? eq : not(eq);
    }

    protected Boolean walkAncestorId(Operand lvalue, Operator op, Operand rvalue) {
        if (op == Operator.STARTSWITH) {
            return walkStartsWith(lvalue, rvalue);
        }
        if (op != Operator.EQ && op != Operator.NOTEQ) {
            throw new QueryParseException(SYS_ANCESTOR_IDS + " requires = or <> operator");
        }
        if (!(rvalue instanceof StringLiteral lit)) {
            throw new QueryParseException(SYS_ANCESTOR_IDS + " requires literal id as right argument");
        }
        String ancestorId = lit.value();
        @SuppressWarnings("unchecked")
        List<Object> ancestorIds = (List<Object>) walkReference(new Reference(SYS_ANCESTOR_IDS));
        boolean eq = op == Operator.EQ;
        if (ancestorIds == null) {
            // placeless
            return eq ? FALSE : TRUE;
        }
        for (Object id : ancestorIds) {
            if (ancestorId.equals(id)) {
                return eq ? TRUE : FALSE;
            }
        }
        return eq ? FALSE : TRUE;
    }

    protected Boolean walkEcmFulltext(Operand lvalue, Operator op, Operand rvalue) {
        if (op == Operator.STARTSWITH) {
            return walkStartsWith(lvalue, rvalue);
        }
        throw new QueryParseException("Fulltext search not available");
    }

    protected Boolean walkIsTrashed(Operand lvalue, Operator op, Operand rvalue) {
        if (op == Operator.STARTSWITH) {
            return walkStartsWith(lvalue, rvalue);
        }
        if (op != Operator.EQ && op != Operator.NOTEQ) {
            throw new QueryParseException(SYS_IS_TRASHED + " requires = or <> operator");
        }
        return walkIsTrashed(new Reference(SYS_IS_TRASHED), op, rvalue, BooleanLiteral.LIT_TRUE);
    }

    protected Boolean walkIsTrashed(Reference ref, Operator op, Operand initialRvalue, Literal deletedRvalue) {
        boolean trashedRequested;
        if (initialRvalue instanceof BooleanLiteral boolLit) {
            trashedRequested = boolLit.value();
        } else if (initialRvalue instanceof IntegerLiteral intLit && (intLit.value() == 0 || intLit.value() == 1)) {
            trashedRequested = intLit.value() == 1;
        } else {
            throw new QueryParseException(SYS_IS_TRASHED + " requires literal TRUE/FALSE (or 0/1) as right argument");
        }
        boolean equalsDeleted = op == Operator.EQ ? trashedRequested : !trashedRequested;
        if (equalsDeleted) {
            return walkEq(ref, deletedRvalue);
        } else {
            return walkNotEq(ref, deletedRvalue);
        }
    }

    public Boolean walkNot(Operand value) {
        return not(bool(walkOperand(value)));
    }

    public Boolean walkIsNull(Operand value) {
        return Boolean.valueOf(walkOperand(value) == null);
    }

    public Boolean walkIsNotNull(Operand value) {
        return Boolean.valueOf(walkOperand(value) != null);
    }

    // ternary logic
    public Boolean walkMultiExpression(MultiExpression expr) {
        boolean and = expr.operator == Operator.AND;
        Boolean res = and ? TRUE : FALSE;
        for (Predicate predicate : expr.predicates) {
            Boolean bool = bool(walkExpression(predicate));
            // don't short-circuit on null, we want to walk all references deterministically
            if (and) {
                res = and(res, bool);
            } else {
                res = or(res, bool);
            }
        }
        return res;
    }

    public Boolean walkAnd(Operand lvalue, Operand rvalue) {
        Boolean left = bool(walkOperand(lvalue));
        Boolean right = bool(walkOperand(rvalue));
        return and(left, right);
    }

    public Boolean walkOr(Operand lvalue, Operand rvalue) {
        Boolean left = bool(walkOperand(lvalue));
        Boolean right = bool(walkOperand(rvalue));
        return or(left, right);
    }

    public Boolean walkEq(Operand lvalue, Operand rvalue) {
        Object right = walkOperand(rvalue);
        if (isMixinTypes(lvalue)) {
            if (!(right instanceof String)) {
                throw new QueryParseException("Invalid EQ rhs: " + rvalue);
            }
            return walkMixinTypes(Collections.singletonList((String) right), true);
        }
        Object left = walkOperand(lvalue);
        return eqMaybeList(left, right);
    }

    public Boolean walkNotEq(Operand lvalue, Operand rvalue) {
        if (isMixinTypes(lvalue)) {
            Object right = walkOperand(rvalue);
            if (!(right instanceof String)) {
                throw new QueryParseException("Invalid NE rhs: " + rvalue);
            }
            return walkMixinTypes(Collections.singletonList((String) right), false);
        }
        return not(walkEq(lvalue, rvalue));
    }

    public Boolean walkLt(Operand lvalue, Operand rvalue) {
        Integer cmp = cmp(lvalue, rvalue);
        return cmp == null ? null : cmp < 0;
    }

    public Boolean walkGt(Operand lvalue, Operand rvalue) {
        Integer cmp = cmp(lvalue, rvalue);
        return cmp == null ? null : cmp > 0;
    }

    public Boolean walkLtEq(Operand lvalue, Operand rvalue) {
        Integer cmp = cmp(lvalue, rvalue);
        return cmp == null ? null : cmp <= 0;
    }

    public Boolean walkGtEq(Operand lvalue, Operand rvalue) {
        Integer cmp = cmp(lvalue, rvalue);
        return cmp == null ? null : cmp >= 0;
    }

    public Object walkBetween(Operand lvalue, Operand rvalue, boolean positive) {
        LiteralList l = (LiteralList) rvalue;
        Predicate va = new Predicate(lvalue, Operator.GTEQ, l.get(0));
        Predicate vb = new Predicate(lvalue, Operator.LTEQ, l.get(1));
        Predicate pred = new Predicate(va, Operator.AND, vb);
        if (!positive) {
            pred = new Predicate(pred, Operator.NOT, null);
        }
        return walkExpression(pred);
    }

    public Boolean walkIn(Operand lvalue, Operand rvalue, boolean positive) {
        Object right = walkOperand(rvalue);
        if (!(right instanceof List)) {
            throw new QueryParseException("Invalid IN rhs: " + rvalue);
        }
        if (isMixinTypes(lvalue)) {
            return walkMixinTypes((List<String>) right, positive);
        }
        Object left = walkOperand(lvalue);
        Boolean in = inMaybeList(left, (List<Object>) right);
        return positive ? in : not(in);
    }

    public Object walkOperand(Operand op) {
        if (op instanceof Literal opLit) {
            return walkLiteral(opLit);
        } else if (op instanceof LiteralList opLitList) {
            return walkLiteralList(opLitList);
        } else if (op instanceof Function opFun) {
            return walkFunction(opFun);
        } else if (op instanceof Expression opExp) {
            return walkExpression(opExp);
        } else if (op instanceof Reference opRef) {
            return walkReference(opRef);
        } else {
            throw new QueryParseException("Unknown operand: " + op);
        }
    }

    public Object walkLiteral(Literal lit) {
        return Literals.valueOf(lit);
    }

    public List<Object> walkLiteralList(LiteralList litList) {
        List<Object> list = new ArrayList<>(litList.size());
        for (Literal lit : litList) {
            list.add(walkLiteral(lit));
        }
        return list;
    }

    public Boolean walkLike(Operand lvalue, Operand rvalue, boolean positive, boolean caseInsensitive) {
        Object left = walkOperand(lvalue);
        Object right = walkOperand(rvalue);
        if (!(right instanceof String)) {
            throw new QueryParseException("Invalid LIKE rhs: " + rvalue);
        }
        return likeMaybeList(left, (String) right, positive, caseInsensitive);
    }

    public Object walkFunction(Function func) {
        throw new UnsupportedOperationException("Function");
    }

    public Boolean walkStartsWith(Operand lvalue, Operand rvalue) {
        if (!(lvalue instanceof Reference)) {
            throw new QueryParseException("Invalid STARTSWITH query, left hand side must be a property: " + lvalue);
        }
        String name = ((Reference) lvalue).name;
        if (!(rvalue instanceof StringLiteral lit)) {
            throw new QueryParseException(
                    "Invalid STARTSWITH query, right hand side must be a literal path: " + rvalue);
        }
        String path = lit.value();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (SYS_PATH.equals(name)) {
            return walkStartsWithPath(path);
        } else {
            return walkStartsWithNonPath(lvalue, path);
        }
    }

    protected Boolean walkStartsWithPath(String path) {
        if (pathResolver == null) {
            throw new QueryParseException(SYS_PATH + " not allowed in condition"); // conditional update
        }
        // resolve path
        String ancestorId = pathResolver.getIdForPath(path);
        // don't return early on null ancestorId, we want to walk all references deterministically
        @SuppressWarnings("unchecked")
        List<Object> ancestorIds = (List<Object>) walkReference(new Reference(SYS_ANCESTOR_IDS));
        if (ancestorId == null) {
            // no such path
            return FALSE;
        }
        if (ancestorIds == null) {
            // placeless
            return FALSE;
        }
        for (Object id : ancestorIds) {
            if (ancestorId.equals(id)) {
                return TRUE;
            }
        }
        return FALSE;
    }

    protected Boolean walkStartsWithNonPath(Operand lvalue, String path) {
        Object left = walkReference((Reference) lvalue);
        // exact match
        Boolean bool = eqMaybeList(left, path);
        if (TRUE.equals(bool)) {
            return TRUE;
        }
        // prefix match TODO escape % chars
        String pattern = path + "/%";
        return likeMaybeList(left, pattern, true, false);
    }

    /**
     * Evaluates a reference over the context state.
     *
     * @param ref the reference
     */
    public abstract Object walkReference(Reference ref);

    protected boolean isMixinTypes(Operand op) {
        if (!(op instanceof Reference)) {
            return false;
        }
        return ((Reference) op).name.equals(SYS_MIXIN_TYPES);
    }

    protected Boolean bool(Object value) {
        if (value == null) {
            return null; // NOSONAR
        }
        if (!(value instanceof Boolean)) {
            throw new QueryParseException("Not a boolean: " + value);
        }
        return (Boolean) value;
    }

    // ternary logic
    protected Boolean not(Boolean value) {
        if (value == null) {
            return null; // NOSONAR
        }
        return !value;
    }

    // ternary logic
    protected Boolean and(Boolean left, Boolean right) {
        if (TRUE.equals(left)) {
            return right;
        } else {
            return left;
        }
    }

    // ternary logic
    protected Boolean or(Boolean left, Boolean right) {
        if (TRUE.equals(left)) {
            return left;
        } else {
            return right;
        }
    }

    // ternary logic
    protected Boolean eq(Object left, Object right) {
        if (left == null || right == null) {
            return null; // NOSONAR
        }
        return left.equals(right);
    }

    // ternary logic
    protected Boolean in(Object left, List<Object> right) {
        if (left == null) {
            return null; // NOSONAR
        }
        boolean hasNull = false;
        for (Object r : right) {
            if (r == null) {
                hasNull = true;
            } else if (left.equals(r)) {
                return TRUE;
            }
        }
        return hasNull ? null : FALSE;
    }

    protected Integer cmp(Operand lvalue, Operand rvalue) {
        Object left = walkOperand(lvalue);
        Object right = walkOperand(rvalue);
        return cmp(left, right);
    }

    // ternary logic
    protected Integer cmp(Object left, Object right) {
        if (left == null || right == null) {
            return null;
        }
        if (!(left instanceof Comparable)) {
            throw new QueryParseException("Not a comparable: " + left);
        }
        return ((Comparable<Object>) left).compareTo(right);
    }

    // ternary logic
    protected Boolean like(Object left, String right, boolean caseInsensitive) {
        if (left == null || right == null) {
            return null; // NOSONAR
        }
        if (!(left instanceof String value)) {
            throw new QueryParseException("Invalid LIKE lhs: " + left);
        }
        if (caseInsensitive) {
            value = value.toLowerCase();
            right = right.toLowerCase();
        }
        String regex = likeToRegex(right);
        return Pattern.matches(regex, value);
    }

    /**
     * Turns a NXQL LIKE pattern into a regex.
     * <p>
     * % and _ are standard wildcards, and \ escapes them.
     *
     * @since 7.4
     */
    public static String likeToRegex(String like) {
        StringBuilder regex = new StringBuilder();
        char[] chars = like.toCharArray();
        boolean escape = false;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            boolean escapeNext = false;
            switch (c) {
            case '%':
                if (escape) {
                    regex.append(c);
                } else {
                    regex.append(".*");
                }
                break;
            case '_':
                if (escape) {
                    regex.append(c);
                } else {
                    regex.append(".");
                }
                break;
            case '\\':
                if (escape) {
                    regex.append("\\\\"); // backslash escaped for regexp
                } else {
                    escapeNext = true;
                }
                break;
            default:
                // escape mostly everything just in case
                if (!CharUtils.isAsciiAlphanumeric(c)) {
                    regex.append("\\");
                }
                regex.append(c);
                break;
            }
            escape = escapeNext;
        }
        if (escape) {
            // invalid string terminated by escape character, ignore
        }
        return regex.toString();
    }

    // if list, use EXIST (SELECT 1 FROM left WHERE left.item = right)
    protected Boolean eqMaybeList(Object left, Object right) {
        if (left instanceof List objects) {
            for (Object l : objects) {
                Boolean eq = eq(l, right);
                if (TRUE.equals(eq)) {
                    return TRUE;
                }
            }
            return FALSE;
        } else {
            return eq(left, right);
        }
    }

    // if list, use EXIST (SELECT 1 FROM left WHERE left.item IN right)
    protected Boolean inMaybeList(Object left, List<Object> right) {
        if (left instanceof List objects) {
            for (Object l : objects) {
                Boolean in = in(l, right);
                if (TRUE.equals(in)) {
                    return TRUE;
                }
            }
            return FALSE;
        } else {
            return in(left, right);
        }
    }

    protected Boolean likeMaybeList(Object left, String right, boolean positive, boolean caseInsensitive) {
        if (left instanceof List objects) {
            for (Object l : objects) {
                Boolean like = like(l, right, caseInsensitive);
                if (TRUE.equals(like)) {
                    return Boolean.valueOf(positive);
                }
            }
            return Boolean.valueOf(!positive);
        } else {
            Boolean like = like(left, right, caseInsensitive);
            return positive ? like : not(like);
        }
    }

    /**
     * Matches the mixin types against a list of values.
     * <p>
     * Used for:
     * <ul>
     * <li>sys_mixinTypes = 'foo'
     * <li>sys_mixinTypes != 'foo'
     * <li>sys_mixinTypes IN ('foo', 'bar')
     * <li>sys_mixinTypes NOT IN ('foo', 'bar')
     * </ul>
     *
     * @param mixins the mixin(s) to match
     * @param include {@code true} for = and IN
     * @since 7.4
     */
    public abstract Boolean walkMixinTypes(List<String> mixins, boolean include);

}
