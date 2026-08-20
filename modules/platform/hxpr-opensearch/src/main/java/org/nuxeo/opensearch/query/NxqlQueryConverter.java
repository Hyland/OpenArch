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
package org.nuxeo.opensearch.query;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_CHECKED_IN;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_SCORE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.opensearch.OpenSearchConstants.FULLTEXT_FIELD;
import static org.nuxeo.opensearch.OpenSearchConstants.OPENSEARCH_SCORE_FIELD;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.query.QueryParameters;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.nxql.SortInfo;
import org.nuxeo.ecm.core.query.sql.HXQLParser;
import org.nuxeo.ecm.core.query.sql.HXQL;
import org.nuxeo.ecm.core.query.sql.model.FromClause;
import org.nuxeo.ecm.core.query.sql.model.FtHint;
import org.nuxeo.ecm.core.query.sql.model.FtHint.FieldHint;
import org.nuxeo.ecm.core.query.sql.model.Function;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.security.SecurityPolicyService;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchPhrasePrefixQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;
import org.opensearch.client.opensearch._types.query_dsl.SimpleQueryStringQuery;
import org.opensearch.client.util.ObjectBuilder;

/**
 * Helper class that holds the conversion logic. Conversion is based on the existing NXQL Parser, we are just using a
 * visitor to build the ES request.
 */
public final class NxqlQueryConverter {

    protected static final String SELECT_ALL = "SELECT * FROM SysContent";

    protected static final String SELECT_ALL_WHERE = "SELECT * FROM SysContent WHERE ";

    protected static final String SIMPLE_QUERY_PREFIX = "es: ";

    /** Keys for boolean system properties that are stored in db and also indexed as true or null (never false). */
    private static final Set<String> TRUE_OR_NULL_BOOLEAN_PROPS = Set.of( //
            SYSVER_IS_CHECKED_IN, //
            SYSVER_IS_VERSION,
            SYSGOV_HAS_LEGAL_HOLD,
            SYSGOV_IS_RECORD);

    private final SchemaManager schemaManager;

