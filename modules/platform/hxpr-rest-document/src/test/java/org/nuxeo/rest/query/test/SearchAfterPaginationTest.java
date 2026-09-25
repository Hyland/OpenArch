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
package org.nuxeo.rest.query.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.opensearch.OpenSearchConstants;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.query.OpenSearchQueryManager;
import org.nuxeo.rest.query.QueryAPI;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.io.SearchAfterToken;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for search_after pagination functionality.
 */
@Features(RestQueryMultipleDocumentsFeature.class)
@Tag("useOpenSearch")
@Import({ QueryAPITestConfiguration.class })
@SpringBootTest(classes = { QueryAPI.class })
public class SearchAfterPaginationTest extends AbstractQueryAPITest {

    protected static final String SORT_FIELD = SYS_TITLE;

    @Autowired
    OpenSearchQueryManager osqm;

    @Test
    public void testSearchAfterBasicPagination() {
        // Given - first page
        DocumentAdvancedQueryDTO firstPageQuery = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 0L, null, true, -1, List.of(SORT_FIELD), null, null, null
        );

        // When - fetch first page
        DocumentQueryResultDTO firstPage = osqm.getResults(firstPageQuery);

        // Then - should have nextSearchAfter token
        assertNotNull(firstPage.nextSearchAfter(), "First page should have nextSearchAfter token");
        assertEquals(3, firstPage.count(), "First page should have 3 documents");
        assertEquals("File0", firstPage.documents().getFirst().getTitle());

