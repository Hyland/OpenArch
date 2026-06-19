/*
 * (C) Copyright 2023 Hyland (http://hyland.com/) and others.
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
 *
 */
package org.nuxeo.rest.query.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.rest.document.DocumentDTO;

public class DocumentQueryResultDTOTest {

    private static final String REPO_ID = "default";

    private static final String QUERY = "SELECT * FROM SysContent";

    private static final long LIMIT = 10;

    private static final long OFFSET = 1;

    private static final Boolean TRACK_TOTAL_COUNT = Boolean.TRUE;

    private static final Boolean TOTAL_COUNT_IS_TRUNCATED = Boolean.FALSE;

    private static final List<DocumentDTO> documents = List.of(
            new DocumentDTO(),
            new DocumentDTO());

    private static final Long totalCount = 15l;

    @Test
    public void testDocumentQueryResultDTODocument() {
        DocumentQueryResultDTO actual = new DocumentQueryResultDTO(REPO_ID, QUERY, LIMIT, OFFSET,
                TRACK_TOTAL_COUNT, TOTAL_COUNT_IS_TRUNCATED, documents, totalCount);

        assertEquals(REPO_ID, actual.repositoryId());
        assertEquals(QUERY, actual.query());
        assertEquals(LIMIT, actual.limit());
        assertEquals(OFFSET, actual.offset());
        assertEquals(documents, actual.documents());
        assertEquals(documents.size(), actual.count());
        assertEquals(totalCount, actual.totalCount());
        assertNull(actual.trackTotalCount());
        assertNull(actual.totalCountIsTruncated());
    }

    @Test
    public void testDocumentQueryResultDTODocumentNullDocuments() {
        DocumentQueryResultDTO actual = new DocumentQueryResultDTO(REPO_ID, QUERY, LIMIT, OFFSET,
                TRACK_TOTAL_COUNT, TOTAL_COUNT_IS_TRUNCATED, null, totalCount);

        assertEquals(REPO_ID, actual.repositoryId());
        assertEquals(QUERY, actual.query());
        assertEquals(LIMIT, actual.limit());
        assertEquals(OFFSET, actual.offset());
        assertEquals(List.of(), actual.documents());
        assertEquals(0, actual.count());
        assertEquals(totalCount, actual.totalCount());
        assertNull(actual.trackTotalCount());
        assertNull(actual.totalCountIsTruncated());
    }

    @Test
    public void testDocumentQueryResultDTODocumentNullTrackTotalCount() {
        DocumentQueryResultDTO actual = new DocumentQueryResultDTO(REPO_ID, QUERY, LIMIT, OFFSET, null,
                TOTAL_COUNT_IS_TRUNCATED, documents, totalCount);

        assertEquals(REPO_ID, actual.repositoryId());
        assertEquals(QUERY, actual.query());
        assertEquals(LIMIT, actual.limit());
        assertEquals(OFFSET, actual.offset());
        assertEquals(documents, actual.documents());
        assertEquals(documents.size(), actual.count());
        assertEquals(totalCount, actual.totalCount());
        assertNull(actual.trackTotalCount());
        assertNull(actual.totalCountIsTruncated());
    }

    @Test
    public void testDocumentQueryResultDTODocumentFalseTrackTotalCount() {
        DocumentQueryResultDTO actual = new DocumentQueryResultDTO(REPO_ID, QUERY, LIMIT, OFFSET, Boolean.FALSE,
                TOTAL_COUNT_IS_TRUNCATED, documents, totalCount);

        assertEquals(REPO_ID, actual.repositoryId());
        assertEquals(QUERY, actual.query());
        assertEquals(LIMIT, actual.limit());
        assertEquals(OFFSET, actual.offset());
        assertEquals(documents, actual.documents());
        assertEquals(documents.size(), actual.count());
        assertNull(actual.totalCount());
        assertEquals(Boolean.FALSE, actual.trackTotalCount());
        assertNull(actual.totalCountIsTruncated());
    }

    @Test
    public void testDocumentQueryResultDTODocumentNullTotalCountIsTruncated() {
        DocumentQueryResultDTO actual = new DocumentQueryResultDTO(REPO_ID, QUERY, LIMIT, OFFSET,
                TRACK_TOTAL_COUNT, null, documents, totalCount);

        assertEquals(REPO_ID, actual.repositoryId());
        assertEquals(QUERY, actual.query());
        assertEquals(LIMIT, actual.limit());
        assertEquals(OFFSET, actual.offset());
        assertEquals(documents, actual.documents());
        assertEquals(documents.size(), actual.count());
        assertEquals(totalCount, actual.totalCount());
        assertNull(actual.trackTotalCount());
        assertNull(actual.totalCountIsTruncated());
    }

    @Test
    public void testDocumentQueryResultDTODocumentTrueTotalCountIsTruncated() {
        DocumentQueryResultDTO actual = new DocumentQueryResultDTO(REPO_ID, QUERY, LIMIT, OFFSET,
                TRACK_TOTAL_COUNT, Boolean.TRUE, documents, totalCount);

        assertEquals(REPO_ID, actual.repositoryId());
        assertEquals(QUERY, actual.query());
        assertEquals(LIMIT, actual.limit());
        assertEquals(OFFSET, actual.offset());
        assertEquals(documents, actual.documents());
        assertEquals(documents.size(), actual.count());
        assertEquals(totalCount, actual.totalCount());
        assertNull(actual.trackTotalCount());
        assertEquals(Boolean.TRUE, actual.totalCountIsTruncated());
    }
}
