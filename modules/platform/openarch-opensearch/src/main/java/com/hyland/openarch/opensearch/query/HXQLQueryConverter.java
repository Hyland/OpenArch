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
package com.hyland.openarch.opensearch.query;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT_SCORE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.INDEX;
import static com.hyland.openarch.opensearch.OpenSearchConstants.FULLTEXT_FIELD;
import static com.hyland.openarch.opensearch.OpenSearchConstants.OPENSEARCH_SCORE_FIELD;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.nxql.SortInfo;
import com.hyland.openarch.ecm.core.query.sql.HXQL;
import com.hyland.openarch.ecm.core.query.sql.HXQLParser;
import com.hyland.openarch.ecm.core.query.sql.model.FtHint;
import com.hyland.openarch.ecm.core.query.sql.model.FtHint.FieldHint;
import com.hyland.openarch.ecm.core.query.sql.model.Function;
import com.hyland.openarch.ecm.core.query.sql.model.Literal;
import com.hyland.openarch.ecm.core.query.sql.model.LiteralList;
import com.hyland.openarch.ecm.core.query.sql.model.MultiExpression;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.OrderByClause;
import com.hyland.openarch.ecm.core.query.sql.model.OrderByExpr;
import com.hyland.openarch.ecm.core.query.sql.model.Parameter;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.query.sql.model.StatementValidator;
import com.hyland.openarch.ecm.core.query.sql.model.StringLiteral;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.ecm.core.schema.types.primitives.BooleanType;
import com.hyland.openarch.ecm.core.security.SecurityPolicyService;
import com.hyland.openarch.ecm.core.security.SecurityService;
import com.hyland.openarch.runtime.api.Framework;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchPhrasePrefixQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;
import org.opensearch.client.opensearch._types.query_dsl.SimpleQueryStringQuery;
import org.opensearch.client.util.ObjectBuilder;

/**
 * Turns an HXQL query into an OpenSearch request, by parsing it and walking the resulting tree.
 */
public final class HXQLQueryConverter {

    protected static final String SELECT_ALL = "SELECT * FROM SysContent";

    protected static final String SELECT_ALL_WHERE = "SELECT * FROM SysContent WHERE ";

    protected static final String SIMPLE_QUERY_PREFIX = "es: ";

    /** The operators that combine predicates rather than compare values. */
    private static final Set<Operator> BOOLEAN_OPERATORS = Set.of(Operator.AND, Operator.OR, Operator.NOT);

    /** The operators a fulltext field can be searched with. */
    private static final Set<Operator> FULLTEXT_OPERATORS = Set.of(Operator.EQ, Operator.NOTEQ, Operator.LIKE,
            Operator.NOTLIKE);

    private static final Set<String> SYNTHETIC_OPENSEARCH_FIELDS = Set.of(SYS_FULLTEXT, SYS_FULLTEXT_SCORE);

    private final SchemaManager schemaManager;