        // When - fetch second page using searchAfter
        DocumentAdvancedQueryDTO secondPageQuery = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 0L, firstPage.nextSearchAfter(), true, -1, List.of(SORT_FIELD), null, null, null
        );
        DocumentQueryResultDTO secondPage = osqm.getResults(secondPageQuery);

        // Then - should have different results
        assertNotNull(secondPage.nextSearchAfter(), "Second page should have nextSearchAfter token");
        assertEquals(3, secondPage.count(), "Second page should have 3 documents");
        assertEquals("File3", secondPage.documents().getFirst().getTitle(),
            "Second page should start with File3");

        // Verify no overlap
        String firstPageLastId = firstPage.documents().get(2).getId();
        String secondPageFirstId = secondPage.documents().getFirst().getId();
        assertNotEquals(firstPageLastId, secondPageFirstId, "Pages should not overlap");

        // check that first and second page have different document ids
        List<String> firstPageIds = firstPage.documents().stream()
            .map(DocumentDTO::getId)
            .toList();
        List<String> secondPageIds = secondPage.documents().stream()
            .map(DocumentDTO::getId)
            .toList();
        assertTrue(firstPageIds.stream().noneMatch(secondPageIds::contains),
            "Pages should not have overlapping document IDs");
    }

    @Test
    public void testSearchAfterWithOffsetIgnored() {
        // Given - query with both offset and searchAfter
        DocumentAdvancedQueryDTO firstQuery = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 0L, null, true, -1, List.of(SORT_FIELD), null, null, null
        );
        DocumentQueryResultDTO firstResult = osqm.getResults(firstQuery);
        String searchAfterToken = firstResult.nextSearchAfter();

        // When - use searchAfter with offset (offset should be ignored)
        DocumentAdvancedQueryDTO queryWithBoth = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 5L, searchAfterToken, true, -1, List.of(SORT_FIELD), null, null, null
        );
        DocumentQueryResultDTO result = osqm.getResults(queryWithBoth);

        // Then - should use search_after (not offset)
        // Result should be the same as using searchAfter alone
        DocumentAdvancedQueryDTO querySearchAfterOnly = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 0L, searchAfterToken, true, -1, List.of(SORT_FIELD), null, null, null
        );
        DocumentQueryResultDTO expectedResult = osqm.getResults(querySearchAfterOnly);

        assertEquals(expectedResult.documents().getFirst().getId(), result.documents().getFirst().getId(),
            "When both offset and searchAfter are provided, searchAfter should take precedence");
    }

    @Test
    public void testSearchAfterInvalidToken() {
        // Given - invalid base64 token
        String invalidToken = "This is not a valid base64 token!!!";

        // When - query with invalid token
        DocumentAdvancedQueryDTO query = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 5L, 0L, invalidToken, true, -1, List.of(SORT_FIELD), null, null, null
        );
        DocumentQueryResultDTO result = osqm.getResults(query);

        // Then - should fall back to offset pagination (offset=0)
        assertNotNull(result, "Result should not be null");
        assertEquals("File0", result.documents().getFirst().getTitle(),
            "Should fall back to first page when token is invalid");
    }

    @Test
    public void testSearchAfterMalformedJson() {
        // Given - valid base64 but invalid JSON content
        String malformedToken = Base64.getEncoder()
                                      .encodeToString("not a json object".getBytes());

        // When - query with malformed token
        DocumentAdvancedQueryDTO query = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 5L, 0L, malformedToken, true, -1, List.of(SORT_FIELD), null, null, null
        );
        DocumentQueryResultDTO result = osqm.getResults(query);

        // Then - should fall back to offset pagination
        assertNotNull(result, "Result should not be null");
        assertEquals("File0", result.documents().getFirst().getTitle(),
            "Should fall back to first page when token JSON is invalid");
    }

    @Test
    public void testSearchAfterLastPage() {
        // Given - navigate to the last page
        DocumentAdvancedQueryDTO query = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 0L, null, true, -1, List.of(SORT_FIELD), null, null, null
        );
        DocumentQueryResultDTO page = osqm.getResults(query);

        // When - there are exactly 10 documents total, last page should have no nextSearchAfter
        // (or if more pages exist, navigate until last page)
        int maxIterations = 10; // safety limit
        int fetchCount = 1; // already fetched the first page

        while (page.nextSearchAfter() != null && fetchCount < maxIterations) {
            DocumentAdvancedQueryDTO nextQuery = new DocumentAdvancedQueryDTO(
                REPO_ID, null, 3L, 0L, page.nextSearchAfter(), true, -1, List.of(SORT_FIELD), null, null, null
            );
            page = osqm.getResults(nextQuery);
            fetchCount++;
        }

        // lastPage should be the last page of results, which should have no nextSearchAfter token
        assertEquals(4, fetchCount, "Should have iterated through 4 pages to reach last page");
        assertEquals(1, page.count(), "Last page should have 1 document");
        assertNull(page.nextSearchAfter(),
            "Last page should not have nextSearchAfter token");
    }

    @Test
    public void testSearchAfterEmptyResults() {
        // Given - query that returns no results
        DocumentAdvancedQueryDTO query = new DocumentAdvancedQueryDTO(
            REPO_ID,
            "SELECT * FROM SysContent WHERE sys_title = 'NonExistentFile'",
            10L, 0L, null, true, -1, List.of(SORT_FIELD), null, null, null
        );

        // When
        DocumentQueryResultDTO result = osqm.getResults(query);

        // Then - should not have nextSearchAfter
        assertEquals(0, result.count(), "Should have no results");
        assertNull(result.nextSearchAfter(),
            "Empty result should not have nextSearchAfter token");
    }

    @Test
    public void testSearchAfterPaginationConsistency() {
        // Given - paginate through all results
        DocumentAdvancedQueryDTO firstQuery = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 2L, 0L, null, true, -1, List.of(SORT_FIELD), null, null, null
        );

        // When - collect all results through pagination
        DocumentQueryResultDTO page = osqm.getResults(firstQuery);
        long totalRetrieved = page.count();
        String previousLastTitle = page.documents().get((int)page.count() - 1).getTitle();

        int maxPages = 20; // safety limit
        int pageCount = 1;

        while (page.nextSearchAfter() != null && pageCount < maxPages) {
            DocumentAdvancedQueryDTO nextQuery = new DocumentAdvancedQueryDTO(
                REPO_ID, null, 2L, 0L, page.nextSearchAfter(), true, -1, List.of(SORT_FIELD), null, null, null
            );
            page = osqm.getResults(nextQuery);

            if (page.count() > 0) {
                String currentFirstTitle = page.documents().getFirst().getTitle();

                // Verify ordering: current page's first title should be > previous page's last title
                assertTrue(currentFirstTitle.compareTo(previousLastTitle) > 0,
                    String.format("Pages should be ordered correctly: %s should be after %s",
                        currentFirstTitle, previousLastTitle));

                previousLastTitle = page.documents().get((int)page.count() - 1).getTitle();
                totalRetrieved += page.count();
            }
            pageCount++;
        }

        // Then - should have retrieved all documents
        assertEquals(10L, totalRetrieved,
            "Should retrieve all 10 documents through pagination");
    }

    @Test
    public void testSearchAfterTokenRoundTrip() {
        // Given - a valid search_after scenario
        DocumentAdvancedQueryDTO query = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 0L, null, true, -1, List.of(SORT_FIELD), null, null, null
        );
        DocumentQueryResultDTO result = osqm.getResults(query);

        // When - decode the token
        String token = result.nextSearchAfter();
        assertNotNull(token, "Result should have nextSearchAfter token");

        SearchAfterToken decoded = SearchAfterToken.decode(token);

        // Then - token should be valid
        assertNotNull(decoded.sortValues(), "Token should have sortValues");
        assertNotNull(decoded.sortFields(), "Token should have sortFields");
        assertEquals(2, decoded.sortValues().size(),
            "Token should have 2 sort values (sys_title + sys_id tiebreaker)");
        assertEquals(2, decoded.sortFields().size(),
            "Token should have 2 sort fields (sys_title + sys_id)");

        // Verify tiebreaker field is present
        assertTrue(decoded.sortFields().contains(OpenSearchConstants.ID_FIELD),
            "Token should include _id tiebreaker");
    }

    @Test
    public void testSearchAfterWithCustomSort() {
        // Given - query with custom sort field
        DocumentAdvancedQueryDTO query = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 0L, null, true, -1,
            List.of("sys_created DESC", "sys_title ASC"), null, null, null
        );

        // When
        DocumentQueryResultDTO result = osqm.getResults(query);

        // Then - should have nextSearchAfter with all sort fields plus tiebreaker
        assertNotNull(result.nextSearchAfter(), "Result should have nextSearchAfter");

        SearchAfterToken token = SearchAfterToken.decode(result.nextSearchAfter());
        assertEquals(3, token.sortFields().size(),
            "Token should have 3 sort fields (sys_created, sys_title, _id)");
        assertTrue(token.sortFields().contains(OpenSearchConstants.ID_FIELD),
            "Token should include _id tiebreaker");
    }

    @Test
    public void testSearchAfterNullOrBlankToken() {
        // Given - queries with null or blank searchAfter
        DocumentAdvancedQueryDTO nullTokenQuery = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 0L, null, true, -1, List.of(SORT_FIELD), null, null, null
        );

        DocumentAdvancedQueryDTO blankTokenQuery = new DocumentAdvancedQueryDTO(
            REPO_ID, null, 3L, 0L, "", true, -1, List.of(SORT_FIELD), null, null, null
        );

        // When
        DocumentQueryResultDTO nullResult = osqm.getResults(nullTokenQuery);
        DocumentQueryResultDTO blankResult = osqm.getResults(blankTokenQuery);

        // Then - both should return first page
        assertEquals(nullResult.documents().getFirst().getId(),
                     blankResult.documents().getFirst().getId(),
            "Null and blank searchAfter should behave the same");
        assertEquals("File0", nullResult.documents().getFirst().getTitle());
        assertEquals("File0", blankResult.documents().getFirst().getTitle());
    }
}
