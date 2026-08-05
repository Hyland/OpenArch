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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.opensearch.core.OpenSearchHitConverter;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.opensearch.query.NxQueryBuilder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TotalHits;

/**
 * Iterable query result of results of an OpenSearch scroll query and next ones.
 * <p>
 * Queries OpenSearch when there's no more result in current response and there's more in cluster.
 * <p>
 * For better performance use {@link NxQueryBuilder#onlyOpenSearchResponse()} for the first scroll requests.
 *
 * @since 8.4
 */
public class OpenSearchIterableQueryResultImpl implements IterableQueryResult, Iterator<Map<String, Object>> {

    private final OpenSearchService searchService;

    private final OpenSearchHitConverter converter;

    private final long size;

    private OpenSearchScrollResult scrollResult;

    private boolean closed;

    private long pos;

    private int relativePos;

    public OpenSearchIterableQueryResultImpl(OpenSearchService searchService, OpenSearchScrollResult scrollResult) {
        if (scrollResult.getQueryBuilder().getSelectFieldsAndTypes().isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.searchService = searchService;
        this.scrollResult = scrollResult;
        converter = new OpenSearchHitConverter(scrollResult.getQueryBuilder().getSelectFieldsAndTypes());
        size = Optional.ofNullable(scrollResult.getOpenSearchResponse().hits().total())
                       .map(TotalHits::value)
                       .orElse(0L);
    }

    @Override
    public void close() {
        if (!closed) {
            searchService.clearScroll(scrollResult);
            closed = true;
            pos = -1;
        }
    }

    @Override
    public boolean mustBeClosed() {
        return true;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public long pos() {
        return pos;
    }

    @Override
    public void skipTo(long pos) {
        checkNotClosed();
        if (pos < this.pos) {
            throw new IllegalArgumentException("Cannot go back in Iterable.");
        } else if (pos > size) {
            pos = size;
        } else {
            while (pos > this.pos) {
                nextHit();
            }
        }
        this.pos = pos;
    }

    @Override
    public Iterator<Map<String, Object>> iterator() {
        return this; // NOSONAR this iterable does not support multiple traversals
    }

    @Override
    public boolean hasNext() {
        checkNotClosed();
        return pos < size;
    }

    @Override
    public Map<String, Object> next() {
        checkNotClosed();
        if (pos == size) {
            throw new NoSuchElementException();
        }
        Hit<DocumentDTO> hit = nextHit();
        return converter.convert(hit);
    }

    private Hit<DocumentDTO> nextHit() {
        List<Hit<DocumentDTO>> hits = scrollResult.getOpenSearchResponse().hits().hits();
        if (relativePos == hits.size()) {
            // Retrieve next scroll
            scrollResult = searchService.scroll(scrollResult);
            relativePos = 0;
        }
        Hit<DocumentDTO> hit = hits.get(relativePos);
        relativePos++;
        pos++;
        return hit;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Query results iterator closed.");
        }
    }

}
