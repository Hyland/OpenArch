/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.opensearch.api;

import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.opensearch.query.NxQueryBuilder;
import org.opensearch.client.opensearch.core.SearchResponse;

/**
 * Wrapper for the results of a scrollable search request.
 *
 * @see OpenSearchService#scroll(NxQueryBuilder, long)
 * @see OpenSearchService#scroll(OpenSearchScrollResult)
 * @since 8.3
 */
public class OpenSearchScrollResult extends OpenSearchResult {

    /**
     * {@link NxQueryBuilder} used for the initial search request.
     */
    protected final NxQueryBuilder queryBuilder;

    /**
     * Scroll id returned by the search request.
     */
    protected final String scrollId;

    /**
     * Timeout for keeping the search context alive.
     */
    protected final long keepAlive;

    public OpenSearchScrollResult(DocumentModelList documents, SearchResponse<DocumentDTO> response, NxQueryBuilder queryBuilder,
            String scrollId, long keepAlive) {
        super(documents, response);
        this.queryBuilder = queryBuilder;
        this.scrollId = scrollId;
        this.keepAlive = keepAlive;
    }

    public OpenSearchScrollResult(IterableQueryResult rows, SearchResponse<DocumentDTO> response, NxQueryBuilder queryBuilder,
            String scrollId, long keepAlive) {
        super(rows, response);
        this.queryBuilder = queryBuilder;
        this.scrollId = scrollId;
        this.keepAlive = keepAlive;
    }

    public OpenSearchScrollResult(SearchResponse<DocumentDTO> response, NxQueryBuilder queryBuilder, String scrollId, long keepAlive) {
        super(response);
        this.queryBuilder = queryBuilder;
        this.scrollId = scrollId;
        this.keepAlive = keepAlive;
    }

    public OpenSearchScrollResult(NxQueryBuilder queryBuilder, String scrollId, long keepAlive) {
        this(null, queryBuilder, scrollId, keepAlive);
    }

    public NxQueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    public String getScrollId() {
        return scrollId;
    }

    public long getKeepAlive() {
        return keepAlive;
    }

}
