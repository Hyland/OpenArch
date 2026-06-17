/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Benoit Delbosc
 */
package org.nuxeo.opensearch.api;

import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.opensearch.query.FieldValueConverter;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

import jakarta.annotation.Nullable;

/**
 * @since 6.0
 */
public class OpenSearchResult {
    private final DocumentModelList documents;

    private final IterableQueryResult rows;

    private final boolean totalCountTruncated;

    private final boolean inconsistentResult;

    private final SearchResponse<DocumentDTO> response;

    public OpenSearchResult(DocumentModelList documents, SearchResponse<DocumentDTO> response) {
        this(documents, null, response);
    }

    public OpenSearchResult(IterableQueryResult rows, SearchResponse<DocumentDTO> response) {
        this(null, rows, response);
    }

    public OpenSearchResult(SearchResponse<DocumentDTO> response) {
        this(null, null, response);
    }

    private OpenSearchResult(DocumentModelList documents, IterableQueryResult rows, SearchResponse<DocumentDTO> response) {
        this.documents = documents;
        this.rows = rows;
        this.response = response;
        this.totalCountTruncated = totalCountTruncated(response);
        this.inconsistentResult = checkForInconsistentResult(documents, response);
    }

    /**
     * @param documents the list of documents (returned by the {@link org.nuxeo.opensearch.fetcher.Fetcher})
     * @param response the original OpenSearch response
     * @return true if the number of documents returned by the Fetcher is different from the hits reported by OpenSearch, false otherwise
     */
    private boolean checkForInconsistentResult(DocumentModelList documents, SearchResponse<DocumentDTO> response) {
        if (response == null || response.hits() == null || documents == null) {
            return false;
        }
        List<Hit<DocumentDTO>> hits = response.hits().hits();
        return documents.size() != hits.size();
    }

    /**
     * Get the list of Nuxeo documents, this is populated when using a SELECT * clause, or when submitting esQuery.
     *
     * @return null if the query returns fields or if the onlyOpenSearchResponse option is set.
     */
    public DocumentModelList getDocuments() {
        return documents;
    }

    /**
     * Iterator to use when selecting fields: SELECT sys_id ...
     *
     * @since 7.2
     * @return null if the query returns documents or if the onlyOpenSearchResponse option is set.
     */
    public IterableQueryResult getRows() {
        return rows;
    }

    /**
     * Returns the original OpenSearch response. Use it at your own risk
     *
     * @since 7.3
     */
    public SearchResponse<DocumentDTO> getOpenSearchResponse() {
        return response;
    }

    /**
     * @return {@code true} If the totalCount is not accurate; {@code false} if the totalCount is accurate or not
     *         calculated
     */
    public boolean isTotalCountTruncated() {
        return totalCountTruncated;
    }

    protected boolean totalCountTruncated(SearchResponse<DocumentDTO> response) {
        if (response == null) {
            return false;
        }
        TotalHits totalHits = response.hits().total();
        return totalHits != null && !totalHits.relation().equals(TotalHitsRelation.Eq);
    }

    /**
     * Extracts the sort values from the last hit in the response for search_after pagination.
     *
     * @return list of sort values from the last hit, or null if not available
     */
    @Nullable
    public List<Object> getLastSortValues() {
        if (response == null || response.hits() == null || response.hits().hits().isEmpty()) {
            return null;
        }
        Hit<DocumentDTO> lastHit = response.hits().hits().getLast();
        if (lastHit.sort().isEmpty()) {
            return null;
        }
        return lastHit.sort().stream().map(FieldValueConverter::toObject).toList();
    }

    /**
     * @return true if the number of documents returned by the Fetcher is different from the hits reported by OpenSearch, false otherwise
     */
    public boolean isInconsistentResult() {
        return inconsistentResult;
    }
}
