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

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_JOB_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_SCORE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_ID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.Document;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.IntegerLiteral;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.AnyType;
import org.nuxeo.ecm.core.storage.ExpressionEvaluator.PathResolver;
import org.nuxeo.ecm.core.storage.QueryAnnotations;
import org.nuxeo.ecm.core.storage.dbs.DBSSession;
import org.nuxeo.runtime.mongodb.MongoDBOperators;

/**
 * Query builder for a MongoDB query of the repository from a {@link Predicate}.
 *
 * @since 5.9.4
 */

public class MongoDBRepositoryQueryBuilder extends MongoDBAbstractQueryBuilder {

    protected final SchemaManager schemaManager;

    protected List<String> documentTypes;

    protected final SelectClause selectClause;

    protected final OrderByClause orderByClause;

    protected final PathResolver pathResolver;

    protected Document orderBy;

    protected Document projection;

    protected Map<String, String> propertyKeys;

    boolean projectionHasWildcard;

    public MongoDBRepositoryQueryBuilder(MongoDBConverter converter, Predicate expression, SelectClause selectClause,
            OrderByClause orderByClause, PathResolver pathResolver, SchemaManager schemaManager) {
        this(converter, expression, selectClause, orderByClause, pathResolver, schemaManager, QueryAnnotations.NONE);
    }

    public MongoDBRepositoryQueryBuilder(MongoDBConverter converter, Predicate expression, SelectClause selectClause,
            OrderByClause orderByClause, PathResolver pathResolver, SchemaManager schemaManager,
            QueryAnnotations annotations) {
        super(converter, expression, annotations);
        this.schemaManager = schemaManager;
        this.selectClause = selectClause;
        this.orderByClause = orderByClause;
        this.pathResolver = pathResolver;
        this.propertyKeys = new HashMap<>();
    }

    @Override
    public void walk() {
        super.walk();
        walkOrderBy();
        walkProjection();
    }

    public Document getOrderBy() {
        return orderBy;
    }

    public Document getProjection() {
        return projection;
    }

    public boolean hasProjectionWildcard() {
        return projectionHasWildcard;
    }

    protected void walkOrderBy() {
        if (orderByClause == null) {
            orderBy = null;
        } else {
            orderBy = new Document();
            for (OrderByExpr ob : orderByClause.elements()) {
                Reference ref = ob.reference();
                boolean desc = ob.isDescending();
                String field = walkReference(ref).queryField;
                if (!orderBy.containsKey(field)) {
                    Object value = desc ? MINUS_ONE : ONE;
                    orderBy.put(field, value);
                }
            }
        }
    }

    protected void walkProjection() {
        projection = new Document();
        for (Operand op : selectClause.elements()) {
            if (!(op instanceof Reference)) {
                throw new QueryParseException("Projection not supported: " + op);
            }
            Reference ref = (Reference) op;
            validateProjection(ref);
            FieldInfo fieldInfo = walkReference(ref);
            String propertyField = fieldInfo.prop;
            if (!propertyField.equals(SYS_ID) //
                    && !propertyField.equals(fieldInfo.projectionField) //
                    && !propertyField.contains("/")) {
                propertyKeys.put(fieldInfo.projectionField, propertyField);
            }
            projection.put(fieldInfo.projectionField, ONE);
            if (propertyField.contains("*")) {
                projectionHasWildcard = true;
            }
            if (fieldInfo.projectionField.equals(SYS_FULLTEXT_SCORE)) {
                throw new QueryParseException("Fulltext search not available");
            }
        }
    }

    protected void validateProjection(Reference ref) {
        String name = DBSSession.compatibilityName(ref.name());
        Field field = schemaManager.getField(name);
        if (field != null && field.getCharacteristics().notProjectable()) {
            throw new QueryParseException("Disallowed system property: " + field.getName());
        }
    }

    @Override
    public Document walkExpression(Predicate expr) {
        Operator op = expr.operator;
        Operand lvalue = expr.lvalue;
        Operand rvalue = expr.rvalue;
        Reference ref = lvalue instanceof Reference lvalueRef ? lvalueRef : null;
        String name = ref != null ? ref.name() : null;
        if (op == Operator.STARTSWITH) {
            return walkStartsWith(lvalue, rvalue);
        } else if (SYS_PATH.equals(name)) {
            return walkEcmPath(op, rvalue);
        } else if (SYS_ANCESTOR_IDS.equals(name)) {
            return walkAncestorId(op, rvalue);
        } else if (SYS_IS_TRASHED.equals(name)) {
            return walkIsTrashed(op, rvalue);
        } else if (name != null && name.startsWith(SYS_FULLTEXT) && !SYS_FULLTEXT_JOB_ID.equals(name)) {
            return walkEcmFulltext(name, op, rvalue);
        } else {
            return super.walkExpression(expr);
        }
    }

    protected Document walkEcmPath(Operator op, Operand rvalue) {
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
        if (id == null) {
            // no such path
            // TODO XXX do better
            return new Document(MONGODB_ID, "__nosuchid__");
        }
        Object bsonId = converter.valueToBson(SYS_ID, id);
        if (op == Operator.EQ) {
            return new Document(MONGODB_ID, bsonId);
        } else {
            return new Document(MONGODB_ID, new Document(MongoDBOperators.NE, bsonId));
        }
    }