    public HXQLQueryConverter(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    public ObjectBuilder<? extends QueryVariant> toESQueryBuilder(final Statement hxqlQuery, final CoreSession session) {
        StatementValidator.rejectSecurityProperties(hxqlQuery, session == null ? null : session.getPrincipal(),
                schemaManager);
        final List<ExpressionBuilder> builders = new ArrayList<>();
        Statement query = hxqlQuery;
        if (session != null) {
            SecurityPolicyService securityPolicyService = Framework.getService(SecurityService.class)
                                                                   .getSecurityPolicyService(
                                                                           session.getRepositoryName());
            QueryParameters queryParams = new QueryParameters(session.getPrincipal(), 0, 0);
            query = securityPolicyService.addSecurityPolicies(query, queryParams);
        }
        ExpressionBuilder ret = new ExpressionBuilder(null);
        builders.add(ret);
        List<String> fromList = new ArrayList<>();
        for (String type : query.getFromClause().elements()) {
            // Removed code for SYS_CONTENT optimization. See, HXCS-3121 Get rid of optimization until inconsistencies are fixed
            Set<String> types = schemaManager.getDocumentTypeNamesExtending(type);
            if (types == null) {
                throw new QueryParseException("Unknown type: " + type);
            }
            fromList.addAll(types);
        }
        if (query.getWhereClause() != null) {
            walkPredicate(query.getWhereClause().predicate(), builders, session);
        }
        ObjectBuilder<? extends QueryVariant> queryBuilder = ret.get();
        if (!fromList.isEmpty()) {
            return QueryBuilders.bool()
                                .must(queryBuilder.build().toQuery())
                                .filter(makeQueryFromSimpleExpression(Operator.IN, SYS_PRIMARY_TYPE, null,
                                        fromList.toArray(String[]::new), null, null).filter.build().toQuery());
        }
        return queryBuilder;
    }

    /**
     * Walks a predicate, pushing a builder for each boolean operator and adding a leaf query for everything else.
     */
    protected void walkPredicate(Operand node, List<ExpressionBuilder> builders, CoreSession session) {
        switch (node) {
        case MultiExpression multiExpression -> walkBoolean(multiExpression.operator, multiExpression.predicates,
                builders, session);
        case Predicate expression -> walkExpression(expression, builders, session);
        default -> throw new IllegalArgumentException("Not a predicate: " + node);
        }
    }

    /**
     * Walks what a boolean operator combines under a builder of its own, which is then merged into the enclosing one.
     */
    protected void walkBoolean(Operator operator, List<? extends Operand> operands,
            List<ExpressionBuilder> builders, CoreSession session) {
        builders.add(new ExpressionBuilder(operator));
        operands.forEach(operand -> walkPredicate(operand, builders, session));
        ExpressionBuilder expr = builders.removeLast();
        if (!builders.isEmpty()) {
            builders.getLast().merge(expr);
        }
    }

    protected void walkExpression(Predicate node, List<ExpressionBuilder> builders, CoreSession session) {
        Operator op = node.operator;
        if (BOOLEAN_OPERATORS.contains(op)) {
            List<Operand> operands = node.rvalue == null ? List.of(node.lvalue) : List.of(node.lvalue, node.rvalue);
            walkBoolean(op, operands, builders, session);
            return;
        }
        if (node.lvalue instanceof Parameter || node.rvalue instanceof Parameter) {
            // a parameter is bound when the query is parsed, so one reaching here was never given a value; without
            // this it would be rendered as its own text and searched for as if it were the value
            throw new QueryParseException("Unbound query parameter in: " + node);
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
        if (ref != null && ref.ftHint() != null) {
            throw new IllegalArgumentException("Query hint not supported: " + ref.ftHint());
        }
        FtHint hint = null;
        builders.getLast().add(makeQueryFromSimpleExpression(op, name, value, values, hint, session));
    }

    /**
     * Parses a query, which may be a bare {@code WHERE} clause rather than a whole query.
     * <p>
     * This is the one place a query text becomes a tree: the tree is what the conversion methods take, so a query is
     * parsed once however many of them are asked about it.
     */
    public static Statement parse(String hxql) {
        return parse(hxql, Map.of());
    }

    /**
     * Parses a query, binding the values of its {@code :name} parameters.
     */
    public static Statement parse(String hxql, Map<String, Object> parameters) {
        String query = completeQueryWithSelect(hxql);
        try {
            return HXQLParser.parse(query, parameters);
        } catch (QueryParseException e) {
            e.addInfo("Query: " + query);
            throw e;
        }
    }

    protected static String completeQueryWithSelect(String hxql) {
        String query = (hxql == null) ? "" : hxql.trim();
        if (query.isEmpty()) {
            query = SELECT_ALL;
        } else if (!query.toLowerCase().startsWith("select ")) {
            query = SELECT_ALL_WHERE + hxql;
        }
        return query;
    }

    protected QueryAndFilter makeQueryFromSimpleExpression(Operator op, String nxqlName, String value,
            String[] values, FtHint hint, CoreSession session) {
        FieldUse fieldUse = resolveFieldUse(nxqlName);
        validateFieldUse(fieldUse, false);
        Field field = fieldUse.field();
        ObjectBuilder<? extends QueryVariant> query = null;
        ObjectBuilder<? extends QueryVariant> filter = null;
        String name = getFieldName(nxqlName, hint);
        if (nxqlName.startsWith(SYS_FULLTEXT) && FULLTEXT_OPERATORS.contains(op)) {
            query = makeFulltextQuery(nxqlName, value, hint);
            if (isNegated(op)) {
                filter = QueryBuilders.bool().mustNot(query.build().toQuery());
                query = null;
            }
        } else if (nxqlName.startsWith(SYS_ANCESTOR_IDS)) {
            filter = makeAncestorIdFilter(value, session);
            if (op == Operator.NOTEQ) {
                filter = QueryBuilders.bool().mustNot(filter.build().toQuery());
            }
        } else if (SYS_MIXIN_TYPES.equals(nxqlName)
                && (op == Operator.EQ || op == Operator.NOTEQ || op == Operator.IN || op == Operator.NOTIN)) {
            filter = makeMixinTypesFilter(values == null ? List.of(value) : Arrays.asList(values));
            if (isNegated(op)) {
                filter = QueryBuilders.bool().mustNot(filter.build().toQuery());
            }
        } else {
            switch (op) {
            case EQ -> {
                FieldValue normval = checkBoolValue(field, value);
                if (isFalseAsNullInIndex(field) && normval == FieldValue.FALSE) {
                    filter = QueryBuilders.bool().mustNot(QueryBuilders.exists().field(name).build().toQuery());
                } else {
                    filter = QueryBuilders.term().field(name).value(normval);
                }
            }
            case NOTEQ -> {
                FieldValue normval = checkBoolValue(field, value);
                if (isFalseAsNullInIndex(field)) {
                    if (normval == FieldValue.TRUE) {
                        filter = QueryBuilders.bool().mustNot(QueryBuilders.exists().field(name).build().toQuery());
                    } else {
                        filter = QueryBuilders.term().field(name).value(FieldValue.TRUE);
                    }
                } else {
                    filter = QueryBuilders.bool()
                                          .mustNot(QueryBuilders.term().field(name).value(normval).build().toQuery());
                }
            }
            case GT -> filter = QueryBuilders.range().field(name).gt(JsonData.of(value));
            case LT -> filter = QueryBuilders.range().field(name).lt(JsonData.of(value));
            case GTEQ -> filter = QueryBuilders.range().field(name).gte(JsonData.of(value));
            case LTEQ -> filter = QueryBuilders.range().field(name).lte(JsonData.of(value));
            case BETWEEN, NOTBETWEEN -> {
                filter = QueryBuilders.range().field(name).from(JsonData.of(values[0])).to(JsonData.of(values[1]));
                if (isNegated(op)) {
                    filter = QueryBuilders.bool().mustNot(filter.build().toQuery());
                }
            }
            case IN, NOTIN -> {
                filter = QueryBuilders.terms()
                                      .field(name)
                                      .terms(t -> t.value(Arrays.stream(values).map(FieldValue::of).toList()));
                if (isNegated(op)) {
                    filter = QueryBuilders.bool().mustNot(filter.build().toQuery());
                }
            }
            case ISNULL -> filter = QueryBuilders.bool()
                                                 .mustNot(QueryBuilders.exists().field(name).build().toQuery());
            case ISNOTNULL -> filter = QueryBuilders.exists().field(name);
            case LIKE, ILIKE, NOTLIKE, NOTILIKE -> {
                query = makeLikeQuery(op, name, value, hint);
                if (isNegated(op)) {
                    filter = QueryBuilders.bool().mustNot(query.build().toQuery());
                    query = null;
                }
            }
            case STARTSWITH -> filter = makeStartsWithQuery(name, value);
            default -> throw new UnsupportedOperationException("Operator: '" + op + "' is unknown");
            }
        }
        return new QueryAndFilter(query, filter);
    }

    /**
     * Matches any of the given mixins, resolving type-defined mixins from the current model even when indexed mixins
     * predate a model change. Like repository queries, type-only mixins never consult the per-document mixin field.
     * <p>
     * Used for:
     * <ul>
     * <li>{@code sys_mixinTypes = 'Foo'}
     * <li>{@code sys_mixinTypes <> 'Foo'}
     * <li>{@code sys_mixinTypes IN ('Foo', 'Bar')}
     * <li>{@code sys_mixinTypes NOT IN ('Foo', 'Bar')}
     * </ul>
     * For example, if {@code Foo} is type-only and {@code Bar} allows per-document queries,
     * {@code sys_mixinTypes IN ('Foo', 'Bar')} produces:
     *
     * <pre>
     * { "bool": {
     *     "should": [
     *       { "terms": { "sys_primaryType": [ ... types with Foo or Bar ... ] } },
     *       { "terms": { "sys_mixinTypes": [ "Bar" ] } }
     *     ]
     * } }
     * </pre>
     *
     * The caller wraps this positive filter in {@code must_not} for {@code <>} and {@code NOT IN}. Thus,
     * {@code sys_mixinTypes NOT IN ('Foo', 'Bar')} produces:
     *
     * <pre>
     * { "bool": { "must_not": [
     *   { "bool": {
     *       "should": [
     *         { "terms": { "sys_primaryType": [ ... types with Foo or Bar ... ] } },
     *         { "terms": { "sys_mixinTypes": [ "Bar" ] } }
     *       ]
     *   } }
     * ] } }
     * </pre>
     *
     * Empty alternatives are omitted; if neither primary types nor per-document mixins can match, returns
     * {@code match_none}.
     */
    protected ObjectBuilder<? extends QueryVariant> makeMixinTypesFilter(List<String> mixins) {
        Set<String> primaryTypes = new TreeSet<>();
        Set<String> instanceMixins = new TreeSet<>();
        Set<String> typeOnlyMixins = schemaManager.getNoPerDocumentQueryMixins();
        for (String mixin : mixins) {
            Set<String> types = schemaManager.getDocumentTypeNamesForMixin(mixin);
            if (types != null) {
                primaryTypes.addAll(types);
            }
            if (!typeOnlyMixins.contains(mixin)) {
                instanceMixins.add(mixin);
            }
        }
        List<Query> alternatives = new ArrayList<>();
        if (!primaryTypes.isEmpty()) {
            alternatives.add(QueryBuilders.terms()
                                          .field(SYS_PRIMARY_TYPE)
                                          .terms(t -> t.value(primaryTypes.stream().map(FieldValue::of).toList()))
                                          .build().toQuery());
        }
        if (!instanceMixins.isEmpty()) {
            alternatives.add(QueryBuilders.terms()
                                          .field(SYS_MIXIN_TYPES)
                                          .terms(t -> t.value(instanceMixins.stream().map(FieldValue::of).toList()))
                                          .build().toQuery());
        }
        if (alternatives.isEmpty()) {
            return QueryBuilders.matchNone();
        }
        return QueryBuilders.bool().should(alternatives);
    }

    /** Whether an operator is the negation of another one, and so is rendered as its query wrapped in mustNot. */
    protected static boolean isNegated(Operator op) {
        return switch (op) {
        case NOTEQ, NOTLIKE, NOTILIKE, NOTIN, NOTBETWEEN -> true;
        default -> false;
        };
    }

    protected FieldValue checkBoolValue(Field field, String value) {
        if (value == null) {
            return FieldValue.NULL;
        }

        // accept 0/1 (legacy HXQL int form) and true/false (BooleanLiteral.asString)
        Boolean bool = parseBoolValue(value);
        if (bool == null) {
            return FieldValue.of(value);
        }
        if (field == null || !BooleanType.ID.equals(field.getType().getName())) {
            return FieldValue.of(value);
        }
        return bool ? FieldValue.TRUE : FieldValue.FALSE;
    }

    protected boolean isFalseAsNullInIndex(Field field) {
        return field != null && field.getCharacteristics().falseAsNull().contains(INDEX);
    }

    /**
     * Parses a string representation of a boolean literal as it appears in an HXQL query: {@code "0"}/{@code "1"}
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

    protected static ObjectBuilder<? extends QueryVariant> makeStartsWithQuery(String name, Object value) {
        if (SYS_PARENT_PATH.equals(name)) {
            throw new QueryParseException(SYS_PARENT_PATH + " does not support STARTSWITH");
        }
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

    protected static ObjectBuilder<? extends QueryVariant> makeLikeQuery(Operator op, String name, String value,
            FtHint hint) {
        String fieldName = name;
        if (op == Operator.ILIKE || op == Operator.NOTILIKE) {
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
        // path properties are keyword fields, not text fields, so match_phrase_prefix cannot be used
        if (!SYS_PATH.equals(name) && !SYS_PARENT_PATH.equals(name) && StringUtils.countMatches(wildcard, "*") == 1
                && wildcard.endsWith("*") && !wildcard.contains("?") && !wildcard.contains("\\")) {
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
     * Turns an HXQL LIKE pattern into a wildcard for WildcardQuery.
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
            case '*': // compat, * = % in HXQL (for some backends)
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

    /** The sorts the query's {@code ORDER BY} asks for, empty if it has none. */
    public List<SortInfo> getSortInfo(Statement query) {
        List<SortInfo> sortInfos = new ArrayList<>();
        OrderByClause orderBy = query.getOrderByClause();
        if (orderBy != null) {
            for (OrderByExpr expr : orderBy.elements()) {
                String referenceName = expr.reference().name();
                validateFieldUse(resolveFieldUse(referenceName), false);
                String name = getFieldName(referenceName, null);
                if (SYS_FULLTEXT_SCORE.equals(name)) {
                    name = OPENSEARCH_SCORE_FIELD;
                }
                sortInfos.add(new SortInfo(name, !expr.isDescending()));
            }
        }
        return sortInfos;
    }

    /** The fields the query projects, with their types, empty for {@code SELECT *}. */
    public Map<String, Type> getSelectClauseFields(Statement query) {
        Map<String, Type> fieldsAndTypes = new LinkedHashMap<>();
        for (Operand op : query.getSelectClause().elements()) {
            if (op instanceof Reference reference) {
                FieldUse fieldUse = resolveFieldUse(reference.name());
                validateFieldUse(fieldUse, true);
                Field field = fieldUse.field();
                fieldsAndTypes.put(reference.name(), field == null ? null : field.getType());
            }
        }
        return fieldsAndTypes;
    }

    protected FieldUse resolveFieldUse(String name) {
        Field field = schemaManager.getField(name);
        int slash = name.indexOf('/');
        Field rootField = slash < 0 ? field : schemaManager.getField(name.substring(0, slash));
        return new FieldUse(field, rootField);
    }

    protected void validateFieldUse(FieldUse fieldUse, boolean projection) {
        Field field = fieldUse.field();
        Field rootField = fieldUse.rootField();
        if (rootField != null && !SYNTHETIC_OPENSEARCH_FIELDS.contains(rootField.getName())
                && rootField.getCharacteristics().notOutput().contains(INDEX)) {
            throw new QueryParseException("Disallowed system property: " + rootField.getName());
        }
        if (field == null) {
            return;
        }
        var characteristics = field.getCharacteristics();
        boolean unsupported = projection ? characteristics.notProjectable() : characteristics.notQueryable();
        if (unsupported) {
            throw new QueryParseException("Disallowed system property: " + field.getName());
        }
    }

    /**
     * Validates a property named directly by an aggregation, rather than reached through a parsed HXQL statement.
     * <p>
     * Such a property reaches OpenSearch without passing through {@link #toESQueryBuilder}, so neither of the checks
     * that path applies runs on it: the security-property rejection performed by
     * {@link StatementValidator#rejectSecurityProperties} and the field-characteristics check performed by
     * {@link #validateFieldUse}. Both are applied here.
     * <p>
     * Two things differ from a statement reference. The name arrives in index notation ({@code a.b}) rather than
     * HXQL notation ({@code a/b}), and a property whose <em>root</em> is not in the content model is rejected
     * rather than ignored, so that an aggregation cannot reach an index field the content model never declared.
     * Only the root is required to resolve: a sub-property of an open type (an {@code xs:anyType} element, say)
     * is dynamic by design and carries no declaration of its own, yet aggregating it is a supported use.
     * <p>
     * A descendant that resolves to nothing is therefore allowed, but only beneath a root that may itself be
     * projected. {@link #validateFieldUse} stops at the root's index representation when the leaf is unresolved,
     * so the root's projectability is checked here: a sub-field of a property the content model keeps out of
     * query results would otherwise expose the very values that property is held back for.
     *
     * @param indexFieldName the property in index (dot-separated) notation
     * @param principal the caller, whose administrator status exempts it from the security-property check, as it
     *            does for a statement reference
     * @throws QueryParseException if the property may not be aggregated
     */
    public void validateAggregationField(String indexFieldName, RepositoryPrincipal principal) {
        if (StringUtils.isBlank(indexFieldName)) {
            throw new QueryParseException("Missing aggregation property");
        }
        String xpath = indexFieldName.replace('.', '/');
        if ((principal == null || !principal.isAdministrator()) && schemaManager.isSecurityProperty(xpath)) {
            throw new QueryParseException("Disallowed system property: " + indexFieldName);
        }
        FieldUse fieldUse = resolveFieldUse(xpath);
        if (fieldUse.rootField() == null) {
            throw new QueryParseException("Unknown property: " + indexFieldName);
        }
        validateFieldUse(fieldUse, true);
        if (fieldUse.field() == null) {
            validateNearestDeclaredAncestor(xpath, principal);
        }
    }

    /**
     * Holds a descendant that resolves to nothing to the restrictions of the deepest property that does resolve
     * along its path.
     * <p>
     * The index can hold sub-fields the content model never declared, a multi-field among them, so a name such as
     * {@code complex.secret.keyword} resolves to no field of its own. Falling back to the root would ask only
     * whether {@code complex} may be projected and so would read the values of {@code complex/secret} straight
     * past that property's own restriction. The deepest declared ancestor is the one whose answer governs.
     */
    private void validateNearestDeclaredAncestor(String xpath, RepositoryPrincipal principal) {
        String ancestorPath = getNearestDeclaredAncestor(xpath);
        if (ancestorPath == null) {
            return; // the root resolved, so this cannot happen; nothing to hold the descendant to either way
        }
        if ((principal == null || !principal.isAdministrator()) && schemaManager.isSecurityProperty(ancestorPath)) {
            throw new QueryParseException("Disallowed system property: " + ancestorPath);
        }
        Field ancestor = schemaManager.getField(ancestorPath);
        var characteristics = ancestor.getCharacteristics();
        if (characteristics.notProjectable()
                || (!SYNTHETIC_OPENSEARCH_FIELDS.contains(ancestor.getName())
                        && characteristics.notOutput().contains(INDEX))) {
            throw new QueryParseException("Disallowed system property: " + ancestor.getName());
        }
    }

    /**
     * The xpath of the deepest property along the given path that the content model declares, or {@code null} if
     * it declares none of them.
     */
    protected String getNearestDeclaredAncestor(String xpath) {
        String nearest = null;
        StringBuilder path = new StringBuilder();
        for (String segment : xpath.split("/")) {
            if (!path.isEmpty()) {
                path.append('/');
            }
            path.append(segment);
            if (schemaManager.getField(path.toString()) != null) {
                nearest = path.toString();
            }
        }
        return nearest;
    }

    protected record FieldUse(Field field, Field rootField) {
    }

    /**
     * Translates from OpenArch syntax to OpenSearch simple_query_string syntax.
     */
    protected static String translateFulltextQuery(String query) {
        // The AND operator does not exist in HXQL it is the default operator
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

        /** The boolean operator this builder combines its parts with, or {@code null} at the top level. */
        public final Operator operator;

        public ObjectBuilder<? extends QueryVariant> query;

        public ExpressionBuilder(final Operator op) {
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
                switch (operator) {
                case AND -> boolQuery.must(inputQuery.build().toQuery());
                case OR -> boolQuery.should(inputQuery.build().toQuery());
                case NOT -> boolQuery.mustNot(inputQuery.build().toQuery());
                default -> throw new IllegalArgumentException("Not a boolean operator: " + operator);
                }
            }
        }

        public void merge(ExpressionBuilder expr) {
            if (expr.operator != null && expr.operator == operator && query == null) {
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
