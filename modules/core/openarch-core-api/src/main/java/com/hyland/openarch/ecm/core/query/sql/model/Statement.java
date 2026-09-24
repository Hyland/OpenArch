/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.query.sql.model;

import java.util.Objects;

import com.hyland.openarch.ecm.core.query.sql.HXQLSerializer;

/**
 * A whole NXQL query.
 * <p>
 * Immutable: the {@code with} methods return a copy with one clause replaced.
 *
 * @author Bogdan Stefanescu
 * @author Florent Guillaume
 */
public final class Statement implements ASTNode {

    private final SelectClause select;

    private final FromClause from;

    private final WhereClause where;

    private final OrderByClause orderBy;

    public Statement() {
        this(new SelectClause(), new FromClause(), null, null);
    }

    public Statement(SelectClause select, FromClause from) {
        this(select, from, null, null);
    }

    public Statement(SelectClause select, FromClause from, WhereClause where) {
        this(select, from, where, null);
    }

    public Statement(SelectClause select, FromClause from, WhereClause where, OrderByClause orderBy) {
        this.select = select;
        this.from = from;
        this.where = where;
        this.orderBy = orderBy;
    }

    public SelectClause getSelectClause() {
        return select;
    }

    public FromClause getFromClause() {
        return from;
    }

    public WhereClause getWhereClause() {
        return where;
    }

    public OrderByClause getOrderByClause() {
        return orderBy;
    }

    /** A copy of this query with the given projection. */
    public Statement withSelectClause(SelectClause select) {
        return new Statement(select, from, where, orderBy);
    }

    /** A copy of this query with the given predicate, or with no where clause if it is {@code null}. */
    public Statement withPredicate(Predicate predicate) {
        return new Statement(select, from, predicate == null ? null : new WhereClause(predicate), orderBy);
    }

    /** A copy of this query with the given sort. */
    public Statement withOrderByClause(OrderByClause orderBy) {
        return new Statement(select, from, where, orderBy);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Statement other)) {
            return false;
        }
        return Objects.equals(select, other.select) //
                && Objects.equals(from, other.from) //
                && Objects.equals(where, other.where) //
                && Objects.equals(orderBy, other.orderBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(select, from, where, orderBy);
    }

    @Override
    public String toString() {
        return HXQLSerializer.serialize(this);
    }

}