    public NxqlQueryConverter(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    // public for tests
    public ObjectBuilder<? extends QueryVariant> toESQueryBuilder(final String nxql) {
        return toESQueryBuilder(nxql, null);
    }

    public ObjectBuilder<? extends QueryVariant> toESQueryBuilder(final String nxql, final CoreSession session) {
        final LinkedList<ExpressionBuilder> builders = new LinkedList<>();
        SQLQuery nxqlQuery = getSqlQuery(nxql);
        if (session != null) {
            SecurityPolicyService securityPolicyService = Framework.getService(SecurityService.class)
                                                                   .getSecurityPolicyService(
                                                                           session.getRepositoryName());
            QueryParameters queryParams = new QueryParameters(session.getPrincipal(), 0, 0);
            nxqlQuery = securityPolicyService.addSecurityPolicies(nxqlQuery, queryParams);
        }
        ExpressionBuilder ret = new ExpressionBuilder(null);
        builders.add(ret);
        List<String> fromList = new ArrayList<>();
        for (String type : nxqlQuery.getFromClause().elements()) {
            // Removed code for SYS_CONTENT optimization. See, HXCS-3121 Get rid of optimization until inconsistencies are fixed
            Set<String> types = schemaManager.getDocumentTypeNamesExtending(type);
            if (types != null) {
                fromList.addAll(types);
            }
        }
        if (nxqlQuery.getWhereClause() != null) {
            walkPredicate(nxqlQuery.getWhereClause().predicate(), builders, session);
        }
        ObjectBuilder<? extends QueryVariant> queryBuilder = ret.get();
        if (!fromList.isEmpty()) {
            return QueryBuilders.bool()
                                .must(queryBuilder.build().toQuery())
                                .filter(makeQueryFromSimpleExpression("IN", SYS_PRIMARY_TYPE, null,
                                        fromList.toArray(String[]::new), null, null).filter.build().toQuery());
        }
        return queryBuilder;
    }

    /**
     * Walks a predicate, pushing a builder for each boolean operator and adding a leaf query for everything else.
     */
    protected void walkPredicate(Operand node, LinkedList<ExpressionBuilder> builders, CoreSession session) {
        switch (node) {
            case MultiExpression multiExpression -> multiExpression.predicates.forEach(
                    predicate -> walkPredicate(predicate, builders, session));
            case Predicate expression -> walkExpression(expression, builders, session);
            default -> throw new IllegalArgumentException("Not a predicate: " + node);
        }
    }

    protected void walkExpression(Predicate node, LinkedList<ExpressionBuilder> builders, CoreSession session) {
        Operator op = node.operator;
        if (op == Operator.AND || op == Operator.OR || op == Operator.NOT) {
            builders.add(new ExpressionBuilder(op.toString()));
            walkPredicate(node.lvalue, builders, session);
            if (node.rvalue != null) {
                walkPredicate(node.rvalue, builders, session);
            }
            ExpressionBuilder expr = builders.removeLast();
            if (!builders.isEmpty()) {
                builders.getLast().merge(expr);
            }
            return;
        }
        Reference ref = node.lvalue instanceof Reference nodeRef ? nodeRef : null;
        String name = ref != null ? ref.name() : node.lvalue.toString();
        String value = null;
        if (node.rvalue instanceof Literal literal) {
            value = literal.asString();
        } else if (node.rvalue instanceof Function function) {
            String func = function.name();
            if (HXQL.NOW_FUNCTION.equalsIgnoreCase(func)) {
                String periodAndDurationText;
                if (function.args() == null || function.args().size() != 1) {
                    periodAndDurationText = null;
                } else {
                    periodAndDurationText = ((StringLiteral) function.args().get(0)).value();
                }
                ZonedDateTime dateTime = HXQL.nowPlusPeriodAndDuration(periodAndDurationText);
                value = dateTime.toInstant().toString();
            } else {
                throw new IllegalArgumentException("Unknown function: " + func);
            }
        } else if (node.rvalue != null) {
            value = node.rvalue.toString();
        }
        String[] values = null;
        if (node.rvalue instanceof LiteralList items) {
            values = new String[items.size()];
            int i = 0;
            for (Literal item : items) {
                values[i++] = item.asString();
            }
        }
        // add expression to the last builder
        if (ref.ftHint() != null) {
            throw new IllegalArgumentException("Query hint not supported: " + ref.ftHint());
        }
        FtHint hint = null;
        builders.getLast().add(makeQueryFromSimpleExpression(op.toString(), name, value, values, hint, session));
    }

    protected static SQLQuery getSqlQuery(String nxql) {
        String query = completeQueryWithSelect(nxql);
        SQLQuery nxqlQuery;
        try {
            nxqlQuery = HXQLParser.parse(query);
        } catch (QueryParseException e) {
            e.addInfo("Query: " + query);
            throw e;
        }
        return nxqlQuery;
    }

    protected static String completeQueryWithSelect(String nxql) {
        String query = (nxql == null) ? "" : nxql.trim();
        if (query.isEmpty()) {
            query = SELECT_ALL;
        } else if (!query.toLowerCase().startsWith("select ")) {
            query = SELECT_ALL_WHERE + nxql;
        }
        return query;
    }

    protected QueryAndFilter makeQueryFromSimpleExpression(String op, String nxqlName, String value, String[] values,
            FtHint hint, CoreSession session) {
        ObjectBuilder<? extends QueryVariant> query = null;
        ObjectBuilder<? extends QueryVariant> filter = null;
        String name = getFieldName(nxqlName, hint);
        if (nxqlName.startsWith(SYS_FULLTEXT) && ("=".equals(op) || "!=".equals(op) || "<>".equals(op)
                || "LIKE".equals(op) || "NOT LIKE".equals(op))) {
            query = makeFulltextQuery(nxqlName, (String) value, hint);
            if ("!=".equals(op) || "<>".equals(op) || "NOT LIKE".equals(op)) {
                filter = QueryBuilders.bool().mustNot(query.build().toQuery());
                query = null;
            }
        } else if (nxqlName.startsWith(SYS_ANCESTOR_IDS)) {
            filter = makeAncestorIdFilter(value, session);
            if ("!=".equals(op) || "<>".equals(op)) {
                filter = QueryBuilders.bool().mustNot(filter.build().toQuery());
            }
        } else if (nxqlName.equals(SYS_IS_TRASHED)) {
            filter = makeTrashedFilter(op, name, value);

        } else {
            switch (op) {
            case "=":
                FieldValue normval = checkBoolValue(nxqlName, value);
                if (TRUE_OR_NULL_BOOLEAN_PROPS.contains(nxqlName) && normval == FieldValue.FALSE) {
                    filter = QueryBuilders.bool().mustNot(QueryBuilders.exists().field(name).build().toQuery());
                } else {
                    filter = QueryBuilders.term().field(name).value(normval);
                }
                break;
            case "<>":
            case "!=":
                FieldValue normval2 = checkBoolValue(nxqlName, value);
                if (TRUE_OR_NULL_BOOLEAN_PROPS.contains(nxqlName)) {
                    if (normval2 == FieldValue.TRUE) {
                        filter = QueryBuilders.bool().mustNot(QueryBuilders.exists().field(name).build().toQuery());
                    } else {
                        filter = QueryBuilders.term().field(name).value(FieldValue.TRUE);
                    }
                } else {
                    filter = QueryBuilders.bool()
                                          .mustNot(QueryBuilders.term().field(name).value(normval2).build().toQuery());
                }
                break;
            case ">":
                filter = QueryBuilders.range().field(name).gt(JsonData.of(value));
                break;
            case "<":
                filter = QueryBuilders.range().field(name).lt(JsonData.of(value));
                break;
            case ">=":
                filter = QueryBuilders.range().field(name).gte(JsonData.of(value));
                break;
            case "<=":
                filter = QueryBuilders.range().field(name).lte(JsonData.of(value));
                break;
            case "BETWEEN":
            case "NOT BETWEEN":
                filter = QueryBuilders.range().field(name).from(JsonData.of(values[0])).to(JsonData.of(values[1]));
                if (op.startsWith("NOT")) {
                    filter = QueryBuilders.bool().mustNot(filter.build().toQuery());
                }
                break;
            case "IN":
            case "NOT IN":
                filter = QueryBuilders.terms()
                                      .field(name)
                                      .terms(t -> t.value(
                                              Arrays.stream(values).map(FieldValue::of).toList()));
                if (op.startsWith("NOT")) {
                    filter = QueryBuilders.bool().mustNot(filter.build().toQuery());
                }
                break;
            case "IS NULL":
                filter = QueryBuilders.bool().mustNot(QueryBuilders.exists().field(name).build().toQuery());
                break;
            case "IS NOT NULL":
                filter = QueryBuilders.exists().field(name);
                break;
            case "LIKE":
            case "ILIKE":
            case "NOT LIKE":
            case "NOT ILIKE":
                query = makeLikeQuery(op, name, value, hint);
                if (op.startsWith("NOT")) {
                    filter = QueryBuilders.bool().mustNot(query.build().toQuery());
                    query = null;
                }
                break;
            case "STARTSWITH":
                filter = makeStartsWithQuery(name, value);
                break;
            default:
                throw new UnsupportedOperationException("Operator: '" + op + "' is unknown");
            }
        }
        return new QueryAndFilter(query, filter);
    }

    protected FieldValue checkBoolValue(String nxqlName, String value) {
        if (value == null) {
            return FieldValue.NULL;
        }
        // accept 0/1 (legacy NXQL int form) and true/false (BooleanLiteral.asString)
        Boolean bool = parseBoolValue(value);
        if (bool == null) {
            return FieldValue.of(value);
        }
        switch (nxqlName) {
        case SYS_IS_FOLDERISH:
        case SYS_IS_TRASHED:
        case SYSGOV_IS_RECORD:
        case SYSGOV_HAS_LEGAL_HOLD:
        case SYSVER_IS_CHECKED_IN:
        case SYSVER_IS_VERSION:
            break;
        default:
            Field field = schemaManager.getField(nxqlName);
            if (field == null || !BooleanType.ID.equals(field.getType().getName())) {
                return FieldValue.of(value);
            }
        }
        return bool ? FieldValue.TRUE : FieldValue.FALSE;
    }

    /**
     * Parses a string representation of a boolean literal as it appears in an NXQL query: {@code "0"}/{@code "1"}
     * (legacy integer form) or {@code "true"}/{@code "false"} (case-insensitive). Returns {@code null} if the value
     * is none of these.
     */
    protected static Boolean parseBoolValue(String value) {
        if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        } else if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        } else {
            return null;
        }
    }

    protected static ObjectBuilder<? extends QueryVariant> makeTrashedFilter(String op, String name, String value) {
        Boolean trashedRequested = parseBoolValue(value);
        if (trashedRequested == null) {
            throw new IllegalArgumentException(SYS_IS_TRASHED + " requires literal TRUE/FALSE (or 0/1) as right argument");
        }
        boolean equalsDeleted = switch (op) {
        case "=" -> trashedRequested;
        case "<>", "!=" -> !trashedRequested;
        default -> throw new IllegalArgumentException(SYS_IS_TRASHED + " requires = or <> operator");
        };
        ObjectBuilder<? extends QueryVariant> filter = QueryBuilders.term().field(name).value(FieldValue.of(true));
        if (!equalsDeleted) {
            filter = QueryBuilders.bool().mustNot(filter.build().toQuery());
        }
        return filter;
    }

    protected static ObjectBuilder<? extends QueryVariant> makeStartsWithQuery(String name, Object value) {
        ObjectBuilder<? extends QueryVariant> filter;
        String indexName = name + ".children";
        if ("/".equals(value)) {
            if (SYS_PATH.equals(name)) {
                // any non orphan|place-less document must have a path starting with "/"
                filter = QueryBuilders.exists().field(SYS_PARENT_ID);
            } else {
                // match any document with a populated field
                filter = QueryBuilders.exists().field(indexName);
            }
        } else {
            String v = String.valueOf(value);
            if (v.endsWith("/")) {
                v = v.replaceAll("/$", "");
            }
            if (SYS_PATH.equals(name)) {
                // we don't want to return the parent when searching on sys_path, see NXP-18955
                filter = QueryBuilders.bool()
                                      .must(QueryBuilders.term()
                                                         .field(indexName)
                                                         .value(FieldValue.of(v))
                                                         .build()
                                                         .toQuery())
                                      .mustNot(QueryBuilders.term()
                                                            .field(name)
                                                            .value(FieldValue.of((String) value))
                                                            .build()
                                                            .toQuery());
            } else {
                filter = QueryBuilders.term().field(indexName).value(FieldValue.of(v));
            }
        }
        return filter;
    }

    protected static ObjectBuilder<? extends QueryVariant> makeAncestorIdFilter(String value, CoreSession session) {
        String path;
        if (session == null) {
            return QueryBuilders.exists().field("ancestorid-without-session");
        } else {
            try {
                DocumentModel doc = session.getDocument(new IdRef(value));
                path = doc.getPathAsString();
            } catch (DocumentNotFoundException e) {
                return QueryBuilders.exists().field("ancestorid-not-found");
            }
        }
        return makeStartsWithQuery(SYS_PATH, path);
    }

    protected static ObjectBuilder<? extends QueryVariant> makeLikeQuery(String op, String name, String value, FtHint hint) {
        String fieldName = name;
        if (op.contains("ILIKE")) {
            // ILIKE will work only with a correct mapping
            value = value.toLowerCase();
            fieldName = name + ".lowercase";
        }
        if (hint != null && hint.index() != null) {
            fieldName = hint.index();
        }
        // convert the value to a wildcard query
        String wildcard = likeToWildcard(value);
        // use match phrase prefix when possible
        if (StringUtils.countMatches(wildcard, "*") == 1 && wildcard.endsWith("*") && !wildcard.contains("?")
                && !wildcard.contains("\\")) {
            MatchPhrasePrefixQuery.Builder query = QueryBuilders.matchPhrasePrefix()
                                                                .field(fieldName)
                                                                .query(wildcard.replace("*", ""));
            if (hint != null && hint.analyzer() != null) {
                query.analyzer(hint.analyzer());
            }
            return query;
        }
        return QueryBuilders.wildcard().field(fieldName).wildcard(wildcard);
    }

    /**
     * Turns a NXQL LIKE pattern into a wildcard for WildcardQuery.
     * <p>
     * % and _ are standard wildcards, and \ escapes them.
     *
     * @since 7.4
     */
    protected static String likeToWildcard(String like) {
        StringBuilder wildcard = new StringBuilder();
        char[] chars = like.toCharArray();
        boolean escape = false;
        for (char c : chars) {
            boolean escapeNext = false;
            switch (c) {
            case '?':
                wildcard.append("\\?");
                break;
            case '*': // compat, * = % in NXQL (for some backends)
            case '%':
                if (escape) {
                    wildcard.append(c);
                } else {
                    wildcard.append("*");
                }
                break;
            case '_':
                if (escape) {
                    wildcard.append(c);
                } else {
                    wildcard.append("?");
                }
                break;
            case '\\':
                if (escape) {
                    wildcard.append("\\\\");
                } else {
                    escapeNext = true;
                }
                break;
            default:
                wildcard.append(c);
                break;
            }
            escape = escapeNext;
        }
        if (escape) {
            // invalid string terminated by escape character, ignore
        }
        return wildcard.toString();
    }

    protected static ObjectBuilder<? extends QueryVariant> makeFulltextQuery(String nxqlName, String value,
            FtHint hint) {
        String name = nxqlName.replace(SYS_FULLTEXT, "");
        if (name.startsWith(".")) {
            name = name.substring(1) + ".fulltext";
        } else {
            // map sys_fulltext_someindex to default
            name = FULLTEXT_FIELD;
        }
        String queryString = value;
        org.opensearch.client.opensearch._types.query_dsl.Operator defaultOperator;
        if (queryString.startsWith(SIMPLE_QUERY_PREFIX)) {
            // OpenSearch-specific syntax
            queryString = queryString.substring(SIMPLE_QUERY_PREFIX.length());
            defaultOperator = org.opensearch.client.opensearch._types.query_dsl.Operator.Or;
        } else {
            queryString = translateFulltextQuery(queryString);
            defaultOperator = org.opensearch.client.opensearch._types.query_dsl.Operator.And;
        }
        String analyzer = (hint != null && hint.analyzer() != null) ? hint.analyzer() : "fulltext";
        SimpleQueryStringQuery.Builder query = QueryBuilders.simpleQueryString()
                                                            .query(queryString)
                                                            .analyzeWildcard(true)
                                                            .defaultOperator(defaultOperator)
                                                            .analyzer(analyzer);
        if (hint != null && hint.index() != null) {
            for (FtHint.FieldHint fieldHint : hint.indexFields()) {
                String field = fieldHint.field();
                if (fieldHint.boost() != FieldHint.DEFAULT_BOOST) {
                    field += "^" + fieldHint.boost();
                }
                query.fields(field);
            }
        } else {
            query.fields(List.of(name));
        }
        return query;
    }

    public String getFieldName(String name, FtHint hint) {
        if (hint != null && hint.index() != null) {
            return hint.index();
        }
        // complex field
        name = name.replace("/*", "");
        name = name.replace("/", ".");
        return name;
    }

    public List<SortInfo> getSortInfo(String nxql) {
        List<SortInfo> sortInfos = new ArrayList<>();
        OrderByClause orderBy = getSqlQuery(nxql).getOrderByClause();
        if (orderBy != null) {
            for (OrderByExpr expr : orderBy.elements()) {
                String name = getFieldName(expr.reference().name(), null);
                if (SYS_FULLTEXT_SCORE.equals(name)) {
                    name = OPENSEARCH_SCORE_FIELD;
                }
                sortInfos.add(new SortInfo(name, !expr.isDescending()));
            }
        }
        return sortInfos;
    }

    public Map<String, Type> getSelectClauseFields(String nxql) {
        Map<String, Type> fieldsAndTypes = new LinkedHashMap<>();
        for (Operand op : getSqlQuery(nxql).getSelectClause().elements()) {
            if (op instanceof Reference reference) {
                Field field = schemaManager.getField(reference.name());
                fieldsAndTypes.put(reference.name(), field == null ? null : field.getType());
            }
        }
        return fieldsAndTypes;
    }

    /**
     * Translates from Nuxeo syntax to OpenSearch simple_query_string syntax.
     */
    protected static String translateFulltextQuery(String query) {
        // The AND operator does not exist in NXQL it is the default operator
        return query.replace(" OR ", " | ").replace(" or ", " | ");
    }

    /**
     * Class to hold both a query and a filter
     */
    public static class QueryAndFilter {

        public final ObjectBuilder<? extends QueryVariant> query;

        public final ObjectBuilder<? extends QueryVariant> filter;

        public QueryAndFilter(ObjectBuilder<? extends QueryVariant> query, ObjectBuilder<? extends QueryVariant> filter) {
            this.query = query;
            this.filter = filter;
        }
    }

    public static class ExpressionBuilder {

        public final String operator;

        public ObjectBuilder<? extends QueryVariant> query;

        public ExpressionBuilder(final String op) {
            operator = op;
            query = null;
        }

        public void add(final QueryAndFilter qf) {
            if (qf != null) {
                add(qf.query, qf.filter);
            }
        }

        public void add(ObjectBuilder<? extends QueryVariant> q) {
            add(q, null);
        }

        public void add(ObjectBuilder<? extends QueryVariant> q, ObjectBuilder<? extends QueryVariant> f) {
            if (q == null && f == null) {
                return;
            }
            ObjectBuilder<? extends QueryVariant> inputQuery = q;
            if (inputQuery == null) {
                inputQuery = QueryBuilders.constantScore().filter(f.build().toQuery());
            }
            if (operator == null) {
                // first level expression
                query = inputQuery;
            } else {
                // boolean query
                if (query == null) {
                    query = QueryBuilders.bool();
                }
                BoolQuery.Builder boolQuery = (BoolQuery.Builder) query;
                if ("AND".equals(operator)) {
                    boolQuery.must(inputQuery.build().toQuery());
                } else if ("OR".equals(operator)) {
                    boolQuery.should(inputQuery.build().toQuery());
                } else if ("NOT".equals(operator)) {
                    boolQuery.mustNot(inputQuery.build().toQuery());
                }
            }
        }

        public void merge(ExpressionBuilder expr) {
            if ((expr.operator != null) && expr.operator.equals(operator) && (query == null)) {
                query = expr.query;
            } else {
                add(new QueryAndFilter(expr.query, null));
            }
        }

        public ObjectBuilder<? extends QueryVariant> get() {
            if (query == null) {
                return QueryBuilders.matchAll();
            }
            return query;
        }

        @Override
        public String toString() {
            return query.toString();
        }

    }
}
