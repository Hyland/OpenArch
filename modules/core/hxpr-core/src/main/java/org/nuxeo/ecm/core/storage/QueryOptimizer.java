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

import static java.util.stream.Collectors.groupingBy;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_RELATION;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_ROOT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.FromClause;
import org.nuxeo.ecm.core.query.sql.model.Function;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.OperandList;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.Statement;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.query.sql.model.WhereClause;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Type;

import org.nuxeo.ecm.core.storage.QueryAnnotations.PrefixInfo;

/**
 * Generic optimizer for a NXQL query.
 *
 * @since 5.9.4
 */
public abstract class QueryOptimizer {


    protected static final int CORR_BASE = 100_000;

    protected final SchemaManager schemaManager;

    protected final Set<String> neverPerInstanceMixins;

    protected int correlationCounter;

    /** Do we match only relations? */
    protected boolean onlyRelations;

    /** What we work out about the query's nodes, kept beside the tree. */
    protected final QueryAnnotations annotations = new QueryAnnotations();

    protected QueryOptimizer(SchemaManager schemaManager) {
        // schemaManager may be null in unit tests
        this.schemaManager = schemaManager;
        Set<String> mixins = schemaManager == null ? Collections.emptySet()
                : schemaManager.getNoPerDocumentQueryMixins();
        neverPerInstanceMixins = new HashSet<>(mixins);
    }

    /**
     * Optimizes a query to provide a WHERE clause containing mixin filters, primary and mixin types. In addition, the
     * top-level AND clauses are analyzed to provide prefix info.
     */
    /** The annotations computed by the last {@link #optimize} call. */
    public QueryAnnotations getAnnotations() {
        return annotations;
    }

    public Statement optimize(Statement query) {
        // rewrite some uncorrelated wildcards and add NOT NULL clauses for projection ones
        query = addWildcardNotNullClauses(query);

        List<Predicate> clauses = new ArrayList<>();
        addTypes(clauses, query.getFromClause());
        addWhere(clauses, query.getWhereClause());
        simplifyTypes(clauses);
        MultiExpression multiExpression = new MultiExpression(Operator.AND, clauses);

        // work out the common reference prefixes, recorded in the annotations
        analyzePrefixes(multiExpression);

        Predicate whereExpr;
        if (!annotations.getPrefix(multiExpression).isEmpty()) {
            // all references have a common prefix
            whereExpr = multiExpression;
        } else {
            // do grouping by common prefix, keeping order
            Map<String, List<Predicate>> grouped = clauses.stream()
                                                         .collect(groupingBy(this::getPredicatePrefix,
                                                                 LinkedHashMap::new, Collectors.toList()));
            Map<String, Predicate> groupedExpressions = new LinkedHashMap<>();
            for (Entry<String, List<Predicate>> en : grouped.entrySet()) {
                String prefix = en.getKey();
                List<Predicate> list = en.getValue();
                groupedExpressions.put(prefix, makeSingleAndPredicate(prefix, list));
            }

            // potentially reorganize into nested grouping
            reorganizeGroupedExpressions(groupedExpressions);

            List<Predicate> expressions = new ArrayList<>(groupedExpressions.values());
            whereExpr = makeSingleAndPredicate("", expressions);
        }

        return query.withPredicate(whereExpr);
    }

    /**
     * Makes a single AND predicate from several expressions known to have a common prefix.
     */
    public Predicate makeSingleAndPredicate(String prefix, List<Predicate> predicates) {
        if (predicates.size() == 1) {
            return predicates.get(0);
        } else {
            int count = prefix.isEmpty() ? 0 : predicates.stream().mapToInt(this::getExpressionCount).sum();
            Predicate e = new MultiExpression(Operator.AND, predicates);
            annotations.setPrefixInfo(e, new PrefixInfo(prefix, count));
            return e;
        }
    }

    protected String getPredicatePrefix(Predicate predicate) {
        return annotations.getPrefix(predicate);
    }

    protected int getExpressionCount(Predicate expr) {
        return annotations.getCount(expr);
    }

    /**
     * Reorganizes the grouped expressions in order to have 2-level nesting in case a group is a prefix of another.
     *
     * @since 9.3
     */
    public void reorganizeGroupedExpressions(Map<String, Predicate> groupedExpressions) {
        if (groupedExpressions.size() > 1) {
            List<String> keys = new ArrayList<>(groupedExpressions.keySet());
            List<String> withPrefix = new ArrayList<>();
            String prefix = findPrefix(keys, withPrefix);
            if (prefix != null) {
                // first part, the expression corresponding to the prefix
                Predicate first = groupedExpressions.remove(prefix);

                // second part, all those that had that prefix
                List<Predicate> exprs = new ArrayList<>();
                for (String k : withPrefix) {
                    exprs.add(groupedExpressions.remove(k));
                }
                String secondPrefix;
                if (withPrefix.size() == 1) {
                    secondPrefix = withPrefix.get(0);
                } else {
                    throw new QueryParseException("Too complex correlated wildcards in query: " + groupedExpressions);
                }
                Predicate second = makeSingleAndPredicate(secondPrefix, exprs);

                // finally bring them all together
                Predicate expr = makeSingleAndPredicate(prefix, Arrays.asList(first, second));
                groupedExpressions.put(prefix, expr);
            }
        }
    }

