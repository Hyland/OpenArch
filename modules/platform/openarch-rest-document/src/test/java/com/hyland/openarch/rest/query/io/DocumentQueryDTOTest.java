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
package com.hyland.openarch.rest.query.io;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.query.QuerySyntax;

public class DocumentQueryDTOTest extends AbstractQueryIOTest {

    @Test
    public void createDocumentQueryDTO() {
        DocumentQueryDTO queryDTO = new DocumentQueryDTO(REPO_ID, DEFAULT_SELECT_STATEMENT, LIMIT, OFFSET, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, SORT);
        assertEquals("test", queryDTO.repositoryId());
        assertEquals("SELECT * FROM SysContent", queryDTO.query());
        assertEquals(-1, queryDTO.limit());
        assertEquals(0, queryDTO.offset());
        assertEquals(true, queryDTO.trackTotalCount());
        assertEquals(0, queryDTO.trackTotalCountUpTo());
        assertEquals(1, queryDTO.sort().size());
        assertEquals(SYS_TITLE, queryDTO.sort().get(0));
    }

    /** The default syntax is HXQL, whose default query is a statement selecting everything. */
    @Test
    public void createDocumentQueryDTOSyntax() {
        DocumentQueryDTO queryDTO = new DocumentQueryDTO(REPO_ID, null, LIMIT, OFFSET, SORT);
        assertEquals(QuerySyntax.HXQL, queryDTO.syntax());
        assertEquals(DEFAULT_SELECT_STATEMENT, queryDTO.query());

        String expr = "content.sys_title == \"a\"";
        queryDTO = new DocumentQueryDTO(REPO_ID, expr, QuerySyntax.EXPR, LIMIT, OFFSET, null, TRACK_TOTAL_COUNT,
                TRACK_TOTAL_COUNT_UPTO, SORT);
        assertEquals(QuerySyntax.EXPR, queryDTO.syntax());
        assertEquals(expr, queryDTO.query());

        // an expression is a filter, so no expression means no filter rather than a default statement
        queryDTO = new DocumentQueryDTO(REPO_ID, "  ", QuerySyntax.EXPR, LIMIT, OFFSET, null, TRACK_TOTAL_COUNT,
                TRACK_TOTAL_COUNT_UPTO, SORT);
        assertNull(queryDTO.query());
    }

    @Test
    public void createDocumentQueryDTOWithoutTrackCountParams() {
        DocumentQueryDTO queryDTO = new DocumentQueryDTO(REPO_ID, DEFAULT_SELECT_STATEMENT, LIMIT, OFFSET, SORT);
        assertEquals("test", queryDTO.repositoryId());
        assertEquals("SELECT * FROM SysContent", queryDTO.query());
        assertEquals(-1, queryDTO.limit());
        assertEquals(0, queryDTO.offset());
        assertEquals(true, queryDTO.trackTotalCount());
        assertNull(queryDTO.trackTotalCountUpTo());
        assertEquals(1, queryDTO.sort().size());
        assertEquals(SYS_TITLE, queryDTO.sort().get(0));
    }

    @Test
    public void createDocumentQueryDTOWithRepositoryIdParam() {
        DocumentQueryDTO queryDTO = new DocumentQueryDTO(REPO_ID);
        assertEquals("test", queryDTO.repositoryId());
        assertEquals("SELECT * FROM SysContent", queryDTO.query());
        assertEquals(10, queryDTO.limit());
        assertEquals(0, queryDTO.offset());
        assertEquals(true, queryDTO.trackTotalCount());
        assertNull(queryDTO.trackTotalCountUpTo());
        assertEquals(0, queryDTO.sort().size());
        assertFalse(queryDTO.sort().contains(SYS_TITLE));
    }

    @Test
    public void createDocumentQueryDTOWithNullLimit() {
        DocumentQueryDTO queryDTO =
                new DocumentQueryDTO(REPO_ID, DEFAULT_SELECT_STATEMENT, null, 0, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, SORT);

        assertEquals("test", queryDTO.repositoryId());
        assertEquals("SELECT * FROM SysContent", queryDTO.query());

        // This is what we are testing
        assertEquals(10, queryDTO.limit());
        assertEquals(0, queryDTO.offset());
        assertEquals(true, queryDTO.trackTotalCount());
        assertEquals(0, queryDTO.trackTotalCountUpTo());
        assertEquals(1, queryDTO.sort().size());
    }

    @Test
    public void createDocumentQueryDTOWithZeroLimit() {
        DocumentQueryDTO queryDTO =
                new DocumentQueryDTO(REPO_ID, DEFAULT_SELECT_STATEMENT, 0L, 0, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, SORT);

        assertEquals("test", queryDTO.repositoryId());
        assertEquals("SELECT * FROM SysContent", queryDTO.query());

        // This is what we are testing
        assertEquals(0, queryDTO.limit());
        assertEquals(0, queryDTO.offset());
        assertEquals(true, queryDTO.trackTotalCount());
        assertEquals(0, queryDTO.trackTotalCountUpTo());
        assertEquals(1, queryDTO.sort().size());
    }

    @Test
    public void createDocumentQueryDTOWithNLimit() {
        DocumentQueryDTO queryDTO =
                new DocumentQueryDTO(REPO_ID, DEFAULT_SELECT_STATEMENT, 5L, 0, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, SORT);

        assertEquals("test", queryDTO.repositoryId());
        assertEquals("SELECT * FROM SysContent", queryDTO.query());

        // This is what we are testing
        assertEquals(5, queryDTO.limit());
        assertEquals(0, queryDTO.offset());
        assertEquals(true, queryDTO.trackTotalCount());
        assertEquals(0, queryDTO.trackTotalCountUpTo());
        assertEquals(1, queryDTO.sort().size());
    }
}
