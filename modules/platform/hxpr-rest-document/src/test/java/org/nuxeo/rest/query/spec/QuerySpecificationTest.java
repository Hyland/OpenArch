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
package org.nuxeo.rest.query.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.TITLE_SORT;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.WhereClauseDTO;
import org.nuxeo.rest.query.io.WhereClauseDTO.RestPredicate;

public class QuerySpecificationTest {

    private static final String REPO_ID = "test";

    private static final String QUERY = "SELECT * FROM SysContent";

    private static final Long LIMIT = -1L;

    private static final Integer OFFSET = 0;

    private static final Long COUNT_UPTO = 1L;

    private static final Integer TRACK_TOTAL_COUNT_UPTO = 10;

    private static final Boolean TRACK_TOTAL_COUNT = true;

    private static final List<String> SORT = List.of(TITLE_SORT);

    private static final Map<String, Object> PARAMETERS = Map.of();

    private static final RestPredicate predicates = RestPredicate.of("sys_fulltext", "FULL_TEXT", "fulltext");

    private static final DocumentQueryDTO documentQueryDTO = new DocumentQueryDTO(REPO_ID, QUERY, LIMIT, OFFSET,
            null, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, SORT);

    @Test
    public void createQuerySpecification() {
        WhereClauseDTO whereClauseDTO = new WhereClauseDTO(QUERY, List.of(predicates));
        QuerySpecification querySpecification = new QuerySpecification(REPO_ID, QUERY, LIMIT,
                OFFSET, null, COUNT_UPTO, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, whereClauseDTO, PARAMETERS, SORT,
                getSearchDocument());

        assertEquals("test", querySpecification.repositoryId());
        assertEquals("SELECT * FROM SysContent", querySpecification.query());
        assertEquals(-1, querySpecification.limit());
        assertEquals(0, querySpecification.offset());
        assertEquals(1L, querySpecification.countUpTo());
        assertEquals(10, querySpecification.trackTotalCountUpTo());
        assertEquals(true, querySpecification.trackTotalCount());
        assertEquals(whereClauseDTO, querySpecification.whereClauseDefinition());
        assertEquals(Map.of(), querySpecification.parameters());
        assertEquals(List.of(TITLE_SORT), querySpecification.sort());
        assertEquals("foo", querySpecification.searchDocument().getId());
    }

    @Test
    public void createQuerySpecificationWithDocumentQueryDTO() {
        QuerySpecification querySpecification = new QuerySpecification(documentQueryDTO, QUERY, LIMIT, COUNT_UPTO,
                TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, getSearchDocument());

        assertEquals("test", querySpecification.repositoryId());
        assertEquals("SELECT * FROM SysContent", querySpecification.query());
        assertEquals(-1, querySpecification.limit());
        assertEquals(1L, querySpecification.countUpTo());
        assertEquals(10, querySpecification.trackTotalCountUpTo());
        assertEquals(true, querySpecification.trackTotalCount());
        assertEquals("foo", querySpecification.searchDocument().getId());
    }

    @Test
    public void createQuerySpecificationWithDocumentAdvancedQueryDTO() {
        DocumentAdvancedQueryDTO documentAdvancedQueryDTO = new DocumentAdvancedQueryDTO(documentQueryDTO);
        QuerySpecification querySpecification = new QuerySpecification(documentAdvancedQueryDTO, QUERY, LIMIT,
                COUNT_UPTO, TRACK_TOTAL_COUNT, TRACK_TOTAL_COUNT_UPTO, getSearchDocument());

        assertEquals("test", querySpecification.repositoryId());
        assertEquals("SELECT * FROM SysContent", querySpecification.query());
        assertEquals(-1, querySpecification.limit());
        assertEquals(1L, querySpecification.countUpTo());
        assertEquals(10, querySpecification.trackTotalCountUpTo());
        assertEquals(true, querySpecification.trackTotalCount());
        assertEquals("foo", querySpecification.searchDocument().getId());
    }

    private DocumentModel getSearchDocument() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("foo");
        return doc;
    }

}
