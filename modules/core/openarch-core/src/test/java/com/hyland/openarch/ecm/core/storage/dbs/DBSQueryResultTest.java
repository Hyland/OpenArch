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
package com.hyland.openarch.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import com.hyland.openarch.ecm.core.api.PartialList;
import com.hyland.openarch.ecm.core.storage.dbs.DBSSession.DBSQueryResult;

public class DBSQueryResultTest {

    @Test
    public void testReportedTotalLargerThanMaterializedResults() {
        DBSQueryResult result = new DBSQueryResult(new PartialList<>(List.of(), 1));

        assertEquals(1, result.size());
        assertFalse(result.hasNext());
        assertThrows(NoSuchElementException.class, result::next);
    }

    @Test
    public void testSkipToUsesMaterializedSize() {
        Map<String, Object> row = Map.of("key", "value");
        DBSQueryResult result = new DBSQueryResult(new PartialList<>(List.of(row), 2));

        result.skipTo(2);

        assertEquals(1, result.pos());
        assertFalse(result.hasNext());
    }

    @Test
    public void testClosedResultHasNoNextElement() {
        DBSQueryResult result = new DBSQueryResult(new PartialList<>(List.of(Map.of()), 1));

        result.close();

        assertFalse(result.hasNext());
        assertThrows(NoSuchElementException.class, result::next);
    }
}
