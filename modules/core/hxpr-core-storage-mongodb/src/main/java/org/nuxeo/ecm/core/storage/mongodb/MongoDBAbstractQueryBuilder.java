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
package org.nuxeo.ecm.core.storage.mongodb;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.bson.Document;
import org.nuxeo.common.utils.DateUtils;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.DateLiteral;
import org.nuxeo.ecm.core.query.sql.model.DoubleLiteral;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Function;
import org.nuxeo.ecm.core.query.sql.model.IntegerLiteral;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.Literals;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.storage.ExpressionEvaluator;
import org.nuxeo.ecm.core.storage.QueryAnnotations;
import org.nuxeo.ecm.core.storage.QueryAnnotations.PrefixInfo;
import org.nuxeo.runtime.mongodb.MongoDBOperators;

/**
 * Abstract query builder for a MongoDB query from a {@link Predicate}.
 * <p>
 * Must be customized by defining an implementation for the {@link #walkReference(String)} method.
 *
 * @since 5.9.4
 */
public abstract class MongoDBAbstractQueryBuilder {

    public static final Long LONG_ZERO = Long.valueOf(0);

    public static final Long LONG_ONE = Long.valueOf(1);

    public static final Double ONE = Double.valueOf(1);

    public static final Double MINUS_ONE = Double.valueOf(-1);

    protected static final String DATE_CAST = "DATE";

    protected final MongoDBConverter converter;

    protected final Predicate expression;

    /** What the optimizer worked out about the query's nodes. */
    protected final QueryAnnotations annotations;

    protected Document query;

    /**
     * Prefix to remove for $elemMatch (including final dot), or {@code null} if there's no current prefix to remove.
     */
    protected String elemMatchPrefix;

    protected MongoDBAbstractQueryBuilder(MongoDBConverter converter, Predicate expression,
            QueryAnnotations annotations) {
        this.converter = converter;
        this.expression = expression;
        this.annotations = annotations;
    }

    public void walk() {
        walkTopLevelExpression();
    }

    public void walkTopLevelExpression() {
        if (expression instanceof MultiExpression multiExpression && multiExpression.predicates.isEmpty()) {
            // special-case empty query
            query = new Document();
        } else {
            query = walkExpression(expression);
        }
    }

    public Document getQuery() {
        return query;
    }

    public Document walkExpression(Predicate expr) {
        Operator op = expr.operator;
        Operand lvalue = expr.lvalue;
        Operand rvalue = expr.rvalue;
        Reference ref = lvalue instanceof Reference lvalueRef ? lvalueRef : null;
        String name = ref != null ? ref.name() : null;
        String cast = ref != null ? ref.cast() : null;
        if (DATE_CAST.equals(cast)) {
            checkDateLiteralForCast(op, rvalue, name);
        }
        return switch (op) {
            case LT -> walkLt(lvalue, rvalue);
            case GT -> walkGt(lvalue, rvalue);
            case EQ -> walkEq(lvalue, rvalue);
            case NOTEQ -> walkNotEq(lvalue, rvalue);
            case LTEQ -> walkLtEq(lvalue, rvalue);
            case GTEQ -> walkGtEq(lvalue, rvalue);
            case AND, OR -> expr instanceof MultiExpression multiExpression //
                    ? walkAndOrMultiExpression(multiExpression)
                    : walkAndOr(expr);
            case NOT -> walkNot(lvalue);
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
            case STARTSWITH -> throw new UnsupportedOperationException(op.toString());
        };
    }

    protected void checkDateLiteralForCast(Operator op, Operand value, String name) {
        if (op == Operator.BETWEEN || op == Operator.NOTBETWEEN) {
            LiteralList l = (LiteralList) value;
            checkDateLiteralForCast(l.get(0), name);
            checkDateLiteralForCast(l.get(1), name);
        } else {
            checkDateLiteralForCast(value, name);
        }
    }

    protected void checkDateLiteralForCast(Operand value, String name) {
        if (value instanceof DateLiteral dateLiteral && !dateLiteral.onlyDate()) {
            throw new QueryParseException("DATE() cast must be used with DATE literal, not TIMESTAMP: " + name);
        }
    }

    public Document walkNot(Operand value) {
        Object val = walkOperand(null, value);
        Object not = pushDownNot(val);
        if (!(not instanceof Document)) {
            throw new QueryParseException("Cannot do NOT on: " + val);
        }
        return (Document) not;
    }