    protected Document walkAncestorId(Operator op, Operand rvalue) {
        if (op != Operator.EQ && op != Operator.NOTEQ) {
            throw new QueryParseException(SYS_ANCESTOR_IDS + " requires = or <> operator");
        }
        if (!(rvalue instanceof StringLiteral lit)) {
            throw new QueryParseException(SYS_ANCESTOR_IDS + " requires literal id as right argument");
        }
        String ancestorId = lit.value();
        Object bsonAncestorId = converter.valueToBson(SYS_ANCESTOR_IDS, ancestorId);
        if (op == Operator.EQ) {
            return new Document(SYS_ANCESTOR_IDS, bsonAncestorId);
        } else {
            return new Document(SYS_ANCESTOR_IDS, new Document(MongoDBOperators.NE, bsonAncestorId));
        }
    }

    protected Document walkEcmFulltext(String name, Operator op, Operand rvalue) {
        throw new QueryParseException("Fulltext search not available");
    }

    protected Document walkIsTrashed(Operator op, Operand rvalue) {
        if (op != Operator.EQ && op != Operator.NOTEQ) {
            throw new QueryParseException(SYS_IS_TRASHED + " requires = or <> operator");
        }
        return walkIsTrashed(new Reference(SYS_IS_TRASHED), op, rvalue, BooleanLiteral.LIT_TRUE);
    }

