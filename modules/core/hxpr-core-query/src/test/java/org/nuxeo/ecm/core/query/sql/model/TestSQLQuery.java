/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

public class TestSQLQuery {

    protected final SQLQuery sqlQuery = new SQLQuery();

    protected final SelectClause selectClause = new SelectClause(new SelectList(List.of(new Reference("p"))));

    protected final OrderByClause orderByClause = new OrderByClause(
            new OrderByList(List.of(new OrderByExpr(new Reference("p"), false))));

    @Test
    public void testWithSelect() {
        SQLQuery query = sqlQuery.withSelectClause(selectClause);
        assertEquals(selectClause, query.getSelectClause());
        // the original is untouched
        assertNotSame(sqlQuery, query);
        assertEquals(new SelectClause(), sqlQuery.getSelectClause());
    }

    @Test
    public void testWithOrderBy() {
        SQLQuery query = sqlQuery.withOrderByClause(orderByClause);
        assertEquals(orderByClause, query.getOrderByClause());
        assertNotSame(sqlQuery, query);
        assertNull(sqlQuery.getOrderByClause());
    }

    @Test
    public void testWithPredicate() {
        Predicate predicate = Predicates.eq("p", "v");
        SQLQuery query = sqlQuery.withPredicate(predicate);
        assertEquals(predicate, query.getWhereClause().predicate());
        assertNull(sqlQuery.getWhereClause());
        // a null predicate drops the where clause
        assertNull(query.withPredicate(null).getWhereClause());
    }

    /** The other clauses survive a with call. */
    @Test
    public void testWithKeepsTheRest() {
        SQLQuery query = sqlQuery.withSelectClause(selectClause).withOrderByClause(orderByClause);
        assertEquals(selectClause, query.getSelectClause());
        assertEquals(orderByClause, query.getOrderByClause());
    }

}