    protected Object pushDownNot(Object object) {
        if (!(object instanceof Document ob)) {
            throw new QueryParseException("Cannot do NOT on: " + object);
        }
        Set<String> keySet = ob.keySet();
        if (keySet.size() != 1) {
            throw new QueryParseException("Cannot do NOT on: " + ob);
        }
        String key = keySet.iterator().next();
        Object value = ob.get(key);
        if (!key.startsWith("$")) {
            if (value instanceof Document) {
                // push down inside dbobject
                return new Document(key, pushDownNot(value));
            } else {
                // k = v -> k != v
                return new Document(key, new Document(MongoDBOperators.NE, value));
            }
        }
        if (MongoDBOperators.NE.equals(key)) {
            // NOT k != v -> k = v
            return value;
        }
        if (MongoDBOperators.NOT.equals(key)) {
            // NOT NOT v -> v
            return value;
        }
        if (MongoDBOperators.AND.equals(key) || MongoDBOperators.OR.equals(key)) {
            // boolean algebra
            // NOT (v1 AND v2) -> NOT v1 OR NOT v2
            // NOT (v1 OR v2) -> NOT v1 AND NOT v2
            String op = MongoDBOperators.AND.equals(key) ? MongoDBOperators.OR : MongoDBOperators.AND;
            List<Object> list = (List<Object>) value;
            for (int i = 0; i < list.size(); i++) {
                list.set(i, pushDownNot(list.get(i)));
            }
            return new Document(op, list);
        }
        if (MongoDBOperators.IN.equals(key) || MongoDBOperators.NIN.equals(key)) {
            // boolean algebra
            // IN <-> NIN
            String op = MongoDBOperators.IN.equals(key) ? MongoDBOperators.NIN : MongoDBOperators.IN;
            return new Document(op, value);
        }
        if (MongoDBOperators.LT.equals(key) || MongoDBOperators.GT.equals(key) || MongoDBOperators.LTE.equals(key)
                || MongoDBOperators.GTE.equals(key)) {
            // TODO use inverse operators?
            return new Document(MongoDBOperators.NOT, ob);
        }
        throw new QueryParseException("Unknown operator for NOT: " + key);
    }

    protected Document newDocumentWithField(FieldInfo fieldInfo, Object value) {
        return new Document(fieldInfo.queryField, value);
    }

    public Document walkIsNull(Operand value) {
        FieldInfo fieldInfo = walkReference(value);
        return newDocumentWithField(fieldInfo, null);
    }

    public Document walkIsNotNull(Operand value) {
        FieldInfo fieldInfo = walkReference(value);
        return newDocumentWithField(fieldInfo, new Document(MongoDBOperators.NE, null));
    }

    public Document walkAndOrMultiExpression(MultiExpression expr) {
        return walkAndOr(expr, expr.predicates);
    }

    public Document walkAndOr(Predicate expr) {
        return walkAndOr(expr, Arrays.asList(expr.lvalue, expr.rvalue));
    }

    protected static final Pattern SLASH_WILDCARD_SLASH = Pattern.compile("/\\*\\d+(/)?");

    protected Document walkAndOr(Predicate expr, List<? extends Operand> values) {
        if (values.size() == 1) {
            return (Document) walkOperand(null, values.get(0));
        }
        boolean and = expr.operator == Operator.AND;
        String op = and ? MongoDBOperators.AND : MongoDBOperators.OR;
        // the prefix info was computed by the QueryOptimizer for common AND predicates
        PrefixInfo info = annotations.getPrefixInfo(expr);
        if (info == null || info.count() < 2 || !and) {
            List<Object> list = walkOperandList(values);
            return new Document(op, list);
        }

        // we have a common prefix for all underlying references, extract it into an $elemMatch node

        // info.prefix() is the DBS common prefix, ex: foo/bar/*1
        // computes MongoDB prefix: foo.bar.
        String prefix = SLASH_WILDCARD_SLASH.matcher(info.prefix()).replaceAll(".");
        // remove current prefix and trailing . for actual field match
        String fieldBase = stripElemMatchPrefix(prefix.substring(0, prefix.length() - 1));

        String previousElemMatchPrefix = elemMatchPrefix;
        elemMatchPrefix = prefix;
        List<Object> list = walkOperandList(values);
        elemMatchPrefix = previousElemMatchPrefix;

        return new Document(fieldBase, new Document(MongoDBOperators.ELEM_MATCH, new Document(op, list)));
    }

    protected String stripElemMatchPrefix(String field) {
        if (elemMatchPrefix != null && field.startsWith(elemMatchPrefix)) {
            field = field.substring(elemMatchPrefix.length());
        }
        return field;
    }

    public Document walkEq(Operand lvalue, Operand rvalue) {
        FieldInfo fieldInfo = walkReference(lvalue);
        return walkEq(fieldInfo, rvalue);
    }

    public Document walkEq(FieldInfo fieldInfo, Operand rvalue) {
        Object right = walkOperand(fieldInfo, rvalue);
        return newDocumentWithField(fieldInfo, right);
    }