    protected Document walkIsTrashed(Reference ref, Operator op, Operand initialRvalue, Literal deletedRvalue) {
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

    @Override
    public Document walkEq(Operand lvalue, Operand rvalue) {
        FieldInfo fieldInfo = walkReference(lvalue);
        if (isMixinTypes(fieldInfo)) {
            Object right = walkOperand(fieldInfo, rvalue);
            if (!(right instanceof String)) {
                throw new QueryParseException("Invalid EQ rhs: " + rvalue);
            }
            return walkMixinTypes(Collections.singletonList((String) right), true);
        }
        return super.walkEq(fieldInfo, rvalue);
    }

    @Override
    public Document walkNotEq(Operand lvalue, Operand rvalue) {
        FieldInfo fieldInfo = walkReference(lvalue);
        if (isMixinTypes(fieldInfo)) {
            Object right = walkOperand(fieldInfo, rvalue);
            if (!(right instanceof String)) {
                throw new QueryParseException("Invalid NE rhs: " + rvalue);
            }
            return walkMixinTypes(Collections.singletonList((String) right), false);
        }
        return super.walkNotEq(fieldInfo, rvalue);
    }

    @Override
    public Document walkIn(Operand lvalue, Operand rvalue, boolean positive) {
        FieldInfo fieldInfo = walkReference(lvalue);
        if (isMixinTypes(fieldInfo)) {
            Object right = walkOperand(fieldInfo, rvalue);
            if (!(right instanceof List)) {
                throw new QueryParseException("Invalid IN, right hand side must be a list: " + rvalue);
            }
            return walkMixinTypes((List<String>) right, positive);
        }
        return super.walkIn(fieldInfo, rvalue, positive);
    }

    public Document walkStartsWith(Operand lvalue, Operand rvalue) {
        if (!(lvalue instanceof Reference ref)) {
            throw new QueryParseException("Invalid STARTSWITH query, left hand side must be a property: " + lvalue);
        }
        String name = ref.name();
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

    protected Document walkStartsWithPath(String path) {
        if (pathResolver == null) {
            throw new QueryParseException(SYS_PATH + " not allowed in condition"); // conditional update
        }
        // resolve path
        String ancestorId = pathResolver.getIdForPath(path);
        if (ancestorId == null) {
            // no such path
            // TODO XXX do better
            return new Document(MONGODB_ID, "__nosuchid__");
        }
        Object bsonAncestorId = converter.valueToBson(SYS_ANCESTOR_IDS, ancestorId);
        return new Document(SYS_ANCESTOR_IDS, bsonAncestorId);
    }

    protected Document walkStartsWithNonPath(Operand lvalue, String path) {
        FieldInfo fieldInfo = walkReference(lvalue);
        Document eq = newDocumentWithField(fieldInfo, path);
        Pattern pattern = Pattern.compile(Pattern.quote(path) + "/.*");
        Document like = newDocumentWithField(fieldInfo, pattern);
        return new Document(MongoDBOperators.OR, Arrays.asList(eq, like));
    }

    @Override
    protected FieldInfo walkReference(String name) {
        name = DBSSession.compatibilityName(name);
        String[] parts = name.split("/");
        String first = parts[0];
        Field rootField = schemaManager.getField(first);
        if (rootField == null) {
            throw new QueryParseException("No such property: " + name);
        }
        boolean managed = rootField.isRepositoryManaged();
        if (managed) {
            int expectedParts = SYS_ACL.equals(first) ? 3 : 1;
            if (rootField.getCharacteristics().notQueryable()) {
                throw new QueryParseException("Disallowed system property: " + rootField.getName());
            }
            if (parts.length != expectedParts) {
                throw new QueryParseException("No such property: " + name);
            }
            if (SYS_FULLTEXT.equals(first)) {
                throw new UnsupportedOperationException(name);
            }
        }
        Type type = rootField.getType();

        // are there wildcards or list indexes?
        List<String> queryFieldParts = new LinkedList<>(); // field for query
        List<String> projectionFieldParts = new LinkedList<>(); // field for projection
        boolean firstPart = true;
        for (String part : parts) {
            if (NumberUtils.isDigits(part)) {
                // explicit list index
                queryFieldParts.add(part);
                type = ((ListType) type).getFieldType();
            } else if (!part.startsWith("*")) {
                // complex or any sub-property
                if (!firstPart) {
                    // we already computed the type of the first part
                    if (type instanceof ComplexType complexType) {
                        Field field = complexType.getField(part);
                        if (field == null) {
                            throw new QueryParseException("No such property: " + name);
                        }
                        type = field.getType();
                    } else if (!(type instanceof AnyType)) {
                        throw new QueryParseException(
                                "Sub property of non-Complex and non-AnyType property: " + name);
                    }
                    // we assume sub-property of AnyType is also AnyType
                }
                String bsonPart = converter.keyToBson(part);
                queryFieldParts.add(bsonPart);
                projectionFieldParts.add(bsonPart);
            } else {
                // wildcard
                type = ((ListType) type).getFieldType();
            }
            firstPart = false;
        }
        String queryField = StringUtils.join(queryFieldParts, '.');
        String projectionField = StringUtils.join(projectionFieldParts, '.');
        queryField = stripElemMatchPrefix(queryField);
        return new FieldInfo(name, name, queryField, projectionField, type);
    }

    protected boolean isMixinTypes(FieldInfo fieldInfo) {
        return fieldInfo.queryField.equals(SYS_MIXIN_TYPES);
    }

    protected Set<String> getMixinDocumentTypes(String mixin) {
        Set<String> types = schemaManager.getDocumentTypeNamesForMixin(mixin);
        return types == null ? Collections.emptySet() : types;
    }

    protected List<String> getDocumentTypes() {
        // TODO precompute in SchemaManager
        if (documentTypes == null) {
            documentTypes = new ArrayList<>();
            for (DocumentType docType : schemaManager.getDocumentTypes()) {
                documentTypes.add(docType.getName());
            }
        }
        return documentTypes;
    }

    protected boolean isNeverPerInstanceMixin(String mixin) {
        return schemaManager.getNoPerDocumentQueryMixins().contains(mixin);
    }

    /**
     * Matches the mixin types against a list of values.
     * <p>
     * Used for:
     * <ul>
     * <li>sys_mixinTypes = 'Foo'
     * <li>sys_mixinTypes != 'Foo'
     * <li>sys_mixinTypes IN ('Foo', 'Bar')
     * <li>sys_mixinTypes NOT IN ('Foo', 'Bar')
     * </ul>
     * <p>
     * sys_mixinTypes IN ('Foo', 'Bar')
     *
     * <pre>
     * { "$or" : [ { "sys_primaryType" : { "$in" : [ ... types with Foo or Bar ...]}} ,
     *             { "sys_mixinTypes" : { "$in" : [ "Foo" , "Bar]}}]}
     * </pre>
     *
     * <p>
     * sys_mixinTypes NOT IN ('Foo', 'Bar')
     *
     * <pre>
     * { "$and" : [ { "sys_primaryType" : { "$in" : [ ... types without Foo nor Bar ...]}} ,
     *              { "sys_mixinTypes" : { "$nin" : [ "Foo" , "Bar]}}]}
     * </pre>
     */
    public Document walkMixinTypes(List<String> mixins, boolean include) {
        /*
         * Primary types that match.
         */
        Set<String> matchPrimaryTypes;
        if (include) {
            matchPrimaryTypes = new HashSet<>();
            for (String mixin : mixins) {
                matchPrimaryTypes.addAll(getMixinDocumentTypes(mixin));
            }
        } else {
            matchPrimaryTypes = new HashSet<>(getDocumentTypes());
            for (String mixin : mixins) {
                matchPrimaryTypes.removeAll(getMixinDocumentTypes(mixin));
            }
        }
        /*
         * Instance mixins that match.
         */
        Set<String> matchMixinTypes = new HashSet<>();
        for (String mixin : mixins) {
            if (!isNeverPerInstanceMixin(mixin)) {
                matchMixinTypes.add(mixin);
            }
        }
        /*
         * MongoDB query generation.
         */
        // match on primary type
        Document p = new Document(SYS_PRIMARY_TYPE, new Document(MongoDBOperators.IN, matchPrimaryTypes));
        // match on mixin types
        // $in/$nin with an array matches if any/no element of the array matches
        String innin = include ? MongoDBOperators.IN : MongoDBOperators.NIN;
        Document m = new Document(SYS_MIXIN_TYPES, new Document(innin, matchMixinTypes));
        // and/or between those
        String op = include ? MongoDBOperators.OR : MongoDBOperators.AND;
        return new Document(op, Arrays.asList(p, m));
    }

}