    /**
     * Finds a non-empty prefix in the strings.
     * <p>
     * If a prefix is found, the other strings having it as a prefix are collected in {@code withPrefix}, and the prefix
     * and the found strings are moved from the input {@code string}.
     * <p>
     * {@code strings} and {@code withPrefix} must both be ArrayLists as they will be mutated and queried by index.
     *
     * @param strings the input strings
     * @param withPrefix (return value) the strings that have the found prefix as a prefix
     * @return the prefix if found, or null if not
     * @since 9.3
     */
    public static String findPrefix(List<String> strings, List<String> withPrefix) {
        // naive algorithm as list size is very small
        String prefix = null;
        ALL: //
        for (int i = 0; i < strings.size(); i++) {
            String candidate = strings.get(i);
            if (candidate.isEmpty()) {
                continue;
            }
            for (int j = 0; j < strings.size(); j++) {
                if (i == j) {
                    continue;
                }
                String s = strings.get(j);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.startsWith(candidate + '/')) {
                    prefix = candidate;
                    break ALL;
                }
            }
        }
        if (prefix != null) {
            for (Iterator<String> it = strings.iterator(); it.hasNext();) {
                String s = it.next();
                if (s.isEmpty()) {
                    continue;
                }
                if (s.equals(prefix)) {
                    it.remove(); // will be returned as method result
                    continue;
                }
                if (s.startsWith(prefix + '/')) {
                    it.remove(); // will be returned in withPrefix
                    withPrefix.add(s);
                }
            }
        }
        return prefix;
    }

    // methods using SchemaManager easily overrideable for easier testing

    protected Set<String> getDocumentTypeNamesForMixin(String mixin) {
        Set<String> types = schemaManager.getDocumentTypeNamesForMixin(mixin);
        if (types == null) {
            // unknown mixin
            types = Collections.emptySet();
        }
        return types;
    }

    protected Set<String> getDocumentTypeNamesExtending(String typeName) {
        Set<String> types = schemaManager.getDocumentTypeNamesExtending(typeName);
        if (types == null) {
            throw new QueryParseException("Unknown type: " + typeName);
        }
        return types;
    }

    protected boolean isTypeRelation(String typeName) {
        do {
            if (SYS_RELATION.equals(typeName)) {
                return true;
            }
            Type t = schemaManager.getDocumentType(typeName);
            if (t != null) {
                t = t.getSuperType();
            }
            typeName = t == null ? null : t.getName();
        } while (typeName != null);
        return false;
    }

    /**
     * Finds all the types to take into account (all concrete types being a subtype of the passed types) based on the
     * FROM list.
     * <p>
     * Adds them as a sys_primaryType match in the toplevel operands.
     */
    protected void addTypes(List<Predicate> clauses, FromClause node) {
        onlyRelations = true;
        Set<String> fromTypes = new HashSet<>();
        for (String typeName : node.elements()) {
            if (SYS_CONTENT.equalsIgnoreCase(typeName)) {
                typeName = SYS_CONTENT;
            }
            fromTypes.addAll(getDocumentTypeNamesExtending(typeName));
            boolean isRelation = isTypeRelation(typeName);
            onlyRelations = onlyRelations && isRelation;
        }
        fromTypes.remove(SYS_ROOT);
        LiteralList list = new LiteralList(fromTypes.stream().map(t -> (Literal) new StringLiteral(t)).toList());
        clauses.add(new Predicate(new Reference(SYS_PRIMARY_TYPE), Operator.IN, list));
    }

    /**
     * Adds a flattened version of all toplevel ANDed WHERE clauses.
     */
    protected void addWhere(List<Predicate> clauses, WhereClause where) {
        if (where != null) {
            addWhere(clauses, where.predicate());
        }
    }

    protected void addWhere(List<Predicate> clauses, Predicate expr) {
        if (expr.operator == Operator.AND && expr.lvalue instanceof Predicate && expr.rvalue instanceof Predicate) {
            addWhere(clauses, (Predicate) expr.lvalue);
            addWhere(clauses, (Predicate) expr.rvalue);
        } else if (expr.operator == Operator.AND && expr instanceof MultiExpression multiExpression) {
            for (Predicate pred : multiExpression.predicates) {
                addWhere(clauses, pred);
            }
        } else {
            clauses.add(expr);
        }
    }

    /**
     * Simplify sys_primaryType positive references, and non-per-instance mixin types.
     */
    protected void simplifyTypes(List<Predicate> clauses) {
        Set<String> primaryTypes = null; // if defined, required
        for (Iterator<Predicate> it = clauses.iterator(); it.hasNext();) {
            // whenever we don't know how to optimize the expression,
            // we just continue the loop
            Predicate predicate = it.next();
            if (!(predicate.lvalue instanceof Reference)) {
                continue;
            }
            String name = ((Reference) predicate.lvalue).name();
            Operator op = predicate.operator;
            Operand rvalue = predicate.rvalue;
            if (SYS_PRIMARY_TYPE.equals(name)) {
                if (op != Operator.EQ && op != Operator.IN) {
                    continue;
                }
                Set<String> set;
                if (op == Operator.EQ) {
                    if (!(rvalue instanceof StringLiteral lit)) {
                        continue;
                    }
                    String primaryType = lit.value();
                    set = new HashSet<>(Collections.singleton(primaryType));
                } else { // Operator.IN
                    if (!(rvalue instanceof LiteralList)) {
                        continue;
                    }
                    set = getStringLiterals((LiteralList) rvalue);
                }
                if (primaryTypes == null) {
                    primaryTypes = set;
                } else {
                    primaryTypes.retainAll(set);
                }
                it.remove(); // expression simplified into primaryTypes set
            } else if (SYS_MIXIN_TYPES.equals(name)) {
                if (op != Operator.EQ && op != Operator.NOTEQ) {
                    continue;
                }
                if (!(rvalue instanceof StringLiteral lit)) {
                    continue;
                }
                String mixin = lit.value();
                if (!neverPerInstanceMixins.contains(mixin)) {
                    // mixin per instance -> primary type checks not enough
                    continue;
                }
                Set<String> set = getDocumentTypeNamesForMixin(mixin);
                if (primaryTypes == null) {
                    if (op == Operator.EQ) {
                        primaryTypes = new HashSet<>(set); // copy
                    } else {
                        continue; // unknown positive, no optimization
                    }
                } else {
                    if (op == Operator.EQ) {
                        primaryTypes.retainAll(set);
                    } else {
                        primaryTypes.removeAll(set);
                    }
                }
                it.remove(); // expression simplified into primaryTypes set
            }
        }
        // readd the simplified primary types constraints
        if (primaryTypes != null) {
            if (primaryTypes.isEmpty()) {
                // TODO better removal
                primaryTypes.add("__NOSUCHTYPE__");
            }
            Predicate predicate;
            if (primaryTypes.size() == 1) {
                String pt = primaryTypes.iterator().next();
                predicate = new Predicate(new Reference(SYS_PRIMARY_TYPE), Operator.EQ, new StringLiteral(pt));
            } else { // primaryTypes.size() > 1
                LiteralList list = new LiteralList(
                        primaryTypes.stream().map(pt -> (Literal) new StringLiteral(pt)).toList());
                predicate = new Predicate(new Reference(SYS_PRIMARY_TYPE), Operator.IN, list);
            }
            clauses.add(predicate);
        }
    }

    protected static Set<String> getStringLiterals(LiteralList list) {
        Set<String> set = new HashSet<>();
        for (Literal literal : list) {
            if (!(literal instanceof StringLiteral lit)) {
                throw new QueryParseException("requires string literals");
            }
            set.add(lit.value());
        }
        return set;
    }

    /**
     * If we have a query like {@code SELECT my_subjects/* FROM ...} then we must make sure we don't match documents
     * that don't have a {@code my_subjects} at all, as this would make the evaluator return one {@code null} value for
     * each due to its semantics of doing the equivalent of LEFT JOINs.
     * <p>
     * To prevent this, we add a clause {@code ... AND my_subjects/* IS NOT NULL}.
     * <p>
     * For correlated wildcards this is enough, but for uncorrelated wildcards we must avoid adding extra JOINs, so we
     * must artificially correlated them. This requires rewriting the query with correlated wildcards instead of
     * uncorrelated ones.
     */
    protected Statement addWildcardNotNullClauses(Statement query) {
        // find wildcards in the projection
        Set<String> wildcards = new HashSet<>();
        Set<String> uncorrelatedWildcards = new HashSet<>();
        forEachProjectionReference(query, ref -> {
            String name = ref.name();
            if (name.endsWith("*")) {
                wildcards.add(name);
                if (name.endsWith("/*")) {
                    uncorrelatedWildcards.add(name);
                }
            }
        });
        if (!uncorrelatedWildcards.isEmpty()) {
            // rename uncorrelated wildcards to unique correlation names
            Map<String, String> map = new HashMap<>(uncorrelatedWildcards.size());
            for (String name : uncorrelatedWildcards) {
                String newName = name + (CORR_BASE + correlationCounter++);
                map.put(name, newName);
                wildcards.remove(name);
                wildcards.add(newName);
            }
            // rename them in the projection, the only place they are looked for
            query = renameProjectionReferences(query, map);
        }

        // add IS NOT NULL clauses for all projection wildcards (now all correlated)
        if (!wildcards.isEmpty()) {
            query = addIsNotNullClauses(query, wildcards);
        }

        return query;
    }

    protected Statement addIsNotNullClauses(Statement query, Collection<String> names) {
        List<Predicate> values = names.stream()
                                      .map(name -> new Predicate(new Reference(name), Operator.ISNOTNULL, null))
                                      .toList();
        Predicate notNull = new MultiExpression(Operator.AND, values);
        WhereClause where = query.getWhereClause();
        Predicate predicate = where == null ? notNull : new Predicate(where.predicate(), Operator.AND, notNull);
        return query.withPredicate(predicate);
    }

    /** Runs the action on every reference of the projection, including inside function arguments. */
    protected static void forEachProjectionReference(Statement query, Consumer<Reference> action) {
        query.getSelectClause().elements().forEach(operand -> forEachReference(operand, action));
    }

    protected static void forEachReference(Operand operand, Consumer<Reference> action) {
        switch (operand) {
            case Reference reference -> action.accept(reference);
            case Function function -> {
                if (function.args() != null) {
                    function.args().forEach(arg -> forEachReference(arg, action));
                }
            }
            default -> {
                // a literal or a parameter holds no reference
            }
        }
    }

    /**
     * Renames the references of the projection, which is the only place uncorrelated wildcards are looked for. The
     * rest of the query is left as it is.
     */
    protected Statement renameProjectionReferences(Statement query, Map<String, String> map) {
        List<Operand> renamed = query.getSelectClause()
                                     .elements()
                                     .stream()
                                     .map(operand -> renameReferences(operand, map))
                                     .toList();
        return query.withSelectClause(new SelectClause(renamed));
    }

    protected Operand renameReferences(Operand operand, Map<String, String> map) {
        return switch (operand) {
            case Reference reference -> {
                String name = reference.name();
                String newName = map.getOrDefault(name, name);
                if (newName.equals(name)) {
                    yield reference;
                }
                Reference renamed = new Reference(newName, reference.cast(), reference.ftHint());
                // the evaluator needs the name the projection was asked for, to key the result by it
                annotations.setOriginalName(renamed, name);
                yield renamed;
            }
            case Function function when function.args() != null -> {
                List<Operand> args = function.args()
                                             .elements()
                                             .stream()
                                             .map(arg -> renameReferences(arg, map))
                                             .toList();
                yield new Function(function.name(), new OperandList(args));
            }
            default -> operand;
        };
    }

    /**
     * Works out the common correlated wildcard prefix of every node under the given one, bottom up, and records it in
     * the annotations so that a parent expression can later factor the references out.
     *
     * @since 9.3
     */
    public PrefixInfo analyzePrefixes(Operand node) {
        PrefixInfo info = switch (node) {
            case Reference reference -> {
                String prefix = getCorrelatedWildcardPrefix(reference.name());
                yield new PrefixInfo(prefix, prefix.isEmpty() ? 0 : 1);
            }
            case MultiExpression multiExpression -> commonPrefix(multiExpression.predicates);
            case Predicate expression -> commonPrefix(Arrays.asList(expression.lvalue, expression.rvalue));
            default -> null;
        };
        annotations.setPrefixInfo(node, info);
        return info;
    }

    /** The prefix shared by all the operands, or {@link PrefixInfo#EMPTY} if they do not all share one. */
    protected PrefixInfo commonPrefix(List<? extends Operand> operands) {
        PrefixInfo common = null;
        for (Operand operand : operands) {
            PrefixInfo info = operand == null ? null : analyzePrefixes(operand);
            if (info == null) {
                continue;
            }
            if (common == null) {
                common = info;
            } else if (common.prefix().equals(info.prefix())) {
                common = new PrefixInfo(common.prefix(), common.count() + info.count());
            } else {
                common = PrefixInfo.EMPTY;
            }
        }
        return common;
    }

    /**
     * Gets the prefix to use for this reference name (NXQL) if it contains a correlated wildcard.
     * <p>
     * The prefix is used to group together sets of expression that all use references with the same prefix.
     *
     * @param name the reference name (NXQL)
     * @return the prefix, or an empty string if there is no correlated wildcard
     * @since 9.3
     */
    public abstract String getCorrelatedWildcardPrefix(String name);

}