    public Document walkNotEq(Operand lvalue, Operand rvalue) {
        FieldInfo fieldInfo = walkReference(lvalue);
        return walkNotEq(fieldInfo, rvalue);
    }

    public Document walkNotEq(FieldInfo fieldInfo, Operand rvalue) {
        Object right = walkOperand(fieldInfo, rvalue);
        return newDocumentWithField(fieldInfo, new Document(MongoDBOperators.NE, right));
    }

    public Document walkLt(Operand lvalue, Operand rvalue) {
        FieldInfo fieldInfo = walkReference(lvalue);
        Object right = walkOperand(fieldInfo, rvalue);
        return newDocumentWithField(fieldInfo, new Document(MongoDBOperators.LT, right));
    }

    public Document walkGt(Operand lvalue, Operand rvalue) {
        FieldInfo fieldInfo = walkReference(lvalue);
        Object right = walkOperand(fieldInfo, rvalue);
        return newDocumentWithField(fieldInfo, new Document(MongoDBOperators.GT, right));
    }

    public Document walkLtEq(Operand lvalue, Operand rvalue) {
        FieldInfo fieldInfo = walkReference(lvalue);
        Object right = walkOperand(fieldInfo, rvalue);
        return newDocumentWithField(fieldInfo, new Document(MongoDBOperators.LTE, right));
    }

    public Document walkGtEq(Operand lvalue, Operand rvalue) {
        FieldInfo fieldInfo = walkReference(lvalue);
        Object right = walkOperand(fieldInfo, rvalue);
        return newDocumentWithField(fieldInfo, new Document(MongoDBOperators.GTE, right));
    }

    public Document walkBetween(Operand lvalue, Operand rvalue, boolean positive) {
        LiteralList l = (LiteralList) rvalue;
        FieldInfo fieldInfo = walkReference(lvalue);
        Object left = walkOperand(fieldInfo, l.get(0));
        Object right = walkOperand(fieldInfo, l.get(1));
        if (positive) {
            Document range = new Document();
            range.put(MongoDBOperators.GTE, left);
            range.put(MongoDBOperators.LTE, right);
            return newDocumentWithField(fieldInfo, range);
        } else {
            Document a = newDocumentWithField(fieldInfo, new Document(MongoDBOperators.LT, left));
            Document b = newDocumentWithField(fieldInfo, new Document(MongoDBOperators.GT, right));
            return new Document(MongoDBOperators.OR, Arrays.asList(a, b));
        }
    }

    public Document walkIn(Operand lvalue, Operand rvalue, boolean positive) {
        FieldInfo fieldInfo = walkReference(lvalue);
        return walkIn(fieldInfo, rvalue, positive);
    }

    public Document walkIn(FieldInfo fieldInfo, Operand rvalue, boolean positive) {
        Object right = walkOperand(fieldInfo, rvalue);
        if (!(right instanceof List)) {
            throw new QueryParseException("Invalid IN, right hand side must be a list: " + rvalue);
        }
        // TODO check list fields
        List<Object> list = (List<Object>) right;
        return newDocumentWithField(fieldInfo,
                new Document(positive ? MongoDBOperators.IN : MongoDBOperators.NIN, list));
    }

    public Document walkLike(Operand lvalue, Operand rvalue, boolean positive, boolean caseInsensitive) {
        FieldInfo fieldInfo = walkReference(lvalue);
        if (!(rvalue instanceof StringLiteral lit)) {
            throw new QueryParseException("Invalid LIKE/ILIKE, right hand side must be a string: " + rvalue);
        }
        // TODO check list fields
        String like = (String) walkStringLiteral(fieldInfo, lit);
        String regex = ExpressionEvaluator.likeToRegex(like);
        // MongoDB native matches are unanchored: optimize the regex for faster matches
        if (regex.startsWith(".*")) {
            regex = regex.substring(2);
        } else {
            regex = "^" + regex; // anchored
        }
        if (regex.endsWith(".*")) {
            regex = regex.substring(0, regex.length() - 2); // better range index use
        } else {
            regex = regex + "$"; // anchored
        }

        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        Pattern pattern = Pattern.compile(regex, flags);
        Object value;
        if (positive) {
            value = pattern;
        } else {
            value = new Document(MongoDBOperators.NOT, pattern);
        }
        return newDocumentWithField(fieldInfo, value);
    }

    public Object walkOperand(FieldInfo fieldInfo, Operand op) {
        if (op instanceof Literal opLit) {
            return walkLiteral(fieldInfo, opLit);
        } else if (op instanceof LiteralList opLitList) {
            return walkLiteralList(fieldInfo, opLitList);
        } else if (op instanceof Function opFun) {
            return walkFunction(opFun);
        } else if (op instanceof Predicate opExp) {
            return walkExpression(opExp);
        } else if (op instanceof Reference opRef) {
            return walkReference(opRef);
        } else {
            throw new QueryParseException("Unknown operand: " + op);
        }
    }

