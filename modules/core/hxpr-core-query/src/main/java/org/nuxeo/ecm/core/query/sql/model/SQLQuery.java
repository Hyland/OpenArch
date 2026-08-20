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

package org.nuxeo.ecm.core.query.sql.model;

/**
 * @author Bogdan Stefanescu
 * @author Florent Guillaume
 */
public final class SQLQuery implements ASTNode {

    private String queryString;

    private SelectClause select;

    public final FromClause from;

    public final WhereClause where;

    private OrderByClause orderBy;

    private long limit;

    private long offset;

    public SQLQuery() {
        this(new SelectClause(), new FromClause(), null, null);
    }

    public SQLQuery(SelectClause select, FromClause from) {
        this(select, from, null, null);
    }

    public SQLQuery(SelectClause select, FromClause from, WhereClause where) {
        this(select, from, where, null);
    }

    public SQLQuery(SelectClause select, FromClause from, WhereClause where, OrderByClause orderBy) {
        this(select, from, where, orderBy, 0, 0);
    }

    public SQLQuery(SelectClause select, FromClause from, WhereClause where, OrderByClause orderBy, long limit,
            long offset) {
        this.select = select;
        this.from = from;
        this.where = where;
        this.orderBy = orderBy;
        this.limit = limit;
        this.offset = offset;
    }

    /**
     * Copying constructor. Does not deep-copy the clauses though.
     */
    public SQLQuery(SQLQuery other) {
        select = other.select;
        from = other.from;
        where = other.where;
        orderBy = other.orderBy;
        limit = other.limit;
        offset = other.offset;
    }

    public SQLQuery withPredicate(Predicate predicate) {
        WhereClause where = predicate == null ? null : new WhereClause(predicate);
        return new SQLQuery(select, from, where, orderBy, limit, offset);
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

    public void setSelectClause(SelectClause select) {
        this.select = select;
    }

    public void setOrderByClause(OrderByClause orderBy) {
        this.orderBy = orderBy;
    }

    @Override
    // FIXME: not finished
    public String toString() {
        if (queryString != null) {
            return queryString;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(select).append(" FROM ").append(from);
        if (where != null) {
            sb.append(" WHERE ").append(where);
        }
        if (orderBy != null) {
            sb.append(" ORDER BY ").append(orderBy);
        }
        return sb.toString();
    }

    public void setLimit(long limit) {
        this.limit = limit;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getLimit() {
        return limit;
    }

    public long getOffset() {
        return offset;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SQLQuery other = (SQLQuery) obj;
        if (select == null) {
            if (other.select != null) {
                return false;
            }
        } else if (!select.equals(other.select)) {
            return false;
        }
        if (from == null) {
            if (other.from != null) {
                return false;
            }
        } else if (!from.equals(other.from)) {
            return false;
        }
        if (where == null) {
            if (other.where != null) {
                return false;
            }
        } else if (!where.equals(other.where)) {
            return false;
        }
        if (orderBy == null) {
            if (other.orderBy != null) {
                return false;
            }
        } else if (!orderBy.equals(other.orderBy)) {
            return false;
        }
        if (limit != other.limit) {
            return false;
        }
        return offset == other.offset;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (select == null ? 0 : select.hashCode());
        result = prime * result + (from == null ? 0 : from.hashCode());
        result = prime * result + (where == null ? 0 : where.hashCode());
        result = prime * result + (orderBy == null ? 0 : orderBy.hashCode());
        result = prime * result + (int) (limit ^ (limit >>> 32));
        result = prime * result + (int) (offset ^ (offset >>> 32));
        return result;
    }

}
