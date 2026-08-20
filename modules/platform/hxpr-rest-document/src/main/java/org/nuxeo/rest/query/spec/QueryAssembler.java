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

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.nxql.SortInfo;
import org.nuxeo.ecm.core.query.sql.NXQLSerializer;
import org.nuxeo.ecm.core.query.sql.QueryParameterBinder;
import org.nuxeo.ecm.core.query.sql.SQLQueryParser;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.Function;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.OperandList;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.OrderByList;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.SelectList;
import org.nuxeo.ecm.core.query.sql.model.WhereClause;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.WhereClauseDTO;

/**
 * Builds the NXQL a query request asks for.
 * <p>
 * The request's parts — a query, predicates, quick filter clauses, parameters and sorts — are combined as a syntax
 * tree and rendered once at the end, so the pieces are joined structurally rather than by splicing strings.
 *
 * @since 12.0
 */
public class QueryAssembler {

    /**
     * Placeholder for the column the results are sorted on, usable in a query definition that does not know it yet.
     */
    public static final String SORTED_COLUMN = "SORTED_COLUMN";

    public static final String DEFAULT_SELECT_STATEMENT = "SELECT * FROM SysContent";

    private QueryAssembler() {
        // utility class
    }

    /**
     * Builds the query text for a request.
     *
     * @param query the request
     * @param sortInfos the sorts to apply, which replace any {@code ORDER BY} the query text already has
     * @return the NXQL to execute
     */
    public static String assemble(DocumentAdvancedQueryDTO query, List<SortInfo> sortInfos) {
        SQLQuery parsed = parse(baseQuery(query));
        Map<String, Object> parameters = query.parameters();

        List<Predicate> conjuncts = new ArrayList<>();
        WhereClauseDTO clauseDef = query.whereClauseDefinition();
        if (clauseDef != null) {
            conjuncts.addAll(PredicateBuilder.build(clauseDef.predicates(), parameters));
        }
        if (parsed.getWhereClause() != null) {
            conjuncts.add(parsed.getWhereClause().predicate());
        }
        for (String clause : query.quickFilterClauses()) {
            if (StringUtils.isNotBlank(clause)) {
                conjuncts.add(parsePredicate(clause));
            }
        }

        SQLQuery assembled = parsed.withPredicate(conjuncts.isEmpty() ? null : conjunction(conjuncts));
        assembled = withSort(assembled, sortInfos);
        assembled = rewriteSortedColumn(assembled, sortedColumn(sortInfos));
        assembled = bind(assembled, parameters);
        return NXQLSerializer.serialize(assembled);
    }

    /** A where clause definition's own query wins over the request's, as it is the more specific one. */
    protected static String baseQuery(DocumentAdvancedQueryDTO query) {
        WhereClauseDTO clauseDef = query.whereClauseDefinition();
        if (clauseDef != null && StringUtils.isNotBlank(clauseDef.query())) {
            return clauseDef.query();
        }
        return StringUtils.isNotBlank(query.query()) ? query.query() : DEFAULT_SELECT_STATEMENT;
    }

    protected static Predicate conjunction(List<Predicate> conjuncts) {
        return conjuncts.size() == 1 ? conjuncts.get(0) : new MultiExpression(Operator.AND, conjuncts);
    }

    protected static SQLQuery parse(String query) {
        try {
            return SQLQueryParser.parse(query);
        } catch (NuxeoException e) {
            e.addInfo("Query: " + query);
            throw e;
        }
    }

    protected static Predicate parsePredicate(String clause) {
        try {
            return SQLQueryParser.parsePredicate(clause);
        } catch (NuxeoException e) {
            e.addInfo("Clause: " + clause);
            throw e;
        }
    }

    protected static SQLQuery bind(SQLQuery query, Map<String, Object> parameters) {
        try {
            return QueryParameterBinder.bind(query, parameters);
        } catch (QueryParseException e) {
            e.addInfo("Query: " + NXQLSerializer.serialize(query));
            throw e;
        }
    }