    public Object walkLiteral(FieldInfo fieldInfo, Literal lit) {
        if (lit instanceof IntegerLiteral intLit) {
            return walkIntegerLiteral(fieldInfo, intLit);
        } else if (lit instanceof StringLiteral strLit) {
            return walkStringLiteral(fieldInfo, strLit);
        } else if (lit instanceof BooleanLiteral boolLit) {
            return walkBooleanLiteral(fieldInfo, boolLit);
        } else  {
            return Literals.valueOf(lit);
        }
    }

    public Object walkIntegerLiteral(FieldInfo fieldInfo, IntegerLiteral lit) {
        long value = lit.value();
        if (fieldInfo != null && fieldInfo.isBoolean()) {
            // convert 0 / 1 to actual booleans; cast to int is fine, we don't care about pathological long values
            BooleanLiteral bl = switch ((int) value) {
                case 0 -> BooleanLiteral.LIT_FALSE;
                case 1 -> BooleanLiteral.LIT_TRUE;
                default -> throw new QueryParseException("Invalid boolean: " + value);
            };
            return walkBooleanLiteral(fieldInfo, bl);
        }
        return Long.valueOf(value);
    }

    public Object walkBooleanLiteral(FieldInfo fieldInfo, BooleanLiteral lit) {
        Boolean value = lit.value();
        if (fieldInfo != null && fieldInfo.isBoolean()) {
            return converter.valueToBson(fieldInfo.key, value);
        }
        return value;
    }

    public Object walkStringLiteral(FieldInfo fieldInfo, StringLiteral lit) {
        String value = lit.value();
        if (fieldInfo != null) {
            return converter.valueToBson(fieldInfo.key, value);
        }
        return value;
    }

    public List<Object> walkLiteralList(FieldInfo fieldInfo, LiteralList litList) {
        List<Object> list = new ArrayList<>(litList.size());
        for (Literal lit : litList) {
            list.add(walkLiteral(fieldInfo, lit));
        }
        return list;
    }

    protected List<Object> walkOperandList(List<? extends Operand> values) {
        List<Object> list = new LinkedList<>();
        for (Operand value : values) {
            list.add(walkOperand(null, value));
        }
        return list;
    }

    public Object walkFunction(Function func) {
        String name = func.name();
        if (NXQL.NOW_FUNCTION.equalsIgnoreCase(name)) {
            String periodAndDurationText;
            if (func.args() == null || func.args().size() != 1) {
                periodAndDurationText = null;
            } else {
                periodAndDurationText = ((StringLiteral) func.args().get(0)).value();
            }
            try {
                ZonedDateTime dateTime = NXQL.nowPlusPeriodAndDuration(periodAndDurationText);
                return dateTime.toInstant();
            } catch (IllegalArgumentException e) {
                throw new QueryParseException(e);
            }
        } else {
            throw new QueryParseException("Function not supported: " + func);
        }
    }

    protected FieldInfo walkReference(Operand value) {
        if (!(value instanceof Reference)) {
            throw new QueryParseException("Invalid query, left hand side must be a property: " + value);
        }
        return walkReference((Reference) value);
    }

    public static class FieldInfo {

        /** NXQL property. */
        public final String prop;

        /**
         * DBS key.
         *
         * @since 11.1
         */
        public final String key;

        /** MongoDB field for query. foo/0/bar -&gt; foo.0.bar; foo / * / bar -&gt; foo.bar */
        public final String queryField;

        /** MongoDB field for projection. */
        public final String projectionField;

        public final Type type;

        public FieldInfo(String prop, String key, String queryField, String projectionField, Type type) {
            this.prop = prop;
            this.key = key;
            this.queryField = queryField;
            this.projectionField = projectionField;
            this.type = type;
        }

        public boolean isBoolean() {
            return type instanceof BooleanType;
        }
    }

    /**
     * Returns the MongoDB field for this reference.
     */
    public FieldInfo walkReference(Reference ref) {
        FieldInfo fieldInfo = walkReference(ref.name());
        if (DATE_CAST.equals(ref.cast())) {
            Type type = fieldInfo.type;
            if (!(type instanceof DateType
                    || (type instanceof ListType listType && listType.getFieldType() instanceof DateType))) {
                throw new QueryParseException("Cannot cast to " + ref.cast() + ": " + ref.name());
            }
            // fieldInfo.isDateCast = true;
        }
        return fieldInfo;
    }

    /**
     * Walks a reference, and returns field info about it.
     */
    protected abstract FieldInfo walkReference(String name);

}
