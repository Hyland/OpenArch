/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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

import org.junit.jupiter.api.Test;

public class TestSQLQuery {

    protected final SQLQuery sqlQuery = new SQLQuery();

    protected final SelectClause selectClause = new SelectClause();

    protected final OrderByList orderByList = new OrderByList();

    protected final OrderByClause orderByClause = new OrderByClause(orderByList);

    @Test
    public void testSetSelect() {
        sqlQuery.setSelectClause(selectClause);
        assertEquals(selectClause, sqlQuery.getSelectClause());
    }

    @Test
    public void testSetOrderBy() {
        sqlQuery.setOrderByClause(orderByClause);
        assertEquals(orderByClause, sqlQuery.getOrderByClause());
    }
}
