/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.opensearch.api;

import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.IterableQueryResult;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.opensearch.query.OpenSearchQueryBuilder;
import org.opensearch.client.opensearch.core.SearchResponse;

/**
 * Wrapper for the results of a scrollable search request.
 *
 * @see OpenSearchService#scroll(OpenSearchQueryBuilder, long)
 * @see OpenSearchService#scroll(OpenSearchScrollResult)
 * @since 8.3
 */
public class OpenSearchScrollResult extends OpenSearchResult {

    /**
     * {@link OpenSearchQueryBuilder} used for the initial search request.
     */
    protected final OpenSearchQueryBuilder queryBuilder;

    /**
     * Scroll id returned by the search request.
     */
    protected final String scrollId;

    /**
     * Timeout for keeping the search context alive.
     */
    protected final long keepAlive;

    public OpenSearchScrollResult(DocumentModelList documents, SearchResponse<DocumentDTO> response, OpenSearchQueryBuilder queryBuilder,
            String scrollId, long keepAlive) {
        super(documents, response);
        this.queryBuilder = queryBuilder;
        this.scrollId = scrollId;
        this.keepAlive = keepAlive;
    }

    public OpenSearchScrollResult(IterableQueryResult rows, SearchResponse<DocumentDTO> response, OpenSearchQueryBuilder queryBuilder,
            String scrollId, long keepAlive) {
        super(rows, response);
        this.queryBuilder = queryBuilder;
        this.scrollId = scrollId;
        this.keepAlive = keepAlive;
    }

    public OpenSearchScrollResult(SearchResponse<DocumentDTO> response, OpenSearchQueryBuilder queryBuilder, String scrollId, long keepAlive) {
        super(response);
        this.queryBuilder = queryBuilder;
        this.scrollId = scrollId;
        this.keepAlive = keepAlive;
    }

    public OpenSearchScrollResult(OpenSearchQueryBuilder queryBuilder, String scrollId, long keepAlive) {
        this(null, queryBuilder, scrollId, keepAlive);
    }

    public OpenSearchQueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    public String getScrollId() {
        return scrollId;
    }

    public long getKeepAlive() {
        return keepAlive;
    }

}