    /**
     * Applies the request's sorts, which replace any {@code ORDER BY} the query text already carries. A request with no
     * sorts leaves it alone.
     */
    protected static SQLQuery withSort(SQLQuery query, List<SortInfo> sortInfos) {
        if (sortInfos.isEmpty()) {
            return query;
        }
        OrderByList orderBy = new OrderByList();
        for (SortInfo sortInfo : sortInfos) {
            orderBy.add(new OrderByExpr(fieldReference(sortInfo.sortColumn()), !sortInfo.sortAscending()));
        }
        return query.withOrderByClause(new OrderByClause(orderBy));
    }

    /** The reference {@link #SORTED_COLUMN} stands for: the first sort column, or the id when there is no sort. */
    protected static Reference sortedColumn(List<SortInfo> sortInfos) {
        return sortInfos.isEmpty() ? new Reference(SYS_ID) : fieldReference(sortInfos.get(0).sortColumn());
    }

    /**
     * A field name goes into the query as an identifier, which NXQL has no way to quote, so one that is not a valid
     * identifier has to be rejected rather than escaped. The grammar itself decides what is valid.
     */
    protected static Reference fieldReference(String name) {
        try {
            return SQLQueryParser.parseReference(name);
        } catch (QueryParseException e) {
            throw new NuxeoException("Not a valid field name: " + name, e, BAD_REQUEST.value());
        }
    }

    // --- SORTED_COLUMN rewriting ---

    protected static SQLQuery rewriteSortedColumn(SQLQuery query, Reference column) {
        SelectClause select = rewriteSelect(query.getSelectClause(), column);
        WhereClause where = query.getWhereClause() == null ? null
                : new WhereClause((Predicate) rewrite(query.getWhereClause().predicate(), column));
        OrderByClause orderBy = rewriteOrderBy(query.getOrderByClause(), column);
        return new SQLQuery(select, query.getFromClause(), where, orderBy, query.getLimit(), query.getOffset());
    }

    protected static SelectClause rewriteSelect(SelectClause select, Reference column) {
        if (select == null || select.elements.isEmpty()) {
            return select;
        }
        return new SelectClause(new SelectList(select.elements.stream().map(o -> rewrite(o, column)).toList()));
    }

    protected static OrderByClause rewriteOrderBy(OrderByClause orderBy, Reference column) {
        if (orderBy == null || orderBy.elements() == null) {
            return orderBy;
        }
        OrderByList rewritten = new OrderByList();
        for (OrderByExpr expr : orderBy.elements()) {
            rewritten.add(new OrderByExpr((Reference) rewrite(expr.reference(), column), expr.isDescending()));
        }
        return new OrderByClause(rewritten);
    }

    protected static Operand rewrite(Operand operand, Reference column) {
        if (operand instanceof Reference reference) {
            return SORTED_COLUMN.equals(reference.name()) ? column : reference;
        }
        if (operand instanceof MultiExpression multiExpression) {
            List<Predicate> predicates = multiExpression.predicates.stream()
                                                                   .map(p -> (Predicate) rewrite(p, column))
                                                                   .toList();
            return new MultiExpression(multiExpression.operator, predicates);
        }
        if (operand instanceof Expression expression) {
            Operand lvalue = rewrite(expression.lvalue, column);
            Operand rvalue = expression.rvalue == null ? null : rewrite(expression.rvalue, column);
            if (lvalue == expression.lvalue && rvalue == expression.rvalue) {
                return expression;
            }
            return new Predicate(lvalue, expression.operator, rvalue);
        }
        if (operand instanceof Function function && function.args() != null) {
            List<Operand> args = function.args().stream().map(arg -> rewrite(arg, column)).toList();
            return new Function(function.name(), new OperandList(args));
        }
        return operand;
    }

}
